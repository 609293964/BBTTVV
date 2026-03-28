package com.android.purebilibili.feature.video.ui.pager

import com.android.purebilibili.feature.video.playback.policy.resolveDisplayedPlaybackTransitionPosition
import com.android.purebilibili.feature.video.playback.policy.shouldHoldPlaybackTransitionPosition

private const val PORTRAIT_PENDING_SEEK_TOLERANCE_MS = 500L

internal fun shouldApplyPortraitProgressSync(
    snapshotBvid: String?,
    snapshotCid: Long,
    currentBvid: String?,
    currentCid: Long
): Boolean {
    if (snapshotBvid.isNullOrBlank()) return false
    if (currentBvid.isNullOrBlank()) return false
    if (snapshotBvid != currentBvid) return false
    if (snapshotCid <= 0L || currentCid <= 0L) return true
    return snapshotCid == currentCid
}

internal fun shouldHoldPortraitSeekUiPosition(
    playerPositionMs: Long,
    pendingSeekPositionMs: Long?,
    toleranceMs: Long = PORTRAIT_PENDING_SEEK_TOLERANCE_MS
): Boolean {
    return shouldHoldPlaybackTransitionPosition(
        playerPositionMs = playerPositionMs,
        transitionPositionMs = pendingSeekPositionMs,
        toleranceMs = toleranceMs
    )
}

internal fun resolvePortraitCommittedSeekPosition(
    requestedPositionMs: Long,
    durationMs: Long
): Long {
    val safeRequestedPosition = requestedPositionMs.coerceAtLeast(0L)
    val safeDuration = durationMs.coerceAtLeast(0L)
    return if (safeDuration > 0L) {
        safeRequestedPosition.coerceAtMost(safeDuration)
    } else {
        safeRequestedPosition
    }
}

internal fun resolvePortraitDisplayedProgressPosition(
    playerPositionMs: Long,
    localSeekPositionMs: Long,
    pendingSeekPositionMs: Long?
): Long {
    val resolvedPlayerPosition = resolveDisplayedPlaybackTransitionPosition(
        playerPositionMs = playerPositionMs,
        transitionPositionMs = pendingSeekPositionMs
    )
    return if (resolvedPlayerPosition == playerPositionMs) {
        playerPositionMs
    } else {
        localSeekPositionMs
    }
}
