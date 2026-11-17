package com.onlinechallenges.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Gender(val label: String) {
    MALE("Мужской"),
    FEMALE("Женский"),
    UNSPECIFIED("Не указывать");

    companion object {
        fun parse(value: String?): Gender =
            entries.firstOrNull { it.name == value } ?: UNSPECIFIED
    }
}

enum class ProofType(val label: String, val shortLabel: String) {
    PHOTO("Фото", "📷"),
    VIDEO("Видео", "🎬"),
    ANY("Фото или видео", "📎");

    companion object {
        fun parse(value: String?): ProofType =
            entries.firstOrNull { it.name == value } ?: PHOTO
    }
}

enum class ChallengeMode(val label: String) {
    DAILY("Каждый день"),
    INTERVAL("Каждые N дней"),
    TOTAL("Всего за период");

    companion object {
        fun parse(value: String?): ChallengeMode =
            entries.firstOrNull { it.name == value } ?: DAILY
    }
}

enum class AttachmentType { PHOTO, VIDEO }

enum class ChallengeStatus(val label: String) {
    UPCOMING("Скоро"),
    ACTIVE("Идёт"),
    FINISHED("Завершён");

    companion object {
        fun of(challenge: Challenge, today: LocalDate): ChallengeStatus = when {
            today.isBefore(parseDate(challenge.startDate)) -> UPCOMING
            today.isAfter(parseDate(challenge.endDate)) -> FINISHED
            else -> ACTIVE
        }
    }
}

data class UserAccount(
    val id: String,
    val email: String,
    val passwordHash: String,
    val salt: String,
    val code: String,
    val createdAt: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("email", email)
        .put("passwordHash", passwordHash)
        .put("salt", salt)
        .put("code", code)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(json: JSONObject): UserAccount = UserAccount(
            id = json.getString("id"),
            email = json.getString("email"),
            passwordHash = json.getString("passwordHash"),
            salt = json.getString("salt"),
            code = json.getString("code"),
            createdAt = json.optString("createdAt", isoNow())
        )
    }
}

data class PendingVerification(
    val email: String,
    val passwordHash: String,
    val salt: String,
    val code: String,
    val expiresAt: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("email", email)
        .put("passwordHash", passwordHash)
        .put("salt", salt)
        .put("code", code)
        .put("expiresAt", expiresAt)

    fun isExpired(): Boolean = runCatching { Instant.parse(expiresAt).isBefore(Instant.now()) }.getOrDefault(true)

    companion object {
        fun fromJson(json: JSONObject): PendingVerification = PendingVerification(
            email = json.getString("email"),
            passwordHash = json.getString("passwordHash"),
            salt = json.getString("salt"),
            code = json.getString("code"),
            expiresAt = json.getString("expiresAt")
        )
    }
}

data class Medal(
    val challengeId: String,
    val challengeTitle: String,
    val place: Int,
    val earnedAt: String
) {
    val key: String get() = "$challengeId#$place"
    val emoji: String get() = when (place) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "🏅"
    }
    val label: String get() = when (place) {
        1 -> "1 место"
        2 -> "2 место"
        3 -> "3 место"
        else -> "$place место"
    }

    fun toJson(): JSONObject = JSONObject()
        .put("challengeId", challengeId)
        .put("challengeTitle", challengeTitle)
        .put("place", place)
        .put("earnedAt", earnedAt)

    companion object {
        fun fromJson(json: JSONObject): Medal = Medal(
            challengeId = json.getString("challengeId"),
            challengeTitle = json.optString("challengeTitle", "Челлендж"),
            place = json.optInt("place", 0),
            earnedAt = json.optString("earnedAt", "")
        )
    }
}

