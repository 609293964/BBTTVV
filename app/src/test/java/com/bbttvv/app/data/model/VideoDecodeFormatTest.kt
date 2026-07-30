package com.bbttvv.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoDecodeFormatTest {
    @Test
    fun `hevc aliases and profiles share one codec family`() {
        assertEquals(VideoDecodeFormat.HEVC, VideoDecodeFormat.fromCodecs("hev1"))
        assertEquals(VideoDecodeFormat.HEVC, VideoDecodeFormat.fromCodecs("hev1.1.6.L120"))
        assertEquals(VideoDecodeFormat.HEVC, VideoDecodeFormat.fromCodecs("hvc1"))
        assertEquals(VideoDecodeFormat.HEVC, VideoDecodeFormat.fromCodecs("  HVC1.2.4.L153  "))
    }

    @Test
    fun `avc av1 and dolby vision are classified after normalization`() {
        assertEquals(VideoDecodeFormat.AVC, VideoDecodeFormat.fromCodecs("AVC1.640028"))
        assertEquals(VideoDecodeFormat.AV1, VideoDecodeFormat.fromCodecs("av01.0.08M.08"))
        assertEquals(VideoDecodeFormat.DVH1, VideoDecodeFormat.fromCodecs("dvh1.05.06"))
        assertEquals(VideoDecodeFormat.DVH1, VideoDecodeFormat.fromCodecs("DVHE.05.06"))
    }

    @Test
    fun `blank null and unknown codecs stay unknown`() {
        assertNull(VideoDecodeFormat.fromCodecs(null))
        assertNull(VideoDecodeFormat.fromCodecs(""))
        assertNull(VideoDecodeFormat.fromCodecs("vp09"))
    }
}
