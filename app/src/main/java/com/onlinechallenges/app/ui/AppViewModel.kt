package com.onlinechallenges.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onlinechallenges.app.data.AppData
import com.onlinechallenges.app.data.AppRepository
import com.onlinechallenges.app.data.Attachment
import com.onlinechallenges.app.data.AuthManager
import com.onlinechallenges.app.data.AuthResult
import com.onlinechallenges.app.data.Awards
import com.onlinechallenges.app.data.Challenge
import com.onlinechallenges.app.data.ChallengeEngine
import com.onlinechallenges.app.data.ChallengeGoal
import com.onlinechallenges.app.data.ChallengeMode
import com.onlinechallenges.app.data.CheckIn
import com.onlinechallenges.app.data.Dates
import com.onlinechallenges.app.data.DemoData
import com.onlinechallenges.app.data.Gender
import com.onlinechallenges.app.data.GitHubConfig
import com.onlinechallenges.app.data.GoalResult
import com.onlinechallenges.app.data.MediaException
import com.onlinechallenges.app.data.MediaManager
import com.onlinechallenges.app.data.Profile
import com.onlinechallenges.app.data.ProofType
import com.onlinechallenges.app.data.SyncManager
import com.onlinechallenges.app.data.SyncPrefs
import com.onlinechallenges.app.data.VerificationCodes
import com.onlinechallenges.app.data.isoNow
import com.onlinechallenges.app.data.parseDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

sealed interface Screen {
    data object Auth : Screen
    data object Verify : Screen
    data object SetupProfile : Screen
    data object Home : Screen
    data object Catalog : Screen
    data object ProfileScreen : Screen
    data object Settings : Screen
    data object CreateChallenge : Screen
    data class ChallengeDetail(val challengeId: String) : Screen
    data class CheckInScreen(val challengeId: String, val date: String) : Screen
    data class Statistics(val challengeId: String, val selectedCode: String? = null) : Screen
}

data class CheckInDraft(
    val challengeId: String,
    val date: String,
    val values: Map<String, String> = emptyMap(),
    val attachments: Map<String, List<Attachment>> = emptyMap(),
    val note: String = "",
    val existing: Boolean = false
)

