package com.onlinechallenges.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class SyncSummary(
    val challenges: Int,
    val checkIns: Int,
    val uploadedMedia: Int,
    val warnings: List<String> = emptyList()
)

class SyncManager(
    private val repo: AppRepository,
    private val prefs: SyncPrefs
) {
    private val mutex = Mutex()

    suspend fun sync(): SyncSummary = mutex.withLock {
        withContext(Dispatchers.IO) {
            val config = prefs.load()
            if (!config.isConfigured) {
                throw GitHubException("Укажите GitHub-токен в настройках синхронизации")
            }
            try {
                val summary = doSync(config)
                val warningText = if (summary.warnings.isEmpty()) "" else
                    " · предупреждений: ${summary.warnings.size}"
                prefs.setLastResult(
                    "Синхронизировано: ${summary.challenges} челленджей, ${summary.checkIns} отметок, " +
                        "файлов: ${summary.uploadedMedia}$warningText",
                    true
                )
                summary
            } catch (error: Exception) {
                prefs.setLastResult(error.message ?: "Ошибка синхронизации", false)
                throw error
            }
        }
    }

    private suspend fun doSync(config: GitHubConfig): SyncSummary {
        val client = GitHubClient(config)
        val cache = mutableMapOf<String, RemoteFile?>()

        val remoteCatalog = readFile(client, PATH_CATALOG, cache)
        val remoteChallenges = readFile(client, PATH_CHALLENGES, cache)
        val remoteProfiles = readFile(client, PATH_PROFILES, cache)
        val remoteCheckIns = readFile(client, PATH_CHECKINS, cache)

        val before = repo.snapshot()
        val remoteActivities = parseActivities(remoteCatalog?.text)
        val remoteChallengesList = parseChallenges(remoteChallenges?.text)
        val remoteProfilesList = parseProfiles(remoteProfiles?.text)
        val remoteCheckInsList = parseCheckIns(remoteCheckIns?.text)

        val merged = Awards.apply(
            before.copy(
                activities = mergeActivities(before.activities, remoteActivities),
                challenges = mergeChallenges(before.challenges, remoteChallengesList),
                profiles = mergeProfiles(before.profiles, remoteProfilesList),
                checkIns = mergeCheckIns(before.checkIns, remoteCheckInsList)
            )
        )
        repo.replace(merged)

        // Сначала публикуем структуру: каталог, челленджи, профили и отметки.
        pushJson(client, PATH_CATALOG, catalogJson(merged.activities), "Sync catalog", cache)
        pushJson(client, PATH_CHALLENGES, challengesJson(merged.challenges), "Sync challenges", cache)
        pushJson(client, PATH_PROFILES, profilesJson(merged.profiles), "Sync profiles", cache)
        pushJson(client, PATH_CHECKINS, checkInsJson(merged.checkIns), "Sync check-ins", cache)

        val warnings = mutableListOf<String>()
        uploadAvatars(repo, client, config, warnings)
        uploadProofs(repo, client, warnings)

        val afterUploads = repo.snapshot()
        pushJson(client, PATH_PROFILES, profilesJson(afterUploads.profiles), "Sync avatars", cache)
        pushJson(client, PATH_CHECKINS, checkInsJson(afterUploads.checkIns), "Sync proof paths", cache)

        val standings = StandingsBuilder.build(afterUploads, config)
        val manifest = StandingsBuilder.manifest(afterUploads, config)
        pushJson(client, PATH_STANDINGS, standings, "Sync standings", cache)
        pushJson(client, PATH_MANIFEST, manifest, "Sync confirmations manifest", cache)

        val realChallenges = afterUploads.challenges.count { !it.isDemo }
        val realCheckIns = afterUploads.checkIns.count { !it.isDemo }
        val uploaded = countRemoteProofs(afterUploads) + afterUploads.profiles.count {
            !it.isDemo && !it.avatarRepoPath.isNullOrBlank()
        }
        return SyncSummary(
            challenges = realChallenges,
            checkIns = realCheckIns,
            uploadedMedia = uploaded,
            warnings = warnings
        )
    }

    private suspend fun readFile(
        client: GitHubClient,
        path: String,
        cache: MutableMap<String, RemoteFile?>
    ): RemoteFile? {
        val file = client.getFile(path)
        cache[path] = file
        return file
    }

    private suspend fun pushJson(
        client: GitHubClient,
        path: String,
        json: JSONObject,
        message: String,
        cache: MutableMap<String, RemoteFile?>
    ) {
        val text = json.toString()
        val old = cache[path]
        if (old?.text == text) return
        val sha = old?.sha
        val newSha = client.putText(path, text, message, sha)
        cache[path] = RemoteFile(text, newSha)
    }

    private suspend fun uploadAvatars(
        repo: AppRepository,
        client: GitHubClient,
        config: GitHubConfig,
        warnings: MutableList<String>
    ) {
        val state = repo.snapshot()
        val updates = mutableMapOf<String, String>()
        for (profile in state.profiles) {
            if (profile.isDemo || !profile.avatarRepoPath.isNullOrBlank()) continue
            val file = repo.fileFor(profile.avatarLocalPath) ?: continue
            if (file.length() > 8 * 1024 * 1024) {
                warnings += "Аватар ${profile.name} больше 8 МБ"
                continue
            }
            val ext = file.extension.ifBlank { "jpg" }
            val path = "avatars/${sanitize(profile.code)}_${System.currentTimeMillis()}.$ext"
            runCatching {
                client.putFile(path, file.readBytes(), "Add avatar for ${profile.code}")
                updates[profile.code] = path
            }.onFailure {
                warnings += "Аватар ${profile.name}: ${it.message}"
            }
        }
        if (updates.isNotEmpty()) {
            repo.update { current ->
                current.copy(profiles = current.profiles.map { profile ->
                    val path = updates[profile.code] ?: return@map profile
                    profile.copy(avatarRepoPath = path, updatedAt = isoNow())
                })
            }
        }
    }

    private suspend fun uploadProofs(
        repo: AppRepository,
        client: GitHubClient,
        warnings: MutableList<String>
    ) {
        val state = repo.snapshot()
        val challengeNames = state.challenges.associate { it.id to it.title }
        val updates = mutableMapOf<String, MutableMap<String, Attachment>>()

        for (checkIn in state.checkIns) {
            if (checkIn.isDemo) continue
            for (result in checkIn.results) {
                for (attachment in result.attachments) {
                    if (!attachment.remotePath.isNullOrBlank()) continue
                    val local = repo.fileFor(attachment.localPath)
                    if (local == null) {
                        warnings += "Файл ${attachment.fileName} не найден на устройстве"
                        continue
                    }
                    if (local.length() > 35L * 1024 * 1024) {
                        warnings += "${attachment.fileName} больше 35 МБ — GitHub не примет файл"
                        continue
                    }
                    val safeName = sanitize(attachment.fileName.ifBlank { "proof_${System.currentTimeMillis()}.jpg" })
                    val basePath = "confirmations/${sanitize(checkIn.userCode)}/${sanitize(checkIn.challengeId)}/$safeName"
                    val path = try {
                        client.putFile(
                            basePath,
                            local.readBytes(),
                            "Add proof for ${challengeNames[checkIn.challengeId] ?: checkIn.challengeId}"
                        )
                        basePath
                    } catch (error: Exception) {
                        // Если файл уже существует без известного sha — пробуем уникальное имя.
                        val alternative = basePath.substringBeforeLast('.') + "_" +
                            UUID.randomUUID().toString().take(8) + "." + basePath.substringAfterLast('.', "jpg")
                        runCatching {
                            client.putFile(
                                alternative,
                                local.readBytes(),
                                "Add proof for ${challengeNames[checkIn.challengeId] ?: checkIn.challengeId}"
                            )
                            alternative
                        }.getOrElse {
                            warnings += "${attachment.fileName}: ${error.message}"
                            null
                        }
                    } ?: continue
                    val uploaded = attachment.copy(remotePath = path, uploadedAt = isoNow())
                    updates.getOrPut(checkIn.id) { mutableMapOf() }[attachment.id] = uploaded
                }
            }
        }

        if (updates.isNotEmpty()) {
            repo.update { current ->
                current.copy(checkIns = current.checkIns.map { checkIn ->
                    val byId = updates[checkIn.id] ?: return@map checkIn
                    checkIn.copy(
                        results = checkIn.results.map { result ->
                            result.copy(attachments = result.attachments.map { attachment ->
                                byId[attachment.id] ?: attachment
                            })
                        },
                        updatedAt = isoNow()
                    )
                })
            }
        }
    }

    private fun countRemoteProofs(data: AppData): Int = data.checkIns
        .filterNot { it.isDemo }
        .sumOf { checkIn ->
            checkIn.results.sumOf { result -> result.attachments.count { !it.remotePath.isNullOrBlank() } }
        }

    private fun parseActivities(text: String?): List<ActivityType> =
        if (text.isNullOrBlank()) emptyList() else runCatching {
            JSONObject(text).optJSONArray("activities").toList { ActivityType.fromJson(it) }
        }.getOrDefault(emptyList())

    private fun parseChallenges(text: String?): List<Challenge> =
        if (text.isNullOrBlank()) emptyList() else runCatching {
            JSONObject(text).optJSONArray("challenges").toList { Challenge.fromJson(it) }
        }.getOrDefault(emptyList())

    private fun parseProfiles(text: String?): List<Profile> =
        if (text.isNullOrBlank()) emptyList() else runCatching {
            JSONObject(text).optJSONArray("profiles").toList { Profile.fromJson(it) }
        }.getOrDefault(emptyList())

    private fun parseCheckIns(text: String?): List<CheckIn> =
        if (text.isNullOrBlank()) emptyList() else runCatching {
            JSONObject(text).optJSONArray("checkIns").toList { CheckIn.fromJson(it) }
        }.getOrDefault(emptyList())

    private fun catalogJson(activities: List<ActivityType>): JSONObject = JSONObject()
        .put("version", 1)
        .put("activities", JSONArray(activities.map { it.toJson() }))

    private fun challengesJson(challenges: List<Challenge>): JSONObject = JSONObject()
        .put("version", 1)
        .put("challenges", JSONArray(challenges.filterNot { it.isDemo }.map { it.toSyncJson() }))

    private fun profilesJson(profiles: List<Profile>): JSONObject = JSONObject()
        .put("version", 1)
        .put("profiles", JSONArray(profiles.filterNot { it.isDemo }.map { it.toSyncJson() }))

    private fun checkInsJson(checkIns: List<CheckIn>): JSONObject = JSONObject()
        .put("version", 1)
        .put("checkIns", JSONArray(checkIns.filterNot { it.isDemo }.map { it.toSyncJson() }))

    private fun mergeActivities(
        local: List<ActivityType>,
        remote: List<ActivityType>
    ): List<ActivityType> {
        val defaults = defaultActivities()
        val result = LinkedHashMap<String, ActivityType>()
        defaults.forEach { result[it.id] = it }
        val remoteById = remote.associateBy { it.id }
        for (activity in local) {
            if (activity.isDefault || result.containsKey(activity.id)) continue
            val remoteVersion = remoteById[activity.id]
            result[activity.id] = when {
                remoteVersion == null -> activity
                remoteVersion.updatedAt > activity.updatedAt -> remoteVersion
                else -> activity
            }
        }
        for (activity in remote) {
            if (activity.isDefault || result.containsKey(activity.id)) continue
            result[activity.id] = activity
        }
        return result.values.toList()
    }

    private fun mergeChallenges(local: List<Challenge>, remote: List<Challenge>): List<Challenge> {
        val demos = local.filter { it.isDemo }
        val map = local.filterNot { it.isDemo }.associateBy { it.id }.toMutableMap()
        for (remoteChallenge in remote) {
            val localChallenge = map[remoteChallenge.id]
            map[remoteChallenge.id] = if (localChallenge == null) {
                remoteChallenge.copy(isDemo = false)
            } else {
                val base = if (remoteChallenge.updatedAt > localChallenge.updatedAt) remoteChallenge else localChallenge
                base.copy(
                    participantCodes = (localChallenge.participantCodes + remoteChallenge.participantCodes).distinct(),
                    isDemo = false
                )
            }
        }
        return demos + map.values
    }

    private fun mergeProfiles(local: List<Profile>, remote: List<Profile>): List<Profile> {
        val demos = local.filter { it.isDemo }.toMutableList()
        val map = local.filterNot { it.isDemo }.associateBy { it.code }.toMutableMap()
        for (remoteProfile in remote) {
            val localProfile = map[remoteProfile.code]
            val merged = if (localProfile == null) {
                remoteProfile.copy(isDemo = false)
            } else {
                val base = if (remoteProfile.updatedAt > localProfile.updatedAt) {
                    remoteProfile.copy(avatarLocalPath = localProfile.avatarLocalPath)
                } else {
                    localProfile
                }
                val medals = (localProfile.medals + remoteProfile.medals).distinctBy { it.key }
                base.copy(medals = medals, isDemo = false)
            }
            map[remoteProfile.code] = merged
        }
        return demos + map.values
    }

    private fun mergeCheckIns(local: List<CheckIn>, remote: List<CheckIn>): List<CheckIn> {
        val demos = local.filter { it.isDemo }
        val map = LinkedHashMap<String, CheckIn>()
        local.filterNot { it.isDemo }.forEach { map[key(it)] = it }
        for (remoteCheckIn in remote) {
            val localCheckIn = map[key(remoteCheckIn)]
            map[key(remoteCheckIn)] = if (localCheckIn == null) {
                remoteCheckIn.copy(isDemo = false)
            } else {
                val base = if (remoteCheckIn.updatedAt > localCheckIn.updatedAt) remoteCheckIn else localCheckIn
                base.copy(
                    results = mergeResults(localCheckIn.results, remoteCheckIn.results, remoteCheckIn.updatedAt > localCheckIn.updatedAt),
                    isDemo = false
                )
            }
        }
        return demos + map.values
    }

    private fun key(checkIn: CheckIn): String =
        "${checkIn.challengeId}|${checkIn.userCode}|${checkIn.date}"

    private fun mergeResults(
        local: List<GoalResult>,
        remote: List<GoalResult>,
        remoteIsNewer: Boolean
    ): List<GoalResult> {
        val localByGoal = local.associateBy { it.goalId }
        val remoteByGoal = remote.associateBy { it.goalId }
        val goalIds = (localByGoal.keys + remoteByGoal.keys).toList()
        return goalIds.map { goalId ->
            val l = localByGoal[goalId]
            val r = remoteByGoal[goalId]
            when {
                l == null && r != null -> r
                l != null && r == null -> l
                l != null && r != null -> {
                    val base = if (remoteIsNewer) r else l
                    val attachments = mergeAttachments(l.attachments, r.attachments)
                    base.copy(actual = if (remoteIsNewer) r.actual else l.actual, attachments = attachments)
                }
                else -> error("unreachable")
            }
        }
    }

    private fun mergeAttachments(local: List<Attachment>, remote: List<Attachment>): List<Attachment> {
        val map = LinkedHashMap<String, Attachment>()
        local.forEach { map[it.id] = it }
        remote.forEach { remoteAttachment ->
            val localAttachment = map[remoteAttachment.id]
            map[remoteAttachment.id] = if (localAttachment == null) {
                remoteAttachment.copy(localPath = null)
            } else {
                localAttachment.copy(
                    remotePath = localAttachment.remotePath ?: remoteAttachment.remotePath,
                    uploadedAt = localAttachment.uploadedAt ?: remoteAttachment.uploadedAt,
                    fileName = localAttachment.fileName.ifBlank { remoteAttachment.fileName },
                    mime = localAttachment.mime.ifBlank { remoteAttachment.mime },
                    sizeBytes = if (localAttachment.sizeBytes > 0) localAttachment.sizeBytes else remoteAttachment.sizeBytes
                )
            }
        }
        return map.values.toList()
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "item" }

    companion object {
        const val PATH_CATALOG = "data/catalog.json"
        const val PATH_CHALLENGES = "data/challenges.json"
        const val PATH_PROFILES = "data/profiles.json"
        const val PATH_CHECKINS = "data/checkins.json"
        const val PATH_STANDINGS = "data/standings.json"
        const val PATH_MANIFEST = "confirmations/manifest.json"
    }
}
