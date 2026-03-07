package com.android.purebilibili.feature.video.screen

internal fun resolveVideoDetailBeyondViewportPageCount(
    isVideoPlaying: Boolean
): Int = if (isVideoPlaying) 0 else 1

internal fun shouldLoadMoreVideoComments(
    lastVisibleItemIndex: Int,
    totalItemsCount: Int,
    isLoading: Boolean,
    isEnd: Boolean,
    prefetchThreshold: Int = 3
): Boolean {
    if (isLoading || isEnd) return false
    if (totalItemsCount <= 0 || lastVisibleItemIndex < 0) return false
    return lastVisibleItemIndex >= totalItemsCount - 1 - prefetchThreshold
}

internal fun shouldUseLightweightCommentRendering(
    selectedTabIndex: Int,
    isVideoPlaying: Boolean
): Boolean = selectedTabIndex == 1 && isVideoPlaying
