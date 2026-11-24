package com.onlinechallenges.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityMergeTest {

    private fun steps(
        updatedAt: String,
        iconPath: String? = null,
        iconLocalPath: String? = null
    ) = ActivityType(
        id = "steps",
        name = "Шаги",
        unit = "шагов",
        emoji = "👟",
        defaultTarget = 10_000.0,
        proofType = ProofType.PHOTO,
        updatedAt = updatedAt,
        isDefault = true,
        iconPath = iconPath,
        iconLocalPath = iconLocalPath
    )

    private fun yoga(
        updatedAt: String,
        iconPath: String? = null,
        iconLocalPath: String? = null
    ) = ActivityType(
        id = "custom-yoga",
        name = "Йога",
        unit = "мин",
        emoji = "🧘",
        defaultTarget = 15.0,
        proofType = ProofType.VIDEO,
        createdByCode = "U-TEST",
        updatedAt = updatedAt,
        isDefault = false,
        iconPath = iconPath,
        iconLocalPath = iconLocalPath
    )

    @Test
    fun `reset of default icon wins over older remote icon`() {
        val merged = mergeActivities(
            local = listOf(steps("2026-11-24T10:00:00Z")),
            remote = listOf(steps("2026-11-20T10:00:00Z", iconPath = "catalog/icons/steps.jpg"))
        ).first { it.id == "steps" }

        assertNull(merged.iconPath)
        assertNull(merged.iconLocalPath)
        assertEquals("👟", merged.emoji)
        assertEquals("2026-11-24T10:00:00Z", merged.updatedAt)
    }

    @Test
    fun `reset propagates to device with older local icon`() {
        val merged = mergeActivities(
            local = listOf(steps("2026-11-20T10:00:00Z", iconPath = "catalog/icons/steps.jpg")),
            remote = listOf(steps("2026-11-24T10:00:00Z"))
        ).first { it.id == "steps" }

        assertNull(merged.iconPath)
        assertNull(merged.iconLocalPath)
    }

    @Test
    fun `newer remote icon wins over local`() {
        val merged = mergeActivities(
            local = listOf(steps("2026-11-20T10:00:00Z", iconPath = "catalog/icons/old.jpg")),
            remote = listOf(steps("2026-11-24T10:00:00Z", iconPath = "catalog/icons/new.jpg"))
        ).first { it.id == "steps" }

        assertEquals("catalog/icons/new.jpg", merged.iconPath)
        assertNull(merged.iconLocalPath)
    }

    @Test
    fun `local icon is kept until it is uploaded`() {
        val merged = mergeActivities(
            local = listOf(steps("2026-11-24T10:00:00Z", iconLocalPath = "/data/icons/steps.jpg")),
            remote = listOf(steps("2026-11-20T10:00:00Z"))
        ).first { it.id == "steps" }

        assertNull(merged.iconPath)
        assertEquals("/data/icons/steps.jpg", merged.iconLocalPath)
    }

    @Test
    fun `reset of custom activity icon also propagates`() {
        val merged = mergeActivities(
            local = listOf(yoga("2026-11-24T10:00:00Z")),
            remote = listOf(yoga("2026-11-20T10:00:00Z", iconPath = "catalog/icons/yoga.jpg"))
        ).first { it.id == "custom-yoga" }

        assertNull(merged.iconPath)
        assertEquals("🧘", merged.emoji)
    }
}
