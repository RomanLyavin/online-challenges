package com.onlinechallenges.app.data

import java.time.LocalDate
import kotlin.math.min

enum class PeriodState {
    COMPLETE,
    PARTIAL,
    MISSED,
    PENDING,
    FUTURE
}

data class GoalScore(
    val goal: ChallengeGoal,
    val actual: Double,
    val target: Double,
    val hasProof: Boolean,
    val completed: Boolean,
    val points: Int,
    val attachments: List<Attachment> = emptyList()
) {
    val progress: Double get() = if (target <= 0.0) 1.0 else (actual / target).coerceIn(0.0, 1.0)
}

data class PeriodScore(
    val index: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val label: String,
    val state: PeriodState,
    val points: Int,
    val maxPoints: Int,
    val goalScores: List<GoalScore>,
    val hasEntry: Boolean
) {
    val isStarted: Boolean get() = state != PeriodState.FUTURE
    val isToday: Boolean get() = !startDate.isAfter(LocalDate.now()) && !endDate.isBefore(LocalDate.now())
}

data class ParticipantScore(
    val code: String,
    val name: String,
    val profile: Profile?,
    val points: Int,
    val maxPoints: Int,
    val progressRatio: Double,
    val periods: List<PeriodScore>,
    val lastActivity: LocalDate?,
    val place: Int = 0,
    val medal: Medal? = null
) {
    val progressPercent: Int get() = if (maxPoints <= 0) 0 else (points * 100 / maxPoints).coerceIn(0, 100)
    val hasMedal: Boolean get() = place in 1..3
}

/**
 * Чистый движок подсчёта. Один балл даётся за каждую полностью выполненную цель.
 * Частичное выполнение баллов не даёт, но показывается процентом.
 */
object ChallengeEngine {

    fun proofMatches(attachmentType: AttachmentType, required: ProofType): Boolean = when (required) {
        ProofType.PHOTO -> attachmentType == AttachmentType.PHOTO
        ProofType.VIDEO -> attachmentType == AttachmentType.VIDEO
        ProofType.ANY -> true
    }

    fun validProofs(attachments: List<Attachment>, required: ProofType): List<Attachment> =
        attachments.filter { proofMatches(it.type, required) }

    fun scoreChallenge(
        challenge: Challenge,
        checkIns: List<CheckIn>,
        profiles: List<Profile>,
        today: LocalDate = LocalDate.now(),
        includeFuture: Boolean = true
    ): List<ParticipantScore> {
        val scores = challenge.participantCodes.map { code ->
            scoreParticipant(challenge, code, checkIns, profiles, today, includeFuture)
        }
        return rankScores(challenge, scores, today)
    }

    fun scoreParticipant(
        challenge: Challenge,
        code: String,
        allCheckIns: List<CheckIn>,
        profiles: List<Profile>,
        today: LocalDate = LocalDate.now(),
        includeFuture: Boolean = true
    ): ParticipantScore {
        val checkIns = allCheckIns.filter { it.challengeId == challenge.id && it.userCode == code }
        val periods = when (challenge.mode) {
            ChallengeMode.DAILY -> dailyPeriods(challenge, checkIns, today, includeFuture)
            ChallengeMode.INTERVAL -> intervalPeriods(challenge, checkIns, today, includeFuture)
            ChallengeMode.TOTAL -> totalPeriods(challenge, checkIns, today, includeFuture)
        }
        val started = periods.filter { it.startDate <= today }
        val points = when (challenge.mode) {
            ChallengeMode.TOTAL -> periods.lastOrNull { it.startDate <= today }
                ?.goalScores?.count { it.completed } ?: 0
            else -> started.sumOf { it.points }
        }
        val maxPoints = when (challenge.mode) {
            ChallengeMode.TOTAL -> challenge.goals.size
            else -> started.size * challenge.goals.size
        }
        val ratio = when (challenge.mode) {
            ChallengeMode.TOTAL -> {
                if (challenge.goals.isEmpty()) 0.0 else {
                    val last = periods.lastOrNull { it.startDate <= today } ?: periods.firstOrNull()
                    last?.goalScores?.sumOf { it.progress }?.div(challenge.goals.size) ?: 0.0
                }
            }
            else -> {
                if (maxPoints == 0) 0.0 else started.flatMap { it.goalScores }
                    .sumOf { it.progress } / maxPoints
            }
        }
        val lastActivity = checkIns.maxOfOrNull { parseDate(it.date) }
        return ParticipantScore(
            code = code,
            name = profiles.firstOrNull { it.code == code }?.name ?: "Участник",
            profile = profiles.firstOrNull { it.code == code },
            points = points,
            maxPoints = maxPoints,
            progressRatio = ratio,
            periods = periods,
            lastActivity = lastActivity
        )
    }