data class Profile(
    val code: String,
    val name: String,
    val birthDate: String = "",
    val gender: Gender = Gender.UNSPECIFIED,
    val avatarLocalPath: String? = null,
    val avatarRepoPath: String? = null,
    val medals: List<Medal> = emptyList(),
    val updatedAt: String = isoNow(),
    val isDemo: Boolean = false
) {
    val isComplete: Boolean
        get() = name.isNotBlank() && birthDate.isNotBlank() && gender != Gender.UNSPECIFIED

    val initials: String
        get() = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "Я" }

    fun withMedals(medals: List<Medal>): Profile = copy(
        medals = medals.distinctBy { it.key }.sortedBy { it.earnedAt },
        updatedAt = isoNow()
    )

    fun toJson(): JSONObject = JSONObject()
        .put("code", code)
        .put("name", name)
        .put("birthDate", birthDate)
        .put("gender", gender.name)
        .put("avatarLocalPath", avatarLocalPath ?: JSONObject.NULL)
        .put("avatarRepoPath", avatarRepoPath ?: JSONObject.NULL)
        .put("medals", JSONArray(medals.map { it.toJson() }))
        .put("updatedAt", updatedAt)
        .put("isDemo", isDemo)

    fun toSyncJson(): JSONObject = JSONObject()
        .put("code", code)
        .put("name", name)
        .put("birthDate", birthDate)
        .put("gender", gender.name)
        .put("avatarRepoPath", avatarRepoPath ?: JSONObject.NULL)
        .put("medals", JSONArray(medals.map { it.toJson() }))
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(json: JSONObject): Profile = Profile(
            code = json.getString("code"),
            name = json.optString("name", "Участник"),
            birthDate = json.optString("birthDate", ""),
            gender = Gender.parse(json.optString("gender", null)),
            avatarLocalPath = json.optStringOrNull("avatarLocalPath"),
            avatarRepoPath = json.optStringOrNull("avatarRepoPath"),
            medals = json.optJSONArray("medals").toList { Medal.fromJson(it) },
            updatedAt = json.optString("updatedAt", isoNow()),
            isDemo = json.optBoolean("isDemo", false)
        )
    }
}

data class ActivityType(
    val id: String,
    val name: String,
    val unit: String,
    val emoji: String,
    val defaultTarget: Double,
    val proofType: ProofType,
    val createdByCode: String? = null,
    val updatedAt: String = "2026-01-01T00:00:00Z",
    val isDefault: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("unit", unit)
        .put("emoji", emoji)
        .put("defaultTarget", defaultTarget)
        .put("proofType", proofType.name)
        .put("createdByCode", createdByCode ?: JSONObject.NULL)
        .put("updatedAt", updatedAt)
        .put("isDefault", isDefault)

    companion object {
        fun fromJson(json: JSONObject): ActivityType = ActivityType(
            id = json.getString("id"),
            name = json.optString("name", "Активность"),
            unit = json.optString("unit", ""),
            emoji = json.optString("emoji", "🎯"),
            defaultTarget = json.optDouble("defaultTarget", 1.0),
            proofType = ProofType.parse(json.optString("proofType", null)),
            createdByCode = json.optStringOrNull("createdByCode"),
            updatedAt = json.optString("updatedAt", "2026-01-01T00:00:00Z"),
            isDefault = json.optBoolean("isDefault", false)
        )
    }
}

data class ChallengeGoal(
    val id: String,
    val activityId: String,
    val name: String,
    val unit: String,
    val emoji: String,
    val target: Double,
    val proofType: ProofType
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("activityId", activityId)
        .put("name", name)
        .put("unit", unit)
        .put("emoji", emoji)
        .put("target", target)
        .put("proofType", proofType.name)

    companion object {
        fun fromJson(json: JSONObject): ChallengeGoal = ChallengeGoal(
            id = json.getString("id"),
            activityId = json.optString("activityId", ""),
            name = json.optString("name", "Цель"),
            unit = json.optString("unit", ""),
            emoji = json.optString("emoji", "🎯"),
            target = json.optDouble("target", 1.0).coerceAtLeast(0.0001),
            proofType = ProofType.parse(json.optString("proofType", null))
        )
    }
}

