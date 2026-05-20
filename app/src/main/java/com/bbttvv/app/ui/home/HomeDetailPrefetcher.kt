package com.bbttvv.app.ui.home

import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.repository.VideoDetailRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Debounces TV focus-driven detail prefetches for the home grid. */
internal class HomeDetailPrefetcher(
    private val scope: CoroutineScope
) {
    private var detailPrefetchJob: Job? = null
    private var pendingPrefetchBvid: String? = null
    private var lastPrefetchedBvid: String? = null
    private var lastFocusRequestAtMs: Long? = null

    fun prime(video: VideoItem) {
        if (video.bvid == pendingPrefetchBvid) {
            detailPrefetchJob?.cancel()
            pendingPrefetchBvid = null
        }
        lastFocusRequestAtMs = System.currentTimeMillis()
        lastPrefetchedBvid = video.bvid.takeIf { it.isNotBlank() } ?: lastPrefetchedBvid
        VideoDetailRepository.prefetchDetailSummary(video)
    }

    fun prefetch(video: VideoItem) {
        if (video.bvid.isBlank()) return
        if (video.bvid == pendingPrefetchBvid || video.bvid == lastPrefetchedBvid) return
        val now = System.currentTimeMillis()
        val delayMs = FocusSummaryPrefetchDelayPolicy.delayMillis(lastFocusRequestAtMs, now)
        lastFocusRequestAtMs = now
        detailPrefetchJob?.cancel()
        pendingPrefetchBvid = video.bvid
        detailPrefetchJob = scope.launch {
            if (delayMs > 0L) {
                delay(delayMs)
            }
            VideoDetailRepository.prefetchDetailSummary(video)
            lastPrefetchedBvid = video.bvid
            if (pendingPrefetchBvid == video.bvid) {
                pendingPrefetchBvid = null
            }
        }
    }

    fun clear() {
        detailPrefetchJob?.cancel()
        detailPrefetchJob = null
        pendingPrefetchBvid = null
        lastFocusRequestAtMs = null
    }
}
