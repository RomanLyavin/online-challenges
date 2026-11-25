package com.onlinechallenges.app.data

import java.time.LocalDate

enum class HomeChallengeFilter { ALL, ACTIVE, FINISHED }

data class HomeChallengeGroups(
    val active: List<Challenge>,
    val finished: List<Challenge>,
    val available: List<Challenge>
)

/** Разделяет челленджи для главного экрана и сортирует их по дате окончания. */
fun groupHomeChallenges(
    challenges: List<Challenge>,
    profileCode: String?,
    today: LocalDate = LocalDate.now(),
    earliestFirst: Boolean = true
): HomeChallengeGroups {
    val endDateOrder = if (earliestFirst) {
        compareBy<Challenge> { it.end }
    } else {
        compareByDescending<Challenge> { it.end }
    }
    val mine = challenges
        .filter { challenge -> profileCode != null && challenge.participantCodes.contains(profileCode) }
        .sortedWith(
            compareBy<Challenge> { it.status(today) == ChallengeStatus.FINISHED }.then(endDateOrder)
        )
    return HomeChallengeGroups(
        active = mine.filter { it.status(today) == ChallengeStatus.ACTIVE },
        finished = mine.filter { it.status(today) == ChallengeStatus.FINISHED },
        available = challenges
            .filter { challenge ->
                profileCode != null && !challenge.participantCodes.contains(profileCode) &&
                    challenge.status(today) != ChallengeStatus.FINISHED
            }
            .sortedByDescending { it.participantCodes.size }
    )
}
