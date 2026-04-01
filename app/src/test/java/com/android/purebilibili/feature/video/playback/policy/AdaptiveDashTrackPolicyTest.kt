package com.android.purebilibili.feature.video.playback.policy

import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import kotlin.test.Test
import kotlin.test.assertEquals

class AdaptiveDashTrackPolicyTest {

    private val dash = Dash(
        video = listOf(
            DashVideo(id = 120, baseUrl = "https://example.com/4k-av1.m4s", codecs = "av01", bandwidth = 20_000_000),
            DashVideo(id = 80, baseUrl = "https://example.com/1080-hevc.m4s", codecs = "hev1", bandwidth = 8_000_000),
            DashVideo(id = 64, baseUrl = "https://example.com/720-avc.m4s", codecs = "avc1", bandwidth = 4_000_000),
            DashVideo(id = 32, baseUrl = "https://example.com/480-avc.m4s", codecs = "avc1", bandwidth = 1_500_000)
        ),
        audio = listOf(
            DashAudio(id = 30280, baseUrl = "https://example.com/audio-192k.m4s", codecs = "mp4a.40.2", bandwidth = 192_000),
            DashAudio(id = 30216, baseUrl = "https://example.com/audio-64k.m4s", codecs = "mp4a.40.2", bandwidth = 64_000)
        )
    )

    @Test
    fun `auto mode keeps all playable tracks up to quality cap`() {
        val result = buildAdaptiveDashTrackSet(
            dash = dash,
            mode = PlaybackQualityMode.AUTO,
            autoQualityCap = 80,
            preferredAudioQuality = -1,
            preferredVideoCodec = "hev1",
            secondaryVideoCodec = "avc1",
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertEquals(listOf(80, 64, 32), result.videoTracks.map { it.id })
        assertEquals(listOf(30280, 30216), result.audioTracks.map { it.id })
    }

    @Test
    fun `locked mode keeps only the requested quality`() {
        val result = buildAdaptiveDashTrackSet(
            dash = dash,
            mode = PlaybackQualityMode.LOCKED(64),
            autoQualityCap = 80,
            preferredAudioQuality = -1,
            preferredVideoCodec = "hev1",
            secondaryVideoCodec = "avc1",
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertEquals(listOf(64), result.videoTracks.map { it.id })
    }

    @Test
    fun `session codec fallback removes blocked av1 tracks from auto mode`() {
        val result = buildAdaptiveDashTrackSet(
            dash = dash,
            mode = PlaybackQualityMode.AUTO,
            autoQualityCap = 120,
            preferredAudioQuality = -1,
            preferredVideoCodec = "av01",
            secondaryVideoCodec = "hev1",
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertEquals(listOf(80, 64, 32), result.videoTracks.map { it.id })
    }
}
