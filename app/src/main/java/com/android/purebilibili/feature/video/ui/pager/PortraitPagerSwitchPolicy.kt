package com.android.purebilibili.feature.video.ui.pager

import androidx.compose.ui.layout.ContentScale
import com.android.purebilibili.data.model.response.Page
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ViewInfo

private const val PORTRAIT_RECOMMENDATION_PREFETCH_THRESHOLD = 1

internal fun resolveCommittedPage(
    isScrollInProgress: Boolean,
    currentPage: Int,
    lastCommittedPage: Int
): Int? {
    if (isScrollInProgress) return null
    if (currentPage == lastCommittedPage) return null
    return currentPage
}

internal fun shouldApplyLoadResult(
    requestGeneration: Int,
    activeGeneration: Int,
    expectedBvid: String,
    currentPlayingBvid: String?
): Boolean {
    if (requestGeneration != activeGeneration) return false
    if (expectedBvid != currentPlayingBvid) return false
    return true
}

internal fun shouldSkipPortraitReloadForCurrentMedia(
    currentPlayingBvid: String?,
    targetBvid: String,
    currentPlayerMediaId: String?
): Boolean {
    val normalizedMediaId = currentPlayerMediaId?.trim().orEmpty()
    if (normalizedMediaId.isBlank()) return false
    return currentPlayingBvid == targetBvid && normalizedMediaId == targetBvid
}

internal fun shouldShowPortraitCover(
    isLoading: Boolean,
    isCurrentPage: Boolean,
    isPlayerReadyForThisVideo: Boolean,
    hasRenderedFirstFrame: Boolean
): Boolean {
    if (isLoading) return true
    if (!isCurrentPage) return true
    if (!isPlayerReadyForThisVideo) return true
    if (!hasRenderedFirstFrame) return true
    return false
}

internal fun shouldUseViewportBoundPortraitCover(
    isCurrentPage: Boolean,
    isPlayerReadyForThisVideo: Boolean,
    hasRenderedFirstFrame: Boolean
): Boolean {
    if (!isCurrentPage) return true
    if (!isPlayerReadyForThisVideo) return true
    if (!hasRenderedFirstFrame) return true
    return false
}

internal fun resolvePortraitCoverContentScale(): ContentScale = ContentScale.Fit

internal fun shouldShowPortraitPauseIcon(
    isCurrentPage: Boolean,
    isPlaying: Boolean,
    playWhenReady: Boolean,
    isLoading: Boolean,
    isSeekGesture: Boolean
): Boolean {
    if (!isCurrentPage) return false
    if (isLoading) return false
    if (isSeekGesture) return false
    if (isPlaying) return false
    if (playWhenReady) return false
    return true
}

internal fun shouldHandlePortraitSeekGesture(scale: Float): Boolean {
    return scale <= 1.01f
}

internal fun shouldHandlePortraitTapGesture(scale: Float): Boolean {
    return scale <= 1.01f
}

internal fun shouldHandlePortraitLongPressGesture(scale: Float): Boolean {
    return scale <= 1.01f
}

internal fun shouldRestorePortraitLongPressSpeed(
    isLongPressing: Boolean,
    isCurrentPage: Boolean
): Boolean {
    return isLongPressing && !isCurrentPage
}

internal fun resolvePortraitInitialProgressPosition(
    isFirstPage: Boolean,
    initialStartPositionMs: Long
): Long {
    if (!isFirstPage) return 0L
    return initialStartPositionMs.coerceAtLeast(0L)
}

internal fun shouldLoadMorePortraitRecommendations(
    committedPage: Int,
    totalItemsCount: Int,
    isLoadingMoreRecommendations: Boolean,
    prefetchThreshold: Int = PORTRAIT_RECOMMENDATION_PREFETCH_THRESHOLD
): Boolean {
    if (isLoadingMoreRecommendations) return false
    if (committedPage < 0 || totalItemsCount <= 0) return false
    val lastTriggerIndex = (totalItemsCount - 1 - prefetchThreshold).coerceAtLeast(0)
    return committedPage >= lastTriggerIndex
}

internal fun mergePortraitRecommendationAppendItems(
    currentBvid: String,
    existingBvids: Set<String>,
    fetchedRecommendations: List<RelatedVideo>
): List<RelatedVideo> {
    return fetchedRecommendations
        .asSequence()
        .filter { candidate ->
            candidate.bvid.isNotBlank() &&
                candidate.bvid != currentBvid &&
                candidate.bvid !in existingBvids
        }
        .distinctBy { it.bvid }
        .toList()
}

internal fun toViewInfoForPortraitDetail(related: RelatedVideo): ViewInfo {
    return ViewInfo(
        bvid = related.bvid,
        aid = related.aid,
        title = related.title,
        desc = "",
        pic = related.pic,
        owner = related.owner,
        stat = related.stat,
        pages = listOf(
            Page(duration = related.duration.toLong())
        )
    )
}
