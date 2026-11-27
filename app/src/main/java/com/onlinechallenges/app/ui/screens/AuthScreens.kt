package com.onlinechallenges.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onlinechallenges.app.ui.AppViewModel
import com.onlinechallenges.app.ui.InfoBanner
import com.onlinechallenges.app.ui.theme.BrandGradient

@Composable
fun AuthScreen(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val remoteError by vm.authError.collectAsStateWithLifecycle()
    val busy by vm.authBusy.collectAsStateWithLifecycle()
    var mode by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val error = localError ?: remoteError

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(BrandGradient, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚡", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        "Онлайн челленджи",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "привычки, спорт и честная борьба",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "Бросай себе вызов.\nСоревнуйся с другими.",
                style = MaterialTheme.typography.displaySmall
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Шаги, пробежки, сон, планка, отжимания и приседания. " +
                    "Отмечай результат каждый день, прикладывай фото/видео или подтверждай чужие цели.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeaturePill("👟 Шаги")
                FeaturePill("🧘 Планка")
                FeaturePill("💪 Отжимания")
            }
            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    TabRow(selectedTabIndex = mode) {
                        Tab(
                            selected = mode == 0,
                            onClick = { mode = 0; localError = null; vm.clearAuthError() },
                            text = { Text("Вход") }
                        )
                        Tab(
                            selected = mode == 1,
                            onClick = { mode = 1; localError = null; vm.clearAuthError() },
                            text = { Text("Регистрация") }
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it.trim()
                            localError = null
                            vm.clearAuthError()
                        },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            localError = null
                            vm.clearAuthError()
                        },
                        label = { Text("Пароль") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(
                                    if (showPassword) "Скрыть" else "Показать",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (mode == 1) ImeAction.Next else ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mode == 1) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = confirm,
                            onValueChange = { confirm = it; localError = null; vm.clearAuthError() },
                            label = { Text("Повторите пароль") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (error != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            localError = if (mode == 0) {
                                vm.login(email, password)
                            } else {
                                vm.register(email, password, confirm)
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(if (mode == 0) "Войти" else "Зарегистрироваться")
                    }
                    if (mode == 0) {
                        TextButton(
                            onClick = { vm.openForgotPassword() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Забыл пароль") }
                    } else {
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = { localError = vm.registerLocal(email, password, confirm) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Зарегистрироваться локально") }
                        Text(
                            "Локальный аккаунт живёт только на этом телефоне. Позже обязательно добавьте email в профиль, иначе восстановить пароль не получится.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (mode == 0) {
                            "Нет аккаунта? Вкладка «Регистрация»."
                        } else {
                            "После регистрации на email придёт ссылка подтверждения. Откройте её, затем войдите."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (busy) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Подождите…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            InfoBanner(
                text = "Профиль хранится на устройстве, а общие челленджи, отметки и подтверждения синхронизируются с GitHub. Подключение уже настроено в приложении."
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Вход через Supabase · версия ${com.onlinechallenges.app.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FeaturePill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun VerifyScreen(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val remoteError by vm.authError.collectAsStateWithLifecycle()
    val busy by vm.authBusy.collectAsStateWithLifecycle()
    val email = data.pendingEmail.orEmpty()
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(BrandGradient, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("✉️", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(16.dp))
        Text("Подтвердите email", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Мы отправили код подтверждения на $email. Введите шестизначный код из письма.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Код приходит на настоящую почту и действует один час. Если вы открыли ссылку из письма " +
                        "на телефоне, приложение подтвердит email автоматически.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { value ->
                code = value.filter { it.isDigit() }.take(6)
            },
            label = { Text("Код из письма") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        if (remoteError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                remoteError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.verifySignupCode(code) },
            enabled = !busy && code.length == 6,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = MaterialTheme.shapes.small
        ) { Text("Подтвердить код") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.completeEmailCheck() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = MaterialTheme.shapes.small
        ) { Text("Я подтвердил(а) — войти") }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { vm.resendConfirmation() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Отправить письмо повторно") }
        TextButton(
            onClick = { vm.cancelVerification() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Отмена") }
    }
}

@Composable
fun ForgotPasswordScreen(vm: AppViewModel) {
    val remoteError by vm.authError.collectAsStateWithLifecycle()
    val busy by vm.authBusy.collectAsStateWithLifecycle()
    val sent by vm.passwordResetSent.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val error = localError ?: remoteError

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Восстановление пароля", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Введите email аккаунта. Придёт ссылка для установки нового пароля.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        InfoBanner(
            text = "Открывайте письмо только на телефоне — с компьютера ссылка не откроет приложение. " +
                "Запросите сброс и перейдите по ссылке на одном и том же телефоне."
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it.trim()
                localError = null
                vm.clearAuthError()
            },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { localError = vm.requestPasswordReset(email) },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = MaterialTheme.shapes.small
        ) { Text("Отправить ссылку") }
        if (sent) {
            Spacer(Modifier.height(16.dp))
            InfoBanner(
                text = "Откройте ссылку из письма на том же телефоне, где нажали «Забыл пароль»: откроется экран нового пароля. " +
                    "Если браузер покажет пустую страницу или «See Other», нажмите ссылку на ней. " +
                    "С компьютера приложение не откроется."
            )
        }
        TextButton(
            onClick = { vm.back() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Назад") }
    }
}

@Composable
fun ResetPasswordScreen(vm: AppViewModel) {
    val remoteError by vm.authError.collectAsStateWithLifecycle()
    val busy by vm.authBusy.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val error = localError ?: remoteError

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Новый пароль", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Введите и подтвердите новый пароль для аккаунта.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; localError = null; vm.clearAuthError() },
            label = { Text("Новый пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it; localError = null; vm.clearAuthError() },
            label = { Text("Повторите пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { localError = vm.updatePassword(password, confirm) },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = MaterialTheme.shapes.small
        ) { Text("Сохранить пароль") }
    }
}
