package com.onlinechallenges.app.data

import java.time.LocalDate

object Awards {
    fun awardFinishedChallenges(repo: AppRepository, today: LocalDate = LocalDate.now()): Boolean {
        val current = repo.snapshot()
        val updated = apply(current, today)
        if (updated == current) return false
        repo.replace(updated)
        return true
    }

    fun apply(data: AppData, today: LocalDate = LocalDate.now()): AppData {
        var profiles = data.profiles
        var changed = false
        for (challenge in data.challenges) {
            if (!today.isAfter(challenge.end)) continue
            val updates = ChallengeEngine.awardMedals(
                challenge,
                data.checkIns,
                profiles,
                today,
                confirmations = data.confirmations
            )
            if (updates.isNotEmpty()) {
                val byCode = updates.associateBy { it.code }
                profiles = profiles.map { byCode[it.code] ?: it }
                changed = true
            }
        }
        return if (changed) data.copy(profiles = profiles) else data
    }
}
