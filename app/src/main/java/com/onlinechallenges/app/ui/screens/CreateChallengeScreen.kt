package com.onlinechallenges.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onlinechallenges.app.data.ActivityType
import com.onlinechallenges.app.data.ChallengeGoal
import com.onlinechallenges.app.data.ChallengeMode
import com.onlinechallenges.app.data.ChallengeVisibility
import com.onlinechallenges.app.ui.ActivityIcon
import com.onlinechallenges.app.ui.AppViewModel
import com.onlinechallenges.app.ui.DateField
import com.onlinechallenges.app.ui.InfoBanner
import com.onlinechallenges.app.ui.PrivateBadge
import com.onlinechallenges.app.ui.SectionTitle
import com.onlinechallenges.app.ui.formatNumber
import com.onlinechallenges.app.ui.formatTarget
import java.time.LocalDate

@Composable
fun CreateChallengeScreen(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now().plusDays(30)) }
    var mode by remember { mutableStateOf(ChallengeMode.DAILY) }
    var visibility by remember { mutableStateOf(ChallengeVisibility.PUBLIC) }
    var intervalDays by remember { mutableStateOf("3") }
    val selected = remember { mutableStateMapOf<String, String>() }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 36.dp)
    ) {
        item {
            Text("Новый челлендж", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Задайте период, режим и цели. Общий челлендж видят все, приватный — только участники по коду приглашения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; error = null },
                label = { Text("Название") },
                placeholder = { Text("Например, «Май без пропусков»") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание (необязательно)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
            SectionTitle("Доступ", subtitle = "Кто сможет видеть данные челленджа")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChallengeVisibility.entries.forEach { option ->
                    FilterChip(
                        selected = visibility == option,
                        onClick = { visibility = option; error = null },
                        label = {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
                if (visibility == ChallengeVisibility.PRIVATE) {
                    Spacer(Modifier.width(2.dp))
                    PrivateBadge()
                }
            }
            Spacer(Modifier.height(8.dp))
            InfoBanner(
                text = if (visibility == ChallengeVisibility.PRIVATE) {
                    "Приватный челлендж: посты, фото, видео и подтверждения шифруются. Посторонние увидят только название и метку «Приватный», на сайте — только очки и места. Код приглашения появится на странице челленджа, передайте его участникам."
                } else {
                    "Общий челлендж: цели, результаты и фото видны всем участникам и на публичном сайте."
                }
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField(
                    label = "Начало",
                    value = startDate,
                    onDateSelected = {
                        startDate = it
                        if (endDate.isBefore(it)) endDate = it
                        error = null
                    },
                    minDate = LocalDate.now(),
                    maxDate = endDate,
                    modifier = Modifier.weight(1f)
                )
                DateField(
                    label = "Окончание",
                    value = endDate,
                    onDateSelected = { endDate = it; error = null },
                    minDate = startDate,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Период: ${com.onlinechallenges.app.data.Dates.range(startDate, endDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            SectionTitle("Режим", subtitle = "Как часто выполнять цели")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChallengeMode.entries.forEach { option ->
                    FilterChip(
                        selected = mode == option,
                        onClick = { mode = option; error = null },
                        label = {
                            Text(
                                when (option) {
                                    ChallengeMode.DAILY -> "Каждый день"
                                    ChallengeMode.INTERVAL -> "Каждые N дней"
                                    ChallengeMode.TOTAL -> "За период"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
            if (mode == ChallengeMode.INTERVAL) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = intervalDays,
                    onValueChange = {
                        intervalDays = it.filter { ch -> ch.isDigit() }.take(2)
                        error = null
                    },
                    label = { Text("Интервал, дней") },
                    supportingText = { Text("Например, 3 — окно из трёх дней. Значения внутри окна суммируются.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(8.dp))
            InfoBanner(
                text = when (mode) {
                    ChallengeMode.DAILY -> "Одна отметка в день. Каждая цель дня оценивается отдельно: 1 балл за полное выполнение."
                    ChallengeMode.INTERVAL -> "Окно из N дней. Можно отмечаться в любой день, результаты окна складываются, баллы даются за закрытые цели окна."
                    ChallengeMode.TOTAL -> "Цель общая на весь период. Балл за цель начисляется, когда суммарный результат достигнет цели."
                }
            )
            Spacer(Modifier.height(18.dp))
            SectionTitle("Цели", subtitle = "Можно выбрать несколько")
            Spacer(Modifier.height(4.dp))
        }
        items(data.activities, key = { it.id }) { activity ->
            val isSelected = selected.containsKey(activity.id)
            GoalSelectorCard(
                activity = activity,
                selected = isSelected,
                target = selected[activity.id].orEmpty(),
                onToggle = {
                    if (isSelected) selected.remove(activity.id)
                    else selected[activity.id] = formatNumber(activity.defaultTarget).replace(" ", "")
                    error = null
                },
                onTargetChange = { selected[activity.id] = it; error = null }
            )
        }
        item {
            Spacer(Modifier.height(8.dp))
            val goalPreview = data.activities.filter { selected.containsKey(it.id) }.map { activity ->
                val target = selected[activity.id].orEmpty()
                "${activity.emoji} $target ${activity.unit}"
            }
            if (goalPreview.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Предпросмотр целей", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(goalPreview.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = submit@{
                    val goals = mutableListOf<ChallengeGoal>()
                    for (activity in data.activities) {
                        val raw = selected[activity.id] ?: continue
                        val target = raw.trim().replace(',', '.').toDoubleOrNull()
                        if (target == null || target <= 0) {
                            error = "Укажите цель больше нуля для «${activity.name}»"
                            return@submit
                        }
                        goals += ChallengeGoal(
                            id = "goal-${activity.id}",
                            activityId = activity.id,
                            name = activity.name,
                            unit = activity.unit,
                            emoji = activity.emoji,
                            target = target,
                            proofType = activity.proofType,
                            iconPath = activity.iconPath
                        )
                    }
                    val interval = intervalDays.toIntOrNull() ?: 0
                    if (mode == ChallengeMode.INTERVAL && interval !in 2..30) {
                        error = "Интервал должен быть от 2 до 30 дней"
                        return@submit
                    }
                    val message = vm.createChallenge(
                        title = title,
                        description = description,
                        startDate = startDate.toString(),
                        endDate = endDate.toString(),
                        mode = mode,
                        intervalDays = interval,
                        goals = goals,
                        visibility = visibility
                    )
                    error = message
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.small
            ) { Text("Создать и участвовать") }
            Spacer(Modifier.height(12.dp))
            Text(
                "Баллы: 1 за каждую полностью выполненную цель. Частичное выполнение показывается цветом, но баллов не даёт. Больше нормы — не влияет.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GoalSelectorCard(
    activity: ActivityType,
    selected: Boolean,
    target: String,
    onToggle: () -> Unit,
    onTargetChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(end = 14.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = { onToggle() })
                ActivityIcon(
                    activityId = activity.id,
                    emoji = activity.emoji,
                    iconPath = activity.iconPath,
                    size = 26.dp
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(activity.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "по умолчанию ${formatNumber(activity.defaultTarget)} ${activity.unit} · ${activity.proofType.label.lowercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = target,
                        onValueChange = onTargetChange,
                        label = { Text("Цель") },
                        suffix = { Text(activity.unit) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(activity.proofType.shortLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
