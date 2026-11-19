package com.onlinechallenges.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object StandingsBuilder {

    fun build(
        data: AppData,
        config: GitHubConfig,
        today: LocalDate = LocalDate.now(),
        previous: JSONObject? = null
    ): JSONObject {
        val profiles = data.profiles.filterNot { it.isDemo }
        val deletedIds = data.deletedChallengeIds.toSet()
        val challenges = normalizeChallenges(data.challenges)
            .filterNot { it.isDemo || it.id in deletedIds }
        val checkIns = data.checkIns.filterNot { it.isDemo || it.challengeId in deletedIds }
        val publicChallenges = challenges.filter { it.visibility != ChallengeVisibility.PRIVATE }
        val privateChallenges = challenges.filter { it.visibility == ChallengeVisibility.PRIVATE }

        val privateEntries = privateEntries(
            privateChallenges,
            checkIns,
            profiles,
            data.confirmations,
            today,
            previous,
            deletedIds
        )
        val privateChallengeIds = privateChallenges.map { it.id }.toSet()

        val userTotals = profiles.map { profile ->
            // Медали приватных челленджей не публикуются: в общем рейтинге
            // учитываются только медали общих челленджей.
            val publicMedals = profile.medals.filterNot {
                it.challengeId in privateChallengeIds || it.challengeId in deletedIds
            }
            val scores = publicChallenges.filter { it.participantCodes.contains(profile.code) }
                .map {
                    ChallengeEngine.scoreParticipant(
                        it,
                        profile.code,
                        checkIns,
                        profiles,
                        today,
                        includeFuture = false,
                        confirmations = data.confirmations
                    )
                }
            var points = scores.sumOf { it.points }
            var maxPoints = scores.sumOf { it.maxPoints }
            var challengeCount = scores.size
            for (entry in privateEntries.values) {
                val participant = entry.optJSONArray("participants")?.toList { it }
                    ?.firstOrNull { it.optString("code") == profile.code }
                    ?: continue
                points += participant.optInt("points", 0)
                maxPoints += participant.optInt("maxPoints", 0)
                challengeCount++
            }
            JSONObject()
                .put("code", profile.code)
                .put("name", profile.name)
                .put("avatarUrl", avatarUrl(config, profile))
                .put("points", points)
                .put("maxPoints", maxPoints)
                .put("challenges", challengeCount)
                .put("gold", publicMedals.count { it.place == 1 })
                .put("silver", publicMedals.count { it.place == 2 })
                .put("bronze", publicMedals.count { it.place == 3 })
        }.sortedWith(
            compareByDescending<JSONObject> {
                it.optInt("gold") * 10_000 + it.optInt("silver") * 100 + it.optInt("bronze")
            }.thenByDescending { it.optInt("points") }.thenBy { it.optString("name") }
        )

        val publicJson = publicChallenges
            .sortedWith(compareBy<Challenge> { it.status(today).ordinal }.thenByDescending { it.endDate })
            .map { challenge -> publicChallengeJson(challenge, checkIns, profiles, data.confirmations, config, today) }

        val privateJson = privateEntries.values.sortedBy { it.optString("title") }

        val publicGoalCount = publicChallenges.sumOf { it.goals.size }

        return JSONObject()
            .put("version", 1)
            .put("generatedAt", isoNow())
            .put("app", "Онлайн челленджи")
            .put("pagesUrl", config.pagesUrl)
            .put("repoUrl", config.repoUrl)
            .put("scoring", "1 балл за каждую полностью выполненную цель с фото, видео или подтверждением другого участника. Частичное выполнение баллов не даёт.")
            .put("totals", JSONObject()
                .put("users", profiles.size)
                .put("challenges", publicJson.size + privateJson.size)
                .put("goals", publicGoalCount))
            .put("users", JSONArray(userTotals))
            .put("challenges", JSONArray(publicJson + privateJson))
    }

    private fun publicChallengeJson(
        challenge: Challenge,
        checkIns: List<CheckIn>,
        profiles: List<Profile>,
        confirmations: List<PeerConfirmation>,
        config: GitHubConfig,
        today: LocalDate
    ): JSONObject {
        val scores = ChallengeEngine.scoreChallenge(
            challenge,
            checkIns,
            profiles,
            today,
            includeFuture = false,
            confirmations = confirmations
        )
        val participants = JSONArray()
        scores.forEach { score ->
            participants.put(
                JSONObject()
                    .put("code", score.code)
                    .put("name", score.name)
                    .put("avatarUrl", avatarUrl(config, score.profile))
                    .put("points", score.points)
                    .put("maxPoints", score.maxPoints)
                    .put("progressPercent", score.progressPercent)
                    .put("place", score.place)
                    .put("medal", score.medal?.emoji ?: "")
                    .put("lastActivity", score.lastActivity?.toString() ?: "")
            )
        }
        val podium = JSONArray()
        scores.filter { it.place <= 3 }.forEach { score ->
            podium.put(
                JSONObject()
                    .put("place", score.place)
                    .put("medal", score.medal?.emoji ?: "")
                    .put("code", score.code)
                    .put("name", score.name)
                    .put("points", score.points)
            )
        }
        val recent = JSONArray()
        ChallengeEngine.recentCheckIns(challenge, checkIns, 10).forEach { checkIn ->
            val profile = profiles.firstOrNull { it.code == checkIn.userCode }
            val results = JSONArray()
            checkIn.results.forEach { result ->
                val goal = challenge.goals.firstOrNull { it.id == result.goalId } ?: return@forEach
                val proofs = ChallengeEngine.validProofs(result.attachments, goal.proofType)
                val peerConfirmations = ChallengeEngine.activeConfirmations(
                    challenge,
                    checkIn,
                    goal.id,
                    result.actual,
                    confirmations
                )
                results.put(
                    JSONObject()
                        .put("goal", goal.name)
                        .put("emoji", goal.emoji)
                        .put("actual", result.actual)
                        .put("target", goal.target)
                        .put("unit", goal.unit)
                        .put("completed", result.actual >= goal.target &&
                            (proofs.isNotEmpty() || peerConfirmations.isNotEmpty()))
                )
            }
            recent.put(
                JSONObject()
                    .put("code", checkIn.userCode)
                    .put("name", profile?.name ?: "Участник")
                    .put("date", checkIn.date)
                    .put("results", results)
            )
        }
        return JSONObject()
            .put("id", challenge.id)
            .put("title", challenge.title)
            .put("visibility", ChallengeVisibility.PUBLIC.name)
            .put("description", challenge.description)
            .put("startDate", challenge.startDate)
            .put("endDate", challenge.endDate)
            .put("mode", challenge.mode.name)
            .put("modeLabel", challenge.mode.label)
            .put("intervalDays", challenge.intervalDays)
            .put("status", challenge.status(today).name)
            .put("statusLabel", challenge.status(today).label)
            .put("goals", JSONArray(challenge.goals.map { goal ->
                JSONObject()
                    .put("name", goal.name)
                    .put("emoji", goal.emoji)
                    .put("target", goal.target)
                    .put("unit", goal.unit)
                    .put("proofType", goal.proofType.name)
            }))
            .put("participantCount", challenge.participantCodes.size)
            .put("participants", participants)
            .put("podium", podium)
            .put("recent", recent)
    }

    /**
     * Для приватных челленджей публикуются только имена, очки и места.
     * Цели, результаты, медиа и подтверждения на сайт не попадают.
     *
     * Если на устройстве нет ключа, берётся предыдущая опубликованная
     * агрегированная запись: так обычные устройства не затирают приватные очки.
     */
    private fun privateEntries(
        privateChallenges: List<Challenge>,
        checkIns: List<CheckIn>,
        profiles: List<Profile>,
        confirmations: List<PeerConfirmation>,
        today: LocalDate,
        previous: JSONObject?,
        deletedIds: Set<String>
    ): Map<String, JSONObject> {
        val preserved = LinkedHashMap<String, JSONObject>()
        previous?.optJSONArray("challenges")?.toList { it }?.forEach { entry ->
            val visibility = entry.optString("visibility")
            val id = entry.optString("id")
            if (visibility.equals(ChallengeVisibility.PRIVATE.name, ignoreCase = true) &&
                id !in deletedIds
            ) {
                preserved[id] = sanitizePrivateEntry(entry)
            }
        }

        val result = LinkedHashMap<String, JSONObject>()
        for (challenge in privateChallenges) {
            val fresh = if (!challenge.privateKey.isNullOrBlank() && challenge.goals.isNotEmpty()) {
                privateChallengeJson(challenge, checkIns, profiles, confirmations, today)
            } else null
            val entry = fresh ?: preserved[challenge.id] ?: continue
            result[challenge.id] = entry
        }
        return result
    }

    /** Оставляет в сохранённой записи только разрешённые публичные поля. */
    private fun sanitizePrivateEntry(entry: JSONObject): JSONObject {
        val participants = JSONArray()
        entry.optJSONArray("participants")?.toList { it }?.forEach { participant ->
            participants.put(
                JSONObject()
                    .put("code", participant.optString("code"))
                    .put("name", participant.optString("name", "Участник"))
                    .put("points", participant.optInt("points", 0))
                    .put("maxPoints", participant.optInt("maxPoints", 0))
                    .put("place", participant.optInt("place", 0))
                    .put("medal", participant.optString("medal"))
            )
        }
        return JSONObject()
            .put("id", entry.optString("id"))
            .put("title", entry.optString("title", "Приватный челлендж"))
            .put("visibility", ChallengeVisibility.PRIVATE.name)
            .put("status", entry.optString("status"))
            .put("statusLabel", entry.optString("statusLabel"))
            .put("participants", participants)
    }

    private fun privateChallengeJson(
        challenge: Challenge,
        checkIns: List<CheckIn>,
        profiles: List<Profile>,
        confirmations: List<PeerConfirmation>,
        today: LocalDate
    ): JSONObject {
        val scores = ChallengeEngine.scoreChallenge(
            challenge,
            checkIns,
            profiles,
            today,
            includeFuture = false,
            confirmations = confirmations
        )
        val participants = JSONArray()
        scores.forEach { score ->
            participants.put(
                JSONObject()
                    .put("code", score.code)
                    .put("name", score.name)
                    .put("points", score.points)
                    .put("maxPoints", score.maxPoints)
                    .put("place", score.place)
                    .put("medal", score.medal?.emoji ?: "")
            )
        }
        // Только имена, очки и места. Статус нужен публичному фильтру,
        // но цели, даты, результаты, медиа и подтверждения не публикуются.
        return JSONObject()
            .put("id", challenge.id)
            .put("title", challenge.title)
            .put("visibility", ChallengeVisibility.PRIVATE.name)
            .put("status", challenge.status(today).name)
            .put("statusLabel", challenge.status(today).label)
            .put("participants", participants)
    }

    private fun avatarUrl(config: GitHubConfig, profile: Profile?): String {
        if (profile == null) return ""
        val path = profile.avatarRepoPath ?: return ""
        return config.pagesUrl + path
    }
}
