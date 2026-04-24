package com.bbttvv.app.ui.components

import com.bbttvv.app.data.model.response.VideoItem

internal fun List<VideoItem>.stableVideoItemKeys(): List<String> {
    val seen = LinkedHashMap<String, Int>(size)
    return mapIndexed { index, video ->
        val baseKey = video.stableVideoIdentityKey() ?: "position:$index"
        val duplicateIndex = seen[baseKey] ?: 0
        seen[baseKey] = duplicateIndex + 1
        if (duplicateIndex == 0) baseKey else "$baseKey#duplicate:$duplicateIndex"
    }
}

private fun VideoItem.stableVideoIdentityKey(): String? {
    dynamicId.trim().takeIf { it.isNotEmpty() }?.let { return "dynamic:$it" }
    bvid.trim().takeIf { it.isNotEmpty() }?.let { return "bvid:$it" }
    if (id > 0L) return "id:$id"
    if (collectionId > 0L) return "collection:$collectionId"
    if (aid > 0L || cid > 0L) return "aid:$aid:cid:$cid"

    val contentKey = buildString {
        title.trim().takeIf { it.isNotEmpty() }?.let { append(it) }
        pic.trim().takeIf { it.isNotEmpty() }?.let {
            if (isNotEmpty()) append(':')
            append(it)
        }
    }
    return contentKey.takeIf { it.isNotEmpty() }?.let { "content:$it" }
}
