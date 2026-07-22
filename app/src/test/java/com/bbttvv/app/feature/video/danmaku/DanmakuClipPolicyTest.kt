package com.bbttvv.app.feature.video.danmaku

import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuClipPolicyTest {
    @Test
    fun `comments clip keeps seventy percent of a full hd viewport`() {
        assertEquals(1344, resolveDanmakuClipWidthPx(1920, 0.70f))
    }

    @Test
    fun `full width and invalid fractions preserve full viewport`() {
        assertEquals(1920, resolveDanmakuClipWidthPx(1920, 1f))
        assertEquals(1920, resolveDanmakuClipWidthPx(1920, Float.NaN))
    }

    @Test
    fun `clip policy clamps values to viewport bounds`() {
        assertEquals(0, resolveDanmakuClipWidthPx(1920, -1f))
        assertEquals(1920, resolveDanmakuClipWidthPx(1920, 2f))
        assertEquals(0, resolveDanmakuClipWidthPx(-1, 0.70f))
    }
}