data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val startDate: String,
    val endDate: String,
    val mode: ChallengeMode,
    val intervalDays: Int,
    val goals: List<ChallengeGoal>,
    val creatorCode: String,
    val creatorName: String,
    val participantCodes: List<String>,
    val createdAt: String,
    val updatedAt: String,
    val isDemo: Boolean = false
) {
    val start: LocalDate get() = parseDate(startDate)
    val end: LocalDate get() = parseDate(endDate)

    fun status(today: LocalDate = LocalDate.now()): ChallengeStatus = ChallengeStatus.of(this, today)

    fun modeDescription(): String = when (mode) {
        ChallengeMode.DAILY -> "Цели нужно выполнять каждый день"
        ChallengeMode.INTERVAL -> "Цели нужно выполнять каждые $intervalDays дн."
        ChallengeMode.TOTAL -> "Цели можно выполнять в любой день — важен общий результат"
    }

    fun withParticipant(code: String): Challenge {
        if (participantCodes.contains(code)) return this
        return copy(participantCodes = participantCodes + code, updatedAt = isoNow())
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("description", description)
        .put("startDate", startDate)
        .put("endDate", endDate)
        .put("mode", mode.name)
        .put("intervalDays", intervalDays)
        .put("goals", JSONArray(goals.map { it.toJson() }))
        .put("creatorCode", creatorCode)
        .put("creatorName", creatorName)
        .put("participantCodes", JSONArray(participantCodes))
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("isDemo", isDemo)

    fun toSyncJson(): JSONObject = toJson().apply { remove("isDemo") }

    companion object {
        fun fromJson(json: JSONObject): Challenge = Challenge(
            id = json.getString("id"),
            title = json.optString("title", "Челлендж"),
            description = json.optString("description", ""),
            startDate = json.optString("startDate", LocalDate.now().toString()),
            endDate = json.optString("endDate", LocalDate.now().plusDays(7).toString()),
            mode = ChallengeMode.parse(json.optString("mode", null)),
            intervalDays = json.optInt("intervalDays", 1).coerceIn(1, 30),
            goals = json.optJSONArray("goals").toList { ChallengeGoal.fromJson(it) },
            creatorCode = json.optString("creatorCode", ""),
            creatorName = json.optString("creatorName", "Организатор"),
            participantCodes = json.optJSONArray("participantCodes").toStringList(),
            createdAt = json.optString("createdAt", isoNow()),
            updatedAt = json.optString("updatedAt", isoNow()),
            isDemo = json.optBoolean("isDemo", false)
        )
    }
}

data class Attachment(
    val id: String,
    val fileName: String,
    val mime: String,
    val type: AttachmentType,
    val sizeBytes: Long,
    val localPath: String? = null,
    val remotePath: String? = null,
    val uploadedAt: String? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("fileName", fileName)
        .put("mime", mime)
        .put("type", type.name)
        .put("sizeBytes", sizeBytes)
        .put("localPath", localPath ?: JSONObject.NULL)
        .put("remotePath", remotePath ?: JSONObject.NULL)
        .put("uploadedAt", uploadedAt ?: JSONObject.NULL)

    fun toSyncJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("fileName", fileName)
        .put("mime", mime)
        .put("type", type.name)
        .put("sizeBytes", sizeBytes)
        .put("remotePath", remotePath ?: JSONObject.NULL)
        .put("uploadedAt", uploadedAt ?: JSONObject.NULL)

    companion object {
        fun fromJson(json: JSONObject): Attachment = Attachment(
            id = json.optString("id", java.util.UUID.randomUUID().toString()),
            fileName = json.optString("fileName", ""),
            mime = json.optString("mime", "application/octet-stream"),
            type = runCatching { AttachmentType.valueOf(json.optString("type", "PHOTO")) }.getOrDefault(AttachmentType.PHOTO),
            sizeBytes = json.optLong("sizeBytes", 0L),
            localPath = json.optStringOrNull("localPath"),
            remotePath = json.optStringOrNull("remotePath"),
            uploadedAt = json.optStringOrNull("uploadedAt")
        )
    }
}

data class GoalResult(
    val goalId: String,
    val actual: Double,
    val attachments: List<Attachment> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("goalId", goalId)
        .put("actual", actual)
        .put("attachments", JSONArray(attachments.map { it.toJson() }))

    fun toSyncJson(): JSONObject = JSONObject()
        .put("goalId", goalId)
        .put("actual", actual)
        .put("attachments", JSONArray(attachments.map { it.toSyncJson() }))

    companion object {
        fun fromJson(json: JSONObject): GoalResult = GoalResult(
            goalId = json.getString("goalId"),
            actual = json.optDouble("actual", 0.0),
            attachments = json.optJSONArray("attachments").toList { Attachment.fromJson(it) }
        )
    }
}

data class CheckIn(
    val id: String,
    val challengeId: String,
    val userCode: String,
    val date: String,
    val results: List<GoalResult>,
    val note: String = "",
    val createdAt: String = isoNow(),
    val updatedAt: String = isoNow(),
    val isDemo: Boolean = false
) {
    val localDate: LocalDate get() = parseDate(date)

    fun resultFor(goalId: String): GoalResult? = results.firstOrNull { it.goalId == goalId }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("challengeId", challengeId)
        .put("userCode", userCode)
        .put("date", date)
        .put("results", JSONArray(results.map { it.toJson() }))
        .put("note", note)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("isDemo", isDemo)

    fun toSyncJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("challengeId", challengeId)
        .put("userCode", userCode)
        .put("date", date)
        .put("results", JSONArray(results.map { it.toSyncJson() }))
        .put("note", note)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(json: JSONObject): CheckIn = CheckIn(
            id = json.optString("id", java.util.UUID.randomUUID().toString()),
            challengeId = json.getString("challengeId"),
            userCode = json.getString("userCode"),
            date = json.getString("date"),
            results = json.optJSONArray("results").toList { GoalResult.fromJson(it) },
            note = json.optString("note", ""),
            createdAt = json.optString("createdAt", isoNow()),
            updatedAt = json.optString("updatedAt", isoNow()),
            isDemo = json.optBoolean("isDemo", false)
        )
    }
}

