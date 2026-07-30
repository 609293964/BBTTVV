package com.bbttvv.app.feature.live

import androidx.media3.common.Player

internal enum class LivePlaybackToggleAction {
    Play,
    Pause,
}

internal fun resolveLivePlaybackToggleAction(playWhenReady: Boolean): LivePlaybackToggleAction {
    return if (playWhenReady) {
        LivePlaybackToggleAction.Pause
    } else {
        LivePlaybackToggleAction.Play
    }
}

internal fun shouldRequestLivePlaybackResumeAfterBackground(
    isPausedByUser: Boolean,
    playWhenReady: Boolean,
    isLoading: Boolean,
    playerState: Int,
    hasError: Boolean,
): Boolean {
    return !isPausedByUser &&
        !hasError &&
        playerState != Player.STATE_ENDED &&
        (playWhenReady || isLoading)
}

internal fun shouldResumeLivePlaybackOnForeground(
    resumeRequested: Boolean,
    isPausedByUser: Boolean,
    isLoading: Boolean,
    hasPlaybackSource: Boolean,
    playerState: Int,
    hasError: Boolean,
): Boolean {
    return resumeRequested &&
        !isPausedByUser &&
        !hasError &&
        playerState != Player.STATE_ENDED &&
        (hasPlaybackSource || isLoading)
}
