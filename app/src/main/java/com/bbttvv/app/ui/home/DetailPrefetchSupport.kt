package com.bbttvv.app.ui.home

import com.bbttvv.app.data.model.response.VideoItem

private val DetailPrefetchNeighborOffsets = listOf(1, 2, -1)
private val HomeDetailSummaryNeighborOffsets = listOf(1, -1)

// Keep the current card first, then warm the most likely next navigation targets nearby.
internal fun buildDetailPrefetchCandidates(
    focusedVideo: VideoItem,
    videos: List<VideoItem>,
    index: Int
): List<VideoItem> {
    return buildList {
        add(focusedVideo)
        if (index >= 0) {
            DetailPrefetchNeighborOffsets.forEach { offset ->
                videos.getOrNull(index + offset)?.let(::add)
            }
        }
    }.distinctBy { video ->
        video.bvid.trim().ifBlank { "aid:${video.aid}:${video.cid}" }
    }
}

internal fun buildHomeDetailSummaryCandidates(
    focusedVideo: VideoItem,
    videos: List<VideoItem>,
    index: Int
): List<VideoItem> {
    return buildList {
        add(focusedVideo)
        if (index >= 0) {
            HomeDetailSummaryNeighborOffsets.forEach { offset ->
                videos.getOrNull(index + offset)?.let(::add)
            }
        }
    }.distinctBy { video ->
        video.bvid.trim().ifBlank { "aid:${video.aid}:${video.cid}" }
    }
}

internal object FocusSummaryPrefetchDelayPolicy {
    fun delayMillis(previousFocusAtMs: Long?, currentFocusAtMs: Long): Long {
        val intervalMs = previousFocusAtMs?.let { currentFocusAtMs - it } ?: return 0L
        return when {
            intervalMs < 100L -> 250L
            intervalMs < 250L -> 100L
            else -> 0L
        }
    }
}
