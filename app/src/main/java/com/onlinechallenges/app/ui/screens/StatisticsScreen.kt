package com.onlinechallenges.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onlinechallenges.app.data.Attachment
import com.onlinechallenges.app.data.Challenge
import com.onlinechallenges.app.data.ChallengeEngine
import com.onlinechallenges.app.data.ChallengeMode
import com.onlinechallenges.app.data.Dates
import com.onlinechallenges.app.data.GoalScore
import com.onlinechallenges.app.data.ParticipantScore
import com.onlinechallenges.app.data.PeriodScore
import com.onlinechallenges.app.data.PeriodState
import com.onlinechallenges.app.ui.AppViewModel
import com.onlinechallenges.app.ui.EmptyState
import com.onlinechallenges.app.ui.GradientHeader
import com.onlinechallenges.app.ui.InfoBanner
import com.onlinechallenges.app.ui.MedalBadge
import com.onlinechallenges.app.ui.PodiumCard
import com.onlinechallenges.app.ui.ProofChip
import com.onlinechallenges.app.ui.ScoreSummary
import com.onlinechallenges.app.ui.SectionTitle
import com.onlinechallenges.app.ui.TriStateChip
import com.onlinechallenges.app.ui.UserAvatar
import com.onlinechallenges.app.ui.formatNumber
import com.onlinechallenges.app.ui.formatTarget
import com.onlinechallenges.app.ui.openAttachment
import com.onlinechallenges.app.ui.stateColor
import com.onlinechallenges.app.ui.stateLabel
import com.onlinechallenges.app.ui.theme.ScoreColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun StatisticsScreen(
    vm: AppViewModel,
    challengeId: String,
    selectedCode: String?
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val challenge = data.challenge(challengeId)
    if (challenge == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                emoji = "🔎",
                title = "Челлендж не найден",
                text = "Возможно, он удалён или ещё не синхронизирован.",
                actionText = "Назад",
                onAction = { vm.back() }
            )
        }
        return
    }

    val today = LocalDate.now()
    val finished = today.isAfter(challenge.end)
    val scores = ChallengeEngine.scoreChallenge(challenge, data.checkIns, data.profiles, today)
    val myCode = data.currentProfile()?.code
    val initialCode = selectedCode?.takeIf { code -> scores.any { it.code == code } }
        ?: myCode?.takeIf { code -> scores.any { it.code == code } }
        ?: scores.firstOrNull()?.code
    var activeCode by remember(challenge.id, selectedCode) { mutableStateOf(initialCode) }
    val selected = scores.firstOrNull { it.code == activeCode } ?: scores.firstOrNull()
    val periods = selected?.periods.orEmpty()
        .filter { !it.startDate.isAfter(today) }
        .asReversed()
    var expandedIndex by remember(challenge.id, selected?.code) { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            GradientHeader {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                if (finished) "Итоги" else "Статистика",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            challenge.modeDescription(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(challenge.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Dates.range(challenge.start, challenge.end),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("${scores.size}", "участников", Modifier.weight(1f))
                        StatTile("${challenge.goals.size}", "целей", Modifier.weight(1f))
                        StatTile(
                            if (finished) "Финиш" else "День ${challengeDay(challenge, today)}",
                            if (finished) "челлендж" else "из ${challengeLength(challenge)}",
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                PodiumCard(scores = scores, finished = finished)
            }
        }
        if (scores.isEmpty()) {
            item {
                EmptyState(
                    emoji = "👥",
                    title = "Пока нет участников",
                    text = "Пригласите друзей — статистика появится после первой отметки."
                )
            }
        } else {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    SectionTitle("Участники", subtitle = "Нажмите на имя, чтобы открыть дневник")
                    Spacer(Modifier.height(8.dp))
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scores, key = { it.code }) { score ->
                        FilterChip(
                            selected = score.code == selected?.code,
                            onClick = { activeCode = score.code },
                            label = {
                                Text(
                                    if (finished && score.place <= 3) {
                                        "${medalEmoji(score.place)} ${score.name}"
                                    } else {
                                        "#${score.place} ${score.name}"
                                    },
                                    maxLines = 1
                                )
                            },
                            leadingIcon = if (finished && score.place <= 3) null else {
                                { Text("${score.points} б.", style = MaterialTheme.typography.labelSmall) }
                            }
                        )
                    }
                }
            }
            if (selected != null) {
                item {
                    ParticipantSummaryCard(
                        score = selected,
                        totalParticipants = scores.size,
                        finished = finished,
                        avatarModel = vm.avatarModel(selected.profile),
                        periods = periods,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                        SectionTitle(
                            "Дневник",
                            subtitle = when (challenge.mode) {
                                ChallengeMode.DAILY -> "Каждый день — отдельная оценка"
                                ChallengeMode.INTERVAL -> "Отметки складываются внутри окна"
                                ChallengeMode.TOTAL -> "Накопленный результат по дням"
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TriStateChip("Выполнено", ScoreColors.complete, true)
                            TriStateChip("Частично", ScoreColors.partial, true)
                            TriStateChip("Пропуск", ScoreColors.missed, true)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
                items(periods, key = { "${selected.code}-${it.index}" }) { period ->
                    PeriodCard(
                        period = period,
                        challenge = challenge,
                        expanded = expandedIndex == period.index,
                        onToggle = {
                            expandedIndex = if (expandedIndex == period.index) null else period.index
                        },
                        onOpenAttachment = { attachment -> openAttachment(context, attachment, vm) }
                    )
                }
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        InfoBanner(
                            text = "Зелёный — все цели дня закрыты, жёлтый — частичный результат, красный — пропуск. " +
                                "1 балл начисляется только за полностью выполненную цель с подтверждением нужного типа; " +
                                "превышение цели баллов не добавляет."
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(value, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun ParticipantSummaryCard(
    score: ParticipantScore,
    totalParticipants: Int,
    finished: Boolean,
    avatarModel: Any?,
    periods: List<PeriodScore>,
    modifier: Modifier = Modifier
) {
    val completed = periods.count { it.state == PeriodState.COMPLETE }
    val partial = periods.count { it.state == PeriodState.PARTIAL }
    val missed = periods.count { it.state == PeriodState.MISSED }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(profile = score.profile, model = avatarModel, size = 54.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(score.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Text(
                        "место ${score.place} из $totalParticipants",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (finished && score.medal != null) {
                        Spacer(Modifier.height(4.dp))
                        MedalBadge(medal = score.medal, compact = true)
                    }
                }
                ScoreSummary(points = score.points, maxPoints = score.maxPoints)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = {
                    if (score.maxPoints == 0) 0f
                    else (score.points.toFloat() / score.maxPoints).coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                PeriodStat(completed, "закрыто", ScoreColors.complete, Modifier.weight(1f))
                PeriodStat(partial, "частично", ScoreColors.partial, Modifier.weight(1f))
                PeriodStat(missed, "пропуск", ScoreColors.missed, Modifier.weight(1f))
                PeriodStat(score.periods.size, "всего", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }
            if (score.lastActivity != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Последняя отметка: ${Dates.long(score.lastActivity)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PeriodStat(value: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodCard(
    period: PeriodScore,
    challenge: Challenge,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenAttachment: (Attachment) -> Unit
) {
    val color = stateColor(period.state)
    val completedGoals = period.goalScores.count { it.completed }
    val rightValue = when {
        challenge.mode == ChallengeMode.TOTAL -> "$completedGoals/${period.maxPoints}"
        else -> "${period.points}/${period.maxPoints}"
    }
    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Column {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = color.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stateEmoji(period.state), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(period.label, style = MaterialTheme.typography.titleSmall)
                        if (period.isToday) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    "сегодня",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        buildString {
                            append(stateLabel(period.state))
                            if (period.hasEntry) append(" · есть отметка")
                            if (challenge.mode == ChallengeMode.TOTAL && period.points > 0) {
                                append(" · +${period.points} б. сегодня")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        rightValue,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (period.points > 0 || completedGoals > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (challenge.mode == ChallengeMode.TOTAL) "выполнено" else "баллов",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    if (expanded) "▴" else "▾",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.padding(12.dp)) {
                    if (!period.hasEntry && period.goalScores.all { it.actual <= 0.0 && it.attachments.isEmpty() }) {
                        Text(
                            if (period.state == PeriodState.FUTURE) "Период ещё не начался."
                            else "Отметок за этот период нет.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    period.goalScores.forEachIndexed { index, goalScore ->
                        GoalDetailRow(goalScore = goalScore, onOpenAttachment = onOpenAttachment)
                        if (index < period.goalScores.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                    if (challenge.mode == ChallengeMode.INTERVAL) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Окно ${Dates.shortRange(period.startDate, period.endDate)}: результаты всех дней складываются.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onToggle,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                    ) { Text("Свернуть") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalDetailRow(
    goalScore: GoalScore,
    onOpenAttachment: (Attachment) -> Unit
) {
    val color = when {
        goalScore.completed -> ScoreColors.complete
        goalScore.actual > 0.0 || goalScore.attachments.isNotEmpty() -> ScoreColors.partial
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Top) {
        Text(goalScore.goal.emoji, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(goalScore.goal.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${formatNumber(goalScore.actual)} из ${formatTarget(goalScore.goal)}",
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
            if (goalScore.actual >= goalScore.target && !goalScore.hasProof) {
                Text(
                    "Цель достигнута, но нет ${goalScore.goal.proofType.label.lowercase()} — балл не начислен.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ScoreColors.partial
                )
            } else if (goalScore.actual > 0.0 && goalScore.actual < goalScore.target) {
                Text(
                    "Не хватает ${formatNumber(goalScore.target - goalScore.actual)} ${goalScore.goal.unit}".trim(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (goalScore.attachments.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    goalScore.attachments.forEach { attachment ->
                        ProofChip(
                            attachment = attachment,
                            model = null,
                            onClick = { onOpenAttachment(attachment) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (goalScore.completed) "+1" else "0",
            style = MaterialTheme.typography.titleMedium,
            color = if (goalScore.completed) ScoreColors.complete else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun stateEmoji(state: PeriodState): String = when (state) {
    PeriodState.COMPLETE -> "✅"
    PeriodState.PARTIAL -> "🟡"
    PeriodState.MISSED -> "✖️"
    PeriodState.PENDING -> "⏳"
    PeriodState.FUTURE -> "•"
}

private fun medalEmoji(place: Int): String = when (place) {
    1 -> "🥇"
    2 -> "🥈"
    3 -> "🥉"
    else -> "#$place"
}

private fun challengeDay(challenge: Challenge, today: LocalDate): Int {
    if (today.isBefore(challenge.start)) return 0
    if (today.isAfter(challenge.end)) return challengeLength(challenge)
    return (ChronoUnit.DAYS.between(challenge.start, today) + 1).toInt()
}

private fun challengeLength(challenge: Challenge): Int =
    (ChronoUnit.DAYS.between(challenge.start, challenge.end) + 1).toInt()
