package com.android.purebilibili.feature.video.state

import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerLifecyclePlaybackPolicyTest {

    @Test
    fun bufferingWithPlayWhenReadyIsTreatedAsActivePlayback() {
        assertTrue(
            isPlaybackActiveForLifecycle(
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING
            )
        )
    }

    @Test
    fun readyWithPlayWhenReadyIsTreatedAsActivePlayback() {
        assertTrue(
            isPlaybackActiveForLifecycle(
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun pausedReadyStateIsNotActivePlayback() {
        assertFalse(
            isPlaybackActiveForLifecycle(
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun endedWithPlayWhenReadyIsNotActivePlayback() {
        assertFalse(
            isPlaybackActiveForLifecycle(
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_ENDED
            )
        )
    }

    @Test
    fun resumeNeededWhenWasActiveButNowInactive() {
        assertTrue(
            shouldResumeAfterLifecyclePause(
                wasPlaybackActive = true,
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun resumeNotNeededWhenReadyPlaybackIntentStillExists() {
        assertFalse(
            shouldResumeAfterLifecyclePause(
                wasPlaybackActive = true,
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun resumeNotNeededWhenStillBuffering() {
        assertFalse(
            shouldResumeAfterLifecyclePause(
                wasPlaybackActive = true,
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING
            )
        )
    }

    @Test
    fun volumeShouldRestoreWhenLifecycleResumesAndPlayerWasMutedByPauseFlow() {
        assertTrue(
            shouldRestorePlayerVolumeOnResume(
                shouldResume = true,
                currentVolume = 0f
            )
        )
    }

    @Test
    fun volumeShouldNotRestoreWhenLifecycleResumeIsNotNeeded() {
        assertFalse(
            shouldRestorePlayerVolumeOnResume(
                shouldResume = false,
                currentVolume = 0f,
                shouldEnsureAudible = false
            )
        )
    }

    @Test
    fun volumeShouldRestoreWhenPlayerIsMutedButForegroundNeedsAudiblePlayback() {
        assertTrue(
            shouldRestorePlayerVolumeOnResume(
                shouldResume = false,
                currentVolume = 0f,
                shouldEnsureAudible = true
            )
        )
    }
}
