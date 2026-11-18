package com.onlinechallenges.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrivacyTest {

    private val config = GitHubConfig(owner = "owner", repo = "repo", token = "token")
    private val today = LocalDate.of(2026, 11, 10)

    private fun challenge(
        id: String = "ch-private",
        visibility: ChallengeVisibility = ChallengeVisibility.PRIVATE,
        privateKey: String? = CryptoBox.newKey(),
        goals: List<ChallengeGoal> = listOf(
            ChallengeGoal("goal-steps", "steps", "Шаги", "шагов", "👟", 1_000.0, ProofType.PHOTO)
        ),
        participants: List<String> = listOf("U-A", "U-B")
    ): Challenge = Challenge(
        id = id,
        title = "Секретный челлендж",
        description = "Только для своих",
        startDate = "2026-11-01",
        endDate = "2026-11-30",
        mode = ChallengeMode.DAILY,
        intervalDays = 1,
        goals = goals,
        creatorCode = participants.first(),
        creatorName = "Анна",
        participantCodes = participants,
        createdAt = "2026-10-25T10:00:00Z",
        updatedAt = "2026-11-05T10:00:00Z",
        visibility = visibility,
        privateKey = privateKey
    )

    private fun profiles(): List<Profile> = listOf(
        Profile("U-A", "Анна", birthDate = "1990-01-01", gender = Gender.FEMALE),
        Profile("U-B", "Борис", birthDate = "1991-02-02", gender = Gender.MALE)
    )

    private fun checkIn(
        challengeId: String = "ch-private",
        attachmentPath: String = "data/private/ch-private/media/att-1.enc"
    ): CheckIn = CheckIn(
        id = "ci-1",
        challengeId = challengeId,
        userCode = "U-A",
        date = "2026-11-05",
        results = listOf(
            GoalResult(
                goalId = "goal-steps",
                actual = 1_500.0,
                attachments = listOf(
                    Attachment(
                        id = "att-1",
                        fileName = "steps.jpg",
                        mime = "image/jpeg",
                        type = AttachmentType.PHOTO,
                        sizeBytes = 120_000,
                        remotePath = attachmentPath
                    )
                )
            )
        ),
        createdAt = "2026-11-05T11:00:00Z",
        updatedAt = "2026-11-05T11:00:00Z"
    )

    @Test
    fun `crypto box encrypts and rejects a wrong key`() {
        val key = CryptoBox.newKey()
        val other = CryptoBox.newKey()
        assertTrue(CryptoBox.isValidKey(key))

        val payload = CryptoBox.encryptText("секрет", key)
        assertEquals("секрет", CryptoBox.decryptText(payload, key))
        assertNull(CryptoBox.decryptText(payload, other))
        assertFalse(String(payload, Charsets.ISO_8859_1).contains("секрет"))
    }

    @Test
    fun `invite code carries the challenge id and key`() {
        val key = CryptoBox.newKey()
        val raw = InviteCodes.create("ch-1", key)
        val parsed = InviteCodes.parse("  $raw  ")

        assertNotNull(parsed)
        assertEquals("ch-1", parsed!!.challengeId)
        assertEquals(key, parsed.key)
        assertNull(InviteCodes.parse("OC1.ch-1.not-a-key"))
        assertNull(InviteCodes.parse("nonsense"))
    }

    @Test
    fun `private stub hides goals participants and the key`() {
        val challenge = challenge()
        val stub = challenge.toSyncJson()

        assertEquals("PRIVATE", stub.getString("visibility"))
        assertFalse(stub.has("goals"))
        assertFalse(stub.has("participantCodes"))
        assertFalse(stub.has("description"))
        assertFalse(stub.has("privateKey"))
        assertFalse(stub.has("creatorCode"))
        assertFalse(stub.has("creatorName"))
        assertEquals("Секретный челлендж", stub.getString("title"))

        val bundle = challenge.toBundleJson()
        assertEquals(1, bundle.getJSONArray("goals").length())
        assertFalse(bundle.has("privateKey"))
        assertFalse(bundle.has("isDemo"))
    }

    @Test
    fun `private access requires participation`() {
        val challenge = challenge()
        assertTrue(challenge.hasAccess("U-A"))
        assertTrue(challenge.hasAccess("U-B"))
        assertFalse(challenge.hasAccess("U-C"))
        assertFalse(challenge.hasAccess(null))

        val public = challenge.copy(visibility = ChallengeVisibility.PUBLIC, privateKey = null)
        assertTrue(public.hasAccess("U-C"))
        assertTrue(public.hasAccess(null))
        assertNull(public.inviteCode())
    }

    @Test
    fun `private bundle roundtrips and uses private repository paths`() {
        val challenge = challenge()
        val bundle = PrivateBundle(challenge, listOf(checkIn()), emptyList())

        val restored = PrivateBundle.fromJson(bundle.toJson().toString())

        assertNotNull(restored)
        assertEquals(challenge.id, restored!!.challenge.id)
        assertEquals(ChallengeVisibility.PRIVATE, restored.challenge.visibility)
        assertEquals(1, restored.checkIns.size)
        assertEquals("goal-steps", restored.checkIns[0].results[0].goalId)

        assertEquals("data/private/ch-private.enc", SyncManager.privateBundlePath(challenge.id))
        assertEquals(
            "data/private/ch-private/media/att-1.enc",
            SyncManager.privateMediaPath(challenge.id, "att-1")
        )
    }

    @Test
    fun `private standings publish only names points and places`() {
        val challenge = challenge()
        val data = AppData(
            profiles = profiles(),
            challenges = listOf(challenge),
            checkIns = listOf(checkIn())
        )

        val json = StandingsBuilder.build(data, config, today)
        val entries = json.getJSONArray("challenges")
        assertEquals(1, entries.length())
        val entry = entries.getJSONObject(0)

        assertEquals(
            setOf("id", "title", "visibility", "status", "statusLabel", "participants"),
            entry.keys().asSequence().toSet()
        )
        val participant = entry.getJSONArray("participants").getJSONObject(0)
        assertEquals(
            setOf("code", "name", "points", "maxPoints", "place", "medal"),
            participant.keys().asSequence().toSet()
        )
        assertEquals("Анна", participant.getString("name"))
        assertEquals(1, participant.getInt("points"))
        assertFalse(entry.has("goals"))
        assertFalse(entry.has("recent"))
        assertFalse(entry.has("podium"))
        assertFalse(entry.toString().contains("data/private"))
        assertFalse(entry.toString().contains("Подтверждено"))
    }

    @Test
    fun `standings keep previous private aggregates when the key is missing`() {
        val stub = challenge(goals = emptyList(), privateKey = null, participants = listOf("U-A"))
        val previous = JSONObject().put(
            "challenges",
            JSONArray(
                listOf(
                    JSONObject()
                        .put("id", "ch-private")
                        .put("title", "Секретный челлендж")
                        .put("visibility", "PRIVATE")
                        .put("participants", JSONArray(
                            listOf(
                                JSONObject()
                                    .put("code", "U-A")
                                    .put("name", "Анна")
                                    .put("points", 7)
                                    .put("maxPoints", 10)
                                    .put("place", 1)
                                    .put("medal", "🥇")
                            )
                        ))
                )
            )
        )

        val json = StandingsBuilder.build(
            AppData(profiles = profiles(), challenges = listOf(stub)),
            config,
            today,
            previous = previous
        )
        val entry = json.getJSONArray("challenges").getJSONObject(0)
        assertEquals("PRIVATE", entry.getString("visibility"))
        assertEquals(7, entry.getJSONArray("participants").getJSONObject(0).getInt("points"))
    }

    @Test
    fun `private medals are excluded from the public leaderboard counts`() {
        val challenge = challenge()
        val medal = Medal(
            challengeId = challenge.id,
            challengeTitle = challenge.title,
            place = 1,
            earnedAt = "2026-11-10T10:00:00Z"
        )
        val profiles = profiles().map { profile ->
            if (profile.code == "U-A") profile.copy(medals = listOf(medal)) else profile
        }
        val data = AppData(
            profiles = profiles,
            challenges = listOf(challenge),
            checkIns = listOf(checkIn())
        )

        val json = StandingsBuilder.build(data, config, today)
        val user = json.getJSONArray("users").toList { it }
            .first { it.getString("code") == "U-A" }

        assertEquals(0, user.getInt("gold"))
        assertEquals(0, user.getInt("silver"))
        assertEquals(0, user.getInt("bronze"))
    }

    @Test
    fun `preserved private aggregates are stripped to public fields`() {
        val stub = challenge(goals = emptyList(), privateKey = null, participants = listOf("U-A"))
        val previous = JSONObject().put(
            "challenges",
            JSONArray(
                listOf(
                    JSONObject()
                        .put("id", "ch-private")
                        .put("title", "Секретный челлендж")
                        .put("visibility", "PRIVATE")
                        .put("description", "секретное описание")
                        .put("goals", JSONArray(listOf(JSONObject().put("name", "Секрет"))))
                        .put("participants", JSONArray(
                            listOf(
                                JSONObject()
                                    .put("code", "U-A")
                                    .put("name", "Анна")
                                    .put("avatarUrl", "avatars/a.jpg")
                                    .put("points", 7)
                                    .put("maxPoints", 10)
                                    .put("place", 1)
                                    .put("medal", "🥇")
                            )
                        ))
                )
            )
        )

        val json = StandingsBuilder.build(
            AppData(profiles = profiles(), challenges = listOf(stub)),
            config,
            today,
            previous = previous
        )
        val entry = json.getJSONArray("challenges").getJSONObject(0)
        assertEquals(
            setOf("id", "title", "visibility", "status", "statusLabel", "participants"),
            entry.keys().asSequence().toSet()
        )
        assertFalse(entry.has("description"))
        assertFalse(entry.has("goals"))
        val participant = entry.getJSONArray("participants").getJSONObject(0)
        assertEquals(
            setOf("code", "name", "points", "maxPoints", "place", "medal"),
            participant.keys().asSequence().toSet()
        )
        assertFalse(participant.has("avatarUrl"))
    }

    @Test
    fun `public standings never expose peer confirmation names`() {
        val public = challenge(visibility = ChallengeVisibility.PUBLIC, privateKey = null)
        val confirmation = PeerConfirmation(
            challengeId = public.id,
            checkInUserCode = "U-A",
            date = "2026-11-05",
            goalId = "goal-steps",
            confirmerCode = "U-B",
            actual = 1_500.0,
            createdAt = "2026-11-05T12:00:00Z",
            updatedAt = "2026-11-05T12:00:00Z"
        )
        val data = AppData(
            profiles = profiles(),
            challenges = listOf(public),
            checkIns = listOf(checkIn(attachmentPath = "confirmations/U-A/ch-private/steps.jpg")),
            confirmations = listOf(confirmation)
        )

        val text = StandingsBuilder.build(data, config, today).toString()
        assertFalse(text.contains("confirmedBy"))
        assertFalse(text.contains("Подтверждено"))
    }
}
