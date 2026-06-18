package com.bbttvv.app.feature.video.usecase

import com.bbttvv.app.data.model.VideoQuality
import com.bbttvv.app.data.model.response.Dash
import com.bbttvv.app.data.model.response.DashVideo
import com.bbttvv.app.data.model.response.Page
import com.bbttvv.app.data.model.response.PlayUrlData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackUseCaseTest {
    private val useCase = VideoPlaybackUseCase()

    @Test
    fun `unsupported hdr auto highest selects 4k sdr fallback`() {
        val source = requireNotNull(
            useCase.resolvePlaybackSource(
                playUrlData = playbackData(
                    reportedQuality = VideoQuality.HDR.code,
                    dashVideos = listOf(
                        dashVideo(VideoQuality.HDR.code),
                        dashVideo(VideoQuality.SUPER_4K.code),
                        dashVideo(VideoQuality.HIGH_1080.code),
                    )
                ),
                currentCid = 100L,
                preferredQuality = VideoQuality.SUPER_8K.code,
                isHevcSupported = true,
                isAv1Supported = false,
                isHdrSupported = false,
                isDolbyVisionSupported = false,
            )
        )

        assertEquals(VideoQuality.SUPER_4K.code, source.actualQuality)
        assertEquals("https://example.com/video-120.m4s", source.videoUrl)
        assertNull(source.activeDynamicRangeLabel)
        assertTrue(source.hasHdrTrack)
        val hdrOption = requireNotNull(source.qualityOptions.firstOrNull { it.id == VideoQuality.HDR.code })
        assertFalse(hdrOption.isSupported)
        assertEquals("当前设备不支持 HDR", hdrOption.unsupportedReason)
    }

    @Test
    fun `unsupported dolby vision can fall back to hdr when hdr is supported`() {
        val source = requireNotNull(
            useCase.resolvePlaybackSource(
                playUrlData = playbackData(
                    reportedQuality = VideoQuality.DOLBY_VISION.code,
                    dashVideos = listOf(
                        dashVideo(VideoQuality.DOLBY_VISION.code, codecs = "dvh1.05.06"),
                        dashVideo(VideoQuality.HDR.code),
                        dashVideo(VideoQuality.SUPER_4K.code),
                    )
                ),
                currentCid = 100L,
                preferredQuality = VideoQuality.SUPER_8K.code,
                isHevcSupported = true,
                isAv1Supported = false,
                isHdrSupported = true,
                isDolbyVisionSupported = false,
            )
        )

        assertEquals(VideoQuality.HDR.code, source.actualQuality)
        assertEquals("HDR 真彩", source.activeDynamicRangeLabel)
        assertTrue(source.hasDolbyVisionTrack)
        val dolbyVisionOption =
            requireNotNull(source.qualityOptions.firstOrNull { it.id == VideoQuality.DOLBY_VISION.code })
        assertFalse(dolbyVisionOption.isSupported)
        assertEquals("当前设备不支持杜比视界", dolbyVisionOption.unsupportedReason)
    }

    @Test
    fun `play url duration wins over page fallback`() {
        assertEquals(
            60_000L,
            resolvePlaybackDurationMs(
                playUrlDurationMs = 60_000L,
                fallbackDurationMs = 90_000L
            )
        )
    }

    @Test
    fun `page duration fills missing play url duration`() {
        val pages = listOf(
            Page(cid = 1L, duration = 30L),
            Page(cid = 2L, duration = 90L)
        )

        assertEquals(90_000L, resolvePageDurationMs(pages, currentCid = 2L))
        assertEquals(
            90_000L,
            resolvePlaybackDurationMs(
                playUrlDurationMs = 0L,
                fallbackDurationMs = resolvePageDurationMs(pages, currentCid = 2L)
            )
        )
    }

    private fun playbackData(
        reportedQuality: Int,
        dashVideos: List<DashVideo>,
    ): PlayUrlData {
        return PlayUrlData(
            quality = reportedQuality,
            timelength = 60_000L,
            acceptQuality = dashVideos.map { it.id }.distinct(),
            dash = Dash(video = dashVideos)
        )
    }

    private fun dashVideo(
        quality: Int,
        codecs: String = "hev1.1.6.L150",
    ): DashVideo {
        return DashVideo(
            id = quality,
            baseUrl = "https://example.com/video-$quality.m4s",
            backupUrl = listOf("https://backup.example.com/video-$quality.m4s"),
            bandwidth = quality * 10_000,
            codecs = codecs,
            width = if (quality >= VideoQuality.SUPER_4K.code) 3840 else 1920,
            height = if (quality >= VideoQuality.SUPER_4K.code) 2160 else 1080,
            frameRate = "30",
            codecid = 12
        )
    }
}
