package com.android.purebilibili.feature.video.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackgroundPlaybackPolicyTest {

    @Test
    fun inAppMiniPlayerShownOnlyWhenEligible() {
        assertTrue(
            shouldShowInAppMiniPlayerByPolicy(
                mode = SettingsManager.MiniPlayerMode.IN_APP_ONLY,
                isActive = true,
                isNavigatingToVideo = false,
                stopPlaybackOnExit = false
            )
        )
        assertFalse(
            shouldShowInAppMiniPlayerByPolicy(
                mode = SettingsManager.MiniPlayerMode.IN_APP_ONLY,
                isActive = true,
                isNavigatingToVideo = true,
                stopPlaybackOnExit = false
            )
        )
    }

    @Test
    fun stopOnExitDisablesInAppMiniPlayer() {
        assertFalse(
            shouldShowInAppMiniPlayerByPolicy(
                mode = SettingsManager.MiniPlayerMode.IN_APP_ONLY,
                isActive = true,
                isNavigatingToVideo = false,
                stopPlaybackOnExit = true
            )
        )
    }

    @Test
    fun stopOnExitDisablesSystemPip() {
        assertFalse(
            shouldEnterPipByPolicy(
                mode = SettingsManager.MiniPlayerMode.SYSTEM_PIP,
                isActive = true,
                stopPlaybackOnExit = true
            )
        )
    }

    @Test
    fun systemPipRequiresActivePlayback() {
        assertTrue(
            shouldEnterPipByPolicy(
                mode = SettingsManager.MiniPlayerMode.SYSTEM_PIP,
                isActive = true,
                stopPlaybackOnExit = false
            )
        )
        assertFalse(
            shouldEnterPipByPolicy(
                mode = SettingsManager.MiniPlayerMode.SYSTEM_PIP,
                isActive = false,
                stopPlaybackOnExit = false
            )
        )
    }

    @Test
    fun stopOnExitDisablesBackgroundAudioEvenInDefaultMode() {
        assertFalse(
            shouldContinueBackgroundAudioByPolicy(
                backgroundPlaybackEnabled = true,
                mode = SettingsManager.MiniPlayerMode.OFF,
                isActive = true,
                isLeavingByNavigation = false,
                stopPlaybackOnExit = true
            )
        )
    }

    @Test
    fun defaultModeStillSupportsBackgroundAudioWhenOptionOff() {
        assertTrue(
            shouldContinueBackgroundAudioByPolicy(
                backgroundPlaybackEnabled = true,
                mode = SettingsManager.MiniPlayerMode.OFF,
                isActive = true,
                isLeavingByNavigation = false,
                stopPlaybackOnExit = false
            )
        )
    }

    @Test
    fun defaultModeStopsBackgroundAudioWhenLeavingByNavigation() {
        assertFalse(
            shouldContinueBackgroundAudioByPolicy(
                backgroundPlaybackEnabled = true,
                mode = SettingsManager.MiniPlayerMode.OFF,
                isActive = true,
                isLeavingByNavigation = true,
                stopPlaybackOnExit = false
            )
        )
    }

    @Test
    fun inAppMiniModeCanFallbackToBackgroundAudioWhenAppLeavesForeground() {
        assertTrue(
            shouldContinueBackgroundAudioByPolicy(
                backgroundPlaybackEnabled = true,
                mode = SettingsManager.MiniPlayerMode.IN_APP_ONLY,
                isActive = true,
                isLeavingByNavigation = false,
                stopPlaybackOnExit = false,
                shouldKeepPlaybackForPipTransition = false
            )
        )
    }

    @Test
    fun systemPipCanFallbackToBackgroundAudioWhenNoPipTransitionWillHappen() {
        assertTrue(
            shouldContinueBackgroundAudioByPolicy(
                backgroundPlaybackEnabled = true,
                mode = SettingsManager.MiniPlayerMode.SYSTEM_PIP,
                isActive = true,
                isLeavingByNavigation = false,
                stopPlaybackOnExit = false,
                shouldKeepPlaybackForPipTransition = false
            )
        )
    }

    @Test
    fun systemPipDoesNotEnableBackgroundAudioDuringPipTransition() {
        assertFalse(
            shouldContinueBackgroundAudioByPolicy(
                backgroundPlaybackEnabled = true,
                mode = SettingsManager.MiniPlayerMode.SYSTEM_PIP,
                isActive = true,
                isLeavingByNavigation = false,
                stopPlaybackOnExit = false,
                shouldKeepPlaybackForPipTransition = true
            )
        )
    }

    @Test
    fun backgroundPlaybackToggleDisablesBackgroundAudioEvenInDefaultMode() {
        assertFalse(
            shouldContinueBackgroundAudioByPolicy(
                backgroundPlaybackEnabled = false,
                mode = SettingsManager.MiniPlayerMode.OFF,
                isActive = true,
                isLeavingByNavigation = false,
                stopPlaybackOnExit = false
            )
        )
    }

    @Test
    fun pipTransitionStateStaysTrueWhileRequestOrActualPipExists() {
        assertTrue(
            shouldKeepPlaybackForPipTransition(
                isSystemPipActive = false,
                hasPendingPlaybackRoutePip = true
            )
        )
        assertTrue(
            shouldKeepPlaybackForPipTransition(
                isSystemPipActive = true,
                hasPendingPlaybackRoutePip = false
            )
        )
        assertFalse(
            shouldKeepPlaybackForPipTransition(
                isSystemPipActive = false,
                hasPendingPlaybackRoutePip = false
            )
        )
    }

    @Test
    fun audioFocusPolicyOnlyHandlesFocusWhenEnabled() {
        assertTrue(resolveHandleAudioFocusByPolicy(audioFocusEnabled = true))
        assertFalse(resolveHandleAudioFocusByPolicy(audioFocusEnabled = false))
    }

    @Test
    fun pausePolicyContinuesForMiniModeOrPipRegardlessOfLeaveHint() {
        assertTrue(
            shouldContinuePlaybackDuringPause(
                isMiniMode = true,
                isPip = false,
                isBackgroundAudio = false,
                wasPlaybackActive = false
            )
        )
        assertTrue(
            shouldContinuePlaybackDuringPause(
                isMiniMode = false,
                isPip = true,
                isBackgroundAudio = false,
                wasPlaybackActive = false
            )
        )
    }

    @Test
    fun pausePolicyKeepsBackgroundAudioWhenSettingAllowsItEvenIfSnapshotLooksInactive() {
        assertTrue(
            shouldContinuePlaybackDuringPause(
                isMiniMode = false,
                isPip = false,
                isBackgroundAudio = true,
                wasPlaybackActive = true
            )
        )
        assertTrue(
            shouldContinuePlaybackDuringPause(
                isMiniMode = false,
                isPip = false,
                isBackgroundAudio = true,
                wasPlaybackActive = false
            )
        )
    }

    @Test
    fun backgroundEntryDisablesVideoTrackForPausedOrAudioOnlySessions() {
        assertTrue(
            shouldDisableVideoTrackOnEnterBackground(
                shouldPauseBuffering = true,
                shouldContinueBackgroundAudio = false
            )
        )
        assertTrue(
            shouldDisableVideoTrackOnEnterBackground(
                shouldPauseBuffering = false,
                shouldContinueBackgroundAudio = true
            )
        )
        assertFalse(
            shouldDisableVideoTrackOnEnterBackground(
                shouldPauseBuffering = false,
                shouldContinueBackgroundAudio = false
            )
        )
    }

    @Test
    fun backgroundEntry_doesNotPauseBufferingWhenBackgroundAudioShouldContinue() {
        assertFalse(
            shouldPauseBufferingOnEnterBackground(
                shouldPauseBuffering = true,
                shouldContinueBackgroundAudio = true
            )
        )
        assertTrue(
            shouldPauseBufferingOnEnterBackground(
                shouldPauseBuffering = true,
                shouldContinueBackgroundAudio = false
            )
        )
    }

    @Test
    fun backgroundTrackSelectionDisablesVideoRendererWhenNeeded() {
        val current = TrackSelectionParameters.Builder().build()

        val result = resolveTrackSelectionParametersForBackground(
            currentTrackSelectionParameters = current,
            shouldDisableVideoTrack = true
        )

        assertTrue(result.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO))
        assertEquals(0, result.maxVideoWidth)
        assertEquals(0, result.maxVideoHeight)
    }

    @Test
    fun backgroundTrackSelectionStaysUntouchedWhenVideoCanRemainEnabled() {
        val current = TrackSelectionParameters.Builder().build()

        val result = resolveTrackSelectionParametersForBackground(
            currentTrackSelectionParameters = current,
            shouldDisableVideoTrack = false
        )

        assertEquals(current, result)
    }

    @Test
    fun backgroundEntryClearsVideoSurface_onlyForAudioOnlyBackgroundPlayback() {
        assertTrue(
            shouldClearVideoSurfaceOnEnterBackground(
                shouldDisableVideoTrack = true,
                shouldContinueBackgroundAudio = true
            )
        )
        assertFalse(
            shouldClearVideoSurfaceOnEnterBackground(
                shouldDisableVideoTrack = true,
                shouldContinueBackgroundAudio = false
            )
        )
        assertFalse(
            shouldClearVideoSurfaceOnEnterBackground(
                shouldDisableVideoTrack = false,
                shouldContinueBackgroundAudio = true
            )
        )
    }

    @Test
    fun backgroundEntryTrimsDanmakuCaches_whenVideoPathIsDisabled() {
        assertTrue(
            shouldTrimDanmakuCachesOnEnterBackground(
                shouldDisableVideoTrack = true
            )
        )
        assertFalse(
            shouldTrimDanmakuCachesOnEnterBackground(
                shouldDisableVideoTrack = false
            )
        )
    }

    @Test
    fun foregroundEntryRequestsFrameRefresh_whenVideoTrackWasRestored() {
        assertTrue(
            shouldRefreshVideoFrameOnEnterForeground(
                hadSavedTrackParams = true,
                hasMediaItems = true,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldRefreshVideoFrameOnEnterForeground(
                hadSavedTrackParams = false,
                hasMediaItems = true,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldRefreshVideoFrameOnEnterForeground(
                hadSavedTrackParams = true,
                hasMediaItems = false,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldRefreshVideoFrameOnEnterForeground(
                hadSavedTrackParams = true,
                hasMediaItems = true,
                playbackState = Player.STATE_IDLE
            )
        )
    }

    @Test
    fun foregroundEntryResumesPlaybackOnlyWhenPlaybackIntentWasStillActive() {
        assertTrue(
            shouldResumePlaybackOnEnterForeground(
                playWhenReady = true,
                isPlaying = false,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldResumePlaybackOnEnterForeground(
                playWhenReady = false,
                isPlaying = false,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldResumePlaybackOnEnterForeground(
                playWhenReady = true,
                isPlaying = true,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldResumePlaybackOnEnterForeground(
                playWhenReady = true,
                isPlaying = false,
                playbackState = Player.STATE_BUFFERING
            )
        )
    }

    @Test
    fun backgroundEntryDoesNotPauseReadyPlaybackIntent() {
        assertFalse(
            shouldPauseBackgroundBuffering(
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun foregroundEntryKicksPlaybackWhenVideoTrackReturnsFromBackgroundAudio() {
        assertTrue(
            shouldKickPlaybackAfterForegroundTrackRestore(
                hadSavedTrackParams = true,
                playWhenReady = true,
                playbackState = Player.STATE_READY
            )
        )
        assertTrue(
            shouldKickPlaybackAfterForegroundTrackRestore(
                hadSavedTrackParams = true,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING
            )
        )
        assertFalse(
            shouldKickPlaybackAfterForegroundTrackRestore(
                hadSavedTrackParams = false,
                playWhenReady = true,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldKickPlaybackAfterForegroundTrackRestore(
                hadSavedTrackParams = true,
                playWhenReady = false,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldKickPlaybackAfterForegroundTrackRestore(
                hadSavedTrackParams = true,
                playWhenReady = true,
                playbackState = Player.STATE_IDLE
            )
        )
    }

    @Test
    fun bufferingWithPlayWhenReadyShouldNotBeTreatedAsInactiveInBackground() {
        assertFalse(
            shouldPauseBackgroundBuffering(
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING
            )
        )
    }

    @Test
    fun pausedStateShouldStillBeTreatedAsInactiveInBackground() {
        assertTrue(
            shouldPauseBackgroundBuffering(
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun navigationExitShouldClearPlaybackNotificationForOffAndPip() {
        assertTrue(
            shouldClearPlaybackNotificationOnNavigationExit(
                mode = SettingsManager.MiniPlayerMode.OFF,
                stopPlaybackOnExit = false
            )
        )
        assertTrue(
            shouldClearPlaybackNotificationOnNavigationExit(
                mode = SettingsManager.MiniPlayerMode.SYSTEM_PIP,
                stopPlaybackOnExit = false
            )
        )
        assertFalse(
            shouldClearPlaybackNotificationOnNavigationExit(
                mode = SettingsManager.MiniPlayerMode.IN_APP_ONLY,
                stopPlaybackOnExit = false
            )
        )
    }

    @Test
    fun navigationLeaveShouldBeIgnoredWhenExpectedBvidDiffersFromCurrent() {
        assertFalse(
            shouldHandleNavigationLeaveForBvid(
                expectedBvid = "BV_OLD",
                currentBvid = "BV_NEW"
            )
        )
    }

    @Test
    fun navigationLeaveShouldProceedWhenBvidMatchesOrMissing() {
        assertTrue(
            shouldHandleNavigationLeaveForBvid(
                expectedBvid = "BV_SAME",
                currentBvid = "BV_SAME"
            )
        )
        assertTrue(
            shouldHandleNavigationLeaveForBvid(
                expectedBvid = null,
                currentBvid = "BV_ANY"
            )
        )
        assertTrue(
            shouldHandleNavigationLeaveForBvid(
                expectedBvid = "BV_ANY",
                currentBvid = null
            )
        )
    }

    @Test
    fun notificationIconShouldFollowSelectedAppIconKey() {
        assertEquals(R.mipmap.ic_launcher_telegram_blue, resolveNotificationSmallIconRes("icon_telegram_blue"))
        assertEquals(R.mipmap.ic_launcher_neon, resolveNotificationSmallIconRes("Neon"))
        assertEquals(R.mipmap.ic_launcher_telegram_pink, resolveNotificationSmallIconRes("Pink"))
        assertEquals(R.mipmap.ic_launcher_telegram_dark, resolveNotificationSmallIconRes("Dark"))
        assertEquals(R.mipmap.ic_launcher_flat_material, resolveNotificationSmallIconRes("icon_flat_material"))
        assertEquals(R.mipmap.ic_launcher_flat_material, resolveNotificationSmallIconRes("Flat Material"))
        assertEquals(R.mipmap.ic_launcher_3d, resolveNotificationSmallIconRes("unknown_key"))
    }

    @Test
    fun notificationIconShouldPreferLauncherIconResourceWhenAvailable() {
        assertEquals(
            12345,
            resolveNotificationIconResByPriority(
                launcherIconRes = 12345,
                fallbackIconKey = "Yuki"
            )
        )
    }

    @Test
    fun notificationIconShouldFallbackToIconKeyWhenLauncherIconMissing() {
        assertEquals(
            R.mipmap.ic_launcher_neon,
            resolveNotificationIconResByPriority(
                launcherIconRes = 0,
                fallbackIconKey = "Neon"
            )
        )
    }

    @Test
    fun playlistNavigationDispatchRequiresCallback() {
        val item = PlaylistItem(
            bvid = "BV1xx",
            title = "sample",
            cover = "",
            owner = "tester"
        )

        var invoked = false
        assertFalse(dispatchPlaylistNavigation(item, callback = null))
        assertFalse(invoked)

        assertTrue(
            dispatchPlaylistNavigation(item) {
                invoked = true
            }
        )
        assertTrue(invoked)
    }

    @Test
    fun playlistNavigationDispatchRejectsBangumiAndEmptyItem() {
        val bangumiItem = PlaylistItem(
            bvid = "BV2yy",
            title = "ep",
            cover = "",
            owner = "tester",
            isBangumi = true
        )
        assertFalse(dispatchPlaylistNavigation(null, callback = {}))
        assertFalse(dispatchPlaylistNavigation(bangumiItem, callback = {}))
    }

    @Test
    fun bangumiNavigationDispatchRequiresValidEpisodeContext() {
        val bangumiItem = PlaylistItem(
            bvid = "",
            title = "第1话",
            cover = "",
            owner = "番剧",
            isBangumi = true,
            seasonId = 100L,
            epId = 200L
        )
        var invoked = false
        assertTrue(
            dispatchBangumiNavigation(bangumiItem) {
                invoked = true
            }
        )
        assertTrue(invoked)

        assertFalse(
            dispatchBangumiNavigation(
                bangumiItem.copy(seasonId = 0L),
                callback = {}
            )
        )
        assertFalse(
            dispatchBangumiNavigation(
                bangumiItem.copy(epId = null),
                callback = {}
            )
        )
        assertFalse(dispatchBangumiNavigation(bangumiItem, callback = null))
        assertFalse(dispatchBangumiNavigation(null, callback = {}))
        assertFalse(dispatchBangumiNavigation(bangumiItem.copy(isBangumi = false), callback = {}))
    }

    @Test
    fun mediaButtonControlTypeShouldMapKeyEvents() {
        assertEquals(
            MediaControlType.PREVIOUS,
            resolveMediaButtonControlType(
                keyCode = android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                action = android.view.KeyEvent.ACTION_DOWN
            )
        )
        assertEquals(
            MediaControlType.PLAY_PAUSE,
            resolveMediaButtonControlType(
                keyCode = android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = android.view.KeyEvent.ACTION_DOWN
            )
        )
        assertEquals(
            MediaControlType.NEXT,
            resolveMediaButtonControlType(
                keyCode = android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
                action = android.view.KeyEvent.ACTION_DOWN
            )
        )
        assertNull(
            resolveMediaButtonControlType(
                keyCode = android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
                action = android.view.KeyEvent.ACTION_UP
            )
        )
    }

    @Test
    fun controlTypeShouldResolveFromIntentExtra() {
        assertEquals(MediaControlType.PREVIOUS, resolveMediaControlType(MiniPlayerManager.ACTION_PREVIOUS))
        assertEquals(MediaControlType.PLAY_PAUSE, resolveMediaControlType(MiniPlayerManager.ACTION_PLAY_PAUSE))
        assertEquals(MediaControlType.NEXT, resolveMediaControlType(MiniPlayerManager.ACTION_NEXT))
        assertNull(resolveMediaControlType(-1))
    }

    @Test
    fun notificationPlaybackStateShouldPreferActualPlayerState() {
        assertTrue(
            resolveNotificationIsPlaying(
                playerIsPlaying = true,
                cachedIsPlaying = false
            )
        )
        assertFalse(
            resolveNotificationIsPlaying(
                playerIsPlaying = false,
                cachedIsPlaying = true
            )
        )
    }

    @Test
    fun notificationPlaybackStateFallsBackToCacheWhenPlayerStateUnknown() {
        assertTrue(
            resolveNotificationIsPlaying(
                playerIsPlaying = null,
                cachedIsPlaying = true
            )
        )
        assertFalse(
            resolveNotificationIsPlaying(
                playerIsPlaying = null,
                cachedIsPlaying = false
            )
        )
    }

    @Test
    fun playbackStateChangeShouldRefreshNotificationOnlyWhenMetadataReadyAndActive() {
        assertTrue(
            shouldRefreshNotificationOnPlaybackStateChange(
                isActive = true,
                title = "测试视频"
            )
        )
        assertFalse(
            shouldRefreshNotificationOnPlaybackStateChange(
                isActive = false,
                title = "测试视频"
            )
        )
        assertFalse(
            shouldRefreshNotificationOnPlaybackStateChange(
                isActive = true,
                title = ""
            )
        )
    }

    @Test
    fun playbackNotificationVisibleOnlyWhilePlaybackIsActuallyActive() {
        assertTrue(
            shouldKeepPlaybackNotificationVisible(
                isActive = true,
                title = "测试视频",
                isPlaying = true,
                appInBackground = false
            )
        )
        assertFalse(
            shouldKeepPlaybackNotificationVisible(
                isActive = true,
                title = "测试视频",
                isPlaying = false,
                appInBackground = false
            )
        )
        assertFalse(
            shouldKeepPlaybackNotificationVisible(
                isActive = false,
                title = "测试视频",
                isPlaying = true,
                appInBackground = false
            )
        )
        assertFalse(
            shouldKeepPlaybackNotificationVisible(
                isActive = true,
                title = "",
                isPlaying = true,
                appInBackground = false
            )
        )
    }

    @Test
    fun appResumeShouldClearStalePlaybackNotificationWhenIdle() {
        assertTrue(
            shouldClearStalePlaybackNotificationOnAppResume(
                isActive = false,
                playerIsPlaying = false
            )
        )
        assertTrue(
            shouldClearStalePlaybackNotificationOnAppResume(
                isActive = true,
                playerIsPlaying = false
            )
        )
        assertFalse(
            shouldClearStalePlaybackNotificationOnAppResume(
                isActive = true,
                playerIsPlaying = true
            )
        )
    }

    @Test
    fun metadataCoverUrlShouldFallbackToCachedValueWhenIncomingCoverEmpty() {
        assertEquals(
            "https://example.com/cached.jpg",
            resolveEffectiveNotificationCoverUrl(
                incomingCoverUrl = "",
                cachedCoverUrl = "https://example.com/cached.jpg"
            )
        )
        assertEquals(
            "https://example.com/new.jpg",
            resolveEffectiveNotificationCoverUrl(
                incomingCoverUrl = "https://example.com/new.jpg",
                cachedCoverUrl = "https://example.com/cached.jpg"
            )
        )
    }

    @Test
    fun notificationArtworkShouldKeepPreviousBitmapWhenIncomingArtworkMissing() {
        assertEquals(
            "cached-artwork",
            resolveEffectiveNotificationArtwork(
                incomingArtwork = null,
                cachedArtwork = "cached-artwork"
            )
        )
        assertEquals(
            "new-artwork",
            resolveEffectiveNotificationArtwork(
                incomingArtwork = "new-artwork",
                cachedArtwork = "cached-artwork"
            )
        )
    }

    @Test
    fun pausedBackgroundSessionShouldKeepPlaybackNotificationVisible() {
        assertTrue(
            shouldKeepPlaybackNotificationVisible(
                isActive = true,
                title = "Background playback",
                isPlaying = false,
                appInBackground = true
            )
        )
    }

    @Test
    fun pausedForegroundSessionShouldNotKeepPlaybackNotificationVisible() {
        assertFalse(
            shouldKeepPlaybackNotificationVisible(
                isActive = true,
                title = "Foreground playback",
                isPlaying = false,
                appInBackground = false
            )
        )
    }

    @Test
    fun mediaSessionShouldRebindWhenPlaybackPlayerDiffers() {
        val sessionPlayer = Any()
        val playbackPlayer = Any()
        assertTrue(
            shouldRebindMediaSessionPlayer(
                sessionPlayer = sessionPlayer,
                playbackPlayer = playbackPlayer
            )
        )
        assertFalse(
            shouldRebindMediaSessionPlayer(
                sessionPlayer = sessionPlayer,
                playbackPlayer = sessionPlayer
            )
        )
        assertFalse(
            shouldRebindMediaSessionPlayer(
                sessionPlayer = sessionPlayer,
                playbackPlayer = null
            )
        )
    }

    @Test
    fun videoSkipShouldFallbackToDirectBackgroundExecutionWhenCallbackMissing() {
        val item = PlaylistItem(
            bvid = "BV1direct",
            title = "next",
            cover = "",
            owner = "tester"
        )

        assertEquals(
            PlaylistSkipExecutionMode.DIRECT_BACKGROUND,
            resolvePlaylistSkipExecutionMode(
                item = item,
                callbackAvailable = false,
                hasDirectPlaybackContext = true
            )
        )
    }

    @Test
    fun bangumiSkipShouldStillRequireCallbackEvenWhenDirectContextExists() {
        val item = PlaylistItem(
            bvid = "",
            title = "第1话",
            cover = "",
            owner = "番剧",
            isBangumi = true,
            seasonId = 100L,
            epId = 200L
        )

        assertEquals(
            PlaylistSkipExecutionMode.NONE,
            resolvePlaylistSkipExecutionMode(
                item = item,
                callbackAvailable = false,
                hasDirectPlaybackContext = true
            )
        )
        assertEquals(
            PlaylistSkipExecutionMode.CALLBACK,
            resolvePlaylistSkipExecutionMode(
                item = item,
                callbackAvailable = true,
                hasDirectPlaybackContext = true
            )
        )
    }

    @Test
    fun playlistSkipExecutionModeShouldRejectMissingItemOrPlaybackContext() {
        assertEquals(
            PlaylistSkipExecutionMode.NONE,
            resolvePlaylistSkipExecutionMode(
                item = null,
                callbackAvailable = false,
                hasDirectPlaybackContext = true
            )
        )
        assertEquals(
            PlaylistSkipExecutionMode.NONE,
            resolvePlaylistSkipExecutionMode(
                item = PlaylistItem(
                    bvid = "BV1noop",
                    title = "sample",
                    cover = "",
                    owner = "tester"
                ),
                callbackAvailable = false,
                hasDirectPlaybackContext = false
            )
        )
    }

    @Test
    fun externalTransportActionOrderShouldPrioritizeNextBeforePlayPauseAndPrevious() {
        assertEquals(
            listOf(
                MiniPlayerManager.ACTION_NEXT,
                MiniPlayerManager.ACTION_PLAY_PAUSE,
                MiniPlayerManager.ACTION_PREVIOUS
            ),
            resolveExternalTransportActionOrder().toList()
        )
    }
}
