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
}
