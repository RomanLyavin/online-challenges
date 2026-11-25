package com.onlinechallenges.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onlinechallenges.app.data.ChallengeEngine
import com.onlinechallenges.app.data.Dates
import com.onlinechallenges.app.data.HomeChallengeFilter
import com.onlinechallenges.app.data.groupHomeChallenges
import com.onlinechallenges.app.data.normalizeChallenges
import com.onlinechallenges.app.ui.AppViewModel
import com.onlinechallenges.app.ui.ChallengeCard
import com.onlinechallenges.app.ui.EmptyState
import com.onlinechallenges.app.ui.GradientHeader
import com.onlinechallenges.app.ui.InfoBanner
import com.onlinechallenges.app.ui.Screen
import com.onlinechallenges.app.ui.SectionTitle
import com.onlinechallenges.app.ui.UserAvatar
import java.time.LocalDate

@Composable
fun HomeScreen(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val syncState by vm.syncState.collectAsStateWithLifecycle()
    val profile = data.currentProfile()
    val today = LocalDate.now()
    var filter by remember { mutableStateOf(HomeChallengeFilter.ALL) }
    var earliestFirst by remember { mutableStateOf(true) }
    val groups = groupHomeChallenges(
        challenges = normalizeChallenges(data.challenges),
        profileCode = profile?.code,
        today = today,
        earliestFirst = earliestFirst
    )
    val active = groups.active
    val finished = groups.finished
    val available = groups.available
    val myChallenges = active + finished
    val totalPoints = if (profile != null) {
        myChallenges.sumOf {
            ChallengeEngine.scoreParticipant(
                it,
                profile.code,
                data.checkIns,
                data.profiles,
                today,
                confirmations = data.confirmations
            ).points
        }
    } else 0
    val medals = profile?.medals.orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            GradientHeader {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                Dates.long(today).replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                "Привет, ${profile?.name?.substringBefore(' ') ?: "участник"}!",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                        }
                        UserAvatar(
                            profile = profile,
                            model = vm.avatarModel(profile),
                            size = 52.dp,
                            onClick = { vm.selectTab(Screen.ProfileScreen) }
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeStat(
                            "${active.size}",
                            "активных",
                            Modifier.weight(1f),
                            onClick = if (active.size == 1) {
                                { vm.openChallenge(active.first().id) }
                            } else {
                                null
                            }
                        )
                        HomeStat("$totalPoints", "баллов", Modifier.weight(1f))
                        HomeStat(
                            "${medals.size}",
                            "медалей",
                            Modifier.weight(1f),
                            onClick = { vm.selectTab(Screen.ProfileScreen) }
                        )
                    }
                    if (config.token.isBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { vm.openSettings() },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.18f),
                                contentColor = Color.White
                            ),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Синхронизация не настроена")
                        }
                    }
                }
            }
        }
        if (syncState.running) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    InfoBanner(text = syncState.message ?: "Синхронизация…")
                }
            }
        } else if (syncState.error && syncState.message != null) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    InfoBanner(text = syncState.message!!, warning = true)
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(
                    title = "Мои челленджи",
                    subtitle = if (myChallenges.isEmpty()) "Пока нет активных" else "Сегодня ${Dates.short(today)}",
                    modifier = Modifier.weight(1f)
                )
                if (config.isConfigured) {
                    OutlinedButton(
                        onClick = { vm.syncNow() },
                        enabled = !syncState.running
                    ) {
                        if (syncState.running) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Синхр.")
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == HomeChallengeFilter.ALL,
                    onClick = { filter = HomeChallengeFilter.ALL },
                    label = { Text("Все") }
                )
                FilterChip(
                    selected = filter == HomeChallengeFilter.ACTIVE,
                    onClick = { filter = HomeChallengeFilter.ACTIVE },
                    label = { Text("Идут сейчас") }
                )
                FilterChip(
                    selected = filter == HomeChallengeFilter.FINISHED,
                    onClick = { filter = HomeChallengeFilter.FINISHED },
                    label = { Text("Завершены") }
                )
            }
            TextButton(
                onClick = { earliestFirst = !earliestFirst },
                modifier = Modifier.padding(start = 14.dp)
            ) {
                Text(if (earliestFirst) "↑ Сначала ближайшие" else "↓ Сначала поздние")
            }
        }
        if (filter != HomeChallengeFilter.FINISHED && active.isEmpty()) {
            item {
                EmptyState(
                    emoji = "💪",
                    title = "Начните первый челлендж",
                    text = "Создайте свой или присоединитесь к доступному ниже.",
                    actionText = "Создать челлендж",
                    onAction = { vm.openCreateChallenge() }
                )
            }
        } else if (filter != HomeChallengeFilter.FINISHED) {
            items(active, key = { it.id }) { challenge ->
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
        if (filter != HomeChallengeFilter.FINISHED && available.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    SectionTitle(
                        "Доступные челленджи",
                        subtitle = "Можно присоединиться сейчас"
                    )
                }
            }
            items(available, key = { it.id }) { challenge ->
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
        if (filter != HomeChallengeFilter.ACTIVE && finished.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    SectionTitle("Завершённые", subtitle = "Итоги и медали")
                }
            }
            items(finished, key = { it.id }) { challenge ->
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
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                PrivateJoinCard(vm)
            }
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Как это работает",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "1. Выберите челлендж и цели.\n" +
                            "2. Каждый день отмечайте результат.\n" +
                            "3. Прикладывайте фото/видео или подтверждайте результаты других.\n" +
                            "4. Получайте 1 балл за каждую закрытую цель.\n" +
                            "5. В конце — места и медали.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivateJoinCard(vm: AppViewModel) {
    val busy by vm.privateJoinBusy.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Приватный челлендж", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "Введите код приглашения от автора, чтобы открыть данные и медиа.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Код приглашения") },
                placeholder = { Text("OC1.…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { vm.joinPrivateChallenge(code) },
                enabled = code.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Войти по коду")
                }
            }
        }
    }
}

@Composable
private fun HomeStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            color = Color.White.copy(alpha = 0.15f),
            shape = MaterialTheme.shapes.medium
        ) { content() }
    } else {
        Surface(
            modifier = modifier,
            color = Color.White.copy(alpha = 0.15f),
            shape = MaterialTheme.shapes.medium
        ) { content() }
    }
}
