package com.onlinechallenges.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.onlinechallenges.app.BuildConfig
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class GitHubConfig(
    val owner: String = DEFAULT_OWNER,
    val repo: String = DEFAULT_REPO,
    val branch: String = "main",
    val token: String = "",
    val autoSync: Boolean = false,
    val hasCustomToken: Boolean = false
) {
    val isConfigured: Boolean get() = owner.isNotBlank() && repo.isNotBlank() && token.isNotBlank()
    val pagesUrl: String get() = "https://${owner.lowercase()}.github.io/$repo/"
    val repoUrl: String get() = "https://github.com/$owner/$repo"

    companion object {
        const val DEFAULT_OWNER = "RomanLyavin"
        const val DEFAULT_REPO = "online-challenges"
    }
}

class SyncPrefs(private val context: Context) {
    private val prefs = context.getSharedPreferences(DemoData.PREFS, Context.MODE_PRIVATE)

    fun load(): GitHubConfig {
        val customToken = TokenVault.decrypt(prefs.getString(KEY_TOKEN, null))
        return GitHubConfig(
            owner = prefs.getString(KEY_OWNER, GitHubConfig.DEFAULT_OWNER) ?: GitHubConfig.DEFAULT_OWNER,
            repo = prefs.getString(KEY_REPO, GitHubConfig.DEFAULT_REPO) ?: GitHubConfig.DEFAULT_REPO,
            branch = prefs.getString(KEY_BRANCH, "main") ?: "main",
            token = customToken.ifBlank { embeddedToken() },
            autoSync = prefs.getBoolean(KEY_AUTO, false),
            hasCustomToken = customToken.isNotBlank()
        )
    }

    private fun embeddedToken(): String = runCatching {
        val encoded = BuildConfig.GITHUB_TOKEN_B64
        if (encoded.isBlank()) {
            ""
        } else {
            val masked = Base64.getDecoder().decode(encoded)
            val bytes = ByteArray(masked.size) { index -> (masked[index].toInt() xor 0x5A).toByte() }
            String(bytes, Charsets.UTF_8)
        }
    }.getOrDefault("")

    fun storedToken(): String = TokenVault.decrypt(prefs.getString(KEY_TOKEN, null))

    fun save(config: GitHubConfig) {
        prefs.edit()
            .putString(KEY_OWNER, config.owner.trim())
            .putString(KEY_REPO, config.repo.trim())
            .putString(KEY_BRANCH, config.branch.trim().ifBlank { "main" })
            .putBoolean(KEY_AUTO, config.autoSync)
            .apply()
        if (config.token.isNotBlank()) {
            TokenVault.encrypt(config.token)?.let {
                prefs.edit().putString(KEY_TOKEN, it).apply()
            }
        }
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun lastSync(): String? = prefs.getString(KEY_LAST_SYNC, null)
    fun lastMessage(): String? = prefs.getString(KEY_LAST_MESSAGE, null)

    fun setLastResult(message: String, success: Boolean) {
        prefs.edit()
            .putString(KEY_LAST_SYNC, isoNow())
            .putString(KEY_LAST_MESSAGE, message)
            .putBoolean(KEY_LAST_SUCCESS, success)
            .apply()
    }

    fun lastSuccess(): Boolean = prefs.getBoolean(KEY_LAST_SUCCESS, false)

    companion object {
        private const val KEY_OWNER = "github_owner"
        private const val KEY_REPO = "github_repo"
        private const val KEY_BRANCH = "github_branch"
        private const val KEY_TOKEN = "github_token_enc"
        private const val KEY_AUTO = "github_auto"
        private const val KEY_LAST_SYNC = "github_last_sync"
        private const val KEY_LAST_MESSAGE = "github_last_message"
        private const val KEY_LAST_SUCCESS = "github_last_success"
    }
}

object TokenVault {
    private const val ALIAS = "online_challenges_token_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(plain: String): String? = runCatching {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        Base64.getEncoder().encodeToString(combined)
    }.getOrNull()

    fun decrypt(encoded: String?): String {
        if (encoded.isNullOrBlank()) return ""
        return runCatching {
            val combined = Base64.getDecoder().decode(encoded)
            if (combined.size < 13) return ""
            val iv = combined.copyOfRange(0, 12)
            val payload = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