    private fun dailyPeriods(
        challenge: Challenge,
        checkIns: List<CheckIn>,
        today: LocalDate,
        includeFuture: Boolean
    ): List<PeriodScore> {
        val result = ArrayList<PeriodScore>()
        var date = challenge.start
        var index = 0
        while (!date.isAfter(challenge.end)) {
            if (includeFuture || !date.isAfter(today)) {
                result += scoreSinglePeriod(
                    challenge = challenge,
                    index = index,
                    start = date,
                    end = date,
                    label = Dates.day(date),
                    entries = checkIns.filter { parseDate(it.date) == date },
                    today = today
                )
            }
            date = date.plusDays(1)
            index++
        }
        return result
    }

    private fun intervalPeriods(
        challenge: Challenge,
        checkIns: List<CheckIn>,
        today: LocalDate,
        includeFuture: Boolean
    ): List<PeriodScore> {
        val result = ArrayList<PeriodScore>()
        val step = challenge.intervalDays.coerceAtLeast(1)
        var slotStart = challenge.start
        var index = 0
        while (!slotStart.isAfter(challenge.end)) {
            val slotEnd = minOf(slotStart.plusDays(step.toLong() - 1), challenge.end)
            if (includeFuture || !slotStart.isAfter(today)) {
                result += scoreSinglePeriod(
                    challenge = challenge,
                    index = index,
                    start = slotStart,
                    end = slotEnd,
                    label = Dates.shortRange(slotStart, slotEnd),
                    entries = checkIns.filter {
                        val d = parseDate(it.date)
                        !d.isBefore(slotStart) && !d.isAfter(slotEnd)
                    },
                    today = today
                )
            }
            slotStart = slotEnd.plusDays(1)
            index++
        }
        return result
    }

    private fun scoreSinglePeriod(
        challenge: Challenge,
        index: Int,
        start: LocalDate,
        end: LocalDate,
        label: String,
        entries: List<CheckIn>,
        today: LocalDate
    ): PeriodScore {
        val goalScores = challenge.goals.map { goal ->
            val goalEntries = entries.mapNotNull { it.resultFor(goal.id) }
            val actual = goalEntries.sumOf { it.actual.coerceAtLeast(0.0) }
            val attachments = goalEntries.flatMap { it.attachments }
            val proofs = validProofs(attachments, goal.proofType)
            val completed = actual >= goal.target && proofs.isNotEmpty()
            GoalScore(
                goal = goal,
                actual = actual,
                target = goal.target,
                hasProof = proofs.isNotEmpty(),
                completed = completed,
                points = if (completed) 1 else 0,
                attachments = attachments
            )
        }
        val points = goalScores.sumOf { it.points }
        val maxPoints = challenge.goals.size
        val hasEntry = entries.isNotEmpty()
        val anyProgress = goalScores.any { it.actual > 0.0 || it.attachments.isNotEmpty() }
        val state = when {
            start.isAfter(today) -> PeriodState.FUTURE
            points == maxPoints && maxPoints > 0 -> PeriodState.COMPLETE
            end.isBefore(today) -> if (anyProgress) PeriodState.PARTIAL else PeriodState.MISSED
            hasEntry || anyProgress -> PeriodState.PARTIAL
            start == today || !end.isBefore(today) -> PeriodState.PENDING
            else -> PeriodState.MISSED
        }
        return PeriodScore(
            index = index,
            startDate = start,
            endDate = end,
            label = label,
            state = state,
            points = points,
            maxPoints = maxPoints,
            goalScores = goalScores,
            hasEntry = hasEntry
        )
    }

