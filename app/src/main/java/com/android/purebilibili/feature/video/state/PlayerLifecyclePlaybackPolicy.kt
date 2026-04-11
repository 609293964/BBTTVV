package com.android.purebilibili.feature.video.state

import androidx.media3.common.Player

internal fun isPlaybackActiveForLifecycle(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int
): Boolean {
    return isPlaying || when (playbackState) {
        Player.STATE_READY,
        Player.STATE_BUFFERING -> playWhenReady
        else -> false
    }
}

internal fun shouldResumeAfterLifecyclePause(
    wasPlaybackActive: Boolean,
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int
): Boolean {
    val currentlyActive = isPlaybackActiveForLifecycle(
        isPlaying = isPlaying,
        playWhenReady = playWhenReady,
        playbackState = playbackState
    )
    return wasPlaybackActive && !currentlyActive
}

internal fun shouldRestorePlayerVolumeOnResume(
    shouldResume: Boolean,
    currentVolume: Float,
    shouldEnsureAudible: Boolean = false
): Boolean {
    return currentVolume <= 0f && (shouldResume || shouldEnsureAudible)
}
