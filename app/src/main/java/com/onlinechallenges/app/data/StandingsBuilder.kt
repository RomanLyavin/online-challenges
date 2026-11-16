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
                .map { ChallengeEngine.scoreParticipant(it, profile.code, checkIns, profiles, today, includeFuture = false) }
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
                    challenge, checkIns, profiles, today, includeFuture = false
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
                        results.put(
                            JSONObject()
                                .put("goal", goal.name)
                                .put("emoji", goal.emoji)
                                .put("actual", result.actual)
                                .put("target", goal.target)
                                .put("unit", goal.unit)
                                .put("completed", result.actual >= goal.target && proofs.isNotEmpty())
                                .put("proofs", JSONArray(proofs.mapNotNull { attachment ->
                                    attachment.remotePath?.let { path ->
                                        JSONObject()
                                            .put("path", path)
                                            .put("url", config.pagesUrl + path)
                                            .put("type", attachment.type.name)
                                    }
                                }))
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

        val confirmationCount = checkIns.sumOf { checkIn ->
            checkIn.results.sumOf { result -> result.attachments.count { !it.remotePath.isNullOrBlank() } }
        }

        return JSONObject()
            .put("version", 1)
            .put("generatedAt", isoNow())
            .put("app", "Онлайн челленджи")
            .put("pagesUrl", config.pagesUrl)
            .put("repoUrl", config.repoUrl)
            .put("scoring", "1 балл за каждую полностью выполненную цель. Частичное выполнение баллов не даёт.")
            .put("totals", JSONObject()
                .put("users", profiles.size)
                .put("challenges", challenges.size)
                .put("confirmations", confirmationCount))
            .put("users", JSONArray(userTotals))
            .put("challenges", JSONArray(challengeJson))
    }

    fun manifest(
        data: AppData,
        config: GitHubConfig,
        today: LocalDate = LocalDate.now()
    ): JSONObject {
        val challenges = data.challenges.filterNot { it.isDemo }.associateBy { it.id }
        val profiles = data.profiles.filterNot { it.isDemo }.associateBy { it.code }
        val files = JSONArray()
        data.checkIns.filterNot { it.isDemo }
            .sortedByDescending { it.date }
            .forEach { checkIn ->
                val challenge = challenges[checkIn.challengeId] ?: return@forEach
                checkIn.results.forEach { result ->
                    val goal = challenge.goals.firstOrNull { it.id == result.goalId } ?: return@forEach
                    result.attachments.filter { !it.remotePath.isNullOrBlank() }.forEach { attachment ->
                        val path = attachment.remotePath ?: return@forEach
                        files.put(
                            JSONObject()
                                .put("path", path)
                                .put("url", config.pagesUrl + path)
                                .put("rawUrl", "https://raw.githubusercontent.com/${config.owner}/${config.repo}/${config.branch}/$path")
                                .put("userCode", checkIn.userCode)
                                .put("userName", profiles[checkIn.userCode]?.name ?: "Участник")
                                .put("challengeId", checkIn.challengeId)
                                .put("challengeTitle", challenge.title)
                                .put("date", checkIn.date)
                                .put("goalId", goal.id)
                                .put("goalName", goal.name)
                                .put("type", attachment.type.name)
                                .put("mime", attachment.mime)
                                .put("size", attachment.sizeBytes)
                                .put("uploadedAt", attachment.uploadedAt ?: "")
                        )
                    }
                }
            }
        return JSONObject()
            .put("version", 1)
            .put("generatedAt", isoNow())
            .put("total", files.length())
            .put("files", files)
    }

    private fun avatarUrl(config: GitHubConfig, profile: Profile?): String {
        if (profile == null) return ""
        val path = profile.avatarRepoPath ?: return ""
        return config.pagesUrl + path
    }
}
