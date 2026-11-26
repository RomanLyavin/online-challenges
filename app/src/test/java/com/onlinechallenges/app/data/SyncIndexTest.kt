package com.onlinechallenges.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncIndexTest {

    private fun index(): SyncIndex = SyncIndex(
        records = mapOf(
            SyncEntity.CHALLENGES to listOf(
                SyncRecordRef("old", "2026-11-01", "fp-old", "2026-11-01T10:00:00Z"),
                SyncRecordRef("fresh", "2026-11-02", "fp-fresh", "2026-11-02T10:00:00Z"),
                SyncRecordRef("same-second", "2026-11-02", "fp-same", "2026-11-02T12:00:00Z")
            ),
            SyncEntity.CHECKINS to listOf(
                SyncRecordRef("checkin", "2026-11-02", "fp-checkin", "2026-11-02T11:00:00Z")
            )
        ),
        deletedChallengeIds = listOf("deleted-1")
    )

    @Test
    fun `only records published after the last sync are selected`() {
        val selected = index().idsPublishedAfter("2026-11-02T10:30:00Z")

        assertEquals(setOf("same-second"), selected[SyncEntity.CHALLENGES])
        assertEquals(setOf("checkin"), selected[SyncEntity.CHECKINS])
        assertTrue(selected[SyncEntity.PROFILES].isNullOrEmpty())
    }

    @Test
    fun `missing last sync selects everything`() {
        val selected = index().idsPublishedAfter(null)

        assertEquals(3, selected[SyncEntity.CHALLENGES]?.size)
        assertEquals(setOf("checkin"), selected[SyncEntity.CHECKINS])
    }

    @Test
    fun `index survives json round trip`() {
        val original = index()
        val restored = SyncIndex.fromJson(original.toJson().toString())

        assertEquals(original.toJson().toString(), restored?.toJson()?.toString())
    }

    @Test
    fun `unsupported version is ignored`() {
        val json = index().toJson().put("version", 1).toString()

        assertNull(SyncIndex.fromJson(json))
        assertNull(SyncIndex.fromJson("not json"))
    }

    @Test
    fun `bucket path is based on utc update date`() {
        assertEquals("2026-11-02", syncBucketFor("2026-11-02T23:59:59Z"))
        assertEquals("2026-11-03", syncBucketFor("2026-11-03T00:00:00Z"))
        assertEquals("unknown", syncBucketFor("bad date"))
        assertEquals("data/v2/checkIns/2026-11-02.json", syncBucketPath(SyncEntity.CHECKINS, "2026-11-02"))
    }

    @Test
    fun `fingerprint changes when record changes`() {
        val first = syncFingerprint(JSONObject().put("a", 1).toString())
        val same = syncFingerprint(JSONObject().put("a", 1).toString())
        val changed = syncFingerprint(JSONObject().put("a", 2).toString())

        assertEquals(first, same)
        assertFalse(first == changed)
    }

    @Test
    fun `bucket json round trip keeps record ids and data`() {
        val data = JSONObject().put("challengeId", "ch-1").put("date", "2026-11-02")
        val text = syncBucketJson("2026-11-02", listOf(SyncBucketItem("ch-1", data))).toString()

        val items = parseSyncBucket(text)

        assertEquals(1, items.size)
        assertEquals("ch-1", items.single().id)
        assertEquals("ch-1", items.single().data.optString("challengeId"))
        assertTrue(parseSyncBucket("broken").isEmpty())
    }
}
