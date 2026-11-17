package com.onlinechallenges.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateTest {

    @Test
    fun `normalizes release tags`() {
        assertEquals("1.0.1", GitHubUpdate.normalizeTag("v1.0.1"))
        assertEquals("1.0.1", GitHubUpdate.normalizeTag("V1.0.1"))
        assertEquals("1.0.1", GitHubUpdate.normalizeTag("1.0.1-beta"))
        assertEquals("1.0.1", GitHubUpdate.normalizeTag("1.0.1+42"))
    }

    @Test
    fun `compares numeric versions`() {
        assertTrue(GitHubUpdate.isNewer("1.0.1", "1.0.0"))
        assertTrue(GitHubUpdate.isNewer("1.1", "1.0.9"))
        assertTrue(GitHubUpdate.isNewer("2.0.0", "1.99.99"))
        assertFalse(GitHubUpdate.isNewer("1.0.0", "1.0.0"))
        assertFalse(GitHubUpdate.isNewer("1.0", "1.0.0"))
        assertFalse(GitHubUpdate.isNewer("0.9.9", "1.0.0"))
        assertEquals(0, GitHubUpdate.compareVersions("невозможно", "1.0.0"))
    }

    @Test
    fun `parses latest release and prefers apk matching the tag`() {
        val json = """
            {
              "tag_name": "v1.0.1",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "OnlineChallenges-1.0.0.apk",
                  "url": "https://api.github.com/assets/old",
                  "browser_download_url": "https://github.com/old.apk",
                  "size": 7000000
                },
                {
                  "name": "OnlineChallenges-1.0.1.apk",
                  "url": "https://api.github.com/assets/new",
                  "browser_download_url": "https://github.com/new.apk",
                  "size": 7100000
                }
              ]
            }
        """.trimIndent()

        val release = GitHubUpdate.parseLatestRelease(json)

        assertEquals("v1.0.1", release?.tag)
        assertEquals("1.0.1", release?.version)
        assertEquals("OnlineChallenges-1.0.1.apk", release?.apkName)
        assertEquals("https://api.github.com/assets/new", release?.apkUrl)
        assertEquals(7_100_000L, release?.apkSize)
    }

    @Test
    fun `release without apk has no download url`() {
        val json = """
            {
              "tag_name": "v1.0.1",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "source.zip",
                  "url": "https://api.github.com/assets/source",
                  "size": 100
                }
              ]
            }
        """.trimIndent()

        val release = GitHubUpdate.parseLatestRelease(json)

        assertEquals("1.0.1", release?.version)
        assertNull(release?.apkUrl)
        assertNull(release?.apkName)
    }

    @Test
    fun `draft and prerelease are ignored`() {
        assertNull(GitHubUpdate.parseLatestRelease("""{"tag_name":"v2.0.0","draft":true}"""))
        assertNull(GitHubUpdate.parseLatestRelease("""{"tag_name":"v2.0.0","prerelease":true}"""))
        assertNull(GitHubUpdate.parseLatestRelease("""{"draft":false,"prerelease":false}"""))
    }
}
