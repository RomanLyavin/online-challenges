package com.onlinechallenges.app.data

import org.json.JSONObject

/** Ищет публичный профиль по хэшу email, не публикуя сам адрес. */
object PublicProfileLookup {
    fun findByEmailHash(json: String?, emailHash: String): Profile? {
        if (json.isNullOrBlank() || emailHash.isBlank()) return null
        return runCatching {
            JSONObject(json)
                .optJSONArray("profiles")
                .toList { Profile.fromJson(it) }
                .firstOrNull { it.emailHash.equals(emailHash, ignoreCase = true) }
        }.getOrNull()
    }
}
