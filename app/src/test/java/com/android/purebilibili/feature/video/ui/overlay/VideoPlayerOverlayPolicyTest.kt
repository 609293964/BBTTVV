package com.android.purebilibili.feature.video.ui.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.android.purebilibili.core.store.BottomProgressBehavior
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

class VideoPlayerOverlayPolicyTest {

    @Test
    fun inlineOverlayProgressPolling_stopsWhenHostLifecycleStops() {
        assertTrue(
            shouldPollInlineVideoOverlayProgress(
                playerExists = true,
                hostLifecycleStarted = true
            )
        )
        assertFalse(
            shouldPollInlineVideoOverlayProgress(
                playerExists = true,
                hostLifecycleStarted = false
            )
        )
        assertFalse(
            shouldPollInlineVideoOverlayProgress(
                playerExists = false,
                hostLifecycleStarted = true
            )
        )
    }

    @Test
    fun episodeEntryShownWhenRelatedVideosExist() {
        assertTrue(
            shouldShowEpisodeEntryFromVideoData(
                relatedVideosCount = 1,
                hasSeasonEpisodes = false,
                pagesCount = 1
            )
        )
    }

    @Test
    fun episodeEntryShownWhenSeasonEpisodesExist() {
        assertTrue(
            shouldShowEpisodeEntryFromVideoData(
                relatedVideosCount = 0,
                hasSeasonEpisodes = true,
                pagesCount = 1
            )
        )
    }

    @Test
    fun episodeEntryShownWhenPagesExist() {
        assertTrue(
            shouldShowEpisodeEntryFromVideoData(
                relatedVideosCount = 0,
                hasSeasonEpisodes = false,
                pagesCount = 3
            )
        )
    }

    @Test
    fun episodeEntryHiddenWhenNoEpisodeData() {
        assertFalse(
            shouldShowEpisodeEntryFromVideoData(
                relatedVideosCount = 0,
                hasSeasonEpisodes = false,
                pagesCount = 1
            )
        )
    }

    @Test
    fun nextEpisodeTargetPrefersPageNext() {
        val target = resolveNextEpisodeTarget(
            pagesCount = 3,
            currentPageIndex = 0,
            seasonEpisodeBvids = listOf("BV1", "BV2"),
            currentBvid = "BV1",
            relatedBvids = listOf("BV3")
        )
        assertTrue(target?.nextPageIndex == 1)
    }

    @Test
    fun nextEpisodeTargetFallsBackToSeasonThenRelated() {
        val seasonTarget = resolveNextEpisodeTarget(
            pagesCount = 1,
            currentPageIndex = 0,
            seasonEpisodeBvids = listOf("BV1", "BV2"),
            currentBvid = "BV1",
            relatedBvids = listOf("BV3")
        )
        assertTrue(seasonTarget?.nextBvid == "BV2")

        val relatedTarget = resolveNextEpisodeTarget(
            pagesCount = 1,
            currentPageIndex = 0,
            seasonEpisodeBvids = listOf("BV1"),
            currentBvid = "BV1",
            relatedBvids = listOf("BV1", "BV3")
        )
        assertTrue(relatedTarget?.nextBvid == "BV3")
    }

    @Test
    fun nextEpisodeTargetReturnsNullWhenNoCandidate() {
        assertTrue(
            resolveNextEpisodeTarget(
                pagesCount = 1,
                currentPageIndex = 0,
                seasonEpisodeBvids = emptyList(),
                currentBvid = "BV1",
                relatedBvids = emptyList()
            )
                == null
        )
    }

    @Test
    fun drawerVisibleShouldConsumeBackgroundGestures() {
        assertTrue(
            shouldConsumeBackgroundGesturesForEndDrawer(
                endDrawerVisible = true
            )
        )
    }

    @Test
    fun drawerHiddenShouldNotConsumeBackgroundGestures() {
        assertFalse(
            shouldConsumeBackgroundGesturesForEndDrawer(
                endDrawerVisible = false
            )
        )
    }

