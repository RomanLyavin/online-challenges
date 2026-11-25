package com.onlinechallenges.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

data class RemoteFile(val text: String, val sha: String)

data class RemoteBytes(val bytes: ByteArray, val sha: String)

class GitHubException(message: String) : Exception(message)

private class MissingShaException(message: String) : Exception(message)

private const val MAX_RETRIES = 2

/** Временные ошибки GitHub и прокси: повторяем запрос, чтобы синхронизация не падала на первом 500. */
internal fun isRetryableStatus(code: Int): Boolean = code == 429 || code in 500..599

internal fun retryDelayMs(attempt: Int, retryAfter: String? = null): Long {
    val fallback = 500L * (1L shl attempt.coerceIn(0, 3))
    val seconds = retryAfter?.trim()?.toLongOrNull()
    return if (seconds != null) (seconds * 1000L).coerceIn(0L, 10_000L) else fallback
}

internal fun isMissingShaError(code: Int, sha: String?, responseBody: String): Boolean {
    if (code != 422 || !sha.isNullOrBlank()) return false
    val detail = runCatching { JSONObject(responseBody).optString("message") }.getOrDefault("")
    return detail.contains("sha", ignoreCase = true) &&
        detail.contains("supplied", ignoreCase = true)
}

class GitHubClient(private val config: GitHubConfig) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun executeWithRetry(newRequest: () -> Request): okhttp3.Response {
        var attempt = 0
        while (true) {
            val response = client.newCall(newRequest()).execute()
            if (!isRetryableStatus(response.code) || attempt >= MAX_RETRIES) return response
            val retryAfter = response.header("Retry-After")
            response.close()
            delay(retryDelayMs(attempt, retryAfter))
            attempt++
        }
    }

    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val request = request("repos/${config.owner}/${config.repo}")
        executeWithRetry { request }.use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GitHubException(errorMessage(response.code, body))
            val json = JSONObject(body)
            val permissions = json.optJSONObject("permissions")
            if (permissions != null && permissions.has("push") && !permissions.optBoolean("push")) {
                throw GitHubException("Токен не имеет права записи в этот репозиторий")
            }
            // Проверка должна соответствовать настройкам синхронизации, а не только главной ветке репозитория.
            val branch = java.net.URLEncoder.encode(config.branch, "UTF-8").replace("+", "%20")
            executeWithRetry { request("repos/${config.owner}/${config.repo}/branches/$branch") }.use { response ->
                if (response.code == 404) {
                    throw GitHubException("GitHub: ветка ${config.branch} не найдена. Исправьте её в настройках.")
                }
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw GitHubException(errorMessage(response.code, body))
            }
            "Подключено: ${json.optString("full_name", "${config.owner}/${config.repo}")}, ветка ${config.branch}"
        }
    }

    suspend fun getFile(path: String): RemoteFile? {
        val remote = getFileBytes(path) ?: return null
        return RemoteFile(String(remote.bytes, Charsets.UTF_8), remote.sha)
    }

    suspend fun getFileBytes(path: String): RemoteBytes? = withContext(Dispatchers.IO) {
        val request = request("repos/${config.owner}/${config.repo}/contents/${encodePath(path)}?ref=${config.branch}")
        executeWithRetry { request }.use { response ->
            if (response.code == 404) return@withContext null
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GitHubException("Не удалось прочитать $path: ${errorMessage(response.code, body)}")
            }
            val json = JSONObject(body)
            if (json.optString("type") != "file") return@withContext null
            val content = json.optString("content", "")
            val decoded = runCatching {
                Base64.getMimeDecoder().decode(content)
            }.getOrElse { throw GitHubException("Не удалось прочитать $path из GitHub") }
            RemoteBytes(decoded, json.optString("sha", ""))
        }
    }

    suspend fun putFile(
        path: String,
        bytes: ByteArray,
        message: String,
        sha: String? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            putOnce(path, bytes, message, sha)
        } catch (missingSha: MissingShaException) {
            // Файл уже существует, но мы не знаем его версию. Запрашиваем sha и повторяем один раз.
            val remoteSha = getFileSha(path)
                ?: throw GitHubException(
                    "GitHub: не удалось получить версию файла $path. Повторите синхронизацию."
                )
            putOnce(path, bytes, message, remoteSha)
        } catch (error: GitHubException) {
            throw GitHubException("Не удалось загрузить $path: ${error.message}")
        }
    }

    private suspend fun putOnce(
        path: String,
        bytes: ByteArray,
        message: String,
        sha: String?
    ): String {
        val payload = JSONObject()
            .put("message", message)
            .put("content", Base64.getEncoder().encodeToString(bytes))
            .put("branch", config.branch)
        if (!sha.isNullOrBlank()) payload.put("sha", sha)
        executeWithRetry {
            Request.Builder()
                .url("https://api.github.com/repos/${config.owner}/${config.repo}/contents/${encodePath(path)}")
                .header("Authorization", "Bearer ${config.token}")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "OnlineChallenges-Android")
                .put(payload.toString().toRequestBody(jsonType))
                .build()
        }.use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (isMissingShaError(response.code, sha, responseBody)) {
                    throw MissingShaException(errorMessage(response.code, responseBody))
                }
                throw GitHubException(errorMessage(response.code, responseBody))
            }
            return runCatching { JSONObject(responseBody).getJSONObject("content").optString("sha", "") }
                .getOrDefault("")
        }
    }

    private suspend fun getFileSha(path: String): String? = withContext(Dispatchers.IO) {
        val request = request("repos/${config.owner}/${config.repo}/contents/${encodePath(path)}?ref=${config.branch}")
        executeWithRetry { request }.use { response ->
            if (response.code == 404) return@withContext null
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GitHubException(errorMessage(response.code, body))
            val json = JSONObject(body)
            if (json.optString("type") != "file") return@withContext null
            json.optString("sha", "").ifBlank { null }
        }
    }

    suspend fun putText(path: String, text: String, message: String, sha: String? = null): String =
        putFile(path, text.toByteArray(Charsets.UTF_8), message, sha)

    /** Удаляет файл из репозитория. Если файла уже нет, возвращает false. */
    suspend fun deleteFile(path: String, message: String): Boolean = withContext(Dispatchers.IO) {
        val sha = getFileSha(path) ?: return@withContext false
        val payload = JSONObject()
            .put("message", message)
            .put("sha", sha)
            .put("branch", config.branch)
        executeWithRetry {
            Request.Builder()
                .url("https://api.github.com/repos/${config.owner}/${config.repo}/contents/${encodePath(path)}")
                .header("Authorization", "Bearer ${config.token}")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "OnlineChallenges-Android")
                .delete(payload.toString().toRequestBody(jsonType))
                .build()
        }.use { response ->
            if (response.code == 404) return@withContext false
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GitHubException(errorMessage(response.code, body))
            true
        }
    }

    fun rawUrl(path: String): String =
        "https://raw.githubusercontent.com/${config.owner}/${config.repo}/${config.branch}/${path.trimStart('/')}"

    fun pageUrl(path: String): String = config.pagesUrl + path.trimStart('/')

    private fun request(path: String): Request = Request.Builder()
        .url("https://api.github.com/$path")
        .header("Authorization", "Bearer ${config.token}")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "OnlineChallenges-Android")
        .get()
        .build()

    private fun encodePath(path: String): String =
        path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    internal fun errorMessage(code: Int, body: String): String {
        val apiMessage = runCatching { JSONObject(body).optString("message") }.getOrDefault("")
        val detail = apiMessage.ifBlank { body.take(300) }
        return when {
            code == 401 -> "GitHub: неверный токен (401)"
            code == 403 && detail.contains("rate limit", ignoreCase = true) ->
                "GitHub: превышен лимит запросов, попробуйте позже"
            code == 404 -> "GitHub: репозиторий или файл не найден (404). Проверьте owner/repo/ветку и доступ токена."
            code == 409 || code == 422 -> "GitHub: конфликт версий файла. Повторите синхронизацию. $detail"
            code == 429 -> "GitHub: слишком много запросов (429). Повторите синхронизацию позже."
            code in 500..599 ->
                "GitHub временно недоступен ($code). Попробуйте синхронизировать позже. ${detail.take(120)}"
            else -> "GitHub $code: $detail"
        }
    }
}
