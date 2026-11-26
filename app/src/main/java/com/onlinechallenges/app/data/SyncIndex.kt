package com.onlinechallenges.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset

/**
 * Инкрементальная синхронизация: небольшой индекс описывает все записи,
 * а сами данные лежат в файлах по дате их обновления. Приложение читает
 * индекс, скачивает только бакеты с записями, опубликованными после
 * последней синхронизации, и не трогает остальные файлы.
 */
enum class SyncEntity(val directory: String) {
    CHALLENGES("challenges"),
    PROFILES("profiles"),
    CHECKINS("checkIns"),
    CONFIRMATIONS("confirmations")
}

data class SyncRecordRef(
    val id: String,
    val bucket: String,
    val fingerprint: String,
    /** Время публикации этой версии записи, а не время её изменения на устройстве. */
    val publishedAt: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("bucket", bucket)
        .put("fingerprint", fingerprint)
        .put("publishedAt", publishedAt)

    companion object {
        fun fromJson(json: JSONObject): SyncRecordRef? {
            val id = json.optString("id", "")
            val bucket = json.optString("bucket", "")
            if (id.isBlank() || bucket.isBlank()) return null
            return SyncRecordRef(
                id = id,
                bucket = bucket,
                fingerprint = json.optString("fingerprint", ""),
                publishedAt = json.optString("publishedAt", "")
            )
        }
    }
}

data class SyncIndex(
    val records: Map<SyncEntity, List<SyncRecordRef>> = emptyMap(),
    val deletedChallengeIds: List<String> = emptyList()
) {
    fun idsPublishedAfter(since: String?): Map<SyncEntity, Set<String>> {
        if (since.isNullOrBlank()) return records.mapValues { (_, refs) -> refs.map { it.id }.toSet() }
        val threshold = runCatching { Instant.parse(since) }.getOrNull()
            ?: return records.mapValues { (_, refs) -> refs.map { it.id }.toSet() }
        return records.mapValues { (_, refs) ->
            refs.filter { ref ->
                val published = runCatching { Instant.parse(ref.publishedAt) }.getOrNull()
                    // Неизвестное время считаем свежим: лучше лишний раз прочитать файл.
                    ?: return@filter true
                !published.isBefore(threshold)
            }.map { it.id }.toSet()
        }
    }

    fun toJson(): JSONObject {
        val recordsJson = JSONObject()
        for (entity in SyncEntity.entries) {
            recordsJson.put(
                entity.name,
                JSONArray(
                    records[entity].orEmpty()
                        .sortedBy { it.id }
                        .map { it.toJson() }
                )
            )
        }
        return JSONObject()
            .put("version", VERSION)
            .put("records", recordsJson)
            .put("deletedChallengeIds", JSONArray(deletedChallengeIds.distinct().sorted()))
    }

    companion object {
        const val VERSION = 2

        fun fromJson(text: String?): SyncIndex? {
            if (text.isNullOrBlank()) return null
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
            if (json.optInt("version", -1) != VERSION) return null
            val recordsJson = json.optJSONObject("records") ?: JSONObject()
            val records = SyncEntity.entries.associateWith { entity ->
                recordsJson.optJSONArray(entity.name).toRefs()
            }
            return SyncIndex(
                records = records,
                deletedChallengeIds = json.optJSONArray("deletedChallengeIds").toStringList().distinct()
            )
        }

        private fun JSONArray?.toRefs(): List<SyncRecordRef> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { index ->
                runCatching { optJSONObject(index) }.getOrNull()?.let { SyncRecordRef.fromJson(it) }
            }
        }
    }
}

data class SyncItem(val id: String, val json: JSONObject) {
    val text: String get() = json.toString()
    val fingerprint: String get() = syncFingerprint(text)
}

data class SyncBucketItem(val id: String, val data: JSONObject)

fun syncBucketPath(entity: SyncEntity, bucket: String): String =
    "data/v2/${entity.directory}/${bucket}.json"

fun syncBucketFor(updatedAt: String): String {
    val instant = runCatching { Instant.parse(updatedAt) }.getOrNull() ?: return "unknown"
    return instant.atZone(ZoneOffset.UTC).toLocalDate().toString()
}

fun syncFingerprint(text: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

fun syncBucketJson(bucket: String, items: List<SyncBucketItem>): JSONObject = JSONObject()
    .put("version", SyncIndex.VERSION)
    .put("date", bucket)
    .put(
        "items",
        JSONArray(
            items.sortedBy { it.id }.map { item ->
                JSONObject().put("id", item.id).put("data", item.data)
            }
        )
    )

fun parseSyncBucket(text: String?): List<SyncBucketItem> {
    if (text.isNullOrBlank()) return emptyList()
    val json = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
    if (json.optInt("version", -1) != SyncIndex.VERSION) return emptyList()
    val array = json.optJSONArray("items") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val entry = runCatching { array.getJSONObject(index) }.getOrNull() ?: return@mapNotNull null
        val id = entry.optString("id", "")
        val data = runCatching { entry.getJSONObject("data") }.getOrNull()
        if (id.isBlank() || data == null) null else SyncBucketItem(id, data)
    }
}
