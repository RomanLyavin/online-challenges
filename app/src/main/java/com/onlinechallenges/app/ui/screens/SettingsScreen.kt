package com.onlinechallenges.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onlinechallenges.app.BuildConfig
import com.onlinechallenges.app.ui.AppViewModel
import com.onlinechallenges.app.ui.InfoBanner
import com.onlinechallenges.app.ui.LinkRow
import com.onlinechallenges.app.ui.SectionTitle
import com.onlinechallenges.app.ui.formatTimestamp
import com.onlinechallenges.app.ui.openUrl
import com.onlinechallenges.app.update.GitHubUpdate
import com.onlinechallenges.app.update.UpdateState
import java.io.File

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val syncState by vm.syncState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var autoSync by remember(config.autoSync) { mutableStateOf(config.autoSync) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmClearDemo by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }
    var showInstallPermission by remember { mutableStateOf(false) }
    var pendingApk by remember { mutableStateOf<File?>(null) }
    val updateState by vm.updateState.collectAsStateWithLifecycle()

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val file = pendingApk
        pendingApk = null
        if (file != null && context.packageManager.canRequestPackageInstalls()) {
            startApkInstall(context, file)
        } else {
            Toast.makeText(
                context,
                "Разрешение на установку не выдано.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(updateState) {
        val ready = updateState as? UpdateState.Ready ?: return@LaunchedEffect
        pendingApk = ready.file
        if (context.packageManager.canRequestPackageInstalls()) {
            pendingApk = null
            startApkInstall(context, ready.file)
        } else {
            showInstallPermission = true
        }
        vm.consumeReadyUpdate()
    }

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
            "Общий каталог, челленджи и отметки синхронизируются автоматически. " +
                "Настройки подключения уже встроены в приложение и не требуют ввода.",
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
                    onClick = { error = vm.saveSyncSettings(autoSync) },
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
            }
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
                        "Они нужны, чтобы посмотреть статистику и медали без синхронизации.",
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
        SectionTitle("Приложение")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showAbout = true },
                modifier = Modifier.weight(1f)
            ) { Text("О программе") }
            OutlinedButton(
                onClick = { showSupport = true },
                modifier = Modifier.weight(1f)
            ) { Text("Поддержать") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                "Подтверждение email и сброс пароля работают через Supabase Auth: " +
                "ссылки приходят на настоящую почту.",
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

    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onUpdate = { showAbout = false; vm.checkForUpdates() },
            onEmail = { openUrl(context, "mailto:lyrv@t-inform.ru") },
            onGitHub = { openUrl(context, GitHubUpdate.REPO_URL) }
        )
    }

    if (showSupport) {
        SupportDialog(
            onDismiss = { showSupport = false },
            onSupport = { openUrl(context, SUPPORT_URL) }
        )
    }

    UpdateStateDialogs(
        state = updateState,
        onDismiss = { vm.dismissUpdate() },
        onDownload = { vm.downloadUpdate(it) },
        onRetry = { vm.retryUpdate() }
    )

    if (showInstallPermission) {
        InstallPermissionDialog(
            onDismiss = {
                showInstallPermission = false
                pendingApk = null
            },
            onOpenSettings = {
                showInstallPermission = false
                try {
                    installPermissionLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                } catch (error: Exception) {
                    pendingApk = null
                    Toast.makeText(
                        context,
                        "Не удалось открыть настройки установки.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}

private const val SUPPORT_URL = "https://t.tb.ru/pm_short/AOdA22MAdsS"
