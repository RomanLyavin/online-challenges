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
    private data class RemotePrivate(
        val bundle: PrivateBundle?,
        val sha: String?,
        val decryptedText: String?,
        val decryptionFailed: Boolean = false
    )

    private val mutex = Mutex()

    suspend fun sync(): SyncSummary = mutex.withLock {
        withContext(Dispatchers.IO) {
            val config = prefs.load()
            if (!config.isConfigured) {
                throw GitHubException("Синхронизация не настроена")
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
        val remoteConfirmations = readFile(client, PATH_CONFIRMATIONS, cache)
        val remoteStandings = readFile(client, PATH_STANDINGS, cache)

        val before = repo.snapshot()
        val remoteActivities = parseActivities(remoteCatalog?.text)
        val remoteChallengesList = parseChallenges(remoteChallenges?.text)
        val remoteProfilesList = parseProfiles(remoteProfiles?.text)
        val remoteCheckInsList = parseCheckIns(remoteCheckIns?.text)
        val remoteConfirmationsList = parseConfirmations(remoteConfirmations?.text)

        val demos = before.challenges.filter { it.isDemo }
        val localPublic = before.challenges.filter { !it.isDemo && it.visibility == ChallengeVisibility.PUBLIC }
        val localPrivate = before.challenges
            .filter { !it.isDemo && it.visibility == ChallengeVisibility.PRIVATE }
            .associateBy { it.id }
            .toMutableMap()

        val remotePublic = remoteChallengesList.filter { it.visibility == ChallengeVisibility.PUBLIC }
        val remoteStubs = remoteChallengesList.filter { it.visibility == ChallengeVisibility.PRIVATE }

        val mergedPublic = mergeChallenges(localPublic, remotePublic)
        val publicIds = mergedPublic.map { it.id }.toSet()

        // Публичные заголовки приватных челленджей и локальные расшифрованные данные.
        for (stub in remoteStubs) {
            val existing = localPrivate[stub.id]
            localPrivate[stub.id] = when {
                existing == null -> stub.copy(isDemo = false)
                existing.privateKey.isNullOrBlank() -> existing.copy(
                    title = stub.title.ifBlank { existing.title },
                    updatedAt = maxOf(existing.updatedAt, stub.updatedAt)
                )
                stub.updatedAt > existing.updatedAt -> existing.copy(
                    title = stub.title.ifBlank { existing.title },
                    updatedAt = stub.updatedAt
                )
                else -> existing
            }
        }

        val privateIds = localPrivate.keys.toSet()
        val privateCheckIns = before.checkIns
            .filter { it.challengeId in privateIds }
            .toMutableList()
        val privateConfirmations = before.confirmations
            .filter { it.challengeId in privateIds }
            .toMutableList()
        val remotePrivate = mutableMapOf<String, RemotePrivate>()

        for (challenge in localPrivate.values.toList()) {
            val key = challenge.privateKey
            if (key.isNullOrBlank()) continue
            val remote = loadPrivateBundle(client, challenge.id, key)
            remotePrivate[challenge.id] = remote
            val bundle = remote.bundle ?: continue
            val remoteChallenge = bundle.challenge.copy(
                visibility = ChallengeVisibility.PRIVATE,
                privateKey = key,
                isDemo = false
            )
            localPrivate[challenge.id] = mergePrivateChallenge(challenge, remoteChallenge)

            val mergedCheckIns = mergeCheckIns(
                privateCheckIns.filter { it.challengeId == challenge.id },
                bundle.checkIns
            )
            privateCheckIns.removeAll { it.challengeId == challenge.id }
            privateCheckIns.addAll(mergedCheckIns)

            val mergedConfirmations = mergeConfirmations(
                privateConfirmations.filter { it.challengeId == challenge.id },
                bundle.confirmations
            )
            privateConfirmations.removeAll { it.challengeId == challenge.id }
            privateConfirmations.addAll(mergedConfirmations)
        }

        // Публичные файлы больше не должны содержать отметки и подтверждения
        // приватных челленджей — даже старые версии этих файлов.
        val baseCheckIns = before.checkIns.filter { it.isDemo || it.challengeId !in privateIds }
        val baseConfirmations = before.confirmations.filter { it.isDemo || it.challengeId !in privateIds }
        val allCheckIns = mergeCheckIns(
            baseCheckIns,
            remoteCheckInsList.filter { it.challengeId in publicIds }
        ) + privateCheckIns
        val allConfirmations = mergeConfirmations(
            baseConfirmations,
            remoteConfirmationsList.filter { it.challengeId in publicIds }
        ) + privateConfirmations

        val merged = Awards.apply(
            before.copy(
                activities = mergeActivities(before.activities, remoteActivities),
                challenges = demos + mergedPublic + localPrivate.values,
                checkIns = allCheckIns,
                confirmations = allConfirmations
            )
        )
        repo.replace(merged)

        val privateIdsForProfiles = merged.challenges
            .filter { it.visibility == ChallengeVisibility.PRIVATE }
            .map { it.id }
            .toSet()

        // Сначала публикуем структуру: каталог, челленджи, профили, отметки и подтверждения.
        pushJson(client, PATH_CATALOG, catalogJson(merged.activities), "Sync catalog", cache)
        pushJson(client, PATH_CHALLENGES, challengesJson(merged.challenges), "Sync challenges", cache)
        pushJson(client, PATH_PROFILES, profilesJson(merged.profiles, privateIdsForProfiles), "Sync profiles", cache)
        pushJson(client, PATH_CHECKINS, checkInsJson(merged.checkIns, publicIds), "Sync check-ins", cache)
        pushJson(
            client,
            PATH_CONFIRMATIONS,
            confirmationsJson(merged.confirmations, publicIds),
            "Sync confirmations",
            cache
        )

        val warnings = mutableListOf<String>()
        uploadAvatars(repo, client, config, warnings)
        uploadPublicProofs(repo, client, publicIds, warnings)
        uploadActivityIcons(repo, client, warnings)
        uploadPrivateMedia(repo, client, warnings)

        val afterUploads = repo.snapshot()
        pushJson(client, PATH_CATALOG, catalogJson(afterUploads.activities), "Sync activity icons", cache)
        pushJson(
            client,
            PATH_PROFILES,
            profilesJson(afterUploads.profiles, privateIdsForProfiles),
            "Sync avatars",
            cache
        )
        pushJson(
            client,
            PATH_CHECKINS,
            checkInsJson(afterUploads.checkIns, publicIds),
            "Sync proof paths",
            cache
        )
        pushPrivateBundles(client, afterUploads, remotePrivate, warnings)

        val previousStandings = remoteStandings?.text?.let {
            runCatching { JSONObject(it) }.getOrNull()
        }
        val standings = StandingsBuilder.build(afterUploads, config, previous = previousStandings)
        pushJson(client, PATH_STANDINGS, standings, "Sync standings", cache)

        val realChallenges = afterUploads.challenges.count { !it.isDemo }
        val realCheckIns = afterUploads.checkIns.count { !it.isDemo }
        val uploaded = countRemoteProofs(afterUploads) + afterUploads.profiles.count {
            !it.isDemo && !it.avatarRepoPath.isNullOrBlank()
        } + afterUploads.activities.count { !it.iconPath.isNullOrBlank() }
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

    private suspend fun loadPrivateBundle(
        client: GitHubClient,
        challengeId: String,
        key: String
    ): RemotePrivate {
        val remote = client.getFileBytes(privateBundlePath(challengeId))
            ?: return RemotePrivate(null, null, null)
        val text = CryptoBox.decryptText(remote.bytes, key)
            ?: return RemotePrivate(null, remote.sha, null, decryptionFailed = true)
        val bundle = PrivateBundle.fromJson(text)
            ?: return RemotePrivate(null, remote.sha, text, decryptionFailed = true)
        return RemotePrivate(bundle, remote.sha, text)
    }

    private suspend fun pushPrivateBundles(
        client: GitHubClient,
        state: AppData,
        remote: Map<String, RemotePrivate>,
        warnings: MutableList<String>
    ) {
        for (challenge in state.challenges) {
            if (challenge.isDemo || !challenge.isPrivate) continue
            val key = challenge.privateKey ?: continue
            val known = remote[challenge.id]
            if (known?.decryptionFailed == true) {
                warnings += "Приватный челлендж «${challenge.title}»: не удалось расшифровать облачную копию"
                continue
            }
            val bundle = PrivateBundle(
                challenge = challenge.copy(isDemo = false),
                checkIns = state.checkIns.filter { it.challengeId == challenge.id },
                confirmations = state.confirmations.filter { it.challengeId == challenge.id }
            )
            val text = bundle.toJson().toString()
            if (known?.decryptedText == text) continue
            val encrypted = CryptoBox.encryptText(text, key)
            runCatching {
                client.putFile(
                    privateBundlePath(challenge.id),
                    encrypted,
                    "Sync private challenge",
                    known?.sha
                )
            }.onFailure {
                warnings += "Приватный челлендж «${challenge.title}»: ${it.message}"
            }
        }
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

    private suspend fun uploadActivityIcons(
        repo: AppRepository,
        client: GitHubClient,
        warnings: MutableList<String>
    ) {
        val state = repo.snapshot()
        val updates = mutableMapOf<String, String>()
        for (activity in state.activities) {
            if (!activity.iconPath.isNullOrBlank()) continue
            val file = repo.fileFor(activity.iconLocalPath) ?: continue
            if (file.length() > 4 * 1024 * 1024) {
                warnings += "Иконка «${activity.name}» больше 4 МБ"
                continue
            }
            val ext = file.extension.ifBlank { "jpg" }
            val path = "catalog/icons/${sanitize(activity.id)}_${System.currentTimeMillis()}.$ext"
            runCatching {
                client.putFile(path, file.readBytes(), "Update activity icon: ${activity.name}")
                updates[activity.id] = path
            }.onFailure {
                warnings += "Иконка «${activity.name}»: ${it.message}"
            }
        }
        if (updates.isNotEmpty()) {
            repo.update { current ->
                current.copy(activities = current.activities.map { activity ->
                    val path = updates[activity.id] ?: return@map activity
                    activity.copy(iconPath = path, updatedAt = isoNow())
                })
            }
        }
    }

    private suspend fun uploadPublicProofs(
        repo: AppRepository,
        client: GitHubClient,
        publicChallengeIds: Set<String>,
        warnings: MutableList<String>
    ) {
        val state = repo.snapshot()
        val challengeNames = state.challenges.associate { it.id to it.title }
        val updates = mutableMapOf<String, MutableMap<String, Attachment>>()

        for (checkIn in state.checkIns) {
            if (checkIn.isDemo || checkIn.challengeId !in publicChallengeIds) continue
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

    private suspend fun uploadPrivateMedia(
        repo: AppRepository,
        client: GitHubClient,
        warnings: MutableList<String>
    ) {
        val state = repo.snapshot()
        val privateChallenges = state.challenges
            .filter { !it.isDemo && it.isPrivate && !it.privateKey.isNullOrBlank() }
        val updates = mutableMapOf<String, MutableMap<String, Attachment>>()

        for (challenge in privateChallenges) {
            val key = challenge.privateKey ?: continue
            for (checkIn in state.checkIns.filter { it.challengeId == challenge.id }) {
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
                        val path = privateMediaPath(challenge.id, attachment.id)
                        runCatching {
                            client.putFile(
                                path,
                                CryptoBox.encryptBytes(local.readBytes(), key),
                                "Add private proof"
                            )
                            updates.getOrPut(checkIn.id) { mutableMapOf() }[attachment.id] =
                                attachment.copy(remotePath = path, uploadedAt = isoNow())
                        }.onFailure {
                            warnings += "${attachment.fileName}: ${it.message}"
                        }
                    }
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

    private fun parseConfirmations(text: String?): List<PeerConfirmation> =
        if (text.isNullOrBlank()) emptyList() else runCatching {
            JSONObject(text).optJSONArray("confirmations").toList { PeerConfirmation.fromJson(it) }
        }.getOrDefault(emptyList())

    private fun catalogJson(activities: List<ActivityType>): JSONObject = JSONObject()
        .put("version", 1)
        .put("activities", JSONArray(activities.map { it.toSyncJson() }))

    private fun challengesJson(challenges: List<Challenge>): JSONObject = JSONObject()
        .put("version", 1)
        .put("challenges", JSONArray(challenges.filterNot { it.isDemo }.map { it.toSyncJson() }))

    private fun profilesJson(profiles: List<Profile>, privateChallengeIds: Set<String>): JSONObject = JSONObject()
        .put("version", 1)
        .put("profiles", JSONArray(profiles.filterNot { it.isDemo }.map { profile ->
            val publicMedals = profile.medals.filterNot { it.challengeId in privateChallengeIds }
            if (publicMedals.size == profile.medals.size) {
                profile.toSyncJson()
            } else {
                profile.copy(medals = publicMedals).toSyncJson()
            }
        }))

    private fun checkInsJson(checkIns: List<CheckIn>, publicChallengeIds: Set<String>): JSONObject = JSONObject()
        .put("version", 1)
        .put("checkIns", JSONArray(
            checkIns
                .filterNot { it.isDemo }
                .filter { it.challengeId in publicChallengeIds }
                .map { it.toSyncJson() }
        ))

    private fun confirmationsJson(
        confirmations: List<PeerConfirmation>,
        publicChallengeIds: Set<String>
    ): JSONObject = JSONObject()
        .put("version", 1)
        .put("confirmations", JSONArray(
            confirmations
                .filterNot { it.isDemo }
                .filter { it.challengeId in publicChallengeIds }
                .map { it.toSyncJson() }
        ))

    private fun mergeActivities(
        local: List<ActivityType>,
        remote: List<ActivityType>
    ): List<ActivityType> {
        val defaults = defaultActivities()
        val localById = local.associateBy { it.id }
        val remoteById = remote.associateBy { it.id }
        val result = LinkedHashMap<String, ActivityType>()

        for (base in defaults) {
            val localVersion = localById[base.id]
            val remoteVersion = remoteById[base.id]
            val chosen = when {
                localVersion != null && remoteVersion != null ->
                    if (localVersion.updatedAt >= remoteVersion.updatedAt) localVersion else remoteVersion
                localVersion != null -> localVersion
                else -> remoteVersion
            }
            result[base.id] = base.copy(
                iconPath = chosen?.iconPath ?: localVersion?.iconPath,
                iconLocalPath = if (chosen === localVersion) localVersion?.iconLocalPath else null,
                updatedAt = chosen?.updatedAt ?: base.updatedAt
            )
        }

        val customIds = (localById.keys + remoteById.keys).filterNot { result.containsKey(it) }
        for (id in customIds) {
            val localVersion = localById[id]
            val remoteVersion = remoteById[id]
            result[id] = when {
                remoteVersion == null -> localVersion!!
                localVersion == null -> remoteVersion
                remoteVersion.updatedAt > localVersion.updatedAt -> remoteVersion
                else -> localVersion
            }
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

    private fun mergePrivateChallenge(local: Challenge, remote: Challenge): Challenge {
        val localNewer = local.updatedAt >= remote.updatedAt
        val base = if (localNewer) local else remote
        return base.copy(
            visibility = ChallengeVisibility.PRIVATE,
            privateKey = local.privateKey,
            participantCodes = (local.participantCodes + remote.participantCodes).distinct(),
            isDemo = false,
            updatedAt = maxOf(local.updatedAt, remote.updatedAt)
        )
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

    private fun mergeConfirmations(
        local: List<PeerConfirmation>,
        remote: List<PeerConfirmation>
    ): List<PeerConfirmation> {
        val demos = local.filter { it.isDemo }
        val map = LinkedHashMap<String, PeerConfirmation>()
        local.filterNot { it.isDemo }.forEach { map[it.key] = it }
        for (remoteConfirmation in remote) {
            val localConfirmation = map[remoteConfirmation.key]
            map[remoteConfirmation.key] = if (localConfirmation == null) {
                remoteConfirmation.copy(isDemo = false)
            } else if (remoteConfirmation.updatedAt > localConfirmation.updatedAt) {
                remoteConfirmation.copy(isDemo = false)
            } else {
                localConfirmation
            }
        }
        return demos + map.values
    }

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
        const val PATH_CONFIRMATIONS = "data/confirmations.json"
        const val PATH_STANDINGS = "data/standings.json"

        fun privateBundlePath(challengeId: String): String {
            val safe = challengeId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "challenge" }
            return "data/private/$safe.enc"
        }

        fun privateMediaPath(challengeId: String, attachmentId: String): String {
            val safeChallenge = challengeId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
                .ifBlank { "challenge" }
            val safeAttachment = attachmentId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
                .ifBlank { "attachment" }
            return "data/private/$safeChallenge/media/$safeAttachment.enc"
        }
    }
}
