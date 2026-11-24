package com.onlinechallenges.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.onlinechallenges.app.data.Attachment
import com.onlinechallenges.app.data.Challenge
import com.onlinechallenges.app.data.ChallengeEngine
import com.onlinechallenges.app.data.ChallengeGoal
import com.onlinechallenges.app.data.ChallengeStatus
import com.onlinechallenges.app.data.ChallengeVisibility
import com.onlinechallenges.app.data.CheckIn
import com.onlinechallenges.app.data.Dates
import com.onlinechallenges.app.data.Gender
import com.onlinechallenges.app.data.Medal
import com.onlinechallenges.app.data.PeerConfirmation
import com.onlinechallenges.app.data.PeriodState
import com.onlinechallenges.app.data.Profile
import com.onlinechallenges.app.ui.theme.BrandGradient
import com.onlinechallenges.app.ui.theme.ScoreColors
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GradientHeader(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = BrandGradient,
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            )
            .padding(horizontal = 20.dp, vertical = 20.dp),
        content = content
    )
}

@Composable
fun UserAvatar(
    profile: Profile?,
    model: Any?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(BrandGradient)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = profile?.initials ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = profile?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
fun StatusBadge(status: ChallengeStatus) {
    val (foreground, background) = when (status) {
        ChallengeStatus.ACTIVE -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ChallengeStatus.UPCOMING -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
        ChallengeStatus.FINISHED -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = background, shape = RoundedCornerShape(50)) {
        Text(
            text = status.label,
            color = foreground,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun DemoBadge() {
    Surface(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
        Text(
            "ДЕМО",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun ModeBadge(challenge: Challenge) {
    val text = when (challenge.mode) {
        com.onlinechallenges.app.data.ChallengeMode.DAILY -> "Каждый день"
        com.onlinechallenges.app.data.ChallengeMode.INTERVAL -> "Каждые ${challenge.intervalDays} дн."
        com.onlinechallenges.app.data.ChallengeMode.TOTAL -> "Всего за период"
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Позволяет показывать фото-иконки активностей в любом месте без передачи vm. */
val LocalActivityIconResolver = staticCompositionLocalOf<(String, String?) -> Any?> {
    { _, _ -> null }
}

@Composable
fun ActivityIcon(
    activityId: String,
    emoji: String,
    iconPath: String? = null,
    size: Dp = 24.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium
) {
    val model = LocalActivityIconResolver.current(activityId, iconPath)
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
        )
    } else {
        Text(emoji, style = textStyle)
    }
}

@Composable
fun PrivateBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            "🔒 Приватный",
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalChip(goal: ChallengeGoal, compact: Boolean = false) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActivityIcon(
                activityId = goal.activityId,
                emoji = goal.emoji,
                iconPath = goal.iconPath,
                size = if (compact) 14.dp else 16.dp,
                textStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = formatTarget(goal),
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChallengeCard(
    challenge: Challenge,
    currentUserCode: String?,
    allCheckIns: List<CheckIn>,
    profiles: List<Profile>,
    confirmations: List<PeerConfirmation> = emptyList(),
    onClick: () -> Unit
) {
    val today = LocalDate.now()
    val status = challenge.status(today)
    val hasAccess = challenge.hasAccess(currentUserCode)
    val joined = currentUserCode != null && challenge.participantCodes.contains(currentUserCode)
    if (!hasAccess) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrivateBadge()
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Нужен код",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    challenge.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Данные доступны только участникам. Введите код приглашения от автора.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    val myScore = if (joined) {
        ChallengeEngine.scoreParticipant(
            challenge,
            currentUserCode!!,
            allCheckIns,
            profiles,
            today,
            confirmations = confirmations
        )
    } else null
    val currentPeriod = if (joined) {
        ChallengeEngine.currentPeriod(
            challenge,
            currentUserCode!!,
            allCheckIns,
            profiles,
            today,
            confirmations = confirmations
        )
    } else null
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(status)
                Spacer(Modifier.width(8.dp))
                ModeBadge(challenge)
                if (challenge.isPrivate) {
                    Spacer(Modifier.width(8.dp))
                    PrivateBadge()
                }
                if (challenge.isDemo) {
                    Spacer(Modifier.width(8.dp))
                    DemoBadge()
                }
                Spacer(Modifier.weight(1f))
                if (myScore != null && myScore.points > 0) {
                    Text(
                        text = "${myScore.points} б.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                challenge.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                Dates.range(challenge.start, challenge.end),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                challenge.goals.take(4).forEach { GoalChip(it, compact = true) }
                if (challenge.goals.size > 4) {
                    Text(
                        "+${challenge.goals.size - 4}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val participants = challenge.participantCodes.mapNotNull { code -> profiles.firstOrNull { it.code == code } }
                participants.take(3).forEachIndexed { index, profile ->
                    UserAvatar(
                        profile = profile,
                        model = null,
                        size = 26.dp,
                        modifier = Modifier.padding(start = if (index == 0) 0.dp else 4.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${challenge.participantCodes.size} участн.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (joined && currentPeriod != null && status == ChallengeStatus.ACTIVE) {
                    val color = stateColor(currentPeriod.state)
                    Text(
                        text = when (currentPeriod.state) {
                            PeriodState.COMPLETE -> "Сегодня закрыто"
                            PeriodState.PARTIAL -> "Сегодня ${currentPeriod.points}/${currentPeriod.maxPoints}"
                            PeriodState.PENDING -> "Нужна отметка"
                            PeriodState.MISSED -> "Пропуск"
                            PeriodState.FUTURE -> "Скоро"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (!joined) {
                    Text(
                        "Присоединиться →",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (joined && myScore != null && myScore.maxPoints > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (myScore.points.toFloat() / myScore.maxPoints).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
fun SectionTitle(
    title: String,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    text: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onAction) { Text(actionText) }
        }
    }
}

@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false
) {
    val color = if (warning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (warning) Icons.Default.Warning else Icons.Default.Info,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun MedalBadge(medal: Medal, compact: Boolean = false) {
    Surface(
        color = medalColor(medal.place).copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(medal.emoji, style = MaterialTheme.typography.titleMedium)
            if (!compact) {
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(medal.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        medal.challengeTitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Spacer(Modifier.width(4.dp))
                Text(medal.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

fun medalColor(place: Int): Color = when (place) {
    1 -> ScoreColors.gold
    2 -> ScoreColors.silver
    3 -> ScoreColors.bronze
    else -> ScoreColors.pending
}

fun stateColor(state: PeriodState): Color = when (state) {
    PeriodState.COMPLETE -> ScoreColors.complete
    PeriodState.PARTIAL -> ScoreColors.partial
    PeriodState.MISSED -> ScoreColors.missed
    PeriodState.PENDING -> ScoreColors.pending
    PeriodState.FUTURE -> ScoreColors.future
}

fun stateLabel(state: PeriodState): String = when (state) {
    PeriodState.COMPLETE -> "Выполнено"
    PeriodState.PARTIAL -> "Частично"
    PeriodState.MISSED -> "Пропуск"
    PeriodState.PENDING -> "Ожидается"
    PeriodState.FUTURE -> "Впереди"
}

@Composable
fun ProofChip(
    attachment: Attachment,
    model: Any?,
    onClick: () -> Unit
) {
    val isVideo = attachment.type == com.onlinechallenges.app.data.AttachmentType.VIDEO
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isVideo && model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(6.dp))
            } else {
                Text(if (isVideo) "🎬" else "📷", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = if (isVideo) "Видео" else "Фото",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isVideo) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(formatBirthDate(value.toString()), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        }
    }
    if (showDialog) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = state.selectedDateMillis
                        if (millis != null) {
                            val selected = java.time.Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                            val clamped = when {
                                minDate != null && selected.isBefore(minDate) -> minDate
                                maxDate != null && selected.isAfter(maxDate) -> maxDate
                                else -> selected
                            }
                            onDateSelected(clamped)
                        }
                        showDialog = false
                    }
                ) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun GenderSelector(
    selected: Gender,
    onSelect: (Gender) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Gender.entries.forEach { gender ->
            FilterChip(
                selected = selected == gender,
                onClick = { onSelect(gender) },
                label = { Text(gender.label, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
}

@Composable
fun ScoreSummary(
    points: Int,
    maxPoints: Int,
    modifier: Modifier = Modifier
) {
    val percent = if (maxPoints <= 0) 0 else (points * 100 / maxPoints).coerceIn(0, 100)
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                "$points / $maxPoints",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text("баллов · $percent%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun QuickStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DateChip(date: LocalDate, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                date.format(DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("ru"))),
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.8f)
            )
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = fg,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun LinkRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SuccessCheck(visible: Boolean) {
    AnimatedVisibility(visible) {
        Icon(Icons.Default.Check, contentDescription = null, tint = ScoreColors.complete)
    }
}

@Composable
fun TriStateChip(text: String, color: Color, active: Boolean) {
    Surface(
        color = if (active) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun CompactMedalRow(medals: List<Medal>) {
    if (medals.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        medals.sortedBy { it.place }.take(5).forEach { medal ->
            Surface(
                color = medalColor(medal.place).copy(alpha = 0.15f),
                shape = CircleShape,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(medal.emoji, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun PodiumCard(
    scores: List<com.onlinechallenges.app.data.ParticipantScore>,
    finished: Boolean,
    avatarModel: (Profile?) -> Any? = { null },
    modifier: Modifier = Modifier
) {
    val top = scores.filter { it.place in 1..3 }.sortedBy { it.place }
    if (top.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (finished) "🏆" else "🔥", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (finished) "Итоговый пьедестал" else "Лидеры сейчас",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(10.dp))
            top.forEach { score ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = medalColor(score.place).copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                when (score.place) {
                                    1 -> "🥇"
                                    2 -> "🥈"
                                    else -> "🥉"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    UserAvatar(profile = score.profile, model = avatarModel(score.profile), size = 32.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(score.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            if (finished) "${score.place} место" else "место ${score.place}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${score.points} б.",
                        style = MaterialTheme.typography.titleMedium,
                        color = medalColor(score.place),
                        fontWeight = FontWeight.Bold
                    )
                }
                if (score != top.last()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