/**
 * Подтверждение чужой отметки другим участником челленджа.
 * Хранится отдельно от отметки, чтобы подтверждающий не перезаписывал чужие данные.
 */
data class PeerConfirmation(
    val challengeId: String,
    val checkInUserCode: String,
    val date: String,
    val goalId: String,
    val confirmerCode: String,
    val actual: Double,
    val createdAt: String = isoNow(),
    val updatedAt: String = isoNow(),
    val revoked: Boolean = false,
    val isDemo: Boolean = false
) {
    val key: String get() = "$challengeId|$checkInUserCode|$date|$goalId|$confirmerCode"

    fun toJson(): JSONObject = JSONObject()
        .put("challengeId", challengeId)
        .put("checkInUserCode", checkInUserCode)
        .put("date", date)
        .put("goalId", goalId)
        .put("confirmerCode", confirmerCode)
        .put("actual", actual)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("revoked", revoked)
        .put("isDemo", isDemo)

    fun toSyncJson(): JSONObject = toJson().apply { remove("isDemo") }

    companion object {
        fun fromJson(json: JSONObject): PeerConfirmation = PeerConfirmation(
            challengeId = json.optString("challengeId", ""),
            checkInUserCode = json.optString("checkInUserCode", ""),
            date = json.optString("date", ""),
            goalId = json.optString("goalId", ""),
            confirmerCode = json.optString("confirmerCode", ""),
            actual = json.optDouble("actual", 0.0),
            createdAt = json.optString("createdAt", isoNow()),
            updatedAt = json.optString("updatedAt", isoNow()),
            revoked = json.optBoolean("revoked", false),
            isDemo = json.optBoolean("isDemo", false)
        )
    }
}

