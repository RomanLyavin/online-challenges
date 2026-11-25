package com.onlinechallenges.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeChallengeGroupsTest {

    private val today = LocalDate.of(2026, 11, 25)

    private fun challenge(
        id: String,
        end: String,
        participants: List<String> = listOf("U-ME"),
        start: String = "2026-11-20"
    ) = Challenge(
        id = id,
        title = id,
        description = "",
        startDate = start,
        endDate = end,
        mode = ChallengeMode.DAILY,
        intervalDays = 1,
        goals = emptyList(),
        creatorCode = "U-ME",
        creatorName = "Я",
        participantCodes = participants,
        createdAt = "",
        updatedAt = ""
    )

    @Test
    fun `groups active finished and available challenges`() {
        val groups = groupHomeChallenges(
            challenges = listOf(
                challenge("active", "2026-11-30"),
                challenge("finished", "2026-11-20", start = "2026-11-01"),
                challenge("popular", "2026-11-29", listOf("U-A", "U-B")),
                challenge("small", "2026-11-28", listOf("U-C")),
                challenge("other-finished", "2026-11-21", listOf("U-A"), "2026-11-01")
            ),
            profileCode = "U-ME",
            today = today
        )

        assertEquals(listOf("active"), groups.active.map { it.id })
        assertEquals(listOf("finished"), groups.finished.map { it.id })
        assertEquals(listOf("popular", "small"), groups.available.map { it.id })
    }

    @Test
    fun `sorts by end date in both directions`() {
        val challenges = listOf(
            challenge("later", "2026-11-30"),
            challenge("sooner", "2026-11-27")
        )

        assertEquals(
            listOf("sooner", "later"),
            groupHomeChallenges(challenges, "U-ME", today, earliestFirst = true).active.map { it.id }
        )
        assertEquals(
            listOf("later", "sooner"),
            groupHomeChallenges(challenges, "U-ME", today, earliestFirst = false).active.map { it.id }
        )
    }

    @Test
    fun `without profile there are no personal or available challenges`() {
        val groups = groupHomeChallenges(listOf(challenge("active", "2026-11-30")), null, today)

        assertEquals(emptyList<String>(), groups.active.map { it.id })
        assertEquals(emptyList<String>(), groups.finished.map { it.id })
        assertEquals(emptyList<String>(), groups.available.map { it.id })
    }
}
