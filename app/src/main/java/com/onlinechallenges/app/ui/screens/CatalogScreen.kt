package com.onlinechallenges.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onlinechallenges.app.data.ActivityType
import com.onlinechallenges.app.data.ProofType
import com.onlinechallenges.app.ui.AppViewModel
import com.onlinechallenges.app.ui.InfoBanner
import com.onlinechallenges.app.ui.SectionTitle
import com.onlinechallenges.app.ui.formatNumber

@Composable
fun CatalogScreen(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    val activities = data.activities.sortedWith(
        compareByDescending<ActivityType> { it.isDefault }.thenBy { it.name }
    )

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp)
    ) {
        item {
            InfoBanner(
                text = "Каталог общий. Добавьте свою активность — после синхронизации она появится у всех участников и её можно будет выбрать при создании челленджа."
            )
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Добавить активность")
            }
            Spacer(Modifier.height(18.dp))
            SectionTitle(
                "Активности",
                subtitle = "${activities.size} видов · цель по умолчанию"
            )
            Spacer(Modifier.height(6.dp))
        }
        items(activities, key = { it.id }) { activity ->
            ActivityRow(activity)
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text(
                "Подтверждение: для шагов, пробежки и сна обычно подходит скриншот трекера; для планки, отжиманий и приседаний — видео.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showAdd) {
        AddActivityDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, unit, emoji, target, proofType, errorCallback ->
                val error = vm.addActivity(name, unit, emoji, target, proofType)
                if (error == null) showAdd = false
                errorCallback(error)
            }
        )
    }
}

@Composable
private fun ActivityRow(activity: ActivityType) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(activity.emoji, style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(activity.name, style = MaterialTheme.typography.titleMedium)
                    if (activity.isDefault) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "база",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    "Цель: ${formatNumber(activity.defaultTarget)} ${activity.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Подтверждение: ${activity.proofType.label.lowercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(activity.proofType.shortLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun AddActivityDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, ProofType, (String?) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🎯") }
    var proofType by remember { mutableStateOf(ProofType.PHOTO) }
    var error by remember { mutableStateOf<String?>(null) }
    val emojis = listOf("🎯", "🏋️", "🧘", "🚴", "🏊", "🥾", "💧", "🍎", "🧗", "🤸", "⏱️", "🦵")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая активность") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it; error = null },
                        label = { Text("Единица") },
                        placeholder = { Text("раз, км, мин") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it; error = null },
                        label = { Text("Цель") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("Иконка", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    emojis.take(6).forEach { option ->
                        EmojiChip(option, emoji == option) { emoji = option }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    emojis.drop(6).forEach { option ->
                        EmojiChip(option, emoji == option) { emoji = option }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Что прикладывать", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProofType.entries.forEach { type ->
                        FilterChip(
                            selected = proofType == type,
                            onClick = { proofType = type },
                            label = { Text(type.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(name, unit, emoji, target, proofType) { message -> error = message }
                }
            ) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun EmojiChip(emoji: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
        }
    }
}
