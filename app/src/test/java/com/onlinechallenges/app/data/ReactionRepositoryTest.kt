package com.onlinechallenges.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionRepositoryTest {
    private val me = "11111111-1111-1111-1111-111111111111"
    private val other = "22222222-2222-2222-2222-222222222222"
    private val key = "challenge-1|USER-1|2026-11-27"

    @Test
    fun emptyResponseHasNoCounts() {
        val summary = ReactionRepository.parse("[]", me)
        assertTrue(summary.counts.isEmpty())
        assertTrue(summary.mine.isEmpty())
    }

    @Test
    fun countsAreGroupedByCheckInAndType() {
        val body = """
            [
              {"checkin_id":"$key","user_id":"$me","reaction":"fire"},
              {"checkin_id":"$key","user_id":"$other","reaction":"fire"},
              {"checkin_id":"$key","user_id":"$other","reaction":"heart"},
              {"checkin_id":"other|USER-2|2026-11-27","user_id":"$other","reaction":"like"},
              {"checkin_id":"$key","user_id":"$other","reaction":"unknown"}
            ]
        """.trimIndent()

        val summary = ReactionRepository.parse(body, me)

        assertEquals(2, summary.counts[key]?.get(ReactionType.FIRE))
        assertEquals(1, summary.counts[key]?.get(ReactionType.HEART))
        assertEquals(ReactionType.FIRE, summary.mine[key])
        assertEquals(1, summary.counts["other|USER-2|2026-11-27"]?.get(ReactionType.LIKE))
    }

    @Test
    fun malformedResponseIsIgnored() {
        val summary = ReactionRepository.parse("not json", me)
        assertTrue(summary.counts.isEmpty())
    }

    @Test
    fun sameReactionIsRemovedAndAnotherReplacesIt() {
        assertTrue(ReactionRepository.shouldRemove(ReactionType.LIKE, ReactionType.LIKE))
        assertFalse(ReactionRepository.shouldRemove(ReactionType.LIKE, ReactionType.FIRE))
        assertFalse(ReactionRepository.shouldRemove(null, ReactionType.FIRE))
    }

    @Test
    fun checkInIdMatchesWebFormat() {
        assertEquals(
            "challenge-1|USER-1|2026-11-27",
            ReactionRepository.checkInId("challenge-1", "USER-1", "2026-11-27")
        )
    }
}