/** Всё, что приложение хранит локально. */
data class AppData(
    val accounts: List<UserAccount> = emptyList(),
    val pending: PendingVerification? = null,
    val sessionCode: String? = null,
    val profiles: List<Profile> = emptyList(),
    val activities: List<ActivityType> = defaultActivities(),
    val challenges: List<Challenge> = emptyList(),
    val checkIns: List<CheckIn> = emptyList(),
    val confirmations: List<PeerConfirmation> = emptyList()
) {
    fun profile(code: String?): Profile? = profiles.firstOrNull { it.code == code }
    fun account(email: String): UserAccount? = accounts.firstOrNull { it.email.equals(email, ignoreCase = true) }
    fun currentProfile(): Profile? = profile(sessionCode)
    fun challenge(id: String): Challenge? = challenges.firstOrNull { it.id == id }
    fun checkInsFor(challengeId: String, code: String): List<CheckIn> =
        checkIns.filter { it.challengeId == challengeId && it.userCode == code }

    fun checkIn(challengeId: String, code: String, date: String): CheckIn? =
        checkIns.firstOrNull { it.challengeId == challengeId && it.userCode == code && it.date == date }

    fun confirmationsFor(challengeId: String, code: String, date: String): List<PeerConfirmation> =
        confirmations.filter {
            it.challengeId == challengeId && it.checkInUserCode == code && it.date == date
        }

    fun withProfile(updated: Profile): AppData {
        val exists = profiles.any { it.code == updated.code }
        return copy(profiles = if (exists) profiles.map { if (it.code == updated.code) updated else it } else profiles + updated)
    }

    fun withChallenge(updated: Challenge): AppData {
        val exists = challenges.any { it.id == updated.id }
        return copy(challenges = if (exists) challenges.map { if (it.id == updated.id) updated else it } else challenges + updated)
    }

    fun withCheckIn(updated: CheckIn): AppData {
        val exists = checkIns.any { it.id == updated.id }
        return copy(checkIns = if (exists) checkIns.map { if (it.id == updated.id) updated else it } else checkIns + updated)
    }
}

fun isoNow(): String = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()

fun parseDate(value: String): LocalDate =
    runCatching { LocalDate.parse(value) }.getOrElse { LocalDate.now() }

fun parseDateOrNull(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

fun defaultActivities(): List<ActivityType> = listOf(
    ActivityType("steps", "Шаги", "шагов", "👟", 10_000.0, ProofType.PHOTO, isDefault = true),
    ActivityType("running", "Пробежка", "км", "🏃", 5.0, ProofType.PHOTO, isDefault = true),
    ActivityType("sleep", "Сон", "ч", "😴", 8.0, ProofType.PHOTO, isDefault = true),
    ActivityType("pushups", "Отжимания", "раз", "💪", 50.0, ProofType.VIDEO, isDefault = true),
    ActivityType("squats", "Приседания", "раз", "🦵", 50.0, ProofType.VIDEO, isDefault = true),
    ActivityType("plank", "Планка", "мин", "🧘", 2.0, ProofType.VIDEO, isDefault = true),
    ActivityType("abs", "Пресс", "раз", "🔥", 50.0, ProofType.VIDEO, isDefault = true)
)

fun challengeGoalId(challengeId: String, activityId: String): String = "$challengeId-$activityId"

internal fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}

internal fun <T> JSONArray?.toList(mapper: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val result = ArrayList<T>(length())
    for (i in 0 until length()) {
        optJSONObject(i)?.let { result += mapper(it) }
    }
    return result
}

internal fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val result = ArrayList<String>(length())
    for (i in 0 until length()) {
        val value = optString(i)
        if (value.isNotBlank()) result += value
    }
    return result
}