    @Test
    fun centerPlayButtonHiddenWhenScrubbingOrBuffering() {
        assertFalse(
            shouldShowCenterPlayButton(
                isVisible = true,
                isPlaying = false,
                isQualitySwitching = false,
                isFullscreen = true,
                isBuffering = true,
                isScrubbing = false,
                isSeekTransitionPending = false
            )
        )
        assertFalse(
            shouldShowCenterPlayButton(
                isVisible = true,
                isPlaying = false,
                isQualitySwitching = false,
                isFullscreen = true,
                isBuffering = false,
                isScrubbing = true,
                isSeekTransitionPending = false
            )
        )
        assertTrue(
            shouldShowCenterPlayButton(
                isVisible = true,
                isPlaying = false,
                isQualitySwitching = false,
                isFullscreen = true,
                isBuffering = false,
                isScrubbing = false,
                isSeekTransitionPending = false
            )
        )
    }

    @Test
    fun centerPlayButtonHiddenDuringSeekResumeTransition() {
        assertFalse(
            shouldShowCenterPlayButton(
                isVisible = true,
                isPlaying = false,
                isQualitySwitching = false,
                isFullscreen = true,
                isBuffering = false,
                isScrubbing = false,
                isSeekTransitionPending = true
            )
        )
    }

    @Test
    fun bufferingIndicatorCanShowDuringScrubbingEvenWhenControlsVisible() {
        assertTrue(
            shouldShowBufferingIndicator(
                isBuffering = true,
                isQualitySwitching = false,
                isVisible = true,
                isScrubbing = true
            )
        )
        assertFalse(
            shouldShowBufferingIndicator(
                isBuffering = true,
                isQualitySwitching = false,
                isVisible = true,
                isScrubbing = false
            )
        )
    }

    @Test
    fun playbackDebugRows_includeAllReadableStatsAndSkipEmptyValues() {
        val rows = resolvePlaybackDebugRows(
            PlaybackDebugInfo(
                resolution = "1920 x 1080",
                videoBitrate = "8.4 Mbps",
                audioBitrate = "192 kbps",
                videoCodec = "HEVC",
                audioCodec = "AAC LC",
                frameRate = "60 fps",
                videoDecoder = "c2.qti.hevc.decoder",
                audioDecoder = ""
            )
        )

        assertEquals(
            listOf(
                DebugStatRow("Resolution", "1920 x 1080"),
                DebugStatRow("Video bitrate", "8.4 Mbps"),
                DebugStatRow("Audio bitrate", "192 kbps"),
                DebugStatRow("Video codec", "HEVC"),
                DebugStatRow("Audio codec", "AAC LC"),
                DebugStatRow("Frame rate", "60 fps"),
                DebugStatRow("Video decoder", "c2.qti.hevc.decoder")
            ),
            rows
        )
    }

    @Test
    fun centerPlaybackButtonStyle_keepsReadableWhiteGlyphAcrossThemes() {
        val darkStyle = resolveCenterPlaybackButtonStyle(isDarkTheme = true)
        val lightStyle = resolveCenterPlaybackButtonStyle(isDarkTheme = false)

        assertTrue(darkStyle.containerColor.alpha > lightStyle.containerColor.alpha)
        assertTrue(darkStyle.innerColor != lightStyle.innerColor)
        assertEquals(Color.White, darkStyle.iconTint)
        assertEquals(Color.White, lightStyle.iconTint)
    }

    @Test
    fun playbackGlyphOpticalOffset_onlyShiftsPlayIcon() {
        assertEquals(0f, resolvePlaybackGlyphHorizontalBias(isPlaying = true))
        assertTrue(resolvePlaybackGlyphHorizontalBias(isPlaying = false) > 0f)
    }

