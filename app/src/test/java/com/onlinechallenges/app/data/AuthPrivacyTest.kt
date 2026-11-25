package com.onlinechallenges.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthPrivacyTest {

    @Test
    fun `email hash is stable and hides the address`() {
        val first = EmailHasher.hash("  User.Name@Example.COM ")
        val second = EmailHasher.hash("user.name@example.com")

        assertEquals(first, second)
        assertEquals(64, first.length)
        assertFalse(first.contains("user.name", ignoreCase = true))
        assertFalse(first.contains("example.com", ignoreCase = true))
    }

    @Test
    fun `public profile json contains hash but never email`() {
        val profile = Profile(
            code = "U-ABC123",
            name = "Анна",
            email = "anna@example.com",
            emailHash = EmailHasher.hash("anna@example.com")
        )

        val json = profile.toSyncJson()

        assertEquals(profile.emailHash, json.getString("emailHash"))
        assertFalse(json.has("email"))
        assertFalse(json.toString().contains("anna@example.com", ignoreCase = true))
    }

    @Test
    fun `public profile lookup restores a profile by email hash`() {
        val hash = EmailHasher.hash("anna@example.com")
        val remote = JSONObject()
            .put(
                "profiles",
                JSONArray().put(
                    JSONObject()
                        .put("code", "U-ABC123")
                        .put("name", "Анна")
                        .put("emailHash", hash)
                )
            )
            .toString()

        val restored = PublicProfileLookup.findByEmailHash(remote, hash)

        assertNotNull(restored)
        assertEquals("U-ABC123", restored!!.code)
        assertEquals(hash, restored.emailHash)
        assertNull(PublicProfileLookup.findByEmailHash(remote, EmailHasher.hash("other@example.com")))
    }

    @Test
    fun `old account json migrates with local provider and computed hash`() {
        val old = JSONObject()
            .put("id", "acc-1")
            .put("email", "old@example.com")
            .put("passwordHash", "hash")
            .put("salt", "salt")
            .put("code", "U-OLD123")
            .put("createdAt", "2026-01-01T00:00:00Z")

        val account = UserAccount.fromJson(old)

        assertEquals("local", account.provider)
        assertEquals(EmailHasher.hash("old@example.com"), account.emailHash)
        assertFalse(account.isSupabase)
    }

    @Test
    fun `recovery redirect is detected in an auth link`() {
        assertTrue(
            SupabaseAuth.isRecoveryRedirect(
                "com.onlinechallenges.app://auth#access_token=abc&type=recovery"
            )
        )
        assertFalse(
            SupabaseAuth.isRecoveryRedirect(
                "com.onlinechallenges.app://auth#access_token=abc&type=signup"
            )
        )
        assertFalse(SupabaseAuth.isRecoveryRedirect(null))
    }

    @Test
    fun `registration rejects an email already used by another account or profile`() {
        val account = UserAccount(
            id = "acc-1",
            email = "anna@example.com",
            passwordHash = "hash",
            salt = "salt",
            code = "U-ANNA",
            createdAt = "2026-01-01T00:00:00Z"
        )
        val profile = Profile(code = "U-BORIS", name = "Борис", email = "boris@example.com")

        assertTrue(AuthRules.emailAlreadyKnown(" ANNA@Example.COM ", listOf(account), emptyList()))
        assertTrue(AuthRules.emailAlreadyKnown("boris@example.com", emptyList(), listOf(profile)))
        assertFalse(AuthRules.emailAlreadyKnown("new@example.com", listOf(account), listOf(profile)))
        assertFalse(
            AuthRules.emailAlreadyKnown(
                "anna@example.com",
                listOf(account),
                emptyList(),
                exceptCode = "U-ANNA"
            )
        )
        assertFalse(AuthManager.isEmailValid("not-an-email"))
        assertTrue(AuthManager.isEmailValid("user@example.com"))
    }

    @Test
    fun `auth link params are read from query and fragment`() {
        val link = "https://project.supabase.co/auth/v1/verify" +
            "?token=pkce_abc&type=recovery&redirect_to=com.onlinechallenges.app%3A%2F%2Fauth" +
            "#error_description=%D0%A1%D1%81%D1%8B%D0%BB%D0%BA%D0%B0+%D0%BD%D0%B5%D0%B4%D0%B5%D0%B9%D1%81%D1%82%D0%B2%D0%B8%D1%82%D0%B5%D0%BB%D1%8C%D0%BD%D0%B0"

        assertTrue(SupabaseAuth.isRecoveryRedirect(link))
        assertEquals("pkce_abc", SupabaseAuth.paramValue(link, "token"))
        assertEquals("com.onlinechallenges.app://auth", SupabaseAuth.paramValue(link, "redirect_to"))
        assertEquals("Ссылка недействительна", SupabaseAuth.redirectError(link))
        assertTrue(
            SupabaseAuth.isEmailChangeRedirect(
                "com.onlinechallenges.app://auth?type=email_change&token_hash=abc"
            )
        )
    }

    @Test
    fun `pending email stays private in public sync json`() {
        val profile = Profile(
            code = "U-ANNA",
            name = "Анна",
            email = "old@example.com",
            emailHash = EmailHasher.hash("old@example.com"),
            pendingEmail = "new@example.com"
        )

        val json = profile.toSyncJson()

        assertFalse(json.toString().contains("old@example.com", ignoreCase = true))
        assertFalse(json.toString().contains("new@example.com", ignoreCase = true))
        assertFalse(json.has("pendingEmail"))
        assertEquals(EmailHasher.hash("old@example.com"), json.getString("emailHash"))
    }
}
