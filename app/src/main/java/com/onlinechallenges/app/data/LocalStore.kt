package com.onlinechallenges.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LocalStore(private val context: Context) {
    private val file = File(context.filesDir, "online-challenges-data.json")
    private val backup = File(context.filesDir, "online-challenges-data.backup.json")

    fun load(): AppData {
        val source = if (file.exists()) file else backup
        if (!source.exists()) return AppData()
        return runCatching {
            val json = JSONObject(source.readText(Charsets.UTF_8))
            val activities = json.optJSONArray("activities").toList { ActivityType.fromJson(it) }
            AppData(
                accounts = json.optJSONArray("accounts").toList { UserAccount.fromJson(it) },
                pending = json.optJSONObject("pending")?.let { PendingVerification.fromJson(it) },
                pendingEmail = json.optStringOrNull("pendingEmail"),
                sessionCode = json.optStringOrNull("sessionCode"),
                profiles = json.optJSONArray("profiles").toList { Profile.fromJson(it) },
                activities = mergeActivities(activities),
                challenges = normalizeChallenges(
                    json.optJSONArray("challenges").toList { Challenge.fromJson(it) }
                ),
                checkIns = json.optJSONArray("checkIns").toList { CheckIn.fromJson(it) }
                    .distinctBy { it.id },
                confirmations = json.optJSONArray("confirmations").toList { PeerConfirmation.fromJson(it) }
                    .distinctBy { it.key },
                deletedChallengeIds = json.optJSONArray("deletedChallengeIds").toStringList().distinct()
            )
        }.getOrElse { AppData() }
    }

    private fun mergeActivities(local: List<ActivityType>): List<ActivityType> {
        val defaults = defaultActivities()
        if (local.isEmpty()) return defaults
        val localById = local.associateBy { it.id }
        val result = LinkedHashMap<String, ActivityType>()
        defaults.forEach { base ->
            val saved = localById[base.id]
            result[base.id] = if (saved == null) base else base.copy(
                iconPath = saved.iconPath,
                iconLocalPath = saved.iconLocalPath,
                updatedAt = saved.updatedAt
            )
        }
        local.forEach { activity ->
            if (!result.containsKey(activity.id)) result[activity.id] = activity
        }
        return result.values.toList()
    }

    fun save(data: AppData) {
        val json = JSONObject()
            .put("version", 1)
            .put("accounts", JSONArray(data.accounts.map { it.toJson() }))
            .put("pending", data.pending?.toJson() ?: JSONObject.NULL)
            .put("pendingEmail", data.pendingEmail ?: JSONObject.NULL)
            .put("sessionCode", data.sessionCode ?: JSONObject.NULL)
            .put("profiles", JSONArray(data.profiles.map { it.toJson() }))
            .put("activities", JSONArray(data.activities.map { it.toJson() }))
            .put("challenges", JSONArray(data.challenges.map { it.toJson() }))
            .put("checkIns", JSONArray(data.checkIns.map { it.toJson() }))
            .put("confirmations", JSONArray(data.confirmations.map { it.toJson() }))
            .put("deletedChallengeIds", JSONArray(data.deletedChallengeIds))
        runCatching {
            val tmp = File(context.filesDir, "online-challenges-data.tmp")
            tmp.writeText(json.toString(), Charsets.UTF_8)
            if (file.exists()) file.copyTo(backup, overwrite = true)
            if (!tmp.renameTo(file)) {
                file.writeText(json.toString(), Charsets.UTF_8)
                tmp.delete()
            }
        }
    }
}

