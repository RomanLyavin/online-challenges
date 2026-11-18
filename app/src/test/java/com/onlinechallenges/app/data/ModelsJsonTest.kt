package com.onlinechallenges.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsJsonTest {

    @Test
    fun `challenge survives a json roundtrip`() {
        val challenge = Challenge(
            id = "ch-1",
            title = "Утренняя дисциплина",
            description = "Каждый день",
            startDate = "2026-11-01",
            endDate = "2026-11-30",
            mode = ChallengeMode.INTERVAL,
            intervalDays = 3,
            goals = listOf(
                ChallengeGoal("g-1", "steps", "Шаги", "шагов", "👟", 10_000.0, ProofType.PHOTO),
                ChallengeGoal("g-2", "plank", "Планка", "мин", "🧘", 2.0, ProofType.VIDEO)
            ),
            creatorCode = "U-ABC123",
            creatorName = "Анна",
            participantCodes = listOf("U-ABC123", "U-XYZ789"),
            createdAt = "2026-10-20T10:00:00Z",
            updatedAt = "2026-10-21T11:30:00Z"
        )

        val restored = Challenge.fromJson(challenge.toJson())

        assertEquals(challenge.id, restored.id)
        assertEquals(challenge.title, restored.title)
        assertEquals(challenge.mode, restored.mode)
        assertEquals(challenge.intervalDays, restored.intervalDays)
        assertEquals(challenge.participantCodes, restored.participantCodes)
        assertEquals(2, restored.goals.size)
        assertEquals("plank", restored.goals[1].activityId)
        assertEquals(ProofType.VIDEO, restored.goals[1].proofType)
        assertEquals(2.0, restored.goals[1].target, 0.0001)
    }

    @Test
    fun `profile keeps medals and avatar paths after a roundtrip`() {
        val profile = Profile(
            code = "U-ABC123",
            name = "Анна",
            birthDate = "1992-04-12",
            gender = Gender.FEMALE,
            avatarLocalPath = "avatars/local.jpg",
            avatarRepoPath = "avatars/U-ABC123_1.jpg",
            medals = listOf(
                Medal("ch-1", "Челлендж", 1, "2026-11-30"),
                Medal("ch-2", "Второй", 3, "2026-12-15")
            )
        )

        val restored = Profile.fromJson(profile.toJson())

        assertEquals(profile.code, restored.code)
        assertEquals(profile.gender, restored.gender)
        assertEquals(profile.avatarRepoPath, restored.avatarRepoPath)
        assertEquals(2, restored.medals.size)
        assertEquals("🥇", restored.medals[0].emoji)
        assertEquals(3, restored.medals[1].place)
        assertTrue(restored.isComplete)
    }

    @Test
    fun `check-in keeps results and attachments`() {
        val checkIn = CheckIn(
            id = "ci-1",
            challengeId = "ch-1",
            userCode = "U-ABC123",
            date = "2026-11-03",
            results = listOf(
                GoalResult(
                    goalId = "g-1",
                    actual = 12345.0,
                    attachments = listOf(
                        Attachment(
                            id = "a-1",
                            fileName = "steps.jpg",
                            mime = "image/jpeg",
                            type = AttachmentType.PHOTO,
                            sizeBytes = 345_000,
                            localPath = "media/steps.jpg",
                            remotePath = "confirmations/U-ABC123/ch-1/steps.jpg",
                            uploadedAt = "2026-11-03T12:00:00Z"
                        )
                    )
                )
            ),
            note = "Всё по плану",
            createdAt = "2026-11-03T12:00:00Z",
            updatedAt = "2026-11-03T12:05:00Z"
        )

        val restored = CheckIn.fromJson(checkIn.toJson())

        assertEquals(checkIn.id, restored.id)
        assertEquals(checkIn.note, restored.note)
        assertEquals(1, restored.results.size)
        assertEquals(12345.0, restored.results[0].actual, 0.0001)
        assertEquals("confirmations/U-ABC123/ch-1/steps.jpg", restored.results[0].attachments[0].remotePath)
        assertEquals(AttachmentType.PHOTO, restored.results[0].attachments[0].type)
    }

    @Test
    fun `sync json does not leak local paths`() {
        val attachment = Attachment(
            id = "a-1",
            fileName = "proof.mp4",
            mime = "video/mp4",
            type = AttachmentType.VIDEO,
            sizeBytes = 4_000_000,
            localPath = "media/proof.mp4",
            remotePath = "confirmations/U/ch/proof.mp4"
        )

        val json = attachment.toSyncJson()

        assertTrue(json.has("remotePath"))
        assertTrue(!json.has("localPath"))
        assertNull(json.optStringOrNull("localPath"))
    }

    @Test
    fun `peer confirmation survives a json roundtrip`() {
        val confirmation = PeerConfirmation(
            challengeId = "ch-1",
            checkInUserCode = "U-ABC123",
            date = "2026-11-03",
            goalId = "g-1",
            confirmerCode = "U-XYZ789",
            actual = 12345.0,
            createdAt = "2026-11-03T12:00:00Z",
            updatedAt = "2026-11-03T12:05:00Z"
        )

        val restored = PeerConfirmation.fromJson(confirmation.toJson())

        assertEquals(confirmation.key, restored.key)
        assertEquals(confirmation.actual, restored.actual, 0.0001)
        assertFalse(restored.revoked)
        assertFalse(restored.isDemo)

        val syncJson = confirmation.toSyncJson()
        assertFalse(syncJson.has("isDemo"))
        assertEquals("U-XYZ789", syncJson.getString("confirmerCode"))
    }

    @Test
    fun `default catalog contains all required activities`() {
        val activities = defaultActivities()
        val ids = activities.map { it.id }

        assertTrue(
            ids.containsAll(
                listOf(
                    "steps", "running", "sleep", "pushups", "squats", "plank", "abs",
                    "teeth", "homework", "dancing", "football", "skiing", "skating"
                )
            )
        )
        assertTrue(activities.all { it.isDefault })
        assertEquals(ProofType.VIDEO, activities.first { it.id == "plank" }.proofType)
        assertEquals(ProofType.PHOTO, activities.first { it.id == "steps" }.proofType)

        val teeth = activities.first { it.id == "teeth" }
        assertEquals(2.0, teeth.defaultTarget, 0.0001)
        assertEquals(ProofType.PHOTO, teeth.proofType)
        val dancing = activities.first { it.id == "dancing" }
        assertEquals(20.0, dancing.defaultTarget, 0.0001)
        assertEquals(ProofType.VIDEO, dancing.proofType)
        val skiing = activities.first { it.id == "skiing" }
        assertEquals(5.0, skiing.defaultTarget, 0.0001)
        assertEquals(ProofType.PHOTO, skiing.proofType)
    }

    @Test
    fun `challenge without visibility defaults to public`() {
        val json = Challenge(
            id = "ch-1",
            title = "Старый челлендж",
            description = "",
            startDate = "2026-11-01",
            endDate = "2026-11-30",
            mode = ChallengeMode.DAILY,
            intervalDays = 1,
            goals = emptyList(),
            creatorCode = "U-ABC123",
            creatorName = "Анна",
            participantCodes = listOf("U-ABC123"),
            createdAt = "2026-10-20T10:00:00Z",
            updatedAt = "2026-10-21T11:30:00Z"
        ).toJson().apply { remove("visibility") }

        val restored = Challenge.fromJson(json)

        assertEquals(ChallengeVisibility.PUBLIC, restored.visibility)
        assertFalse(restored.isPrivate)
        assertTrue(restored.hasAccess(null))
    }

    @Test
    fun `activity icon paths survive a roundtrip`() {
        val activity = ActivityType(
            id = "teeth",
            name = "Чистка зубов",
            unit = "раз",
            emoji = "🪥",
            defaultTarget = 2.0,
            proofType = ProofType.PHOTO,
            isDefault = true,
            iconPath = "catalog/icons/teeth.jpg"
        )

        val restored = ActivityType.fromJson(activity.toJson())

        assertEquals("catalog/icons/teeth.jpg", restored.iconPath)
        assertEquals("catalog/icons/teeth.jpg", activity.toSyncJson().getString("iconPath"))
        assertFalse(activity.toSyncJson().has("iconLocalPath"))

        val goal = ChallengeGoal(
            "g-1", "teeth", "Чистка зубов", "раз", "🪥", 2.0, ProofType.PHOTO,
            iconPath = "catalog/icons/teeth.jpg"
        )
        assertEquals("catalog/icons/teeth.jpg", ChallengeGoal.fromJson(goal.toJson()).iconPath)
    }

    @Test
    fun `unknown enum values fall back to safe defaults`() {
        assertEquals(Gender.UNSPECIFIED, Gender.parse("OTHER"))
        assertEquals(ProofType.PHOTO, ProofType.parse(null))
        assertEquals(ChallengeMode.DAILY, ChallengeMode.parse("UNKNOWN"))
    }
}