    private fun totalPeriods(
        challenge: Challenge,
        checkIns: List<CheckIn>,
        today: LocalDate,
        includeFuture: Boolean
    ): List<PeriodScore> {
        val result = ArrayList<PeriodScore>()
        val sorted = checkIns.sortedBy { it.date }
        var date = challenge.start
        var index = 0
        val everCompleted = mutableSetOf<String>()
        while (!date.isAfter(challenge.end)) {
            if (includeFuture || !date.isAfter(today)) {
                val entriesUpToDate = sorted.filter { !parseDate(it.date).isAfter(date) }
                var earnedToday = 0
                val goalScores = challenge.goals.map { goal ->
                    val goalEntries = entriesUpToDate.mapNotNull { it.resultFor(goal.id) }
                    val actual = goalEntries.sumOf { it.actual.coerceAtLeast(0.0) }
                    val attachments = goalEntries.flatMap { it.attachments }
                    val proofs = validProofs(attachments, goal.proofType)
                    val completed = actual >= goal.target && proofs.isNotEmpty()
                    val newCompletion = completed && everCompleted.add(goal.id)
                    if (newCompletion) earnedToday++
                    GoalScore(
                        goal = goal,
                        actual = actual,
                        target = goal.target,
                        hasProof = proofs.isNotEmpty(),
                        completed = completed,
                        points = if (completed) 1 else 0,
                        attachments = attachments
                    )
                }
                val completedCount = goalScores.count { it.completed }
                val anyProgress = goalScores.any { it.actual > 0.0 }
                val state = when {
                    date.isAfter(today) -> PeriodState.FUTURE
                    completedCount == challenge.goals.size && completedCount > 0 -> PeriodState.COMPLETE
                    anyProgress -> PeriodState.PARTIAL
                    date.isBefore(today) -> PeriodState.MISSED
                    else -> PeriodState.PENDING
                }
                result += PeriodScore(
                    index = index,
                    startDate = date,
                    endDate = date,
                    label = Dates.day(date),
                    state = state,
                    points = earnedToday,
                    maxPoints = challenge.goals.size,
                    goalScores = goalScores,
                    hasEntry = entriesUpToDate.any { parseDate(it.date) == date }
                )
            }
            date = date.plusDays(1)
            index++
        }
        return result
    }

    fun rankScores(
        challenge: Challenge,
        scores: List<ParticipantScore>,
        today: LocalDate = LocalDate.now()
    ): List<ParticipantScore> {
        val sorted = scores.sortedWith(
            compareByDescending<ParticipantScore> { it.points }
                .thenByDescending { it.progressRatio }
                .thenBy { it.lastActivity ?: LocalDate.MAX }
                .thenBy { it.code }
        )
        val finished = today.isAfter(challenge.end)
        return sorted.mapIndexed { index, score ->
            val place = index + 1
            score.copy(
                place = place,
                medal = if (finished && place <= 3) {
                    Medal(
                        challengeId = challenge.id,
                        challengeTitle = challenge.title,
                        place = place,
                        earnedAt = challenge.endDate
                    )
                } else null
            )
        }
    }

    fun currentPeriod(
        challenge: Challenge,
        code: String,
        checkIns: List<CheckIn>,
        profiles: List<Profile>,
        today: LocalDate = LocalDate.now()
    ): PeriodScore? {
        val score = scoreParticipant(challenge, code, checkIns, profiles, today)
        return when (challenge.mode) {
            ChallengeMode.TOTAL -> score.periods.lastOrNull { it.startDate <= today }
            else -> score.periods.firstOrNull { !today.isBefore(it.startDate) && !today.isAfter(it.endDate) }
                ?: score.periods.lastOrNull { it.startDate <= today }
        }
    }

    fun totalScore(
        challenge: Challenge,
        code: String,
        checkIns: List<CheckIn>,
        profiles: List<Profile>,
        today: LocalDate = LocalDate.now()
    ): ParticipantScore = scoreParticipant(challenge, code, checkIns, profiles, today)

    /** Добавляет медали всем участникам завершённого челленджа. Возвращает только изменённые профили. */
    fun awardMedals(
        challenge: Challenge,
        checkIns: List<CheckIn>,
        profiles: List<Profile>,
        today: LocalDate = LocalDate.now()
    ): List<Profile> {
        if (!today.isAfter(challenge.end)) return emptyList()
        val ranked = scoreChallenge(challenge, checkIns, profiles, today, includeFuture = false)
        val changed = ArrayList<Profile>()
        for (score in ranked.filter { it.place in 1..3 }) {
            val profile = profiles.firstOrNull { it.code == score.code } ?: continue
            val medal = Medal(
                challengeId = challenge.id,
                challengeTitle = challenge.title,
                place = score.place,
                earnedAt = challenge.endDate
            )
            if (profile.medals.none { it.key == medal.key }) {
                changed += profile.copy(
                    medals = (profile.medals + medal).distinctBy { it.key }.sortedBy { it.earnedAt },
                    updatedAt = isoNow()
                )
            }
        }
        return changed
    }

    fun recentCheckIns(
        challenge: Challenge,
        checkIns: List<CheckIn>,
        limit: Int = 8
    ): List<CheckIn> = checkIns
        .filter { it.challengeId == challenge.id }
        .sortedByDescending { it.date + it.updatedAt }
        .take(limit)

    fun totalProgress(challenge: Challenge, checkIns: List<CheckIn>, code: String): Int =
        checkIns.count { it.challengeId == challenge.id && it.userCode == code }
}
