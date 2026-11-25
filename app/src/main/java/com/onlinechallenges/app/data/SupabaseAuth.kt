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

    fun prepare(intent: Intent) {
        if (isRecoveryRedirect(intent.dataString)) recoveryRedirectPending = true
    }

    fun clearRecoveryFlag() {
        recoveryRedirectPending = false
    }

    fun currentUser(): UserInfo? = clientOrNull?.auth?.currentUserOrNull()

    fun isSignedIn(): Boolean = clientOrNull?.auth?.currentSessionOrNull() != null

    suspend fun signUp(email: String, password: String) {
        client.auth.signUpWith(Email, redirectUrl = REDIRECT_URL) {
            this.email = email
            this.password = password
        }
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

    suspend fun signOut() {
        clientOrNull?.auth?.signOut()
    }

    fun handleDeeplinks(intent: Intent) {
        prepare(intent)
        clientOrNull?.handleDeeplinks(intent)
    }

    /** Ссылка сброса пароля приходит с type=recovery во фрагменте или query. */
    fun isRecoveryRedirect(raw: String?): Boolean =
        !raw.isNullOrBlank() && raw.contains("type=recovery", ignoreCase = true)
}
