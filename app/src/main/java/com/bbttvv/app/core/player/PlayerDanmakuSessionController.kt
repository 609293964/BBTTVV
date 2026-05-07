package com.bbttvv.app.core.player

import com.bbttvv.app.data.repository.DANMAKU_SEGMENT_DURATION_MS

internal data class PlayerDanmakuSessionSnapshot(
    val cid: Long,
    val aid: Long,
    val loadedSegments: Set<Int>,
    val loadingSegments: Set<Int>,
    val failedSegments: Set<Int>,
)

internal class PlayerDanmakuSessionController(
    private val windowRadius: Int = DEFAULT_WINDOW_RADIUS,
) {
    private var currentCid: Long = 0L
    private var currentAid: Long = 0L
    private val loadedSegments = linkedSetOf<Int>()
    private val loadingSegments = linkedSetOf<Int>()
    private val failedSegments = linkedSetOf<Int>()

    fun begin(
        cid: Long,
        aid: Long,
        startPositionMs: Long,
    ): Int {
        clear()
        currentCid = cid
        currentAid = aid
        return segmentIndexFor(startPositionMs)
    }

    fun matches(cid: Long): Boolean {
        return currentCid > 0L && currentCid == cid
    }

    fun markLoaded(segmentIndex: Int) {
        if (segmentIndex <= 0) return
        loadingSegments.remove(segmentIndex)
        failedSegments.remove(segmentIndex)
        loadedSegments.add(segmentIndex)
    }

    fun markFailed(segmentIndex: Int) {
        if (segmentIndex <= 0) return
        loadingSegments.remove(segmentIndex)
        if (!loadedSegments.contains(segmentIndex)) {
            failedSegments.add(segmentIndex)
        }
    }

    fun markLoading(segmentIndex: Int): Boolean {
        if (segmentIndex <= 0) return false
        if (loadedSegments.contains(segmentIndex)) return false
        if (loadingSegments.contains(segmentIndex)) return false
        if (failedSegments.contains(segmentIndex)) return false
        loadingSegments.add(segmentIndex)
        return true
    }

    fun prefetchWindow(positionMs: Long): List<Int> {
        if (currentCid <= 0L) return emptyList()
        val currentSegment = segmentIndexFor(positionMs)
        val start = (currentSegment - windowRadius).coerceAtLeast(1)
        val end = currentSegment + windowRadius
        return (start..end).filter { segmentIndex ->
            !loadedSegments.contains(segmentIndex) &&
                !loadingSegments.contains(segmentIndex) &&
                !failedSegments.contains(segmentIndex)
        }
    }

    fun clear() {
        currentCid = 0L
        currentAid = 0L
        loadedSegments.clear()
        loadingSegments.clear()
        failedSegments.clear()
    }

    fun snapshot(): PlayerDanmakuSessionSnapshot {
        return PlayerDanmakuSessionSnapshot(
            cid = currentCid,
            aid = currentAid,
            loadedSegments = loadedSegments.toSet(),
            loadingSegments = loadingSegments.toSet(),
            failedSegments = failedSegments.toSet(),
        )
    }

    companion object {
        private const val DEFAULT_WINDOW_RADIUS = 1

        fun segmentIndexFor(positionMs: Long): Int {
            return (positionMs / DANMAKU_SEGMENT_DURATION_MS)
                .coerceAtLeast(0L)
                .toInt() + 1
        }
    }
}