data class SyncUiState(
    val running: Boolean = false,
    val message: String? = null,
    val error: Boolean = false,
    val lastSync: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val repository = AppRepository(application)
    private val auth = AuthManager(repository)
    private val media = MediaManager(application, repository)
    private val syncPrefs = SyncPrefs(application)
    private val syncManager = SyncManager(repository, syncPrefs)

    val data: StateFlow<AppData> = repository.data
    val screenStack = mutableStateListOf<Screen>()
    val config = MutableStateFlow(syncPrefs.load())
    val syncState = MutableStateFlow(
        SyncUiState(
            message = syncPrefs.lastMessage(),
            error = !syncPrefs.lastSuccess() && syncPrefs.lastMessage() != null,
            lastSync = syncPrefs.lastSync()
        )
    )
    val draft = MutableStateFlow<CheckInDraft?>(null)
    val avatarDraft = MutableStateFlow<String?>(null)
    val uiMessage = MutableStateFlow<String?>(null)
    val mediaBusyGoal = MutableStateFlow<String?>(null)
    val avatarBusy = MutableStateFlow(false)
    val initialized = MutableStateFlow(false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            DemoData.seedIfNeeded(repository)
            Awards.awardFinishedChallenges(repository)
            withContext(Dispatchers.Main) {
                initializeNavigation()
                initialized.value = true
                if (syncPrefs.load().autoSync) syncNow()
            }
        }
    }

    private fun initializeNavigation() {
        val state = repository.snapshot()
        screenStack.clear()
        val profile = state.currentProfile()
        when {
            profile != null && !profile.isComplete -> screenStack += Screen.SetupProfile
            profile != null -> screenStack += Screen.Home
            state.pending != null -> screenStack += Screen.Verify
            else -> screenStack += Screen.Auth
        }
    }

    fun currentProfile(): Profile? = repository.snapshot().currentProfile()

    fun currentScreen(): Screen? = screenStack.lastOrNull()

    fun navigate(screen: Screen) {
        if (screenStack.lastOrNull() == screen) return
        screenStack += screen
    }

    fun back() {
        if (screenStack.size <= 1) return
        val removed = screenStack.removeAt(screenStack.lastIndex)
        if (removed is Screen.CheckInScreen) draft.value = null
    }

    fun selectTab(screen: Screen) {
        if (currentProfile()?.isComplete != true) {
            screenStack.clear()
            screenStack += Screen.SetupProfile
            return
        }
        screenStack.clear()
        screenStack += screen
    }

    fun openChallenge(id: String) = navigate(Screen.ChallengeDetail(id))
    fun openStatistics(id: String, selectedCode: String? = null) =
        navigate(Screen.Statistics(id, selectedCode))
    fun openCreateChallenge() = navigate(Screen.CreateChallenge)
    fun openSettings() = navigate(Screen.Settings)
    fun openProfile() = navigate(Screen.ProfileScreen)

    // ---------- Регистрация и вход ----------

    fun register(email: String, password: String, confirm: String): String? {
        if (password != confirm) return "Пароли не совпадают"
        val result = auth.register(email, password)
        if (result is AuthResult.Error) return result.message
        screenStack.clear()
        screenStack += Screen.Verify
        return null
    }

    fun verify(code: String): String? {
        val result = auth.verify(code)
        if (result is AuthResult.Error) return result.message
        val profile = repository.snapshot().currentProfile()
        screenStack.clear()
        screenStack += if (profile?.isComplete == true) Screen.Home else Screen.SetupProfile
        uiMessage.value = "Email подтверждён"
        return null
    }

    fun resendCode(): String? {
        val result = auth.resendCode()
        return (result as? AuthResult.Error)?.message
    }

    fun cancelVerification() {
        auth.cancelPending()
        screenStack.clear()
        screenStack += Screen.Auth
    }

    fun login(email: String, password: String): String? {
        val result = auth.login(email, password)
        if (result is AuthResult.Error) return result.message
        val profile = repository.snapshot().currentProfile()
        screenStack.clear()
        screenStack += if (profile?.isComplete == true) Screen.Home else Screen.SetupProfile
        return null
    }

    fun logout() {
        auth.logout()
        draft.value = null
        screenStack.clear()
        screenStack += Screen.Auth
    }

    // ---------- Профиль ----------

    fun beginProfileEdit() {
        val profile = currentProfile() ?: return
        avatarDraft.value = profile.avatarLocalPath
        if (screenStack.lastOrNull() != Screen.SetupProfile) screenStack += Screen.SetupProfile
    }

    fun pickAvatar(uri: Uri) {
        val profile = currentProfile() ?: return
        viewModelScope.launch {
            avatarBusy.value = true
            try {
                val path = withContext(Dispatchers.IO) { media.importAvatar(uri, profile.code) }
                avatarDraft.value = path
            } catch (error: MediaException) {
                uiMessage.value = error.message
            } catch (error: Exception) {
                uiMessage.value = "Не удалось загрузить фото: ${error.message}"
            } finally {
                avatarBusy.value = false
            }
        }
    }

    fun saveProfile(name: String, birthDate: String, gender: Gender): String? {
        val result = auth.updateProfile(name, birthDate, gender, avatarDraft.value)
        if (result is AuthResult.Error) return result.message
        avatarDraft.value = null
        screenStack.clear()
        screenStack += Screen.Home
        uiMessage.value = "Профиль сохранён"
        autoSyncIfEnabled()
        return null
    }

    // ---------- Каталог ----------

    fun addActivity(
        name: String,
        unit: String,
        emoji: String,
        target: String,
        proofType: ProofType
    ): String? {
        if (name.isBlank()) return "Введите название"
        if (unit.isBlank()) return "Укажите единицу измерения"
        val targetValue = parseNumber(target) ?: return "Некорректная цель"
        if (targetValue <= 0) return "Цель должна быть больше нуля"
        val profile = currentProfile() ?: return "Сначала войдите"
        val activity = com.onlinechallenges.app.data.ActivityType(
            id = "custom-${System.currentTimeMillis()}",
            name = name.trim(),
            unit = unit.trim(),
            emoji = emoji.ifBlank { "🎯" },
            defaultTarget = targetValue,
            proofType = proofType,
            createdByCode = profile.code,
            updatedAt = isoNow(),
            isDefault = false
        )
        repository.update { it.copy(activities = it.activities + activity) }
        uiMessage.value = "Активность добавлена в общий каталог"
        autoSyncIfEnabled()
        return null
    }

    // ---------- Челленджи ----------

    fun createChallenge(
        title: String,
        description: String,
        startDate: String,
        endDate: String,
        mode: ChallengeMode,
        intervalDays: Int,
        goals: List<ChallengeGoal>
    ): String? {
        val profile = currentProfile() ?: return "Сначала войдите"
        if (title.isBlank()) return "Введите название челленджа"
        val start = parseDate(startDate)
        val end = parseDate(endDate)
        if (end.isBefore(start)) return "Дата окончания раньше даты начала"
        if (end.isBefore(LocalDate.now())) return "Дата окончания уже прошла"
        if (goals.isEmpty()) return "Выберите хотя бы одну цель"
        if (goals.any { it.target <= 0 }) return "Все цели должны быть больше нуля"
        val challenge = Challenge(
            id = "ch-${System.currentTimeMillis()}",
            title = title.trim(),
            description = description.trim(),
            startDate = start.toString(),
            endDate = end.toString(),
            mode = mode,
            intervalDays = if (mode == ChallengeMode.INTERVAL) intervalDays.coerceIn(1, 30) else 1,
            goals = goals,
            creatorCode = profile.code,
            creatorName = profile.name,
            participantCodes = listOf(profile.code),
            createdAt = isoNow(),
            updatedAt = isoNow()
        )
        repository.update { it.withChallenge(challenge) }
        screenStack += Screen.ChallengeDetail(challenge.id)
        uiMessage.value = "Челлендж создан"
        autoSyncIfEnabled()
        return null
    }

    fun joinChallenge(id: String): String? {
        val profile = currentProfile() ?: return "Сначала войдите"
        val challenge = repository.snapshot().challenge(id) ?: return "Челлендж не найден"
        if (challenge.participantCodes.contains(profile.code)) return "Вы уже участвуете"
        if (challenge.status() == com.onlinechallenges.app.data.ChallengeStatus.FINISHED) {
            return "Челлендж уже завершён"
        }
        repository.update { it.withChallenge(challenge.withParticipant(profile.code)) }
        uiMessage.value = "Вы присоединились к челленджу"
        autoSyncIfEnabled()
        return null
    }

    // ---------- Отметки ----------

    fun openCheckIn(challengeId: String, date: String) {
        val state = repository.snapshot()
        val challenge = state.challenge(challengeId) ?: return
        val profile = state.currentProfile() ?: return
        if (!challenge.participantCodes.contains(profile.code)) {
            uiMessage.value = "Сначала присоединитесь к челленджу"
            return
        }
        val today = LocalDate.now()
        if (today.isAfter(challenge.end)) {
            uiMessage.value = "Челлендж завершён — отметки закрыты"
            return
        }
        if (challenge.start.isAfter(today)) {
            uiMessage.value = "Челлендж ещё не начался"
            return
        }
        val requested = Dates.clamp(parseDate(date), challenge.start, minOf(challenge.end, today))
        val existing = state.checkIn(challengeId, profile.code, requested.toString())
        draft.value = CheckInDraft(
            challengeId = challengeId,
            date = requested.toString(),
            values = challenge.goals.associate { goal ->
                goal.id to (existing?.resultFor(goal.id)?.actual?.let(::formatInput) ?: "")
            },
            attachments = challenge.goals.associate { goal ->
                goal.id to (existing?.resultFor(goal.id)?.attachments ?: emptyList())
            },
            note = existing?.note ?: "",
            existing = existing != null
        )
        if (screenStack.lastOrNull() !is Screen.CheckInScreen) {
            screenStack += Screen.CheckInScreen(challengeId, requested.toString())
        }
    }

    fun setDraftDate(date: String) {
        val current = draft.value ?: return
        val challenge = repository.snapshot().challenge(current.challengeId) ?: return
        val today = LocalDate.now()
        val requested = Dates.clamp(parseDate(date), challenge.start, minOf(challenge.end, today))
        val state = repository.snapshot()
        val profile = state.currentProfile() ?: return
        val existing = state.checkIn(current.challengeId, profile.code, requested.toString())
        draft.value = current.copy(
            date = requested.toString(),
            values = challenge.goals.associate { goal ->
                goal.id to (existing?.resultFor(goal.id)?.actual?.let(::formatInput) ?: "")
            },
            attachments = challenge.goals.associate { goal ->
                goal.id to (existing?.resultFor(goal.id)?.attachments ?: emptyList())
            },
            note = existing?.note ?: "",
            existing = existing != null
        )
        val screen = screenStack.lastOrNull()
        if (screen is Screen.CheckInScreen) {
            screenStack[screenStack.lastIndex] = Screen.CheckInScreen(current.challengeId, requested.toString())
        }
    }

    fun setDraftValue(goalId: String, value: String) {
        val current = draft.value ?: return
        draft.value = current.copy(values = current.values + (goalId to value))
    }

    fun setDraftNote(note: String) {
        draft.value = draft.value?.copy(note = note)
    }

    fun attachMedia(uri: Uri, goalId: String) {
        val current = draft.value ?: return
        val challenge = repository.snapshot().challenge(current.challengeId) ?: return
        val goal = challenge.goals.firstOrNull { it.id == goalId } ?: return
        val profile = repository.snapshot().currentProfile() ?: return
        if ((current.attachments[goalId]?.size ?: 0) >= 4) {
            uiMessage.value = "К одной цели можно приложить не больше 4 файлов"
            return
        }
        viewModelScope.launch {
            mediaBusyGoal.value = goalId
            try {
                val attachment = withContext(Dispatchers.IO) {
                    media.importProof(
                        uri = uri,
                        userCode = profile.code,
                        challengeId = challenge.id,
                        date = current.date,
                        goalId = goalId,
                        preferredType = goal.proofType
                    )
                }
                if (!ChallengeEngine.proofMatches(attachment.type, goal.proofType)) {
                    withContext(Dispatchers.IO) { media.deleteAttachment(attachment) }
                    uiMessage.value = "Для цели «${goal.name}» нужно ${goal.proofType.label.lowercase()}"
                    return@launch
                }
                val latest = draft.value ?: return@launch
                if (latest.challengeId != current.challengeId || latest.date != current.date) return@launch
                val list = latest.attachments[goalId].orEmpty() + attachment
                draft.value = latest.copy(attachments = latest.attachments + (goalId to list))
            } catch (error: MediaException) {
                uiMessage.value = error.message
            } catch (error: Exception) {
                uiMessage.value = "Не удалось приложить файл: ${error.message}"
            } finally {
                mediaBusyGoal.value = null
            }
        }
    }

    fun removeDraftAttachment(goalId: String, attachmentId: String) {
        val current = draft.value ?: return
        val list = current.attachments[goalId].orEmpty().filterNot { it.id == attachmentId }
        draft.value = current.copy(attachments = current.attachments + (goalId to list))
    }

    fun saveCheckIn(): String? {
        val current = draft.value ?: return "Черновик не найден"
        val state = repository.snapshot()
        val challenge = state.challenge(current.challengeId) ?: return "Челлендж не найден"
        val profile = state.currentProfile() ?: return "Сначала войдите"
        val results = mutableListOf<GoalResult>()
        for (goal in challenge.goals) {
            val raw = current.values[goal.id].orEmpty().trim()
            val actual = if (raw.isBlank()) 0.0 else parseNumber(raw)
                ?: return "Некорректное значение для «${goal.name}»"
            if (actual < 0) return "Значение не может быть отрицательным"
            if (actual > 10_000_000) return "Слишком большое значение для «${goal.name}»"
            results += GoalResult(
                goalId = goal.id,
                actual = actual,
                attachments = current.attachments[goal.id].orEmpty()
            )
        }
        if (results.all { it.actual <= 0.0 && it.attachments.isEmpty() }) {
            return "Отметьте хотя бы одну цель"
        }
        val existing = state.checkIn(challenge.id, profile.code, current.date)
        val now = isoNow()
        val checkIn = existing?.copy(
            results = results,
            note = current.note.trim(),
            updatedAt = now
        ) ?: CheckIn(
            id = java.util.UUID.randomUUID().toString(),
            challengeId = challenge.id,
            userCode = profile.code,
            date = current.date,
            results = results,
            note = current.note.trim(),
            createdAt = now,
            updatedAt = now
        )
        repository.update { it.withCheckIn(checkIn) }
        Awards.awardFinishedChallenges(repository)
        draft.value = null
        back()
        uiMessage.value = if (existing == null) "Отметка сохранена" else "Отметка обновлена"
        autoSyncIfEnabled()
        return null
    }

    // ---------- Синхронизация ----------

    fun saveGitHubConfig(
        owner: String,
        repo: String,
        branch: String,
        tokenInput: String,
        autoSync: Boolean
    ): String? {
        if (owner.isBlank() || repo.isBlank()) return "Укажите владельца и репозиторий"
        if (!Regex("^[A-Za-z0-9_.-]+$").matches(owner)) return "Некорректный владелец репозитория"
        if (!Regex("^[A-Za-z0-9_.-]+$").matches(repo)) return "Некорректное имя репозитория"
        val previous = syncPrefs.load()
        val token = tokenInput.trim().ifBlank { previous.token }
        val updated = GitHubConfig(
            owner = owner.trim(),
            repo = repo.trim(),
            branch = branch.trim().ifBlank { "main" },
            token = token,
            autoSync = autoSync
        )
        syncPrefs.save(updated)
        config.value = syncPrefs.load()
        uiMessage.value = "Настройки GitHub сохранены"
        return null
    }

    fun clearGitHubToken() {
        syncPrefs.clearToken()
        config.value = syncPrefs.load()
        uiMessage.value = "Токен удалён"
    }

    fun testConnection() {
        val current = syncPrefs.load()
        if (!current.isConfigured) {
            syncState.value = syncState.value.copy(message = "Сначала укажите токен", error = true)
            return
        }
        viewModelScope.launch {
            syncState.value = syncState.value.copy(running = true, message = "Проверяем подключение…", error = false)
            try {
                val message = withContext(Dispatchers.IO) {
                    com.onlinechallenges.app.data.GitHubClient(current).testConnection()
                }
                syncState.value = syncState.value.copy(running = false, message = message, error = false)
            } catch (error: Exception) {
                syncState.value = syncState.value.copy(
                    running = false,
                    message = error.message ?: "Не удалось подключиться",
                    error = true
                )
            }
        }
    }

    fun syncNow() {
        if (syncState.value.running) return
        val current = syncPrefs.load()
        if (!current.isConfigured) {
            uiMessage.value = "Укажите GitHub-токен в настройках"
            return
        }
        viewModelScope.launch {
            syncState.value = SyncUiState(
                running = true,
                message = "Синхронизация с GitHub…",
                error = false,
                lastSync = syncState.value.lastSync
            )
            try {
                val summary = withContext(Dispatchers.IO) { syncManager.sync() }
                config.value = syncPrefs.load()
                syncState.value = SyncUiState(
                    running = false,
                    message = buildString {
                        append("Готово: ${summary.challenges} челленджей, ${summary.checkIns} отметок")
                        if (summary.uploadedMedia > 0) append(", файлов: ${summary.uploadedMedia}")
                        if (summary.warnings.isNotEmpty()) append(". Предупреждений: ${summary.warnings.size}")
                    },
                    error = summary.warnings.isNotEmpty(),
                    lastSync = syncPrefs.lastSync()
                )
                if (summary.warnings.isNotEmpty()) {
                    uiMessage.value = summary.warnings.first()
                }
                Awards.awardFinishedChallenges(repository)
            } catch (error: Exception) {
                syncState.value = SyncUiState(
                    running = false,
                    message = error.message ?: "Ошибка синхронизации",
                    error = true,
                    lastSync = syncState.value.lastSync
                )
            }
        }
    }

    private fun autoSyncIfEnabled() {
        if (syncPrefs.load().autoSync && syncPrefs.load().isConfigured) syncNow()
    }

    // ---------- Демо и утилиты ----------

    fun clearDemoData() {
        DemoData.clear(repository)
        uiMessage.value = "Демо-данные удалены"
    }

    fun consumeMessage() {
        uiMessage.value = null
    }

    fun attachmentModel(attachment: Attachment): Any? {
        val local = repository.fileFor(attachment.localPath)
        if (local != null) return local
        val remote = attachment.remotePath
        if (!remote.isNullOrBlank()) return config.value.pagesUrl + remote
        return null
    }

    fun attachmentLocalFile(attachment: Attachment): File? = repository.fileFor(attachment.localPath)

    fun remotePageUrl(attachment: Attachment): String? {
        val remote = attachment.remotePath ?: return null
        return config.value.pagesUrl + remote
    }

    fun avatarModel(profile: Profile?): Any? {
        if (profile == null) return null
        val local = repository.fileFor(profile.avatarLocalPath)
        if (local != null) return local
        val remote = profile.avatarRepoPath
        if (!remote.isNullOrBlank()) return config.value.pagesUrl + remote
        return null
    }

    fun newPendingCode(): String? = repository.snapshot().pending?.code

    fun formatInput(value: Double): String =
        if (value % 1.0 == 0.0) value.roundToInt().toString()
        else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

    private fun parseNumber(value: String): Double? =
        value.trim().replace(',', '.').toDoubleOrNull()

    fun userCode(): String = currentProfile()?.code ?: ""
}
