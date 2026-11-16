package com.onlinechallenges.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onlinechallenges.app.data.Challenge
import com.onlinechallenges.app.data.ChallengeEngine
import com.onlinechallenges.app.data.ChallengeMode
import com.onlinechallenges.app.data.Dates
import com.onlinechallenges.app.data.GoalResult
import com.onlinechallenges.app.data.MediaManager
import com.onlinechallenges.app.data.ProofType
import com.onlinechallenges.app.ui.AppViewModel
import com.onlinechallenges.app.ui.DateChip
import com.onlinechallenges.app.ui.DateField
import com.onlinechallenges.app.ui.EmptyState
import com.onlinechallenges.app.ui.InfoBanner
import com.onlinechallenges.app.ui.ProofChip
import com.onlinechallenges.app.ui.SectionTitle
import com.onlinechallenges.app.ui.formatNumber
import com.onlinechallenges.app.ui.formatTarget
import com.onlinechallenges.app.ui.openAttachment
import com.onlinechallenges.app.ui.stateColor
import com.onlinechallenges.app.data.PeriodState
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckInScreen(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val busyGoal by vm.mediaBusyGoal.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var pendingGoalId by remember { mutableStateOf<String?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val goalId = pendingGoalId
        if (uri != null && goalId != null) vm.attachMedia(uri, goalId)
        pendingGoalId = null
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val goalId = pendingGoalId
        if (uri != null && goalId != null) vm.attachMedia(uri, goalId)
        pendingGoalId = null
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val goalId = pendingGoalId
        val file = cameraFile
        if (success && file != null && goalId != null) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            vm.attachMedia(uri, goalId)
        } else {
            file?.delete()
        }
        pendingGoalId = null
        cameraFile = null
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        val goalId = pendingGoalId
        val file = cameraFile
        if (success && file != null && goalId != null) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            vm.attachMedia(uri, goalId)
        } else {
            file?.delete()
        }
        pendingGoalId = null
        cameraFile = null
    }

    if (draft == null) {
        EmptyState(
            emoji = "✏️",
            title = "Отметка не открыта",
            text = "Вернитесь к челленджу и выберите дату.",
            actionText = "Назад",
            onAction = { vm.back() }
        )
        return
    }
    val current = draft!!
    val challenge = data.challenge(current.challengeId)
    val profile = data.currentProfile()
    if (challenge == null || profile == null) {
        EmptyState(
            emoji = "🔎",
            title = "Челлендж не найден",
            text = "Попробуйте открыть его заново.",
            actionText = "Назад",
            onAction = { vm.back() }
        )
        return
    }
    val date = LocalDate.parse(current.date)
    val today = LocalDate.now()

    val otherCheckIns = data.checkIns.filter {
        it.challengeId == challenge.id && it.userCode == profile.code && it.date != current.date
    }
    val slot = slotBounds(challenge, date)
    val periodCheckIns = otherCheckIns.filter { checkIn ->
        val d = checkIn.localDate
        !d.isBefore(slot.first) && !d.isAfter(slot.second)
    }
    val accumulated = challenge.goals.associate { goal ->
        val existing = periodCheckIns.sumOf { checkIn ->
            checkIn.resultFor(goal.id)?.actual ?: 0.0
        }
        goal.id to existing
    }
    val draftPreview = challenge.goals.map { goal ->
        val actual = parseInput(current.values[goal.id]) ?: 0.0
        val attachments = current.attachments[goal.id].orEmpty()
        val proofs = ChallengeEngine.validProofs(attachments, goal.proofType)
        val total = (if (challenge.mode == ChallengeMode.DAILY) 0.0 else accumulated[goal.id] ?: 0.0) + actual
        goal to (total >= goal.target && proofs.isNotEmpty())
    }
    val previewPoints = draftPreview.count { it.second }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 34.dp),
        modifier = Modifier.imePadding()
    ) {
        item {
            Text(challenge.title, style = MaterialTheme.typography.titleLarge)
            Text(
                "Отметка за ${Dates.long(date)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            val recentDates = buildList {
                var d = today
                val earliest = maxOf(challenge.start, today.minusDays(6))
                while (!d.isBefore(earliest)) {
                    add(d)
                    d = d.minusDays(1)
                }
            }.reversed()
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                recentDates.forEach { option ->
                    DateChip(option, option == date) { vm.setDraftDate(option.toString()) }
                }
            }
            Spacer(Modifier.height(10.dp))
            DateField(
                label = "Другая дата",
                value = date,
                onDateSelected = { vm.setDraftDate(it.toString()) },
                minDate = challenge.start,
                maxDate = minOf(challenge.end, today)
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Будет начислено: $previewPoints из ${challenge.goals.size}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Балл — только за полную цель с подтверждением",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "$previewPoints б.",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            if (challenge.mode == ChallengeMode.INTERVAL) {
                Spacer(Modifier.height(8.dp))
                InfoBanner(
                    text = "Окно ${Dates.shortRange(slot.first, slot.second)}. Результаты всех дней окна складываются; балл даётся за закрытую цель окна."
                )
            } else if (challenge.mode == ChallengeMode.TOTAL) {
                Spacer(Modifier.height(8.dp))
                InfoBanner(
                    text = "Цель общая на весь период. Введите вклад за этот день — он прибавится к накопленному результату."
                )
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("Цели", subtitle = "Введите фактический результат")
            Spacer(Modifier.height(6.dp))
        }
        items(challenge.goals, key = { it.id }) { goal ->
            val value = current.values[goal.id].orEmpty()
            val attachments = current.attachments[goal.id].orEmpty()
            val actual = parseInput(value) ?: 0.0
            val proofs = ChallengeEngine.validProofs(attachments, goal.proofType)
            val accumulatedBefore = accumulated[goal.id] ?: 0.0
            val totalWithInput = (if (challenge.mode == ChallengeMode.DAILY) 0.0 else accumulatedBefore) + actual
            val complete = totalWithInput >= goal.target && proofs.isNotEmpty()
            val needsProof = totalWithInput >= goal.target && proofs.isEmpty()
            val busy = busyGoal == goal.id
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(goal.emoji, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(goal.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Цель: ${formatTarget(goal)} · ${goal.proofType.label.lowercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = if (complete) stateColor(PeriodState.COMPLETE).copy(alpha = 0.14f)
                            else stateColor(PeriodState.PENDING).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                if (complete) "+1" else "0",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (complete) stateColor(PeriodState.COMPLETE) else stateColor(PeriodState.PENDING),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = value,
                        onValueChange = { vm.setDraftValue(goal.id, it); error = null },
                        label = { Text(if (challenge.mode == ChallengeMode.TOTAL) "Сколько за этот день" else "Фактически") },
                        suffix = { Text(goal.unit) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (challenge.mode != ChallengeMode.DAILY) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            (if (challenge.mode == ChallengeMode.TOTAL) "Накоплено за период: " else "В окне уже: ") +
                                "${formatNumber(accumulatedBefore)} · с этой отметкой: ${formatNumber(totalWithInput)} / ${formatNumber(goal.target)} ${goal.unit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (totalWithInput >= goal.target) stateColor(PeriodState.COMPLETE) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (needsProof) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Нужно подтверждение: ${goal.proofType.label.lowercase()}. Без него балл не начислится.",
                            style = MaterialTheme.typography.bodySmall,
                            color = stateColor(PeriodState.PARTIAL)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (attachments.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            attachments.forEach { attachment ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ProofChip(
                                        attachment = attachment,
                                        model = vm.attachmentModel(attachment),
                                        onClick = { openAttachment(context, attachment, vm) }
                                    )
                                    TextButton(
                                        onClick = { vm.removeDraftAttachment(goal.id, attachment.id) },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                        modifier = Modifier.size(30.dp)
                                    ) { Text("×", style = MaterialTheme.typography.titleMedium) }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Обрабатываем файл…", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (goal.proofType == ProofType.PHOTO || goal.proofType == ProofType.ANY) {
                                OutlinedButton(onClick = {
                                    pendingGoalId = goal.id
                                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }) { Text("📷 Галерея") }
                                OutlinedButton(onClick = {
                                    pendingGoalId = goal.id
                                    val file = File.createTempFile("proof_", ".jpg", context.cacheDir)
                                    cameraFile = file
                                    cameraLauncher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                                }) { Text("Сделать фото") }
                            }
                            if (goal.proofType == ProofType.VIDEO || goal.proofType == ProofType.ANY) {
                                OutlinedButton(onClick = {
                                    pendingGoalId = goal.id
                                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                }) { Text("🎬 Галерея") }
                                OutlinedButton(onClick = {
                                    pendingGoalId = goal.id
                                    val file = File.createTempFile("proof_", ".mp4", context.cacheDir)
                                    cameraFile = file
                                    videoLauncher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                                }) { Text("Записать видео") }
                            }
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = current.note,
                onValueChange = { vm.setDraftNote(it) },
                label = { Text("Комментарий (необязательно)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            InfoBanner(
                text = "Фото подходит для скриншотов шагов, пробежки и сна. Для планки, отжиманий, приседаний и пресса нужно видео. Файлы до 25 МБ; фото сжимаются автоматически.",
                warning = false
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { error = vm.saveCheckIn() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.small
            ) { Text(if (current.existing) "Обновить отметку" else "Сохранить отметку") }
            if (current.existing) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Отметку можно изменить до конца челленджа.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun parseInput(value: String?): Double? =
    value?.trim()?.replace(',', '.')?.toDoubleOrNull()

private fun slotBounds(challenge: Challenge, date: LocalDate): Pair<LocalDate, LocalDate> = when (challenge.mode) {
    ChallengeMode.DAILY -> date to date
    ChallengeMode.INTERVAL -> {
        val index = (ChronoUnit.DAYS.between(challenge.start, date) / challenge.intervalDays).coerceAtLeast(0)
        val start = challenge.start.plusDays(index * challenge.intervalDays)
        val end = minOf(start.plusDays(challenge.intervalDays.toLong() - 1), challenge.end)
        start to end
    }
    ChallengeMode.TOTAL -> challenge.start to challenge.end
}
