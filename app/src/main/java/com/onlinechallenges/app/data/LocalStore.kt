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
                sessionCode = json.optStringOrNull("sessionCode"),
                profiles = json.optJSONArray("profiles").toList { Profile.fromJson(it) },
                activities = mergeActivities(activities),
                challenges = json.optJSONArray("challenges").toList { Challenge.fromJson(it) },
                checkIns = json.optJSONArray("checkIns").toList { CheckIn.fromJson(it) }
            )
        }.getOrElse { AppData() }
    }

    private fun mergeActivities(local: List<ActivityType>): List<ActivityType> {
        val defaults = defaultActivities()
        if (local.isEmpty()) return defaults
        val byId = LinkedHashMap<String, ActivityType>()
        defaults.forEach { byId[it.id] = it }
        local.forEach { if (!byId.containsKey(it.id)) byId[it.id] = it else if (!it.isDefault) byId[it.id] = it }
        return byId.values.toList()
    }

    fun save(data: AppData) {
        val json = JSONObject()
            .put("version", 1)
            .put("accounts", JSONArray(data.accounts.map { it.toJson() }))
            .put("pending", data.pending?.toJson() ?: JSONObject.NULL)
            .put("sessionCode", data.sessionCode ?: JSONObject.NULL)
            .put("profiles", JSONArray(data.profiles.map { it.toJson() }))
            .put("activities", JSONArray(data.activities.map { it.toJson() }))
            .put("challenges", JSONArray(data.challenges.map { it.toJson() }))
            .put("checkIns", JSONArray(data.checkIns.map { it.toJson() }))
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
        val normalized = email.trim().lowercase()
        if (!isEmailValid(normalized)) return AuthResult.Error("Введите корректный email")
        if (password.length < 6) return AuthResult.Error("Пароль должен быть не короче 6 символов")
        if (repo.snapshot().accounts.any { it.email.equals(normalized, ignoreCase = true) }) {
            return AuthResult.Error("Аккаунт с таким email уже зарегистрирован")
        }
        val salt = PasswordHasher.newSalt()
        val pending = PendingVerification(
            email = normalized,
            passwordHash = PasswordHasher.hash(password, salt),
            salt = salt,
            code = VerificationCodes.newCode(),
            expiresAt = java.time.Instant.now().plusSeconds(15 * 60).toString()
        )
        repo.update { it.copy(pending = pending) }
        // Демо-режим: код не отправляется письмом, а показывается на экране подтверждения.
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
        val userCode = VerificationCodes.newUserCode()
        val account = UserAccount(
            id = java.util.UUID.randomUUID().toString(),
            email = pending.email,
            passwordHash = pending.passwordHash,
            salt = pending.salt,
            code = userCode,
            createdAt = isoNow()
        )
        val displayName = pending.email.substringBefore("@").replaceFirstChar { it.uppercase() }
        val profile = Profile(
            code = userCode,
            name = displayName.ifBlank { "Участник" },
            updatedAt = isoNow()
        )
        repo.update {
            it.copy(
                accounts = it.accounts + account,
                profiles = it.profiles + profile,
                pending = null,
                sessionCode = userCode
            )
        }
        return AuthResult.Success
    }

    fun login(email: String, password: String): AuthResult {
        val normalized = email.trim().lowercase()
        val account = repo.snapshot().accounts.firstOrNull { it.email.equals(normalized, ignoreCase = true) }
            ?: return AuthResult.Error("Аккаунт не найден")
        if (!PasswordHasher.verify(password, account.salt, account.passwordHash)) {
            return AuthResult.Error("Неверный пароль")
        }
        repo.update { it.copy(sessionCode = account.code) }
        return AuthResult.Success
    }

    fun logout() {
        repo.update { it.copy(sessionCode = null) }
    }

    fun cancelPending() {
        repo.update { it.copy(pending = null) }
    }

    fun updateProfile(
        name: String,
        birthDate: String,
        gender: Gender,
        avatarLocalPath: String? = null
    ): AuthResult {
        val state = repo.snapshot()
        val current = state.currentProfile() ?: return AuthResult.Error("Профиль не найден")
        if (name.isBlank()) return AuthResult.Error("Введите имя")
        if (birthDate.isBlank()) return AuthResult.Error("Укажите дату рождения")
        val avatarChanged = avatarLocalPath != null && avatarLocalPath != current.avatarLocalPath
        val updated = current.copy(
            name = name.trim(),
            birthDate = birthDate,
            gender = gender,
            avatarLocalPath = avatarLocalPath ?: current.avatarLocalPath,
            avatarRepoPath = if (avatarChanged) null else current.avatarRepoPath,
            updatedAt = isoNow()
        )
        repo.update { it.withProfile(updated) }
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

    fun newUserCode(): String {
        val random = java.security.SecureRandom()
        val body = (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        return "U-$body"
    }
}
