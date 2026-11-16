package com.onlinechallenges.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onlinechallenges.app.data.Challenge
import com.onlinechallenges.app.data.ChallengeEngine
import com.onlinechallenges.app.data.ChallengeStatus
import com.onlinechallenges.app.data.Dates
import com.onlinechallenges.app.data.ParticipantScore
import com.onlinechallenges.app.data.PeriodScore
import com.onlinechallenges.app.data.PeriodState
import com.onlinechallenges.app.ui.AppViewModel
import com.onlinechallenges.app.ui.DemoBadge
import com.onlinechallenges.app.ui.EmptyState
import com.onlinechallenges.app.ui.GradientHeader
import com.onlinechallenges.app.ui.InfoBanner
import com.onlinechallenges.app.ui.PodiumCard
import com.onlinechallenges.app.ui.ProofChip
import com.onlinechallenges.app.ui.ScoreSummary
import com.onlinechallenges.app.ui.SectionTitle
import com.onlinechallenges.app.ui.UserAvatar
import com.onlinechallenges.app.ui.formatTarget
import com.onlinechallenges.app.ui.openAttachment
import com.onlinechallenges.app.ui.stateColor
import com.onlinechallenges.app.ui.stateLabel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun ChallengeDetailScreen(vm: AppViewModel, challengeId: String) {
    val data by vm.data.collectAsStateWithLifecycle()
    val challenge = data.challenge(challengeId)
    val context = LocalContext.current
    if (challenge == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                emoji = "🔎",
                title = "Челлендж не найден",
                text = "Возможно, он был удалён или ещё не синхронизирован.",
                actionText = "Назад",
                onAction = { vm.back() }
            )
        }
        return
    }
    val profile = data.currentProfile()
    val today = LocalDate.now()
    val status = challenge.status(today)
    val scores = ChallengeEngine.scoreChallenge(challenge, data.checkIns, data.profiles, today)
    val myScore = scores.firstOrNull { it.code == profile?.code }
    val currentPeriod = profile?.let {
        ChallengeEngine.currentPeriod(challenge, it.code, data.checkIns, data.profiles, today)
    }
    val recent = ChallengeEngine.recentCheckIns(challenge, data.checkIns, 6)
    var actionError by remember(challenge.id) { mutableStateOf<String?>(null) }
    val joined = profile != null && challenge.participantCodes.contains(profile.code)

    LazyColumn(
        contentPadding = PaddingValues(bottom = 34.dp)
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
                                status.label,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                when (challenge.mode) {
                                    com.onlinechallenges.app.data.ChallengeMode.DAILY -> "Каждый день"
                                    com.onlinechallenges.app.data.ChallengeMode.INTERVAL -> "Каждые ${challenge.intervalDays} дн."
                                    com.onlinechallenges.app.data.ChallengeMode.TOTAL -> "За весь период"
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        if (challenge.isDemo) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    "ДЕМО",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        challenge.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    if (challenge.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            challenge.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.86f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        Dates.range(challenge.start, challenge.end),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeaderStat(
                            "${challenge.participantCodes.size}",
                            "участников",
                            Modifier.weight(1f)
                        )
                        HeaderStat(
                            "${challenge.goals.size}",
                            "целей",
                            Modifier.weight(1f)
                        )
                        HeaderStat(
                            challengeDayText(challenge, today),
                            "день",
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                if (actionError != null) {
                    InfoBanner(text = actionError!!, warning = true)
                    Spacer(Modifier.height(8.dp))
                }
                when {
                    !joined && status != ChallengeStatus.FINISHED -> {
                        Button(
                            onClick = { actionError = vm.joinChallenge(challenge.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = MaterialTheme.shapes.small
                        ) { Text("Присоединиться к челленджу") }
                    }
                    joined && status == ChallengeStatus.ACTIVE -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    vm.openCheckIn(challenge.id, today.toString())
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = MaterialTheme.shapes.small
                            ) { Text("Отметить сегодня") }
                            OutlinedButton(
                                onClick = { vm.openStatistics(challenge.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = MaterialTheme.shapes.small
                            ) { Text("Статистика") }
                        }
                    }
                    else -> {
                        OutlinedButton(
                            onClick = { vm.openStatistics(challenge.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(if (status == ChallengeStatus.FINISHED) "Итоговая статистика" else "Статистика")
                        }
                    }
                }
                if (status == ChallengeStatus.UPCOMING) {
                    Spacer(Modifier.height(8.dp))
                    InfoBanner(
                        text = "Старт ${Dates.long(challenge.start)}. Присоединиться можно уже сейчас."
                    )
                }
                if (status == ChallengeStatus.FINISHED) {
                    Spacer(Modifier.height(8.dp))
                    InfoBanner(
                        text = "Челлендж завершён. Отметки закрыты, места и медали зафиксированы.",
                    )
                }
            }
        }
        if (myScore != null) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Мой результат", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "место ${myScore.place} из ${scores.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                ScoreSummary(points = myScore.points, maxPoints = myScore.maxPoints)
                            }
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { if (myScore.maxPoints == 0) 0f else (myScore.points.toFloat() / myScore.maxPoints).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            if (currentPeriod != null) {
                                Spacer(Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = stateColor(currentPeriod.state).copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            stateLabel(currentPeriod.state),
                                            color = stateColor(currentPeriod.state),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Сейчас: ${currentPeriod.label} · ${currentPeriod.points}/${currentPeriod.maxPoints} б.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                PodiumCard(scores = scores, finished = status == ChallengeStatus.FINISHED)
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                SectionTitle("Условия", subtitle = challenge.modeDescription())
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        challenge.goals.forEachIndexed { index, goal ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(goal.emoji, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(goal.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Цель: ${formatTarget(goal)} · ${goal.proofType.label.lowercase()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text("1 б.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            if (index < challenge.goals.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Организатор: ${challenge.creatorName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                SectionTitle("Участники", subtitle = "Нажмите, чтобы открыть дневник")
                Spacer(Modifier.height(6.dp))
            }
        }
        items(scores, key = { it.code }) { score ->
            ParticipantRow(
                score = score,
                today = today,
                finished = status == ChallengeStatus.FINISHED,
                isMe = score.code == profile?.code,
                avatarModel = vm.avatarModel(score.profile),
                onClick = { vm.openStatistics(challenge.id, score.code) }
            )
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                SectionTitle("Кто уже что сделал", subtitle = "Последние подтверждённые отметки")
                Spacer(Modifier.height(6.dp))
                if (recent.isEmpty()) {
                    EmptyState(
                        emoji = "📝",
                        title = "Отметок пока нет",
                        text = "Станьте первым, кто добавит результат с подтверждением."
                    )
                }
            }
        }
        items(recent, key = { it.id }) { checkIn ->
            val participant = data.profiles.firstOrNull { it.code == checkIn.userCode }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(profile = participant, model = vm.avatarModel(participant), size = 30.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            participant?.name ?: "Участник",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            Dates.short(checkIn.localDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    checkIn.results.forEach { result ->
                        val goal = challenge.goals.firstOrNull { it.id == result.goalId } ?: return@forEach
                        val proofs = ChallengeEngine.validProofs(result.attachments, goal.proofType)
                        val complete = result.actual >= goal.target && proofs.isNotEmpty()
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(goal.emoji, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${goal.name}: ${com.onlinechallenges.app.ui.formatNumber(result.actual)} / ${formatTarget(goal)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (complete) stateColor(PeriodState.COMPLETE) else stateColor(PeriodState.PARTIAL)
                            )
                            Spacer(Modifier.weight(1f))
                            if (proofs.isEmpty()) {
                                Text("без фото", style = MaterialTheme.typography.labelSmall, color = stateColor(PeriodState.MISSED))
                            }
                        }
                        if (result.attachments.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                                result.attachments.forEach { attachment ->
                                    ProofChip(
                                        attachment = attachment,
                                        model = vm.attachmentModel(attachment),
                                        onClick = { openAttachment(context, attachment, vm) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            InfoBanner(
                text = "Балл начисляется только за полностью выполненную цель с подтверждением нужного типа. Превышение цели не добавляет баллов. Частичный результат выделяется жёлтым, пропуск — красным.",
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun HeaderStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

private fun challengeDayText(challenge: Challenge, today: LocalDate): String {
    if (today.isBefore(challenge.start)) return "0"
    if (today.isAfter(challenge.end)) return "—"
    return "${ChronoUnit.DAYS.between(challenge.start, today) + 1}"
}

@Composable
private fun ParticipantRow(
    score: ParticipantScore,
    today: LocalDate,
    finished: Boolean,
    isMe: Boolean,
    avatarModel: Any?,
    onClick: () -> Unit
) {
    val period = score.periods.lastOrNull { !today.isBefore(it.startDate) && !today.isAfter(it.endDate) }
        ?: score.periods.lastOrNull { it.startDate <= today }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (finished && score.place <= 3) {
                    Text(
                        when (score.place) {
                            1 -> "🥇"
                            2 -> "🥈"
                            else -> "🥉"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Text(
                        "#${score.place}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            UserAvatar(profile = score.profile, model = avatarModel, size = 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        score.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isMe) {
                        Spacer(Modifier.width(6.dp))
                        Text("вы", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (period != null) {
                    Text(
                        "${stateLabel(period.state)} · ${period.points}/${period.maxPoints} б.",
                        style = MaterialTheme.typography.labelSmall,
                        color = stateColor(period.state)
                    )
                } else {
                    Text(
                        "Нет отметок",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${score.points}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("баллов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
