package com.onlinechallenges.app.data

import android.util.Log
import com.onlinechallenges.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.net.URLEncoder

/** Эмоции над отметками участников. Источник данных — Supabase Postgres с RLS. */
enum class ReactionType(val apiValue: String, val emoji: String) {
    LIKE("like", "👍"),
    FIRE("fire", "🔥"),
    DISLIKE("dislike", "👎"),
    HEART("heart", "❤️");

    companion object {
        fun fromApi(value: String?): ReactionType? = entries.firstOrNull { it.apiValue == value }
    }
}

data class ReactionSummary(
    val counts: Map<String, Map<ReactionType, Int>> = emptyMap(),
    val mine: Map<String, ReactionType> = emptyMap()
)

object ReactionRepository {
    private const val TAG = "ReactionRepository"
    private const val TABLE = "checkin_reactions"
    private val client by lazy { OkHttpClient() }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    /** Детерминированный ключ, совпадающий с веб-версией. */
    fun checkInId(challengeId: String, userCode: String, date: String): String =
        "$challengeId|$userCode|$date"

    suspend fun load(checkInIds: List<String>): ReactionSummary = withContext(Dispatchers.IO) {
        val unique = checkInIds.distinct().filter { it.isNotBlank() }
        if (!isConfigured || unique.isEmpty()) return@withContext ReactionSummary()

        val values = unique.joinToString(",") { encode(it) }
        val url = restUrl(
            "$TABLE?select=checkin_id,user_id,reaction&checkin_id=in.($values)"
        )
        val response = client.newCall(authorizedRequest(url).get().build()).execute()
        response.use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error(errorMessage(resp.code, body))
            parse(body)
        }
    }

    suspend fun toggle(checkInId: String, type: ReactionType) = withContext(Dispatchers.IO) {
        if (!isConfigured) error("Подключение к серверу реакций недоступно")
        val userId = SupabaseAuth.currentUserId()
            ?: error("Войдите, чтобы оставить реакцию")

        val existing = currentReaction(checkInId, userId)
        if (shouldRemove(existing, type)) {
            val url = restUrl(
                "$TABLE?checkin_id=eq.${encode(checkInId)}&user_id=eq.${encode(userId)}"
            )
            execute(authorizedRequest(url).delete().build())
        } else {
            val url = restUrl("$TABLE?on_conflict=checkin_id,user_id")
            val body = org.json.JSONObject()
                .put("checkin_id", checkInId)
                .put("user_id", userId)
                .put("reaction", type.apiValue)
                .put("updated_at", isoNow())
                .toString()
                .toRequestBody(jsonMediaType)
            val request = authorizedRequest(url)
                .header("Prefer", "return=minimal,resolution=merge-duplicates")
                .post(body)
                .build()
            execute(request)
        }
    }

    internal fun shouldRemove(current: ReactionType?, selected: ReactionType): Boolean =
        current == selected

    private fun currentReaction(checkInId: String, userId: String): ReactionType? {
        val url = restUrl(
            "$TABLE?select=reaction&checkin_id=eq.${encode(checkInId)}&user_id=eq.${encode(userId)}"
        )
        val response = client.newCall(authorizedRequest(url).get().build()).execute()
        response.use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error(errorMessage(resp.code, body))
            val array = runCatching { JSONArray(body) }.getOrDefault(JSONArray())
            if (array.length() == 0) return null
            return ReactionType.fromApi(array.optJSONObject(0)?.optString("reaction"))
        }
    }

    private fun execute(request: Request) {
        val response = client.newCall(request).execute()
        response.use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error(errorMessage(resp.code, body))
        }
    }

    private fun authorizedRequest(url: String): Request.Builder {
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        val token = SupabaseAuth.currentAccessToken() ?: key
        return Request.Builder()
            .url(url)
            .header("apikey", key)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
    }

    private fun restUrl(suffix: String): String =
        BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/" + suffix

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun errorMessage(code: Int, body: String): String {
        val message = runCatching {
            org.json.JSONObject(body).optString("message")
        }.getOrDefault("").ifBlank { body.take(160) }
        return if (message.isBlank()) "Ошибка сервера реакций ($code)" else "Реакции: $message"
    }

    internal fun parse(
        body: String,
        userId: String? = SupabaseAuth.currentUserId()
    ): ReactionSummary {
        val array = runCatching { JSONArray(body) }.getOrDefault(JSONArray())
        val counts = LinkedHashMap<String, MutableMap<ReactionType, Int>>()
        val mine = LinkedHashMap<String, ReactionType>()
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val checkInId = row.optString("checkin_id", "")
            val type = ReactionType.fromApi(row.optString("reaction", "")) ?: continue
            if (checkInId.isBlank()) continue
            val perType = counts.getOrPut(checkInId) { LinkedHashMap() }
            perType[type] = (perType[type] ?: 0) + 1
            if (userId != null && userId == row.optString("user_id", "")) {
                mine[checkInId] = type
            }
        }
        return ReactionSummary(counts = counts, mine = mine)
    }
}
