package com.onlinechallenges.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeCardPresentationTest {

    @Test
    fun `daily challenges hide only the mode badge`() {
        assertFalse(shouldShowModeBadge(ChallengeMode.DAILY))
        assertTrue(shouldShowModeBadge(ChallengeMode.INTERVAL))
        assertTrue(shouldShowModeBadge(ChallengeMode.TOTAL))
    }

    @Test
    fun `daily challenges still show positive score`() {
        assertTrue(shouldShowChallengeCardScore(25))
    }

    @Test
    fun `score badge is shown for all modes when points are positive`() {
        assertTrue(shouldShowChallengeCardScore(10))
        assertTrue(shouldShowChallengeCardScore(1))
    }

    @Test
    fun `zero or negative score is always hidden`() {
        assertFalse(shouldShowChallengeCardScore(0))
        assertFalse(shouldShowChallengeCardScore(-1))
    }
}
