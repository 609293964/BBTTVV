package com.bbttvv.app.feature.profile

import com.bbttvv.app.data.model.response.FavFolder
import com.bbttvv.app.data.model.response.FavFolderSource

internal fun mergeProfileFavoriteFoldersForDisplay(
    ownedFolders: List<FavFolder>,
    subscribedFolders: List<FavFolder>
): List<FavFolder> {
    val seenIds = HashSet<Long>()
    return (ownedFolders + subscribedFolders).filter { folder ->
        val valid = folder.title.isNotBlank() && (folder.id > 0L || folder.fid > 0L)
        val identity = folder.id.takeIf { it > 0L } ?: folder.fid
        valid && seenIds.add(identity)
    }
}

internal fun resolveProfileFavoriteFolderKey(folder: FavFolder): String {
    return buildString {
        append(folder.source.name)
        append(':')
        append(folder.id)
        append(':')
        append(folder.fid)
        append(':')
        append(folder.mid)
    }
}

internal fun resolveProfileFavoriteFolderLabel(folder: FavFolder): String {
    return if (folder.source == FavFolderSource.SUBSCRIBED) {
        "${folder.title} · 订阅"
    } else {
        folder.title
    }
}

internal fun resolveProfileFavoriteFolderMediaId(folder: FavFolder): Long {
    if (folder.source != FavFolderSource.SUBSCRIBED) {
        return folder.id.takeIf { it > 0L } ?: folder.fid
    }

    val normalizedFromFid = when {
        folder.fid > 0L && folder.mid > 0L -> {
            val suffix = (folder.mid % 100L).toString().padStart(2, '0')
            "${folder.fid}$suffix".toLongOrNull()
        }
        else -> null
    }

    return when {
        folder.id > 0L && folder.id == normalizedFromFid -> folder.id
        folder.id > 0L && folder.id != folder.fid && folder.id > 100_000_000L -> folder.id
        normalizedFromFid != null -> normalizedFromFid
        folder.id > 0L -> folder.id
        else -> folder.fid
    }
}
