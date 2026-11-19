package com.onlinechallenges.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onlinechallenges.app.data.Challenge
import com.onlinechallenges.app.data.Gender
import com.onlinechallenges.app.data.Profile
import com.onlinechallenges.app.data.normalizeChallenges
import com.onlinechallenges.app.ui.AppViewModel
import com.onlinechallenges.app.ui.ChallengeCard
import com.onlinechallenges.app.ui.DateField
import com.onlinechallenges.app.ui.EmptyState
import com.onlinechallenges.app.ui.GenderSelector
import com.onlinechallenges.app.ui.GradientHeader
import com.onlinechallenges.app.ui.InfoBanner
import com.onlinechallenges.app.ui.LinkRow
import com.onlinechallenges.app.ui.MedalBadge
import com.onlinechallenges.app.ui.SectionTitle
import com.onlinechallenges.app.ui.UserAvatar
import com.onlinechallenges.app.ui.theme.BrandGradient
import java.io.File
import java.time.LocalDate

@Composable
fun SetupProfileScreen(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val profile = data.currentProfile()
    val avatarPath by vm.avatarDraft.collectAsStateWithLifecycle()
    val avatarBusy by vm.avatarBusy.collectAsStateWithLifecycle()
    val editing = profile?.isComplete == true
    var name by remember(profile?.code) { mutableStateOf(profile?.name.orEmpty()) }
    var birthDate by remember(profile?.code) {
        mutableStateOf(profile?.birthDate?.takeIf { it.isNotBlank() } ?: LocalDate.now().minusYears(25).toString())
    }
    var gender by remember(profile?.code) { mutableStateOf(profile?.gender ?: Gender.UNSPECIFIED) }
    var error by remember { mutableStateOf<String?>(null) }
    val avatarModel = avatarPath?.let { File(it) } ?: vm.avatarModel(profile)
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickAvatar(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp)
    ) {
        if (!editing) {
            Text("Заполните профиль", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Это увидят другие участники. Email и пароль остаются только на устройстве.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text("Редактировать профиль", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(104.dp)
                .clip(CircleShape)
                .background(BrandGradient),
            contentAlignment = Alignment.Center
        ) {
            UserAvatar(profile = profile, model = avatarModel, size = 104.dp)
            if (avatarBusy) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text(if (avatarModel == null) "Добавить фото" else "Изменить фото") }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text("Имя") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        DateField(
            label = "Дата рождения",
            value = LocalDate.parse(birthDate),
            onDateSelected = { birthDate = it.toString(); error = null },
            minDate = LocalDate.of(1900, 1, 1),
            maxDate = LocalDate.now()
        )
        Spacer(Modifier.height(14.dp))
        Text("Пол", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        GenderSelector(selected = gender, onSelect = { gender = it; error = null })
        Spacer(Modifier.height(18.dp))
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = { error = vm.saveProfile(name, birthDate, gender) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = MaterialTheme.shapes.small
        ) { Text(if (editing) "Сохранить" else "Продолжить") }
        if (editing) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { vm.back() }, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        }
        if (profile != null) {
            Spacer(Modifier.height(18.dp))
            InfoBanner(
                text = "Ваш код участника: ${profile.code}. Он используется для синхронизации и участия в челленджах."
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val profile = data.currentProfile()
    val myChallenges = normalizeChallenges(data.challenges).filter { challenge ->
        profile != null && challenge.participantCodes.contains(profile.code)
    }.sortedByDescending { it.startDate }
    val medals = profile?.medals.orEmpty()
    val totalPoints = if (profile != null) {
        myChallenges.sumOf { challenge ->
            com.onlinechallenges.app.data.ChallengeEngine.scoreParticipant(
                challenge,
                profile.code,
                data.checkIns,
                data.profiles,
                confirmations = data.confirmations
            ).points
        }
    } else 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)
    ) {
        item {
            GradientHeader {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            profile = profile,
                            model = vm.avatarModel(profile),
                            size = 78.dp
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                profile?.name ?: "Профиль",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White
                            )
                            Text(
                                profile?.let { "${it.code} · ${it.gender.label}" } ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            AgeBadge(profile)
                        }
                        IconButtonEdit(onClick = { vm.beginProfileEdit() })
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        ProfileStat("${myChallenges.size}", "челленджей")
                        ProfileStat("$totalPoints", "баллов")
                        ProfileStat("${medals.size}", "медалей")
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { vm.openSettings() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Настройки")
                }
                OutlinedButton(
                    onClick = { vm.logout() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Выйти")
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                SectionTitle("Медали", subtitle = "Награды за призовые места")
                Spacer(Modifier.height(8.dp))
                if (medals.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Пока без медалей. Завершите челлендж в тройке лидеров, чтобы получить награду.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        medals.sortedBy { it.place }.forEach { MedalBadge(it) }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Профиль", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        ProfileInfoRow("Дата рождения", formatBirthDateSafe(profile?.birthDate))
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        ProfileInfoRow("Пол", profile?.gender?.label ?: "—")
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        ProfileInfoRow("Код участника", profile?.code ?: "—")
                    }
                }
                Spacer(Modifier.height(20.dp))
                SectionTitle("Мои челленджи", subtitle = if (myChallenges.isEmpty()) "Пока пусто" else "Можно открыть статистику")
                Spacer(Modifier.height(8.dp))
            }
        }
        if (myChallenges.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🏁",
                    title = "Вы ещё не участвуете",
                    text = "Выберите челлендж на главной или создайте свой.",
                    actionText = "На главную",
                    onAction = { vm.selectTab(com.onlinechallenges.app.ui.Screen.Home) }
                )
            }
        } else {
            items(myChallenges, key = { it.id }) { challenge ->
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                    ChallengeCard(
                        challenge = challenge,
                        currentUserCode = profile?.code,
                        allCheckIns = data.checkIns,
                        profiles = data.profiles,
                        confirmations = data.confirmations,
                        onClick = { vm.openChallenge(challenge.id) }
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            LinkRow(
                title = "Синхронизация",
                subtitle = null,
                onClick = { vm.openSettings() }
            )
            Text(
                "Онлайн челленджи · ${com.onlinechallenges.app.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun IconButtonEdit(onClick: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.18f),
        shape = CircleShape,
        modifier = Modifier.size(40.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(Icons.Default.Edit, contentDescription = "Редактировать", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun AgeBadge(profile: Profile?) {
    val age = com.onlinechallenges.app.ui.ageFromBirthDate(profile?.birthDate)
    if (age != null) {
        Text(
            "$age лет · ${formatBirthDateSafe(profile?.birthDate)}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    } else {
        Text(
            "Дата рождения не указана",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun formatBirthDateSafe(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    return com.onlinechallenges.app.ui.formatBirthDate(value)
}
