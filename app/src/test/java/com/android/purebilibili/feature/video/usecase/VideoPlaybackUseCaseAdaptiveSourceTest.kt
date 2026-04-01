package com.android.purebilibili.feature.video.usecase

import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.PlayUrlData
import com.android.purebilibili.data.model.response.SegmentBase
import com.android.purebilibili.feature.video.playback.dash.AdaptiveDashPlaybackSource
import com.android.purebilibili.feature.video.playback.policy.PlaybackQualityMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VideoPlaybackUseCaseAdaptiveSourceTest {

    @Test
    fun `resolve playback selection exposes adaptive dash source in auto mode`() {
        val useCase = VideoPlaybackUseCase()
        val playUrlData = PlayUrlData(
            quality = 80,
            acceptQuality = listOf(80, 64, 32),
            dash = Dash(
                duration = 123,
                minBufferTime = 1.5f,
                video = listOf(
                    DashVideo(
                        id = 80,
                        baseUrl = "https://example.com/video-1080.m4s",
                        bandwidth = 8_000_000,
                        mimeType = "video/mp4",
                        codecs = "hev1",
                        width = 1920,
                        height = 1080,
                        frameRate = "60",
                        segmentBase = SegmentBase("0-999", "1000-1999")
                    ),
                    DashVideo(
                        id = 64,
                        baseUrl = "https://example.com/video-720.m4s",
                        bandwidth = 4_000_000,
                        mimeType = "video/mp4",
                        codecs = "avc1",
                        width = 1280,
                        height = 720,
                        frameRate = "30",
                        segmentBase = SegmentBase("0-888", "889-1777")
                    )
                ),
                audio = listOf(
                    DashAudio(
                        id = 30280,
                        baseUrl = "https://example.com/audio.m4s",
                        bandwidth = 192_000,
                        mimeType = "audio/mp4",
                        codecs = "mp4a.40.2",
                        segmentBase = SegmentBase("0-555", "556-999")
                    )
                )
            )
        )

        val selection = useCase.resolvePlaybackSelection(
            playUrlData = playUrlData,
            targetQuality = 80,
            audioQualityPreference = -1,
            videoCodecPreference = "hev1",
            videoSecondCodecPreference = "avc1",
            playbackQualityMode = PlaybackQualityMode.AUTO,
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertNotNull(selection)
        assertNotNull(selection.adaptiveDashSource)
        assertEquals(PlaybackQualityMode.AUTO, selection.adaptiveDashSource.playbackQualityMode)
        assertEquals(listOf(80, 64), selection.adaptiveDashSource.videoTracks.map { it.id })
    }

    @Test
    fun `adaptive dash playback takes precedence when source exists`() {
        val source = AdaptiveDashPlaybackSource(
            manifest = "<MPD />",
            videoTracks = emptyList(),
            audioTracks = emptyList(),
            playbackQualityMode = PlaybackQualityMode.AUTO
        )

        assertTrue(
            shouldUseAdaptiveDashPlayback(
                adaptiveDashSource = source,
                audioUrl = "https://example.com/audio.m4s"
            )
        )
    }

    @Test
    fun `local dash manifest file name is stable and uses mpd suffix`() {
        val fileName = resolveLocalDashManifestFileName("<MPD>same</MPD>")

        assertEquals(fileName, resolveLocalDashManifestFileName("<MPD>same</MPD>"))
        assertTrue(fileName.endsWith(".mpd"))
    }
}
