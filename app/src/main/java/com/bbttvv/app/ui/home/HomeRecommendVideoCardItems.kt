package com.bbttvv.app.ui.home

import com.bbttvv.app.data.model.response.VideoItem

data class HomeRecommendVideoCardItem(
    val key: String,
    val stableId: Long,
    val video: VideoItem,
)

internal fun List<VideoItem>.toHomeRecommendVideoCardItems(): List<HomeRecommendVideoCardItem> {
    return HomeRecommendVideoKeyRegistry().resetAndBuild(this)
}

internal class HomeRecommendVideoKeyRegistry {
    private val nextDuplicateIndexByBaseKey = LinkedHashMap<String, Int>()
    private val issuedKeysByBaseKey = LinkedHashMap<String, MutableList<String>>()

    fun resetAndBuild(videos: List<VideoItem>): List<HomeRecommendVideoCardItem> {
        nextDuplicateIndexByBaseKey.clear()
        issuedKeysByBaseKey.clear()
        return append(videos, startIndex = 0)
    }

    fun append(
        videos: List<VideoItem>,
        startIndex: Int,
    ): List<HomeRecommendVideoCardItem> {
        if (videos.isEmpty()) return emptyList()
        return videos.mapIndexed { index, video ->
            val baseKey = video.homeRecommendVideoBaseKey(startIndex + index)
            createNewItem(baseKey = baseKey, video = video)
        }
    }

    fun rebuildReusingAssigned(videos: List<VideoItem>): List<HomeRecommendVideoCardItem> {
        if (videos.isEmpty()) return emptyList()
        val usedCountsByBaseKey = LinkedHashMap<String, Int>(videos.size)
        return videos.mapIndexed { index, video ->
            val baseKey = video.homeRecommendVideoBaseKey(index)
            val occurrenceIndex = usedCountsByBaseKey[baseKey] ?: 0
            usedCountsByBaseKey[baseKey] = occurrenceIndex + 1
            val issuedKey = issuedKeysByBaseKey[baseKey]?.getOrNull(occurrenceIndex)
            if (issuedKey != null) {
                HomeRecommendVideoCardItem(
                    key = issuedKey,
                    stableId = stableLongHash(issuedKey),
                    video = video,
                )
            } else {
                createNewItem(baseKey = baseKey, video = video)
            }
        }
    }

    private fun createNewItem(
        baseKey: String,
        video: VideoItem,
    ): HomeRecommendVideoCardItem {
        val key = nextKeyFor(baseKey)
        return HomeRecommendVideoCardItem(
            key = key,
            stableId = stableLongHash(key),
            video = video,
        )
    }

    private fun nextKeyFor(baseKey: String): String {
        val duplicateIndex = nextDuplicateIndexByBaseKey[baseKey] ?: 0
        nextDuplicateIndexByBaseKey[baseKey] = duplicateIndex + 1
        val key = if (duplicateIndex == 0) {
            baseKey
        } else {
            "$baseKey#duplicate:$duplicateIndex"
        }
        issuedKeysByBaseKey.getOrPut(baseKey) { mutableListOf() }.add(key)
        return key
    }
}

private fun VideoItem.homeRecommendVideoBaseKey(position: Int): String {
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
        ?: "position:$position"
}

private fun stableLongHash(value: String): Long {
    var hash = 1125899906842597L
    for (char in value) {
        hash = 31 * hash + char.code
    }
    return hash and Long.MAX_VALUE
}