class AppRepository(val context: Context) {
    private val store = LocalStore(context)
    private val _data = MutableStateFlow(store.load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    init {
        // Если сессия ссылается на удалённый аккаунт — сбрасываем её.
        val state = _data.value
        if (state.sessionCode != null && state.accounts.none { it.code == state.sessionCode }) {
            update { it.copy(sessionCode = null) }
        }
    }

    fun snapshot(): AppData = _data.value

    @Synchronized
    fun update(transform: (AppData) -> AppData): AppData {
        val updated = transform(_data.value)
        _data.value = updated
        store.save(updated)
        return updated
    }

    @Synchronized
    fun replace(updated: AppData) {
        _data.value = updated
        store.save(updated)
    }

    fun mediaRoot(): File = File(context.filesDir, "media").apply { mkdirs() }

    fun avatarRoot(): File = File(context.filesDir, "avatars").apply { mkdirs() }

    fun activityIconRoot(): File = File(context.filesDir, "activity-icons").apply { mkdirs() }

    fun fileFor(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        return if (file.exists()) file else null
    }
}

sealed interface AuthResult {
    data object Success : AuthResult
    data class Error(val message: String) : AuthResult
}

class AuthManager(private val repo: AppRepository) {
    fun register(email: String, password: String): AuthResult {
        val normalized = EmailHasher.normalize(email)
        if (!isEmailValid(normalized)) return AuthResult.Error("Введите корректный email")
        if (password.length < 6) return AuthResult.Error("Пароль должен быть не короче 6 символов")
        val state = repo.snapshot()
        if (state.accounts.any { it.email.equals(normalized, ignoreCase = true) }) {
            return AuthResult.Error("Аккаунт с таким email уже зарегистрирован")
        }
        val salt = PasswordHasher.newSalt()
        val userCode = VerificationCodes.newUserCode(
            state.accounts.map { it.code }.toSet() + state.profiles.map { it.code }.toSet()
        )
        val emailHash = EmailHasher.hash(normalized)
        val account = UserAccount(
            id = java.util.UUID.randomUUID().toString(),
            email = normalized,
            passwordHash = PasswordHasher.hash(password, salt),
            salt = salt,
            code = userCode,
            createdAt = isoNow(),
            provider = "local",
            emailHash = emailHash
        )
        val displayName = normalized.substringBefore("@").replaceFirstChar { it.uppercase() }
        val profile = Profile(
            code = userCode,
            name = displayName.ifBlank { "Участник" },
            email = normalized,
            emailHash = emailHash,
            provider = "local",
            updatedAt = isoNow()
        )
        repo.update {
            it.copy(
                accounts = it.accounts + account,
                profiles = it.profiles + profile,
                pending = null,
                pendingEmail = null,
                sessionCode = userCode
            )
        }
        return AuthResult.Success
    }

    fun resendCode(): AuthResult {
        val pending = repo.snapshot().pending ?: return AuthResult.Error("Нет ожидающей регистрации")
        val updated = pending.copy(
            code = VerificationCodes.newCode(),
            expiresAt = java.time.Instant.now().plusSeconds(15 * 60).toString()
        )
        repo.update { it.copy(pending = updated) }
        return AuthResult.Success
    }

    fun verify(code: String): AuthResult {
        val state = repo.snapshot()
        val pending = state.pending ?: return AuthResult.Error("Нет ожидающей регистрации")
        if (!VerificationCodes.matches(code, pending.code)) return AuthResult.Error("Неверный код")
        if (pending.isExpired()) return AuthResult.Error("Код истёк. Отправьте новый")
        if (state.accounts.any { it.email.equals(pending.email, ignoreCase = true) }) {
            return AuthResult.Error("Аккаунт уже существует — войдите")
        }
        val userCode = VerificationCodes.newUserCode(
            state.accounts.map { it.code }.toSet() + state.profiles.map { it.code }.toSet()
        )
        val emailHash = EmailHasher.hash(pending.email)
        val account = UserAccount(
            id = java.util.UUID.randomUUID().toString(),
            email = pending.email,
            passwordHash = pending.passwordHash,
            salt = pending.salt,
            code = userCode,
            createdAt = isoNow(),
            emailHash = emailHash
        )
        val displayName = pending.email.substringBefore("@").replaceFirstChar { it.uppercase() }
        val profile = Profile(
            code = userCode,
            name = displayName.ifBlank { "Участник" },
            email = pending.email,
            emailHash = emailHash,
            updatedAt = isoNow()
        )
        repo.update {
            it.copy(
                accounts = it.accounts + account,
                profiles = it.profiles + profile,
                pending = null,
                pendingEmail = null,
                sessionCode = userCode
            )
        }
        return AuthResult.Success
    }

