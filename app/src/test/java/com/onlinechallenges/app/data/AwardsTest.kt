package com.onlinechallenges.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AwardsTest {

    private val today = LocalDate.of(2026, 11, 19)

    private fun challenge(
        id: String,
        endDate: String,
        participants: List<String> = listOf("A", "B")
    ): Challenge = Challenge(
        id = id,
        title = "Челлендж",
        description = "",
        startDate = "2026-11-01",
        endDate = endDate,
        mode = ChallengeMode.DAILY,
        intervalDays = 1,
        goals = emptyList(),
        creatorCode = "A",
        creatorName = "Анна",
        participantCodes = participants,
        createdAt = "2026-11-01T10:00:00Z",
        updatedAt = "2026-11-01T10:00:00Z"
    )

    private fun profile(medal: Medal): Profile = Profile(
        code = "A",
        name = "Анна",
        medals = listOf(medal)
    )

    @Test
    fun `medals of a deleted challenge are removed`() {
        val medal = Medal("ch-deleted", "Удалённый", 1, "2026-11-10T10:00:00Z")
        val data = AppData(
            profiles = listOf(profile(medal)),
            challenges = listOf(challenge("ch-other", "2026-11-10")),
            deletedChallengeIds = listOf("ch-deleted")
        )

        val result = Awards.apply(data, today)

        assertTrue(result.profiles.single().medals.none { it.challengeId == "ch-deleted" })
    }

    @Test
    fun `medals of an extended challenge are removed`() {
        val medal = Medal("ch-extended", "Продлённый", 1, "2026-11-10T10:00:00Z")
        val data = AppData(
            profiles = listOf(profile(medal)),
            challenges = listOf(challenge("ch-extended", "2026-11-30"))
        )

        val result = Awards.apply(data, today)

        assertTrue(result.profiles.single().medals.isEmpty())
    }

    @Test
    fun `medals of a finished challenge are kept`() {
        val medal = Medal("ch-finished", "Завершённый", 1, "2026-11-10T10:00:00Z")
        val data = AppData(
            profiles = listOf(profile(medal)),
            challenges = listOf(challenge("ch-finished", "2026-11-10"))
        )

        val result = Awards.apply(data, today)

        assertEquals(listOf(medal), result.profiles.single().medals)
    }

    @Test
    fun `old medals of a single participant challenge are removed`() {
        val medal = Medal("ch-solo", "Одиночный", 1, "2026-11-10T10:00:00Z")
        val data = AppData(
            profiles = listOf(profile(medal)),
            challenges = listOf(challenge("ch-solo", "2026-11-10", participants = listOf("A")))
        )

        val result = Awards.apply(data, today)

        assertTrue(result.profiles.single().medals.isEmpty())
    }

    @Test
    fun `medals are not awarded for a finished challenge with one participant`() {
        val data = AppData(
            profiles = listOf(Profile(code = "A", name = "Анна")),
            challenges = listOf(challenge("ch-solo", "2026-11-10", participants = listOf("A")))
        )

        val result = Awards.apply(data, today)

        assertTrue(result.profiles.single().medals.isEmpty())
    }
}
