package com.onlinechallenges.app.data

/**
 * Сливает общий каталог активностей с учётом времени изменения.
 *
 * Иконка берётся из более новой версии активности. Если в новой версии
 * `iconPath` пуст, значит иконку сбросили к стандартной: старое фото не
 * возвращается. Это важно для синхронизации сброса между устройствами.
 */
internal fun mergeActivities(
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
            iconPath = chosen?.iconPath,
            iconLocalPath = if (chosen != null && chosen === localVersion) {
                chosen.iconLocalPath
            } else {
                null
            },
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