    fun login(email: String, password: String): AuthResult {
        val normalized = EmailHasher.normalize(email)
        val account = repo.snapshot().accounts.firstOrNull {
            it.email.equals(normalized, ignoreCase = true) && !it.isSupabase
        } ?: return AuthResult.Error("Аккаунт не найден")
        if (!PasswordHasher.verify(password, account.salt, account.passwordHash)) {
            return AuthResult.Error("Неверный пароль")
        }
        val hash = account.emailHash.ifBlank { EmailHasher.hash(account.email) }
        repo.update { state ->
            state.copy(
                sessionCode = account.code,
                // Старые локальные профили не содержали email — подтягиваем его из аккаунта.
                profiles = state.profiles.map { profile ->
                    if (profile.code == account.code && profile.email.isBlank()) {
                        profile.copy(
                            email = account.email,
                            emailHash = hash,
                            provider = account.provider
                        )
                    } else profile
                }
            )
        }
        return AuthResult.Success
    }

    fun logout() {
        repo.update { it.copy(sessionCode = null) }
    }

    fun cancelPending() {
        repo.update { it.copy(pending = null, pendingEmail = null) }
    }

    fun updateProfile(
        name: String,
        birthDate: String,
        gender: Gender,
        avatarLocalPath: String? = null,
        email: String? = null,
        emailHash: String? = null,
        emailPending: String? = null
    ): AuthResult {
        val state = repo.snapshot()
        val current = state.currentProfile() ?: return AuthResult.Error("Профиль не найден")
        if (name.isBlank()) return AuthResult.Error("Введите имя")
        if (birthDate.isBlank()) return AuthResult.Error("Укажите дату рождения")
        val normalizedEmail = email?.let { EmailHasher.normalize(it) }
        if (normalizedEmail != null && !isEmailValid(normalizedEmail)) {
            return AuthResult.Error("Введите корректный email")
        }
        val avatarChanged = avatarLocalPath != null && avatarLocalPath != current.avatarLocalPath
        val updated = current.copy(
            name = name.trim(),
            birthDate = birthDate,
            gender = gender,
            avatarLocalPath = avatarLocalPath ?: current.avatarLocalPath,
            avatarRepoPath = if (avatarChanged) null else current.avatarRepoPath,
            updatedAt = isoNow(),
            email = normalizedEmail ?: current.email,
            emailHash = emailHash ?: current.emailHash.ifBlank {
                normalizedEmail?.let { EmailHasher.hash(it) } ?: ""
            },
            pendingEmail = emailPending ?: current.pendingEmail
        )
        repo.update { currentState ->
            val accounts = if (normalizedEmail != null && !normalizedEmail.equals(current.email, ignoreCase = true)) {
                currentState.accounts.map { account ->
                    if (account.code == current.code) {
                        account.copy(
                            email = normalizedEmail,
                            emailHash = updated.emailHash,
                            provider = current.provider,
                            supabaseUserId = current.supabaseUserId
                        )
                    } else account
                }
            } else currentState.accounts
            currentState.copy(accounts = accounts).withProfile(updated)
        }
        return AuthResult.Success
    }

    companion object {
        private val emailRegex = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

        fun isEmailValid(email: String): Boolean = emailRegex.matches(email)
    }
}

object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256

    fun newSalt(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(password: String, salt: String): String {
        val saltBytes = java.util.Base64.getDecoder().decode(salt)
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }

    fun verify(password: String, salt: String, expected: String): Boolean = runCatching {
        val actual = hash(password, salt)
        java.security.MessageDigest.isEqual(actual.toByteArray(), expected.toByteArray())
    }.getOrDefault(false)
}

object VerificationCodes {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun newCode(): String {
        val random = java.security.SecureRandom()
        return (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
    }

    fun matches(input: String, expected: String): Boolean =
        input.trim().uppercase() == expected.uppercase()

    fun newUserCode(excluded: Set<String> = emptySet()): String {
        val random = java.security.SecureRandom()
        while (true) {
            val body = (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
            val code = "U-$body"
            if (code !in excluded) return code
        }
    }
}
