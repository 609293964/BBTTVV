package com.android.purebilibili.feature.video.playback.dash

import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.SegmentBase
import kotlin.test.Test
import kotlin.test.assertTrue

class LocalDashManifestBuilderTest {

    @Test
    fun `build manifests multiple representations and adaptation sets`() {
        val manifest = buildLocalDashManifest(
            durationMs = 123_456L,
            minBufferTimeMs = 1_500L,
            videoTracks = listOf(
                DashVideo(
                    id = 80,
                    baseUrl = "https://example.com/video-1080.m4s",
                    bandwidth = 8_000_000,
                    mimeType = "video/mp4",
                    codecs = "hev1",
                    width = 1920,
                    height = 1080,
                    frameRate = "60",
                    segmentBase = SegmentBase(
                        initialization = "0-999",
                        indexRange = "1000-1999"
                    )
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
                    segmentBase = SegmentBase(
                        initialization = "0-888",
                        indexRange = "889-1777"
                    )
                )
            ),
            audioTracks = listOf(
                DashAudio(
                    id = 30280,
                    baseUrl = "https://example.com/audio.m4s",
                    bandwidth = 192_000,
                    mimeType = "audio/mp4",
                    codecs = "mp4a.40.2",
                    segmentBase = SegmentBase(
                        initialization = "0-555",
                        indexRange = "556-999"
                    )
                )
            )
        )

        assertTrue(manifest.contains("<MPD"))
        assertTrue(manifest.contains("mediaPresentationDuration=\"PT123.456S\""))
        assertTrue(manifest.contains("minBufferTime=\"PT1.500S\""))
        assertTrue(manifest.contains("Representation id=\"80\""))
        assertTrue(manifest.contains("Representation id=\"64\""))
        assertTrue(manifest.contains("Representation id=\"30280\""))
        assertTrue(manifest.contains("<BaseURL>https://example.com/video-1080.m4s</BaseURL>"))
        assertTrue(manifest.contains("<Initialization range=\"0-999\"/>"))
        assertTrue(manifest.contains("indexRange=\"1000-1999\""))
    }
}
