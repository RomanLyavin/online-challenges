package com.onlinechallenges.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object StandingsBuilder {

    fun build(
        data: AppData,
        config: GitHubConfig,
        today: LocalDate = LocalDate.now()
    ): JSONObject {
        val profiles = data.profiles.filterNot { it.isDemo }
        val challenges = data.challenges.filterNot { it.isDemo }
        val checkIns = data.checkIns.filterNot { it.isDemo }
        val profileMap = profiles.associateBy { it.code }

        val userTotals = profiles.associate { profile ->
            val scores = challenges.filter { it.participantCodes.contains(profile.code) }
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
            profile.code to JSONObject()
                .put("code", profile.code)
                .put("name", profile.name)
                .put("avatarUrl", avatarUrl(config, profile))
                .put("points", scores.sumOf { it.points })
                .put("maxPoints", scores.sumOf { it.maxPoints })
                .put("challenges", scores.size)
                .put("gold", profile.medals.count { it.place == 1 })
                .put("silver", profile.medals.count { it.place == 2 })
                .put("bronze", profile.medals.count { it.place == 3 })
        }.values.sortedWith(
            compareByDescending<JSONObject> {
                it.optInt("gold") * 10_000 + it.optInt("silver") * 100 + it.optInt("bronze")
            }.thenByDescending { it.optInt("points") }.thenBy { it.optString("name") }
        )

        val challengeJson = challenges
            .sortedWith(compareBy<Challenge> { it.status(today).ordinal }.thenByDescending { it.endDate })
            .map { challenge ->
                val scores = ChallengeEngine.scoreChallenge(
                    challenge,
                    checkIns,
                    profiles,
                    today,
                    includeFuture = false,
                    confirmations = data.confirmations
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
                    val profile = profileMap[checkIn.userCode]
                    val results = JSONArray()
                    checkIn.results.forEach { result ->
                        val goal = challenge.goals.firstOrNull { it.id == result.goalId } ?: return@forEach
                        val proofs = ChallengeEngine.validProofs(result.attachments, goal.proofType)
                        val confirmations = ChallengeEngine.activeConfirmations(
                            challenge,
                            checkIn,
                            goal.id,
                            result.actual,
                            data.confirmations
                        )
                        val confirmedBy = confirmations.mapNotNull { confirmation ->
                            profileMap[confirmation.confirmerCode]?.name
                                ?: confirmation.confirmerCode.takeIf { it.isNotBlank() }
                        }
                        results.put(
                            JSONObject()
                                .put("goal", goal.name)
                                .put("emoji", goal.emoji)
                                .put("actual", result.actual)
                                .put("target", goal.target)
                                .put("unit", goal.unit)
                                .put("completed", result.actual >= goal.target && (proofs.isNotEmpty() || confirmations.isNotEmpty()))
                                .put("confirmedBy", JSONArray(confirmedBy))
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
                JSONObject()
                    .put("id", challenge.id)
                    .put("title", challenge.title)
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

        val confirmationCount = data.confirmations
            .filterNot { it.isDemo }
            .filter { confirmation ->
                val challenge = challenges.firstOrNull { it.id == confirmation.challengeId } ?: return@filter false
                !confirmation.revoked &&
                    challenge.participantCodes.contains(confirmation.confirmerCode) &&
                    challenge.participantCodes.contains(confirmation.checkInUserCode) &&
                    confirmation.confirmerCode != confirmation.checkInUserCode
            }
            .distinctBy { it.key }
            .size

        return JSONObject()
            .put("version", 1)
            .put("generatedAt", isoNow())
            .put("app", "Онлайн челленджи")
            .put("pagesUrl", config.pagesUrl)
            .put("repoUrl", config.repoUrl)
            .put("scoring", "1 балл за каждую полностью выполненную цель с фото, видео или подтверждением другого участника. Частичное выполнение баллов не даёт.")
            .put("totals", JSONObject()
                .put("users", profiles.size)
                .put("challenges", challenges.size)
                .put("goals", challenges.sumOf { it.goals.size })
                .put("confirmations", confirmationCount))
            .put("users", JSONArray(userTotals))
            .put("challenges", JSONArray(challengeJson))
    }

    private fun avatarUrl(config: GitHubConfig, profile: Profile?): String {
        if (profile == null) return ""
        val path = profile.avatarRepoPath ?: return ""
        return config.pagesUrl + path
    }
}
