package com.onlinechallenges.app.data

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
}
