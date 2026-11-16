package com.onlinechallenges.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onlinechallenges.app.BuildConfig
import com.onlinechallenges.app.ui.AppViewModel
import com.onlinechallenges.app.ui.InfoBanner
import com.onlinechallenges.app.ui.LinkRow
import com.onlinechallenges.app.ui.SectionTitle
import com.onlinechallenges.app.ui.formatTimestamp
import com.onlinechallenges.app.ui.openUrl

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val syncState by vm.syncState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var owner by remember(config.owner) { mutableStateOf(config.owner) }
    var repo by remember(config.repo) { mutableStateOf(config.repo) }
    var branch by remember(config.branch) { mutableStateOf(config.branch) }
    var token by remember { mutableStateOf("") }
    var autoSync by remember(config.autoSync) { mutableStateOf(config.autoSync) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmClearDemo by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp)
    ) {
        Text("Синхронизация", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Общий каталог, челленджи, отметки и подтверждения хранятся в публичном GitHub-репозитории. " +
                "Токен нужен только для записи и хранится на устройстве в зашифрованном виде.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it; error = null },
                    label = { Text("Владелец репозитория") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it; error = null },
                    label = { Text("Репозиторий") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Ветка") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; error = null },
                    label = { Text(if (config.token.isBlank()) "Personal access token" else "Новый токен (необязательно)") },
                    placeholder = { Text(if (config.token.isBlank()) "github_pat_…" else "Токен уже сохранён") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Автосинхронизация", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Отправлять изменения сразу после сохранения",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoSync, onCheckedChange = { autoSync = it })
                }
                Spacer(Modifier.height(12.dp))
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        error = vm.saveGitHubConfig(owner, repo, branch, token, autoSync)
                        if (error == null) token = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = MaterialTheme.shapes.small
                ) { Text("Сохранить настройки") }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.testConnection() },
                        enabled = !syncState.running,
                        modifier = Modifier.weight(1f)
                    ) { Text("Проверить") }
                    OutlinedButton(
                        onClick = { vm.syncNow() },
                        enabled = !syncState.running,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (syncState.running) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Синхронизировать")
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        when {
            syncState.running -> InfoBanner(text = syncState.message ?: "Синхронизация…")
            syncState.message != null -> InfoBanner(text = syncState.message!!, warning = syncState.error)
        }
        if (syncState.lastSync != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Последняя синхронизация: ${formatTimestamp(syncState.lastSync)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(18.dp))
        SectionTitle("Репозиторий", subtitle = "${config.owner}/${config.repo}")
        Spacer(Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                LinkRow(
                    title = "GitHub-репозиторий",
                    subtitle = config.repoUrl,
                    onClick = { openUrl(context, config.repoUrl) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinkRow(
                    title = "Сайт с итогами",
                    subtitle = config.pagesUrl,
                    onClick = { openUrl(context, config.pagesUrl) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinkRow(
                    title = "Подтверждения",
                    subtitle = "confirmations/${vm.userCode()}/",
                    onClick = { openUrl(context, config.pagesUrl + "confirmations/") }
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        InfoBanner(
            text = "Как получить токен: GitHub → Settings → Developer settings → Personal access tokens → " +
                "Fine-grained tokens. Дайте доступ только к репозиторию ${config.owner}/${config.repo} и право " +
                "Contents: Read and write. Классический токен с областью repo тоже подойдёт.",
            warning = true
        )
        if (config.token.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { vm.clearGitHubToken() }) { Text("Удалить токен с устройства") }
        }
        Spacer(Modifier.height(18.dp))
        SectionTitle("Данные", subtitle = "Демо-примеры не синхронизируются")
        Spacer(Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "При первом запуске добавляются демонстрационные участники и челленджи с пометкой ДЕМО. " +
                        "Они нужны, чтобы посмотреть статистику, медали и подтверждения без подключения GitHub.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { confirmClearDemo = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Удалить демо-данные") }
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionTitle("О приложении")
        Spacer(Modifier.height(6.dp))
        Text(
            "Онлайн челленджи · версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                "Email-подтверждение работает в демо-режиме: код показывается на экране проверки. " +
                "Почтовый сервер не подключён.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
    }

    if (confirmClearDemo) {
        AlertDialog(
            onDismissRequest = { confirmClearDemo = false },
            title = { Text("Удалить демо-данные?") },
            text = {
                Text(
                    "Будут удалены только демонстрационные профили, челленджи и отметки. " +
                        "Ваши собственные данные останутся."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearDemo = false
                        vm.clearDemoData()
                    }
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDemo = false }) { Text("Отмена") }
            }
        )
    }
}
