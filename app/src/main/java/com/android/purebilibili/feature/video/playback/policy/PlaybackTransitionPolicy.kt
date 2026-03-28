package com.android.purebilibili.feature.video.playback.policy

import kotlin.math.abs

private const val PLAYBACK_TRANSITION_POSITION_TOLERANCE_MS = 500L

internal fun shouldHoldPlaybackTransitionPosition(
    playerPositionMs: Long,
    transitionPositionMs: Long?,
    toleranceMs: Long = PLAYBACK_TRANSITION_POSITION_TOLERANCE_MS
): Boolean {
    val targetPositionMs = transitionPositionMs ?: return false
    return abs(playerPositionMs - targetPositionMs) > toleranceMs
}

internal fun resolveDisplayedPlaybackTransitionPosition(
    playerPositionMs: Long,
    transitionPositionMs: Long?
): Long {
    return if (shouldHoldPlaybackTransitionPosition(playerPositionMs, transitionPositionMs)) {
        transitionPositionMs ?: playerPositionMs
    } else {
        playerPositionMs
    }
}

internal fun resolveDisplayedQualityId(
    currentQuality: Int,
    requestedQuality: Int?,
    isQualitySwitching: Boolean
): Int {
    return if (isQualitySwitching) {
        requestedQuality ?: currentQuality
    } else {
        currentQuality
    }
}
