package com.onlinechallenges.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeCardPresentationTest {

    @Test
    fun `daily challenges never show score badge`() {
        assertFalse(shouldShowChallengeCardScore(ChallengeMode.DAILY, 25))
        assertFalse(shouldShowChallengeCardScore(ChallengeMode.DAILY, 0))
    }

    @Test
    fun `interval and total challenges show positive score`() {
        assertTrue(shouldShowChallengeCardScore(ChallengeMode.INTERVAL, 10))
        assertTrue(shouldShowChallengeCardScore(ChallengeMode.TOTAL, 1))
    }

    @Test
    fun `zero or negative score is always hidden`() {
        assertFalse(shouldShowChallengeCardScore(ChallengeMode.INTERVAL, 0))
        assertFalse(shouldShowChallengeCardScore(ChallengeMode.TOTAL, -1))
    }
}
