package com.onlinechallenges.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubClientTest {

    private val missingShaBody = """
        {"message":"Invalid request.\n\n\"sha\" wasn't supplied.","documentation_url":"https://docs.github.com/rest"}
    """.trimIndent()

    @Test
    fun `detects missing sha on update without sha`() {
        assertTrue(isMissingShaError(422, null, missingShaBody))
        assertTrue(isMissingShaError(422, "", missingShaBody))
    }

    @Test
    fun `does not treat other errors as missing sha`() {
        assertFalse(isMissingShaError(409, null, missingShaBody))
        assertFalse(isMissingShaError(422, "existing-sha", missingShaBody))
        assertFalse(isMissingShaError(422, null, """{"message":"Invalid request."}"""))
    }

    @Test
    fun `retries only transient github errors`() {
        listOf(429, 500, 502, 503, 504).forEach { assertTrue(isRetryableStatus(it)) }
        listOf(400, 401, 403, 404, 409, 422).forEach { assertFalse(isRetryableStatus(it)) }
    }

    @Test
    fun `retry delay respects retry-after header`() {
        assertEquals(500L, retryDelayMs(0))
        assertEquals(1000L, retryDelayMs(1))
        assertEquals(2000L, retryDelayMs(0, "2"))
        assertEquals(10_000L, retryDelayMs(0, "60"))
        assertEquals(500L, retryDelayMs(0, "invalid"))
    }

    @Test
    fun `server error has understandable message`() {
        val client = GitHubClient(GitHubConfig())
        val message = client.errorMessage(500, """{"message":"Internal Server Error"}""")
        assertTrue(message.contains("временно недоступен"))
        assertTrue(message.contains("500"))
    }
}
