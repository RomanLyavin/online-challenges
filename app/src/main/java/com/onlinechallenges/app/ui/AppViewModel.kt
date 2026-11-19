package com.onlinechallenges.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onlinechallenges.app.BuildConfig
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
import com.onlinechallenges.app.data.ChallengeVisibility
import com.onlinechallenges.app.data.CheckIn
import com.onlinechallenges.app.data.CryptoBox
import com.onlinechallenges.app.data.Dates
import com.onlinechallenges.app.data.DemoData
import com.onlinechallenges.app.data.Gender
import com.onlinechallenges.app.data.GitHubClient
import com.onlinechallenges.app.data.GitHubConfig
import com.onlinechallenges.app.data.GoalResult
import com.onlinechallenges.app.data.InviteCodes
import com.onlinechallenges.app.data.MediaException
import com.onlinechallenges.app.data.MediaManager
import com.onlinechallenges.app.data.PeerConfirmation
import com.onlinechallenges.app.data.PrivateBundle
import com.onlinechallenges.app.data.Profile
import com.onlinechallenges.app.data.ProofType
import com.onlinechallenges.app.data.SyncManager
import com.onlinechallenges.app.data.SyncPrefs
import com.onlinechallenges.app.data.VerificationCodes
import com.onlinechallenges.app.data.isoNow
import com.onlinechallenges.app.data.parseDate
import com.onlinechallenges.app.data.parseDateOrNull
import com.onlinechallenges.app.update.ApkVerifier
import com.onlinechallenges.app.update.GitHubRelease
import com.onlinechallenges.app.update.GitHubUpdate
import com.onlinechallenges.app.update.UpdateState
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
    val privateJoinBusy = MutableStateFlow(false)
    val activityIconBusy = MutableStateFlow<String?>(null)
    val initialized = MutableStateFlow(false)
    val updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)

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

    fun setActivityIcon(activityId: String, uri: Uri) {
        if (activityIconBusy.value != null) return
        val activity = repository.snapshot().activities.firstOrNull { it.id == activityId } ?: return
        viewModelScope.launch {
            activityIconBusy.value = activityId
            try {
                val path = withContext(Dispatchers.IO) { media.importActivityIcon(uri, activity.id) }
                repository.update { data ->
                    data.copy(activities = data.activities.map {
                        if (it.id == activityId) {
                            it.copy(iconLocalPath = path, iconPath = null, updatedAt = isoNow())
                        } else {
                            it
                        }
                    })
                }
                uiMessage.value = "Иконка «${activity.name}» обновлена"
                autoSyncIfEnabled()
            } catch (error: MediaException) {
                uiMessage.value = error.message
            } catch (error: Exception) {
                uiMessage.value = "Не удалось обновить иконку: ${error.message}"
            } finally {
                activityIconBusy.value = null
            }
        }
    }

    fun activityIconModel(activityId: String, fallbackPath: String? = null): Any? {
        val activity = repository.snapshot().activities.firstOrNull { it.id == activityId }
        val local = repository.fileFor(activity?.iconLocalPath)
        if (local != null) return local
        val path = activity?.iconPath ?: fallbackPath
        if (path.isNullOrBlank()) return null
        return config.value.pagesUrl + path
    }

    // ---------- Челленджи ----------

    fun createChallenge(
        title: String,
        description: String,
        startDate: String,
        endDate: String,
        mode: ChallengeMode,
        intervalDays: Int,
        goals: List<ChallengeGoal>,
        visibility: ChallengeVisibility = ChallengeVisibility.PUBLIC
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
            updatedAt = isoNow(),
            visibility = visibility,
            privateKey = if (visibility == ChallengeVisibility.PRIVATE) CryptoBox.newKey() else null
        )
        repository.update { it.withChallenge(challenge) }
        screenStack += Screen.ChallengeDetail(challenge.id)
        uiMessage.value = if (visibility == ChallengeVisibility.PRIVATE) {
            "Приватный челлендж создан. Код приглашения — на странице челленджа"
        } else {
            "Челлендж создан"
        }
        autoSyncIfEnabled()
        return null
    }

    fun joinChallenge(id: String): String? {
        val profile = currentProfile() ?: return "Сначала войдите"
        val challenge = repository.snapshot().challenge(id) ?: return "Челлендж не найден"
        if (challenge.isPrivate) return "Это приватный челлендж — введите код приглашения"
        if (challenge.participantCodes.contains(profile.code)) return "Вы уже участвуете"
        if (challenge.status() == com.onlinechallenges.app.data.ChallengeStatus.FINISHED) {
            return "Челлендж уже завершён"
        }
        repository.update { it.withChallenge(challenge.withParticipant(profile.code)) }
        uiMessage.value = "Вы присоединились к челленджу"
        autoSyncIfEnabled()
        return null
    }

    fun joinPrivateChallenge(rawCode: String) {
        if (privateJoinBusy.value) return
        val profile = currentProfile() ?: run {
            uiMessage.value = "Сначала войдите"
            return
        }
        val parsed = InviteCodes.parse(rawCode) ?: run {
            uiMessage.value = "Неверный код приглашения"
            return
        }
        if (parsed.challengeId in repository.snapshot().deletedChallengeIds) {
            uiMessage.value = "Этот челлендж удалён организатором"
            return
        }
        val config = syncPrefs.load()
        if (!config.isConfigured) {
            uiMessage.value = "Синхронизация не настроена"
            return
        }
        privateJoinBusy.value = true
        viewModelScope.launch {
            try {
                val joined = withContext(Dispatchers.IO) {
                    val client = GitHubClient(config)
                    val path = SyncManager.privateBundlePath(parsed.challengeId)
                    val remote = client.getFileBytes(path)
                        ?: throw IllegalStateException("Челлендж не найден в репозитории")
                    val text = CryptoBox.decryptText(remote.bytes, parsed.key)
                        ?: throw IllegalStateException("Код не подходит к этому челленджу")
                    val bundle = PrivateBundle.fromJson(text)
                        ?: throw IllegalStateException("Не удалось прочитать данные челленджа")
                    if (bundle.challenge.id != parsed.challengeId) {
                        throw IllegalStateException("Код относится к другому челленджу")
                    }
                    if (!bundle.challenge.isPrivate) {
                        throw IllegalStateException("Это не приватный челлендж")
                    }
                    val existing = repository.snapshot().challenge(parsed.challengeId)
                    // В локальном состоянии может быть только публичная заглушка
                    // без целей. Полные данные всегда берём из расшифрованного бандла.
                    val base = if (existing != null && existing.goals.isNotEmpty()) {
                        existing
                    } else {
                        bundle.challenge
                    }
                    val alreadyParticipant = existing?.goals?.isNotEmpty() == true &&
                        existing.hasAccess(profile.code)
                    val challenge = base.copy(
                        visibility = ChallengeVisibility.PRIVATE,
                        privateKey = parsed.key,
                        participantCodes = (
                            base.participantCodes + bundle.challenge.participantCodes +
                                (existing?.participantCodes ?: emptyList())
                            ).distinct(),
                        isDemo = false
                    ).withParticipant(profile.code)
                    val mergedCheckIns = mergePrivateCheckIns(
                        repository.snapshot().checkIns.filter { it.challengeId == challenge.id },
                        bundle.checkIns
                    )
                    val mergedConfirmations = mergePrivateConfirmations(
                        repository.snapshot().confirmations.filter { it.challengeId == challenge.id },
                        bundle.confirmations
                    )
                    val updatedBundle = PrivateBundle(challenge, mergedCheckIns, mergedConfirmations)
                    // Повторный ввод того же кода не должен перезаписывать бандл.
                    // Иначе два устройства затирают отметки друг друга и получают
                    // конфликт версий при синхронизации.
                    if (!alreadyParticipant &&
                        updatedBundle.toJson().toString() != bundle.toJson().toString()
                    ) {
                        client.putFile(
                            path,
                            CryptoBox.encryptText(updatedBundle.toJson().toString(), parsed.key),
                            "Join private challenge",
                            remote.sha
                        )
                    }
                    updatedBundle to alreadyParticipant
                }
                repository.update { current ->
                    current
                        .withChallenge(joined.first.challenge)
                        .copy(
                            checkIns = current.checkIns.filterNot { it.challengeId == joined.first.challenge.id } +
                                joined.first.checkIns,
                            confirmations = current.confirmations.filterNot {
                                it.challengeId == joined.first.challenge.id
                            } + joined.first.confirmations
                        )
                }
                uiMessage.value = if (joined.second) {
                    "Вы уже участвуете в этом челлендже"
                } else {
                    "Вы присоединились к приватному челленджу"
                }
                syncNow()
            } catch (error: Exception) {
                uiMessage.value = error.message ?: "Не удалось присоединиться"
            } finally {
                privateJoinBusy.value = false
            }
        }
    }

    /** Меняет дату окончания. Доступно только организатору челленджа. */
    fun updateChallengeEndDate(challengeId: String, newEndDate: String): String? {
        val profile = currentProfile() ?: return "Сначала войдите"
        val challenge = repository.snapshot().challenge(challengeId) ?: return "Челлендж не найден"
        if (challenge.isDemo) return "Демо-челлендж нельзя изменить"
        if (!challenge.isManagedBy(profile.code)) return "Менять дату может только организатор"
        val newEnd = parseDateOrNull(newEndDate) ?: return "Введите корректную дату"
        if (newEnd.isBefore(challenge.start)) return "Дата окончания раньше даты начала"
        if (newEnd.isBefore(LocalDate.now())) return "Дата окончания уже прошла"
        val updated = challenge.copy(endDate = newEnd.toString(), updatedAt = isoNow())
        repository.update { current -> Awards.apply(current.withChallenge(updated)) }
        uiMessage.value = "Дата окончания изменена"
        autoSyncIfEnabled()
        return null
    }

    /** Удаляет челлендж и связанные с ним отметки. Доступно организатору. */
    fun deleteChallenge(challengeId: String): String? {
        val profile = currentProfile() ?: return "Сначала войдите"
        val state = repository.snapshot()
        val challenge = state.challenge(challengeId) ?: return "Челлендж не найден"
        if (challenge.isDemo) return "Демо-челлендж нельзя удалить"
        if (!challenge.isManagedBy(profile.code)) return "Удалять может только организатор"
        val remoteFiles = state.checkIns
            .filter { it.challengeId == challengeId }
            .flatMap { checkIn -> checkIn.results.flatMap { it.attachments } }
            .mapNotNull { it.remotePath }
            .filter { it.isNotBlank() }
            .distinct()
        repository.update { it.withoutChallenge(challengeId) }
        uiMessage.value = "Челлендж удалён"
        val config = syncPrefs.load()
        if (config.isConfigured) {
            viewModelScope.launch {
                val paths = if (challenge.isPrivate) {
                    listOf(SyncManager.privateBundlePath(challengeId)) + remoteFiles
                } else {
                    remoteFiles
                }
                val failed = withContext(Dispatchers.IO) {
                    val client = GitHubClient(config)
                    var count = 0
                    for (path in paths.distinct()) {
                        try {
                            client.deleteFile(path, "Delete challenge")
                        } catch (error: Exception) {
                            count++
                        }
                    }
                    count
                }
                if (failed > 0) {
                    uiMessage.value = "Челлендж удалён, но не все файлы удалось убрать из GitHub"
                }
            }
        }
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
        val changedGoalIds = if (existing == null) emptySet() else challenge.goals
            .filter { goal ->
                val before = existing.resultFor(goal.id)?.actual ?: 0.0
                val after = results.first { it.goalId == goal.id }.actual
                kotlin.math.abs(before - after) >= 0.0001
            }
            .map { it.id }
            .toSet()
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
        repository.update { data ->
            val confirmations = if (changedGoalIds.isEmpty()) data.confirmations else data.confirmations.map { confirmation ->
                if (confirmation.challengeId == checkIn.challengeId &&
                    confirmation.checkInUserCode == checkIn.userCode &&
                    confirmation.date == checkIn.date &&
                    confirmation.goalId in changedGoalIds &&
                    !confirmation.revoked
                ) {
                    confirmation.copy(revoked = true, updatedAt = now)
                } else {
                    confirmation
                }
            }
            data.withCheckIn(checkIn).copy(confirmations = confirmations)
        }
        Awards.awardFinishedChallenges(repository)
        draft.value = null
        back()
        uiMessage.value = if (existing == null) "Отметка сохранена" else "Отметка обновлена"
        autoSyncIfEnabled()
        return null
    }

    /**
     * Ставит или снимает галочку подтверждения чужой цели.
     * Подтверждать можно только до окончания челленджа и только участникам.
     */
    fun toggleConfirmation(checkIn: CheckIn, goalId: String, actual: Double): String? {
        val state = repository.snapshot()
        val profile = state.currentProfile() ?: return "Сначала войдите"
        val challenge = state.challenge(checkIn.challengeId) ?: return "Челлендж не найден"
        val today = LocalDate.now()
        if (today.isAfter(challenge.end)) return "Челлендж завершён — подтверждения закрыты"
        if (!challenge.participantCodes.contains(profile.code)) return "Вы не участвуете в челлендже"
        if (checkIn.userCode == profile.code) return "Нельзя подтверждать свою отметку"
        if (!challenge.participantCodes.contains(checkIn.userCode)) return "Участник не найден"
        val goal = challenge.goals.firstOrNull { it.id == goalId } ?: return "Цель не найдена"
        if (actual <= 0.0) return "Нет результата для подтверждения"
        val now = isoNow()
        val existing = state.confirmations.firstOrNull {
            it.challengeId == challenge.id &&
                it.checkInUserCode == checkIn.userCode &&
                it.date == checkIn.date &&
                it.goalId == goal.id &&
                it.confirmerCode == profile.code
        }
        val alreadyActive = existing != null &&
            !existing.revoked &&
            kotlin.math.abs(existing.actual - actual) < 0.0001
        val updated = when {
            alreadyActive -> existing!!.copy(revoked = true, updatedAt = now)
            existing == null -> PeerConfirmation(
                challengeId = challenge.id,
                checkInUserCode = checkIn.userCode,
                date = checkIn.date,
                goalId = goal.id,
                confirmerCode = profile.code,
                actual = actual,
                createdAt = now,
                updatedAt = now
            )
            else -> existing.copy(actual = actual, revoked = false, updatedAt = now)
        }
        repository.update { data ->
            data.copy(confirmations = data.confirmations.filterNot { it.key == updated.key } + updated)
        }
        uiMessage.value = if (updated.revoked) "Подтверждение снято" else "Результат подтверждён"
        autoSyncIfEnabled()
        return null
    }

    // ---------- Синхронизация ----------

    fun saveSyncSettings(autoSync: Boolean): String? {
        val current = syncPrefs.load()
        syncPrefs.save(current.copy(autoSync = autoSync, token = ""))
        config.value = syncPrefs.load()
        uiMessage.value = "Настройки синхронизации сохранены"
        return null
    }

    fun testConnection() {
        val current = syncPrefs.load()
        if (!current.isConfigured) {
            syncState.value = syncState.value.copy(message = "Синхронизация не настроена", error = true)
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
            uiMessage.value = "Синхронизация не настроена"
            return
        }
        viewModelScope.launch {
            syncState.value = SyncUiState(
                running = true,
                message = "Синхронизация...",
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

    // ---------- Обновления ----------

    fun checkForUpdates() {
        when (updateState.value) {
            is UpdateState.Checking, is UpdateState.Downloading -> return
            else -> Unit
        }
        val currentVersion = BuildConfig.VERSION_NAME
        updateState.value = UpdateState.Checking
        viewModelScope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    GitHubUpdate.fetchLatest(currentVersion)
                }
                updateState.value = when {
                    !GitHubUpdate.isNewer(release.version, currentVersion) ->
                        UpdateState.UpToDate(currentVersion)
                    release.apkUrl.isNullOrBlank() -> UpdateState.Error(
                        title = "Обновление недоступно",
                        message = "В последнем релизе на GitHub не найден APK-файл.",
                        retry = UpdateState.Retry.Check
                    )
                    else -> UpdateState.Available(release)
                }
            } catch (error: Exception) {
                updateState.value = UpdateState.Error(
                    title = "Не удалось проверить обновления",
                    message = "Не удалось получить данные с GitHub. " +
                        "Проверьте подключение к интернету и попробуйте ещё раз.",
                    retry = UpdateState.Retry.Check
                )
            }
        }
    }

    fun downloadUpdate(release: GitHubRelease) {
        val url = release.apkUrl ?: return
        if (updateState.value is UpdateState.Downloading) return
        val currentVersion = BuildConfig.VERSION_NAME
        val app = getApplication<Application>()
        updateState.value = UpdateState.Downloading(release.version, 0)
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val directory = File(app.cacheDir, "updates").apply { mkdirs() }
                    directory.listFiles()?.forEach { it.delete() }
                    val target = File(directory, "OnlineChallenges-${release.version}.apk")
                    GitHubUpdate.downloadApk(
                        url = url,
                        target = target,
                        expectedSize = release.apkSize,
                        currentVersion = currentVersion
                    ) { percent ->
                        updateState.value = UpdateState.Downloading(release.version, percent)
                    }
                    val archiveVersion = ApkVerifier.versionName(app, target)
                    if (!ApkVerifier.isSameApp(app, target) ||
                        archiveVersion != release.version
                    ) {
                        target.delete()
                        throw InvalidUpdateApkException()
                    }
                    target
                }
                updateState.value = UpdateState.Ready(release, file)
            } catch (error: InvalidUpdateApkException) {
                updateState.value = UpdateState.Error(
                    title = "Не удалось скачать обновление",
                    message = "Скачанный файл не подходит: это не APK «Онлайн челленджей» " +
                        "или его подпись не совпадает с установленной версией.",
                    retry = UpdateState.Retry.Download(release)
                )
            } catch (error: Exception) {
                updateState.value = UpdateState.Error(
                    title = "Не удалось скачать обновление",
                    message = "Не удалось скачать APK. Проверьте подключение к интернету " +
                        "и попробуйте ещё раз.",
                    retry = UpdateState.Retry.Download(release)
                )
            }
        }
    }

    fun retryUpdate() {
        when (val state = updateState.value) {
            is UpdateState.Error -> when (val retry = state.retry) {
                is UpdateState.Retry.Check -> checkForUpdates()
                is UpdateState.Retry.Download -> downloadUpdate(retry.release)
            }
            else -> Unit
        }
    }

    fun dismissUpdate() {
        if (updateState.value is UpdateState.Downloading) return
        updateState.value = UpdateState.Idle
    }

    fun consumeReadyUpdate() {
        if (updateState.value is UpdateState.Ready) updateState.value = UpdateState.Idle
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
        if (!remote.isNullOrBlank() && !isPrivateMediaPath(remote)) {
            return config.value.pagesUrl + remote
        }
        return null
    }

    fun attachmentLocalFile(attachment: Attachment): File? = repository.fileFor(attachment.localPath)

    fun remotePageUrl(attachment: Attachment): String? {
        val remote = attachment.remotePath ?: return null
        if (isPrivateMediaPath(remote)) return null
        return config.value.pagesUrl + remote
    }

    private fun isPrivateMediaPath(path: String): Boolean =
        path.startsWith("data/private/", ignoreCase = true)

    /**
     * Локальные файлы открываются сразу, публичные — через GitHub Pages,
     * приватные скачиваются и расшифровываются во временную папку.
     */
    fun openAttachment(context: Context, attachment: Attachment, challengeId: String) {
        val local = repository.fileFor(attachment.localPath)
        if (local != null) {
            openLocalFile(context, local, attachment.mime)
            return
        }
        val remote = attachment.remotePath
        if (remote.isNullOrBlank()) {
            Toast.makeText(context, "Файл ещё не загружен", Toast.LENGTH_SHORT).show()
            return
        }
        val challenge = repository.snapshot().challenge(challengeId)
        if (challenge?.isPrivate != true || !isPrivateMediaPath(remote)) {
            openUrl(context, config.value.pagesUrl + remote, attachment.mime)
            return
        }
        val key = challenge.privateKey
        if (key.isNullOrBlank()) {
            Toast.makeText(context, "Нет ключа для расшифровки файла", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            Toast.makeText(context, "Скачиваем файл…", Toast.LENGTH_SHORT).show()
            try {
                val file = withContext(Dispatchers.IO) {
                    val client = GitHubClient(syncPrefs.load())
                    val remoteFile = client.getFileBytes(remote)
                        ?: throw IllegalStateException("Файл не найден в репозитории")
                    val bytes = CryptoBox.decryptBytes(remoteFile.bytes, key)
                        ?: throw IllegalStateException("Не удалось расшифровать файл")
                    val dir = File(getApplication<Application>().cacheDir, "private-media").apply { mkdirs() }
                    val safeName = attachment.fileName.ifBlank { attachment.id }
                        .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val target = File(dir, "${attachment.id.take(12)}_$safeName")
                    target.writeBytes(bytes)
                    target
                }
                openLocalFile(context, file, attachment.mime)
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    error.message ?: "Не удалось открыть файл",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openLocalFile(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        openUrl(context, uri.toString(), mime, grantRead = true)
    }

    private fun openUrl(context: Context, url: String, mime: String, grantRead: Boolean = false) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), mime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (grantRead) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, "Нет приложения для этого файла", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mergePrivateCheckIns(local: List<CheckIn>, remote: List<CheckIn>): List<CheckIn> {
        val map = LinkedHashMap<String, CheckIn>()
        local.forEach { map["${it.challengeId}|${it.userCode}|${it.date}"] = it }
        remote.forEach { candidate ->
            val key = "${candidate.challengeId}|${candidate.userCode}|${candidate.date}"
            val current = map[key]
            map[key] = if (current == null || candidate.updatedAt > current.updatedAt) {
                candidate.copy(isDemo = false)
            } else {
                current
            }
        }
        return map.values.toList()
    }

    private fun mergePrivateConfirmations(
        local: List<PeerConfirmation>,
        remote: List<PeerConfirmation>
    ): List<PeerConfirmation> {
        val map = LinkedHashMap<String, PeerConfirmation>()
        local.forEach { map[it.key] = it }
        remote.forEach { candidate ->
            val current = map[candidate.key]
            map[candidate.key] = if (current == null || candidate.updatedAt > current.updatedAt) {
                candidate.copy(isDemo = false)
            } else {
                current
            }
        }
        return map.values.toList()
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

private class InvalidUpdateApkException : Exception()
