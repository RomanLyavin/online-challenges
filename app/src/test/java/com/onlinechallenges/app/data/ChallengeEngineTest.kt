package com.onlinechallenges.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChallengeEngineTest {

    private val start = LocalDate.of(2026, 1, 5)
    private val today = LocalDate.of(2026, 1, 7)

    @Test
    fun `daily challenge scores only fully completed goals with proof`() {
        val challenge = challenge(
            id = "daily",
            mode = ChallengeMode.DAILY,
            start = start,
            end = start.plusDays(2),
            goals = listOf(
                goal("steps", target = 10_000.0, proof = ProofType.PHOTO),
                goal("plank", target = 2.0, proof = ProofType.VIDEO)
            )
        )
        val checkIns = listOf(
            checkIn(
                challenge.id, "A", start,
                result("steps", 12_000.0, photo("steps-1")),
                result("plank", 2.5, video("plank-1"))
            ),
            checkIn(
                challenge.id, "A", start.plusDays(1),
                result("steps", 8_000.0, photo("steps-2")),
                result("plank", 3.0, withProof = false)
            )
        )

        val score = ChallengeEngine.scoreParticipant(
            challenge, "A", checkIns, profiles("A"), today
        )

        assertEquals(2, score.points)
        assertEquals(6, score.maxPoints)
        assertEquals(3, score.periods.size)
        assertEquals(PeriodState.COMPLETE, score.periods[0].state)
        assertEquals(2, score.periods[0].points)
        assertEquals(PeriodState.PARTIAL, score.periods[1].state)
        assertEquals(0, score.periods[1].points)
        assertEquals(PeriodState.PENDING, score.periods[2].state)
    }

    @Test
    fun `missed day is red after the day ends`() {
        val challenge = challenge(
            id = "missed",
            mode = ChallengeMode.DAILY,
            start = start,
            end = start.plusDays(2),
            goals = listOf(goal("steps", target = 10_000.0))
        )
        val checkIns = listOf(
            checkIn(challenge.id, "A", start, result("steps", 10_000.0, photo("steps")))
        )

        val score = ChallengeEngine.scoreParticipant(
            challenge, "A", checkIns, profiles("A"), today.plusDays(1)
        )

        assertEquals(1, score.points)
        assertEquals(PeriodState.MISSED, score.periods[1].state)
        assertEquals(PeriodState.MISSED, score.periods[2].state)
    }

    @Test
    fun `duplicate participants and check-ins are counted once`() {
        val challenge = challenge(
            id = "duplicates",
            mode = ChallengeMode.DAILY,
            start = start,
            end = start,
            goals = listOf(goal("steps", target = 10_000.0)),
            participants = listOf("A", "A", "A")
        )
        val checkIn = checkIn(challenge.id, "A", start, result("steps", 12_000.0, photo("steps")))
        val duplicate = checkIn.copy(id = "duplicate-id")

        val scores = ChallengeEngine.scoreChallenge(
            challenge,
            listOf(checkIn, duplicate),
            profiles("A"),
            start
        )

        assertEquals(1, scores.size)
        assertEquals("A", scores.single().code)
        assertEquals(1, scores.single().points)
        assertEquals(1, scores.single().place)
    }

    @Test
    fun `daily goal without matching proof gives no point`() {
        val challenge = challenge(
            id = "proof-type",
            mode = ChallengeMode.DAILY,
            start = start,
            end = start,
            goals = listOf(goal("plank", target = 2.0, proof = ProofType.VIDEO))
        )
        val checkIns = listOf(
            checkIn(
                challenge.id, "A", start,
                result("plank", 3.0, photo("wrong-type"))
            )
        )

        val score = ChallengeEngine.scoreParticipant(challenge, "A", checkIns, profiles("A"), start)

        assertEquals(0, score.points)
        assertEquals(PeriodState.PARTIAL, score.periods.single().state)
        val goalScore = score.periods.single().goalScores.single()
        assertFalse(goalScore.completed)
        assertFalse(goalScore.hasProof)
    }

    @Test
    fun `overachievement never adds extra points`() {
        val challenge = challenge(
            id = "over",
            mode = ChallengeMode.DAILY,
            start = start,
            end = start,
            goals = listOf(goal("steps", target = 10_000.0))
        )
        val checkIns = listOf(
            checkIn(challenge.id, "A", start, result("steps", 55_000.0, photo("steps")))
        )

        val score = ChallengeEngine.scoreParticipant(challenge, "A", checkIns, profiles("A"), start)

        assertEquals(1, score.points)
        assertEquals(1, score.periods.single().maxPoints)
        assertEquals(1.0, score.periods.single().goalScores.single().progress, 0.0001)
    }

    @Test
    fun `interval challenge sums all days inside the window`() {
        val challenge = challenge(
            id = "interval",
            mode = ChallengeMode.INTERVAL,
            start = LocalDate.of(2026, 1, 1),
            end = LocalDate.of(2026, 1, 6),
            goals = listOf(goal("running", target = 10.0)),
            intervalDays = 3
        )
        val checkIns = listOf(
            checkIn(challenge.id, "A", LocalDate.of(2026, 1, 1), result("running", 4.0, photo("run-1"))),
            checkIn(
                challenge.id, "A", LocalDate.of(2026, 1, 2),
                result("running", 6.0, withProof = false)
            ),
            checkIn(challenge.id, "A", LocalDate.of(2026, 1, 4), result("running", 10.0, photo("run-2")))
        )

        val score = ChallengeEngine.scoreParticipant(
            challenge, "A", checkIns, profiles("A"), LocalDate.of(2026, 1, 6)
        )

        assertEquals(2, score.periods.size)
        assertEquals(2, score.points)
        assertEquals(2, score.maxPoints)
        assertEquals(PeriodState.COMPLETE, score.periods[0].state)
        assertEquals(10.0, score.periods[0].goalScores.single().actual, 0.0001)
        assertEquals(PeriodState.COMPLETE, score.periods[1].state)
    }

    @Test
    fun `interval progress in a window does not score before target`() {
        val challenge = challenge(
            id = "interval-partial",
            mode = ChallengeMode.INTERVAL,
            start = LocalDate.of(2026, 1, 1),
            end = LocalDate.of(2026, 1, 3),
            goals = listOf(goal("running", target = 10.0)),
            intervalDays = 3
        )
        val checkIns = listOf(
            checkIn(
                challenge.id, "A", LocalDate.of(2026, 1, 2),
                result("running", 9.9, photo("run"))
            )
        )

        val score = ChallengeEngine.scoreParticipant(
            challenge, "A", checkIns, profiles("A"), LocalDate.of(2026, 1, 3)
        )

        assertEquals(0, score.points)
        assertEquals(PeriodState.PARTIAL, score.periods.single().state)
        assertEquals(0.99, score.periods.single().goalScores.single().progress, 0.0001)
    }

    @Test
    fun `total challenge gives one point per goal when cumulative target is reached`() {
        val challenge = challenge(
            id = "total",
            mode = ChallengeMode.TOTAL,
            start = LocalDate.of(2026, 1, 1),
            end = LocalDate.of(2026, 1, 5),
            goals = listOf(goal("pushups", target = 100.0))
        )
        val checkIns = listOf(
            checkIn(challenge.id, "A", LocalDate.of(2026, 1, 1), result("pushups", 60.0, photo("p-1"))),
            checkIn(
                challenge.id, "A", LocalDate.of(2026, 1, 3),
                result("pushups", 80.0, withProof = false)
            )
        )

        val score = ChallengeEngine.scoreParticipant(
            challenge, "A", checkIns, profiles("A"), LocalDate.of(2026, 1, 5)
        )

        assertEquals(1, score.points)
        assertEquals(1, score.maxPoints)
        assertEquals(5, score.periods.size)
        assertEquals(0, score.periods[0].points)
        assertEquals(PeriodState.PARTIAL, score.periods[0].state)
        assertEquals(1, score.periods[2].points)
        assertEquals(PeriodState.COMPLETE, score.periods[2].state)
        assertEquals(140.0, score.periods[2].goalScores.single().actual, 0.0001)
        assertEquals(1, score.periods[2].goalScores.single().points)
    }

    @Test
    fun `ranking and medals are assigned after the challenge ends`() {
        val challenge = challenge(
            id = "ranked",
            mode = ChallengeMode.DAILY,
            start = LocalDate.of(2026, 1, 1),
            end = LocalDate.of(2026, 1, 2),
            goals = listOf(goal("steps", target = 1.0)),
            participants = listOf("A", "B", "C", "D")
        )
        val checkIns = listOf(
            checkIn(challenge.id, "A", LocalDate.of(2026, 1, 1), result("steps", 1.0, photo("a-1"))),
            checkIn(challenge.id, "A", LocalDate.of(2026, 1, 2), result("steps", 1.0, photo("a-2"))),
            checkIn(challenge.id, "C", LocalDate.of(2026, 1, 2), result("steps", 1.0, photo("c-1"))),
            checkIn(challenge.id, "B", LocalDate.of(2026, 1, 1), result("steps", 1.0, photo("b-1")))
        )
        val people = profiles("A", "B", "C", "D")

        val ranked = ChallengeEngine.scoreChallenge(
            challenge, checkIns, people, LocalDate.of(2026, 1, 5)
        )

        assertEquals(listOf("A", "B", "C", "D"), ranked.map { it.code })
        assertEquals(listOf(1, 2, 3, 4), ranked.map { it.place })
        assertNotNull(ranked[0].medal)
        assertEquals(1, ranked[0].medal?.place)
        assertEquals(2, ranked[1].medal?.place)
        assertEquals(3, ranked[2].medal?.place)
        assertNull(ranked[3].medal)

        val awarded = ChallengeEngine.awardMedals(
            challenge, checkIns, people, LocalDate.of(2026, 1, 5)
        )
        assertEquals(3, awarded.size)
        assertEquals(setOf("A", "B", "C"), awarded.map { it.code }.toSet())
    }

    @Test
    fun `medals are not awarded while the challenge is still running`() {
        val challenge = challenge(
            id = "running",
            mode = ChallengeMode.DAILY,
            start = LocalDate.of(2026, 1, 1),
            end = LocalDate.of(2026, 1, 10),
            goals = listOf(goal("steps", target = 1.0))
        )
        val checkIns = listOf(
            checkIn(challenge.id, "A", LocalDate.of(2026, 1, 1), result("steps", 1.0, photo("a")))
        )

        val ranked = ChallengeEngine.scoreChallenge(
            challenge, checkIns, profiles("A"), LocalDate.of(2026, 1, 2)
        )
        val awarded = ChallengeEngine.awardMedals(
            challenge, checkIns, profiles("A"), LocalDate.of(2026, 1, 2)
        )

        assertEquals(1, ranked.single().place)
        assertNull(ranked.single().medal)
        assertTrue(awarded.isEmpty())
    }

    @Test
    fun `medals are not awarded for a finished challenge with one participant`() {
        val challenge = challenge(
            id = "solo",
            mode = ChallengeMode.DAILY,
            start = LocalDate.of(2026, 1, 1),
            end = LocalDate.of(2026, 1, 2),
            goals = listOf(goal("steps", target = 1.0)),
            participants = listOf("A")
        )
        val checkIns = listOf(
            checkIn(challenge.id, "A", LocalDate.of(2026, 1, 1), result("steps", 1.0, photo("a"))),
            checkIn(challenge.id, "A", LocalDate.of(2026, 1, 2), result("steps", 1.0, photo("b")))
        )

        val awarded = ChallengeEngine.awardMedals(
            challenge, checkIns, profiles("A"), LocalDate.of(2026, 1, 5)
        )

        assertTrue(awarded.isEmpty())
    }

    @Test
    fun `current period is the interval window containing today`() {
        val challenge = challenge(
            id = "current",
            mode = ChallengeMode.INTERVAL,
            start = LocalDate.of(2026, 1, 1),
            end = LocalDate.of(2026, 1, 9),
            goals = listOf(goal("steps", target = 1.0)),
            intervalDays = 3
        )

        val period = ChallengeEngine.currentPeriod(
            challenge, "A", emptyList(), profiles("A"), LocalDate.of(2026, 1, 7)
        )

        assertNotNull(period)
        assertEquals(LocalDate.of(2026, 1, 7), period?.startDate)
        assertEquals(LocalDate.of(2026, 1, 9), period?.endDate)
    }

    @Test
    fun `proof matching respects required media type`() {
        assertTrue(ChallengeEngine.proofMatches(AttachmentType.PHOTO, ProofType.PHOTO))
        assertFalse(ChallengeEngine.proofMatches(AttachmentType.VIDEO, ProofType.PHOTO))
        assertTrue(ChallengeEngine.proofMatches(AttachmentType.VIDEO, ProofType.VIDEO))
        assertFalse(ChallengeEngine.proofMatches(AttachmentType.PHOTO, ProofType.VIDEO))
        assertTrue(ChallengeEngine.proofMatches(AttachmentType.PHOTO, ProofType.ANY))
        assertTrue(ChallengeEngine.proofMatches(AttachmentType.VIDEO, ProofType.ANY))
    }

    @Test
    fun `peer confirmation completes a goal without media`() {
        val challenge = challenge(
            id = "peer-basic",
            mode = ChallengeMode.DAILY,
            start = start,
            end = start,
            goals = listOf(goal("running", target = 5.0, proof = ProofType.PHOTO)),
            participants = listOf("A", "B")
        )
        val checkIns = listOf(
            checkIn(challenge.id, "A", start, result("running", 5.0, withProof = false))
        )
        val confirmations = listOf(confirmation(challenge.id, "A", start, "running", "B", 5.0))

        val score = ChallengeEngine.scoreParticipant(
            challenge,
            "A",
            checkIns,
            profiles("A", "B"),
            start,
            confirmations = confirmations
        )

        assertEquals(1, score.points)
        val goalScore = score.periods.single().goalScores.single()
        assertTrue(goalScore.completed)
        assertFalse(goalScore.hasProof)
        assertTrue(goalScore.hasPeerConfirmation)
        assertEquals(listOf("B"), goalScore.peerConfirmations.map { it.confirmerCode })
    }

    @Test
    fun `self confirmation and non participant confirmation are ignored`() {
        val challenge = challenge(
            id = "peer-invalid",
            mode = ChallengeMode.DAILY,
            start = start,
            end = start,
            goals = listOf(goal("running", target = 5.0)),
            participants = listOf("A", "B")
        )
        val checkIns = listOf(
            checkIn(challenge.id, "A", start, result("running", 5.0, withProof = false))
        )
        val confirmations = listOf(
            confirmation(challenge.id, "A", start, "running", "A", 5.0),
            confirmation(challenge.id, "A", start, "running", "C", 5.0)
        )

        val score = ChallengeEngine.scoreParticipant(
            challenge,
            "A",
            checkIns,
            profiles("A", "B", "C"),
            start,
            confirmations = confirmations
        )

        assertEquals(0, score.points)
        assertFalse(score.periods.single().goalScores.single().hasPeerConfirmation)
    }

    @Test
    fun `revoked or stale confirmation gives no point`() {
        val challenge = challenge(
            id = "peer-revoked",
            mode = ChallengeMode.DAILY,
            start = start,
            end = start,
            goals = listOf(goal("running", target = 5.0)),
            participants = listOf("A", "B")
        )
        val checkIns = listOf(
            checkIn(challenge.id, "A", start, result("running", 5.0, withProof = false))
        )
        val revoked = confirmation(challenge.id, "A", start, "running", "B", 5.0, revoked = true)
        val stale = confirmation(challenge.id, "A", start, "running", "B", 4.0)

        val revokedScore = ChallengeEngine.scoreParticipant(
            challenge, "A", checkIns, profiles("A", "B"), start, confirmations = listOf(revoked)
        )
        val staleScore = ChallengeEngine.scoreParticipant(
            challenge, "A", checkIns, profiles("A", "B"), start, confirmations = listOf(stale)
        )

        assertEquals(0, revokedScore.points)
        assertEquals(0, staleScore.points)
    }

    @Test
    fun `confirmation covers only the goal and date it was given for`() {
        val challenge = challenge(
            id = "peer-scope",
            mode = ChallengeMode.DAILY,
            start = start,
            end = start.plusDays(1),
            goals = listOf(
                goal("running", target = 5.0),
                goal("plank", target = 2.0, proof = ProofType.VIDEO)
            ),
            participants = listOf("A", "B")
        )
        val checkIns = listOf(
            checkIn(
                challenge.id, "A", start,
                result("running", 5.0, withProof = false),
                result("plank", 2.0, withProof = false)
            )
        )
        val confirmations = listOf(
            confirmation(challenge.id, "A", start, "running", "B", 5.0),
            confirmation(challenge.id, "A", start.plusDays(1), "plank", "B", 2.0)
        )

        val score = ChallengeEngine.scoreParticipant(
            challenge, "A", checkIns, profiles("A", "B"), start, confirmations = confirmations
        )
        val goalScores = score.periods.first().goalScores

        assertTrue(goalScores.first { it.goal.id == "running" }.completed)
        assertFalse(goalScores.first { it.goal.id == "plank" }.completed)
        assertEquals(1, score.points)
    }

    @Test
    fun `one peer confirmation is enough and the author keeps media as an alternative`() {
        val challenge = challenge(
            id = "peer-one",
            mode = ChallengeMode.DAILY,
            start = start,
            end = start,
            goals = listOf(goal("running", target = 5.0)),
            participants = listOf("A", "B", "C")
        )
        val checkIns = listOf(
            checkIn(challenge.id, "A", start, result("running", 5.0, photo("run")))
        )
        val confirmations = listOf(
            confirmation(challenge.id, "A", start, "running", "B", 5.0),
            confirmation(challenge.id, "A", start, "running", "C", 5.0)
        )

        val score = ChallengeEngine.scoreParticipant(
            challenge,
            "A",
            checkIns,
            profiles("A", "B", "C"),
            start,
            confirmations = confirmations
        )

        assertEquals(1, score.points)
        val goalScore = score.periods.single().goalScores.single()
        assertTrue(goalScore.hasProof)
        assertEquals(2, goalScore.peerConfirmations.size)
    }

    private fun confirmation(
        challengeId: String,
        userCode: String,
        date: LocalDate,
        goalId: String,
        confirmerCode: String,
        actual: Double,
        revoked: Boolean = false
    ): PeerConfirmation = PeerConfirmation(
        challengeId = challengeId,
        checkInUserCode = userCode,
        date = date.toString(),
        goalId = goalId,
        confirmerCode = confirmerCode,
        actual = actual,
        createdAt = "${date}T11:00:00Z",
        updatedAt = "${date}T11:00:00Z",
        revoked = revoked
    )

    private fun challenge(
        id: String,
        mode: ChallengeMode,
        start: LocalDate,
        end: LocalDate,
        goals: List<ChallengeGoal>,
        participants: List<String> = listOf("A"),
        intervalDays: Int = 1
    ): Challenge = Challenge(
        id = id,
        title = "Тест",
        description = "",
        startDate = start.toString(),
        endDate = end.toString(),
        mode = mode,
        intervalDays = intervalDays,
        goals = goals,
        creatorCode = "A",
        creatorName = "Анна",
        participantCodes = participants,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z"
    )

    private fun goal(id: String, target: Double, proof: ProofType = ProofType.PHOTO): ChallengeGoal =
        ChallengeGoal(
            id = id,
            activityId = id,
            name = id,
            unit = "ед.",
            emoji = "🎯",
            target = target,
            proofType = proof
        )

    private fun checkIn(
        challengeId: String,
        code: String,
        date: LocalDate,
        vararg results: GoalResult
    ): CheckIn = CheckIn(
        id = "$challengeId-$code-$date",
        challengeId = challengeId,
        userCode = code,
        date = date.toString(),
        results = results.toList(),
        createdAt = "${date}T10:00:00Z",
        updatedAt = "${date}T10:00:00Z"
    )

    private fun result(
        goalId: String,
        actual: Double,
        attachment: Attachment? = null,
        withProof: Boolean = true
    ): GoalResult = GoalResult(
        goalId = goalId,
        actual = actual,
        attachments = if (withProof && attachment != null) listOf(attachment) else emptyList()
    )

    private fun photo(name: String): Attachment = attachment(name, AttachmentType.PHOTO, "image/jpeg")

    private fun video(name: String): Attachment = attachment(name, AttachmentType.VIDEO, "video/mp4")

    private fun attachment(name: String, type: AttachmentType, mime: String): Attachment = Attachment(
        id = name,
        fileName = "$name.${if (type == AttachmentType.PHOTO) "jpg" else "mp4"}",
        mime = mime,
        type = type,
        sizeBytes = 1024
    )

    private fun profiles(vararg codes: String): List<Profile> = codes.map { code ->
        Profile(code = code, name = "Участник $code", birthDate = "1990-01-01", gender = Gender.UNSPECIFIED)
    }
}
