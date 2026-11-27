package com.onlinechallenges.app.data

import android.content.Intent
import com.onlinechallenges.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.createSupabaseClient
import java.net.URLDecoder

/** Supabase Auth поверх Publishable key. Секретные ключи в приложение не попадают. */
object SupabaseAuth {
    const val SCHEME = "com.onlinechallenges.app"
    const val HOST = "auth"
    const val REDIRECT_URL = "$SCHEME://$HOST"

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth) {
                scheme = SCHEME
                host = HOST
                flowType = FlowType.PKCE
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                autoSaveToStorage = true
            }
        }
    }

    val clientOrNull: SupabaseClient?
        get() = if (isConfigured) runCatching { client }.getOrNull() else null

    val sessionStatus: SessionStatus?
        get() = clientOrNull?.auth?.sessionStatus?.value

    /** Выставляется до создания ViewModel, чтобы гонка с загруженной сессией не потеряла сброс пароля. */
    @Volatile
    var recoveryRedirectPending: Boolean = false
        private set

    @Volatile
    var emailChangePending: Boolean = false
        private set

    fun prepare(intent: Intent) {
        val raw = intent.dataString
        if (isRecoveryRedirect(raw)) recoveryRedirectPending = true
        if (isEmailChangeRedirect(raw)) emailChangePending = true
    }

    fun clearRecoveryFlag() {
        recoveryRedirectPending = false
    }

    fun clearEmailChangeFlag() {
        emailChangePending = false
    }

    fun currentUser(): UserInfo? = clientOrNull?.auth?.currentUserOrNull()

    fun currentUserId(): String? = currentUser()?.id

    fun currentAccessToken(): String? = clientOrNull?.auth?.currentSessionOrNull()?.accessToken

    suspend fun verifyEmailCode(type: OtpType.Email, email: String, code: String) {
        client.auth.verifyEmailOtp(type = type, email = email, token = code.trim())
    }

    fun isSignedIn(): Boolean = clientOrNull?.auth?.currentSessionOrNull() != null

    suspend fun signUp(email: String, password: String): UserInfo? =
        client.auth.signUpWith(Email, redirectUrl = REDIRECT_URL) {
            this.email = email
            this.password = password
        }

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun resendConfirmation(email: String) {
        client.auth.resendEmail(OtpType.Email.SIGNUP, email)
    }

    suspend fun requestPasswordReset(email: String) {
        client.auth.resetPasswordForEmail(email, redirectUrl = REDIRECT_URL)
    }

    suspend fun updatePassword(password: String) {
        client.auth.updateUser { this.password = password }
    }

    /** Возвращает пользователя: если подтверждение не требуется, newEmail пуст. */
    suspend fun updateEmail(email: String): UserInfo =
        client.auth.updateUser(redirectUrl = REDIRECT_URL) { this.email = email }

    /** Поддержка писем с token_hash, когда ссылка ведёт сразу в приложение. */
    suspend fun verifyEmailOtp(type: OtpType.Email, tokenHash: String) {
        client.auth.verifyEmailOtp(type, tokenHash)
    }

    suspend fun signOut() {
        clientOrNull?.auth?.signOut()
    }

    fun handleDeeplinks(intent: Intent) {
        prepare(intent)
        clientOrNull?.handleDeeplinks(intent)
    }

    /** Ссылка сброса пароля приходит с type=recovery в query или фрагменте. */
    fun isRecoveryRedirect(raw: String?): Boolean = isType(raw, "recovery")

    fun isEmailChangeRedirect(raw: String?): Boolean = isType(raw, "email_change")

    fun isEmailConfirmationRedirect(raw: String?): Boolean =
        isType(raw, "signup") || isType(raw, "email") || isType(raw, "magiclink")

    private fun isType(raw: String?, type: String): Boolean =
        paramValue(raw, "type").equals(type, ignoreCase = true)

    /** Читает параметр из query или фрагмента — Supabase использует оба варианта. */
    fun paramValue(raw: String?, key: String): String? {
        if (raw.isNullOrBlank()) return null
        val regex = Regex("(?:[?&#]|^)" + Regex.escape(key) + "=([^&#]+)")
        val value = regex.find(raw)?.groupValues?.getOrNull(1) ?: return null
        return runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }

    fun redirectError(raw: String?): String? =
        paramValue(raw, "error_description") ?: paramValue(raw, "error")
}
