package com.android.purebilibili.feature.video.ui.section

import androidx.media3.common.PlaybackParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPlayerSectionPolicyTest {

    @Test
    fun danmakuLayerTopOffset_keepsInlinePortraitDanmakuAnchoredToViewportTop() {
        assertEquals(
            0,
            resolveDanmakuLayerTopOffsetPx(
                isFullscreen = false,
                statusBarHeightPx = 96
            )
        )
        assertEquals(
            0,
            resolveDanmakuLayerTopOffsetPx(
                isFullscreen = true,
                statusBarHeightPx = 96
            )
        )
    }

    @Test
    fun inlinePlayerTakeover_disablesKeepingLastFrame_whenPortraitFullscreenOwnsPlayback() {
        assertFalse(
            shouldKeepInlinePlayerContentOnReset(
                isPortraitFullscreen = true,
                forceCoverDuringReturnAnimation = false
            )
        )
        assertTrue(
            shouldKeepInlinePlayerContentOnReset(
                isPortraitFullscreen = false,
                forceCoverDuringReturnAnimation = false
            )
        )
    }

    @Test
    fun inlinePlayerTakeover_hidesInlinePlayerView_whenPortraitFullscreenOwnsPlayback() {
        assertFalse(
            shouldShowInlinePlayerView(
                isPortraitFullscreen = true,
                forceCoverDuringReturnAnimation = false
            )
        )
        assertTrue(
            shouldShowInlinePlayerView(
                isPortraitFullscreen = false,
                forceCoverDuringReturnAnimation = false
            )
        )
    }

    @Test
    fun forcedReturnTakeover_disablesInlinePlayerRetentionBindingAndVisibility() {
        assertFalse(
            shouldKeepInlinePlayerContentOnReset(
                isPortraitFullscreen = false,
                forceCoverDuringReturnAnimation = true
            )
        )
        assertFalse(
            shouldShowInlinePlayerView(
                isPortraitFullscreen = false,
                forceCoverDuringReturnAnimation = true
            )
        )
        assertFalse(
            shouldBindInlinePlayerViewToPlayer(
                isPortraitFullscreen = false,
                hostLifecycleStarted = true,
                isInPipMode = false,
                forceCoverDuringReturnAnimation = true
            )
        )
    }

    @Test
    fun danmakuLayers_hidden_whenHostLifecycleStopped() {
        assertFalse(
            shouldShowDanmakuLayers(
                isInPipMode = false,
                danmakuEnabled = true,
                isPortraitFullscreen = false,
                pipNoDanmakuEnabled = false,
                hostLifecycleStarted = false
            )
        )
        assertTrue(
            shouldShowDanmakuLayers(
                isInPipMode = false,
                danmakuEnabled = true,
                isPortraitFullscreen = false,
                pipNoDanmakuEnabled = false,
                hostLifecycleStarted = true
            )
        )
    }

    @Test
    fun livePlayerSharedElement_enabledOnlyWhenAllGuardsPass() {
        assertTrue(
            shouldEnableLivePlayerSharedElement(
                transitionEnabled = true,
                allowLivePlayerSharedElement = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
    }

    @Test
    fun livePlayerSharedElement_disabledWhenPredictiveBackRequiresStability() {
        assertFalse(
            shouldEnableLivePlayerSharedElement(
                transitionEnabled = true,
                allowLivePlayerSharedElement = false,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
    }

    @Test
    fun livePlayerSharedElement_disabledWhenTransitionSwitchOff() {
        assertFalse(
            shouldEnableLivePlayerSharedElement(
                transitionEnabled = false,
                allowLivePlayerSharedElement = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
    }

    @Test
    fun playerSurfaceRebind_onlyWhenForegroundVideoSurfaceCanRender() {
        assertTrue(
            shouldRebindPlayerSurfaceOnForeground(
                hasPlayerView = true,
                isInPipMode = false,
                videoWidth = 1920,
                videoHeight = 1080
            )
        )
    }

    @Test
    fun playerSurfaceRebind_skipsOnlyWhenPipOrPlayerViewMissing() {
        assertFalse(
            shouldRebindPlayerSurfaceOnForeground(
                hasPlayerView = true,
                isInPipMode = true,
                videoWidth = 1920,
                videoHeight = 1080
            )
        )
        assertTrue(
            shouldRebindPlayerSurfaceOnForeground(
                hasPlayerView = true,
                isInPipMode = false,
                videoWidth = 0,
                videoHeight = 1080
            )
        )
        assertFalse(
            shouldRebindPlayerSurfaceOnForeground(
                hasPlayerView = false,
                isInPipMode = false,
                videoWidth = 1920,
                videoHeight = 1080
            )
        )
    }

    @Test
    fun foregroundSurfaceRecovery_runsOnlyForInlineForegroundPlayerView() {
        assertTrue(
            shouldStartForegroundSurfaceRecovery(
                hasPlayerView = true,
                shouldBindInlinePlayerView = true,
                isInPipMode = false
            )
        )
        assertFalse(
            shouldStartForegroundSurfaceRecovery(
                hasPlayerView = false,
                shouldBindInlinePlayerView = true,
                isInPipMode = false
            )
        )
        assertFalse(
            shouldStartForegroundSurfaceRecovery(
                hasPlayerView = true,
                shouldBindInlinePlayerView = false,
                isInPipMode = false
            )
        )
        assertFalse(
            shouldStartForegroundSurfaceRecovery(
                hasPlayerView = true,
                shouldBindInlinePlayerView = true,
                isInPipMode = true
            )
        )
    }

    @Test
    fun foregroundSurfaceRecovery_kicksPlaybackOnlyWhenRenderChainLooksStuck() {
        assertTrue(
            shouldKickPlaybackAfterSurfaceRecovery(
                playWhenReady = true,
                isPlaying = false,
                playbackState = androidx.media3.common.Player.STATE_READY
            )
        )
        assertTrue(
            shouldKickPlaybackAfterSurfaceRecovery(
                playWhenReady = true,
                isPlaying = false,
                playbackState = androidx.media3.common.Player.STATE_BUFFERING
            )
        )
        assertFalse(
            shouldKickPlaybackAfterSurfaceRecovery(
                playWhenReady = false,
                isPlaying = false,
                playbackState = androidx.media3.common.Player.STATE_READY
            )
        )
        assertFalse(
            shouldKickPlaybackAfterSurfaceRecovery(
                playWhenReady = true,
                isPlaying = true,
                playbackState = androidx.media3.common.Player.STATE_READY
            )
        )
    }

    @Test
    fun foregroundRecoveryWatchdog_logsOnlyWhenNoFrameReturns() {
        assertTrue(
            shouldLogForegroundSurfaceRecoveryTimeout(
                hasRenderedFirstFrameSinceRecovery = false,
                playWhenReady = true,
                playbackState = androidx.media3.common.Player.STATE_READY
            )
        )
        assertTrue(
            shouldLogForegroundSurfaceRecoveryTimeout(
                hasRenderedFirstFrameSinceRecovery = false,
                playWhenReady = true,
                playbackState = androidx.media3.common.Player.STATE_BUFFERING
            )
        )
        assertFalse(
            shouldLogForegroundSurfaceRecoveryTimeout(
                hasRenderedFirstFrameSinceRecovery = true,
                playWhenReady = true,
                playbackState = androidx.media3.common.Player.STATE_READY
            )
        )
        assertFalse(
            shouldLogForegroundSurfaceRecoveryTimeout(
                hasRenderedFirstFrameSinceRecovery = false,
                playWhenReady = false,
                playbackState = androidx.media3.common.Player.STATE_READY
            )
        )
    }

    @Test
    fun playbackStallLogging_requiresMeaningfulDelayDuringActivePlayback() {
        assertTrue(
            shouldLogPlaybackStall(
                bufferingDurationMs = 900L,
                playWhenReady = true,
                currentPositionMs = 12_000L
            )
        )
        assertFalse(
            shouldLogPlaybackStall(
                bufferingDurationMs = 300L,
                playWhenReady = true,
                currentPositionMs = 12_000L
            )
        )
        assertFalse(
            shouldLogPlaybackStall(
                bufferingDurationMs = 900L,
                playWhenReady = false,
                currentPositionMs = 12_000L
            )
        )
        assertFalse(
            shouldLogPlaybackStall(
                bufferingDurationMs = 900L,
                playWhenReady = true,
                currentPositionMs = 0L
            )
        )
    }

    @Test
    fun inlinePlayerBinding_keepsSurfaceAttached_onlyWhenForegroundOrPip() {
        assertTrue(
            shouldBindInlinePlayerViewToPlayer(
                isPortraitFullscreen = false,
                hostLifecycleStarted = true,
                isInPipMode = false,
                forceCoverDuringReturnAnimation = false
            )
        )
        assertTrue(
            shouldBindInlinePlayerViewToPlayer(
                isPortraitFullscreen = false,
                hostLifecycleStarted = false,
                isInPipMode = true,
                forceCoverDuringReturnAnimation = false
            )
        )
    }

    @Test
    fun inlinePlayerBinding_detachesSurface_whenBackgroundedOrPortraitFullscreenOwnsPlayback() {
        assertFalse(
            shouldBindInlinePlayerViewToPlayer(
                isPortraitFullscreen = false,
                hostLifecycleStarted = false,
                isInPipMode = false,
                forceCoverDuringReturnAnimation = false
            )
        )
        assertFalse(
            shouldBindInlinePlayerViewToPlayer(
                isPortraitFullscreen = true,
                hostLifecycleStarted = true,
                isInPipMode = false,
                forceCoverDuringReturnAnimation = false
            )
        )
    }

    @Test
    fun danmakuReload_runsOnlyWhenForegroundHostCanActuallyLoad() {
        assertTrue(
            shouldLoadDanmakuForForegroundHost(
                hostLifecycleStarted = true,
                shouldLoadImmediately = true
            )
        )
        assertFalse(
            shouldLoadDanmakuForForegroundHost(
                hostLifecycleStarted = false,
                shouldLoadImmediately = true
            )
        )
        assertFalse(
            shouldLoadDanmakuForForegroundHost(
                hostLifecycleStarted = true,
                shouldLoadImmediately = false
            )
        )
    }

    @Test
    fun longPressSpeed_clampsHiResToCompatibilityLimit() {
        val effective = resolveEffectiveLongPressSpeed(
            requestedSpeed = 3.0f,
            currentAudioQuality = 30251
        )

        assertTrue(effective == 1.5f)
    }

    @Test
    fun longPressSpeed_keepsStandardAudioAtRequestedHighSpeed() {
        val effective = resolveEffectiveLongPressSpeed(
            requestedSpeed = 3.0f,
            currentAudioQuality = 30280
        )

        assertTrue(effective == 3.0f)
    }

    @Test
    fun longPressSpeed_keepsRequestedSpeedWithinCompatibilityLimit() {
        val effective = resolveEffectiveLongPressSpeed(
            requestedSpeed = 1.25f,
            currentAudioQuality = 30280
        )

        assertTrue(effective == 1.25f)
    }

    @Test
    fun longPressPlaybackParameters_forceNaturalPitch() {
        val parameters = resolveLongPressPlaybackParameters(
            requestedSpeed = 1.25f,
            currentAudioQuality = 30280
        )

        assertEquals(1.25f, parameters.speed)
        assertEquals(1.0f, parameters.pitch)
    }

    @Test
    fun longPressPlaybackParameters_clampHiResSpeedBeforeApplyingPitchSafePlayback() {
        val parameters = resolveLongPressPlaybackParameters(
            requestedSpeed = 3.0f,
            currentAudioQuality = 30251
        )

        assertEquals(PlaybackParameters(1.5f, 1.0f), parameters)
    }

    @Test
    fun longPressPlaybackParameters_keepStandardAudioRequestedHighSpeed() {
        val parameters = resolveLongPressPlaybackParameters(
            requestedSpeed = 3.0f,
            currentAudioQuality = 30280
        )

        assertEquals(PlaybackParameters(3.0f, 1.0f), parameters)
    }

    @Test
    fun longPressStart_capturesCurrentPlaybackParameters_whenNoLockIsActive() {
        val decision = resolveLongPressSpeedStartDecision(
            currentPlaybackParameters = PlaybackParameters(1.25f, 1.0f),
            previousOriginalPlaybackParameters = PlaybackParameters.DEFAULT,
            longPressSpeedLocked = false,
            requestedSpeed = 2.0f,
            currentAudioQuality = 30280
        )

        assertEquals(PlaybackParameters(1.25f, 1.0f), decision.originalPlaybackParameters)
        assertEquals(PlaybackParameters(2.0f, 1.0f), decision.targetPlaybackParameters)
        assertFalse(decision.clearExistingLock)
    }

    @Test
    fun longPressStart_preservesPreLockOriginalPlaybackParameters_whenLockAlreadyActive() {
        val decision = resolveLongPressSpeedStartDecision(
            currentPlaybackParameters = PlaybackParameters(2.0f, 1.0f),
            previousOriginalPlaybackParameters = PlaybackParameters(1.0f, 1.0f),
            longPressSpeedLocked = true,
            requestedSpeed = 2.0f,
            currentAudioQuality = 30280
        )

        assertEquals(PlaybackParameters(1.0f, 1.0f), decision.originalPlaybackParameters)
        assertEquals(PlaybackParameters(2.0f, 1.0f), decision.targetPlaybackParameters)
        assertTrue(decision.clearExistingLock)
    }

    @Test
    fun hiResLongPressCompatHint_showsOnlyWhenClampHappensFirstTime() {
        assertTrue(
            shouldShowHiResLongPressCompatHint(
                requestedSpeed = 3.0f,
                effectiveSpeed = 1.5f,
                hasShownHint = false
            )
        )
    }

    @Test
    fun hiResLongPressCompatHint_staysSilentAfterFirstReminderOrWithoutClamp() {
        assertFalse(
            shouldShowHiResLongPressCompatHint(
                requestedSpeed = 3.0f,
                effectiveSpeed = 1.5f,
                hasShownHint = true
            )
        )
        assertFalse(
            shouldShowHiResLongPressCompatHint(
                requestedSpeed = 1.5f,
                effectiveSpeed = 1.5f,
                hasShownHint = false
            )
        )
        assertFalse(
            shouldShowHiResLongPressCompatHint(
                requestedSpeed = 3.0f,
                effectiveSpeed = 3.0f,
                hasShownHint = false
            )
        )
        assertFalse(
            shouldShowHiResLongPressCompatHint(
                requestedSpeed = 1.5004f,
                effectiveSpeed = 1.5f,
                hasShownHint = false
            )
        )
    }

    @Test
    fun playbackReadyAutoFullscreen_enabledForPhonesInOrientationDrivenMode() {
        assertTrue(
            shouldAllowPlaybackStateAutoFullscreen(
                smallestScreenWidthDp = 411
            )
        )
    }

    @Test
    fun playbackReadyAutoFullscreen_disabledForTabletsEvenWhenUsingCompactLayout() {
        assertFalse(
            shouldAllowPlaybackStateAutoFullscreen(
                smallestScreenWidthDp = 600
            )
        )
    }

    @Test
    fun autoplayChromeAutoHide_triggersOnceAfterFirstFrameWhilePlaying() {
        assertTrue(
            shouldAutoHidePlayerChromeOnPlaybackStart(
                showControls = true,
                hasAutoHiddenForCurrentVideo = false,
                isPlaying = true,
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false
            )
        )
    }

    @Test
    fun autoplayChromeAutoHide_staysOffForPausedForcedOrAlreadyHandledStates() {
        assertFalse(
            shouldAutoHidePlayerChromeOnPlaybackStart(
                showControls = false,
                hasAutoHiddenForCurrentVideo = false,
                isPlaying = true,
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false
            )
        )
        assertFalse(
            shouldAutoHidePlayerChromeOnPlaybackStart(
                showControls = true,
                hasAutoHiddenForCurrentVideo = true,
                isPlaying = true,
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false
            )
        )
        assertFalse(
            shouldAutoHidePlayerChromeOnPlaybackStart(
                showControls = true,
                hasAutoHiddenForCurrentVideo = false,
                isPlaying = false,
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false
            )
        )
        assertFalse(
            shouldAutoHidePlayerChromeOnPlaybackStart(
                showControls = true,
                hasAutoHiddenForCurrentVideo = false,
                isPlaying = true,
                isFirstFrameRendered = false,
                forceCoverDuringReturnAnimation = false
            )
        )
        assertFalse(
            shouldAutoHidePlayerChromeOnPlaybackStart(
                showControls = true,
                hasAutoHiddenForCurrentVideo = false,
                isPlaying = true,
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = true
            )
        )
    }

    @Test
    fun longPressSpeedLock_onlyTriggersForUpwardSwipePastThreshold() {
        assertTrue(
            shouldLockLongPressSpeedBySwipe(
                isLongPressing = true,
                alreadyLocked = false,
                totalDragDistanceY = -96f,
                thresholdPx = 80f
            )
        )
        assertFalse(
            shouldLockLongPressSpeedBySwipe(
                isLongPressing = true,
                alreadyLocked = false,
                totalDragDistanceY = -40f,
                thresholdPx = 80f
            )
        )
        assertFalse(
            shouldLockLongPressSpeedBySwipe(
                isLongPressing = true,
                alreadyLocked = true,
                totalDragDistanceY = -120f,
                thresholdPx = 80f
            )
        )
        assertFalse(
            shouldLockLongPressSpeedBySwipe(
                isLongPressing = false,
                alreadyLocked = false,
                totalDragDistanceY = -120f,
                thresholdPx = 80f
            )
        )
    }

    @Test
    fun longPressRelease_restoresOriginalSpeedOnlyWhenNotLocked() {
        assertTrue(
            shouldRestorePlaybackParametersAfterLongPressRelease(
                wasLongPressing = true,
                longPressSpeedLocked = false,
                gestureEnded = true
            )
        )
        assertFalse(
            shouldRestorePlaybackParametersAfterLongPressRelease(
                wasLongPressing = true,
                longPressSpeedLocked = true,
                gestureEnded = true
            )
        )
        assertFalse(
            shouldRestorePlaybackParametersAfterLongPressRelease(
                wasLongPressing = false,
                longPressSpeedLocked = false,
                gestureEnded = true
            )
        )
        assertFalse(
            shouldRestorePlaybackParametersAfterLongPressRelease(
                wasLongPressing = true,
                longPressSpeedLocked = false,
                gestureEnded = false
            )
        )
    }

    @Test
    fun longPressSpeedGesture_disabledWhenScreenLockedOrVideoScaled() {
        assertFalse(
            shouldEnableLongPressSpeedGesture(
                isScreenLocked = true,
                scale = 1f,
                isMultiTouchActive = false
            )
        )
        assertFalse(
            shouldEnableLongPressSpeedGesture(
                isScreenLocked = false,
                scale = 1.2f,
                isMultiTouchActive = false
            )
        )
        assertFalse(
            shouldEnableLongPressSpeedGesture(
                isScreenLocked = false,
                scale = 1f,
                isMultiTouchActive = true
            )
        )
        assertTrue(
            shouldEnableLongPressSpeedGesture(
                isScreenLocked = false,
                scale = 1f,
                isMultiTouchActive = false
            )
        )
    }
}
