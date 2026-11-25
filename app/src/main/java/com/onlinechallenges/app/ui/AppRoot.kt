package com.onlinechallenges.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onlinechallenges.app.ui.screens.AuthScreen
import com.onlinechallenges.app.ui.screens.CatalogScreen
import com.onlinechallenges.app.ui.screens.ChallengeDetailScreen
import com.onlinechallenges.app.ui.screens.CheckInScreen
import com.onlinechallenges.app.ui.screens.CreateChallengeScreen
import com.onlinechallenges.app.ui.screens.ForgotPasswordScreen
import com.onlinechallenges.app.ui.screens.HomeScreen
import com.onlinechallenges.app.ui.screens.MedalDetailsScreen
import com.onlinechallenges.app.ui.screens.ProfileScreen
import com.onlinechallenges.app.ui.screens.ResetPasswordScreen
import com.onlinechallenges.app.ui.screens.SettingsScreen
import com.onlinechallenges.app.ui.screens.SetupProfileScreen
import com.onlinechallenges.app.ui.screens.StatisticsScreen
import com.onlinechallenges.app.ui.screens.VerifyScreen
import com.onlinechallenges.app.ui.theme.BrandGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel) {
    val initialized by vm.initialized.collectAsStateWithLifecycle()
    val message by vm.uiMessage.collectAsStateWithLifecycle()
    val syncState by vm.syncState.collectAsStateWithLifecycle()
    val appData by vm.data.collectAsStateWithLifecycle()
    val stack = vm.screenStack
    val current = stack.lastOrNull()
    val snackbarHostState = remember { SnackbarHostState() }
    val iconResolver = remember(appData.activities) {
        { activityId: String, iconPath: String? -> vm.activityIconModel(activityId, iconPath) }
    }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message!!)
            vm.consumeMessage()
        }
    }

    if (!initialized || current == null) {
        SplashScreen()
        return
    }

    val topLevelIndex = when (current) {
        Screen.Home -> 0
        Screen.Catalog -> 1
        Screen.ProfileScreen -> 2
        else -> -1
    }
    val showTopBar = when (current) {
        Screen.Auth, Screen.Verify, Screen.ForgotPassword, Screen.ResetPassword,
        Screen.Home, Screen.Catalog, Screen.ProfileScreen -> false
        Screen.SetupProfile -> stack.size > 1
        else -> true
    }
    val showBottomBar = topLevelIndex >= 0
    val showFab = current is Screen.Home || current is Screen.Catalog

    BackHandler(enabled = stack.size > 1) { vm.back() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(topBarTitle(current)) },
                    navigationIcon = {
                        IconButton(onClick = { vm.back() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        if (syncState.running) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 14.dp)
                                    .size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = topLevelIndex == 0,
                        onClick = { vm.selectTab(Screen.Home) },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Главная") }
                    )
                    NavigationBarItem(
                        selected = topLevelIndex == 1,
                        onClick = { vm.selectTab(Screen.Catalog) },
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text("Каталог") }
                    )
                    NavigationBarItem(
                        selected = topLevelIndex == 2,
                        onClick = { vm.selectTab(Screen.ProfileScreen) },
                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        label = { Text("Профиль") }
                    )
                }
            }
        },
        floatingActionButton = {
            if (showFab) {
                ExtendedFloatingActionButton(
                    onClick = { vm.openCreateChallenge() },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Челлендж") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        CompositionLocalProvider(LocalActivityIconResolver provides iconResolver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (current) {
                    Screen.Auth -> AuthScreen(vm)
                    Screen.Verify -> VerifyScreen(vm)
                    Screen.ForgotPassword -> ForgotPasswordScreen(vm)
                    Screen.ResetPassword -> ResetPasswordScreen(vm)
                    Screen.SetupProfile -> SetupProfileScreen(vm)
                    Screen.Home -> HomeScreen(vm)
                    Screen.Catalog -> CatalogScreen(vm)
                    Screen.ProfileScreen -> ProfileScreen(vm)
                    Screen.Settings -> SettingsScreen(vm)
                    Screen.CreateChallenge -> CreateChallengeScreen(vm)
                    is Screen.ChallengeDetail -> ChallengeDetailScreen(vm, current.challengeId)
                    is Screen.CheckInScreen -> CheckInScreen(vm)
                    is Screen.Statistics -> StatisticsScreen(vm, current.challengeId, current.selectedCode)
                    is Screen.MedalDetails -> MedalDetailsScreen(vm, current.challengeId, current.place)
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(BrandGradient, RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("🏆", style = MaterialTheme.typography.displaySmall)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Онлайн челленджи",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Спорт, дисциплина и подтверждения",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
        }
    }
}

private fun topBarTitle(screen: Screen): String = when (screen) {
    Screen.SetupProfile -> "Профиль"
    Screen.Settings -> "Настройки"
    Screen.CreateChallenge -> "Новый челлендж"
    is Screen.ChallengeDetail -> "Челлендж"
    is Screen.CheckInScreen -> "Отметка"
    is Screen.Statistics -> "Статистика"
    is Screen.MedalDetails -> "Медаль"
    else -> "Онлайн челленджи"
}
