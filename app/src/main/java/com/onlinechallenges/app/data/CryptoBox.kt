package com.onlinechallenges.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Шифрование приватных челленджей. Репозиторий публичный, поэтому приватность
 * обеспечивается не правами доступа к папке, а ключом, который передаётся
 * участникам в коде-приглашении.
 */
object CryptoBox {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BYTES = 32
    private val MAGIC = byteArrayOf('O'.code.toByte(), 'C'.code.toByte(), '1'.code.toByte())

    fun newKey(): String {
        val bytes = ByteArray(KEY_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun isValidKey(key: String): Boolean =
        runCatching { decodeKey(key).size == KEY_BYTES }.getOrDefault(false)

    fun encryptText(text: String, key: String): ByteArray =
        encryptBytes(text.toByteArray(Charsets.UTF_8), key)

    fun decryptText(payload: ByteArray, key: String): String? =
        decryptBytes(payload, key)?.let { String(it, Charsets.UTF_8) }

    fun encryptBytes(plain: ByteArray, key: String): ByteArray {
        val secret = SecretKeySpec(decodeKey(key), "AES")
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain)
        val output = ByteArrayOutputStream(MAGIC.size + iv.size + encrypted.size)
        output.write(MAGIC)
        output.write(iv)
        output.write(encrypted)
        return output.toByteArray()
    }

    fun decryptBytes(payload: ByteArray, key: String): ByteArray? = runCatching {
        if (payload.size <= MAGIC.size + IV_BYTES) return@runCatching null
        for (index in MAGIC.indices) {
            if (payload[index] != MAGIC[index]) return@runCatching null
        }
        val iv = payload.copyOfRange(MAGIC.size, MAGIC.size + IV_BYTES)
        val encrypted = payload.copyOfRange(MAGIC.size + IV_BYTES, payload.size)
        val secret = SecretKeySpec(decodeKey(key), "AES")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, iv))
        cipher.doFinal(encrypted)
    }.getOrNull()

    private fun decodeKey(key: String): ByteArray {
        val normalized = key.trim()
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return Base64.getUrlDecoder().decode(padded)
    }
}

/** Код-приглашение: версия, идентификатор челленджа и ключ шифрования. */
object InviteCodes {
    private const val PREFIX = "OC1"
    private const val SEPARATOR = '.'

    data class Parsed(val challengeId: String, val key: String)

    fun create(challengeId: String, key: String): String =
        listOf(PREFIX, challengeId, key).joinToString(SEPARATOR.toString())

    fun parse(raw: String?): Parsed? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw.trim().replace(Regex("\\s+"), "")
        val parts = normalized.split(SEPARATOR)
        if (parts.size != 3) return null
        if (parts[0] != PREFIX) return null
        val challengeId = parts[1]
        val key = parts[2]
        if (challengeId.isBlank() || !CryptoBox.isValidKey(key)) return null
        return Parsed(challengeId, key)
    }
}

/**
 * Содержимое шифрованного файла приватного челленджа. В публичный репозиторий
 * попадает только в зашифрованном виде.
 */
data class PrivateBundle(
    val challenge: Challenge,
    val checkIns: List<CheckIn>,
    val confirmations: List<PeerConfirmation>
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", 1)
        .put("challenge", challenge.toBundleJson())
        .put("checkIns", JSONArray(checkIns.map { it.toSyncJson() }))
        .put("confirmations", JSONArray(confirmations.map { it.toSyncJson() }))

    companion object {
        fun fromJson(text: String): PrivateBundle? = runCatching {
            val json = JSONObject(text)
            PrivateBundle(
                challenge = Challenge.fromJson(json.getJSONObject("challenge")),
                checkIns = json.optJSONArray("checkIns").toList { CheckIn.fromJson(it) },
                confirmations = json.optJSONArray("confirmations").toList { PeerConfirmation.fromJson(it) }
            )
        }.getOrNull()
    }
}