    @Test
    fun pageSelectorSheetOuterBottomPadding_isZeroInFullscreen() {
        assertEquals(0, resolvePageSelectorSheetOuterBottomPaddingDp(isFullscreen = true))
        assertEquals(8, resolvePageSelectorSheetOuterBottomPaddingDp(isFullscreen = false))
    }

    @Test
    fun persistentBottomProgressBarRespectsAlwaysShowBehavior() {
        assertTrue(
            shouldShowPersistentBottomProgressBar(
                controlsVisible = false,
                isFullscreen = false,
                behavior = BottomProgressBehavior.ALWAYS_SHOW
            )
        )
        assertFalse(
            shouldShowPersistentBottomProgressBar(
                controlsVisible = true,
                isFullscreen = true,
                behavior = BottomProgressBehavior.ALWAYS_SHOW
            )
        )
    }

    @Test
    fun persistentBottomProgressBarRespectsAlwaysHideBehavior() {
        assertFalse(
            shouldShowPersistentBottomProgressBar(
                controlsVisible = false,
                isFullscreen = true,
                behavior = BottomProgressBehavior.ALWAYS_HIDE
            )
        )
    }

    @Test
    fun persistentBottomProgressBarRespectsOnlyShowFullscreenBehavior() {
        assertTrue(
            shouldShowPersistentBottomProgressBar(
                controlsVisible = false,
                isFullscreen = true,
                behavior = BottomProgressBehavior.ONLY_SHOW_FULLSCREEN
            )
        )
        assertFalse(
            shouldShowPersistentBottomProgressBar(
                controlsVisible = false,
                isFullscreen = false,
                behavior = BottomProgressBehavior.ONLY_SHOW_FULLSCREEN
            )
        )
    }

    @Test
    fun persistentBottomProgressBarRespectsOnlyHideFullscreenBehavior() {
        assertTrue(
            shouldShowPersistentBottomProgressBar(
                controlsVisible = false,
                isFullscreen = false,
                behavior = BottomProgressBehavior.ONLY_HIDE_FULLSCREEN
            )
        )
        assertFalse(
            shouldShowPersistentBottomProgressBar(
                controlsVisible = false,
                isFullscreen = true,
                behavior = BottomProgressBehavior.ONLY_HIDE_FULLSCREEN
            )
        )
    }

    @Test
    fun resolveDisplayedOnlineCountRespectsSetting() {
        assertEquals(
            "123人正在看",
            resolveDisplayedOnlineCount(
                onlineCount = "123人正在看",
                showOnlineCount = true
            )
        )
        assertEquals(
            "",
            resolveDisplayedOnlineCount(
                onlineCount = "123人正在看",
                showOnlineCount = false
            )
        )
    }

    @Test
    fun displayedProgressPrefersPreviewWhileSeeking() {
        val resolved = resolveDisplayedPlayerProgress(
            progress = PlayerProgress(
                current = 12_000L,
                duration = 60_000L,
                buffered = 20_000L
            ),
            previewPositionMs = 34_000L,
            previewActive = true
        )

        assertEquals(34_000L, resolved.current)
        assertEquals(60_000L, resolved.duration)
        assertEquals(20_000L, resolved.buffered)
    }

    @Test
    fun displayedProgressClampsPreviewWithinDuration() {
        val resolved = resolveDisplayedPlayerProgress(
            progress = PlayerProgress(
                current = 12_000L,
                duration = 60_000L,
                buffered = 20_000L
            ),
            previewPositionMs = 90_000L,
            previewActive = true
        )

        assertEquals(60_000L, resolved.current)
    }

    @Test
    fun displayedProgressFallsBackToPlayerPositionWhenPreviewInactive() {
        val resolved = resolveDisplayedPlayerProgress(
            progress = PlayerProgress(
                current = 12_000L,
                duration = 60_000L,
                buffered = 20_000L
            ),
            previewPositionMs = 34_000L,
            previewActive = false
        )

        assertEquals(12_000L, resolved.current)
    }
}
