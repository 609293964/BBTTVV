package com.bbttvv.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuResourceBoundsTest {
    @Test
    fun `duration derived segment count is capped without overflow`() {
        assertEquals(
            DANMAKU_MAX_SEGMENT_COUNT,
            resolveDanmakuSegmentCount(Long.MAX_VALUE, null)
        )
    }

    @Test
    fun `metadata segment count is capped`() {
        assertEquals(
            DANMAKU_MAX_SEGMENT_COUNT,
            resolveDanmakuSegmentCount(0L, Int.MAX_VALUE)
        )
    }

    @Test
    fun `normal segment counts keep existing behavior`() {
        assertEquals(1, resolveDanmakuSegmentCount(1L, null))
        assertEquals(2, resolveDanmakuSegmentCount(DANMAKU_SEGMENT_DURATION_MS + 1L, null))
        assertEquals(12, resolveDanmakuSegmentCount(0L, 12))
        assertEquals(DANMAKU_SEGMENT_SAFE_FALLBACK_COUNT, resolveDanmakuSegmentCount(0L, null))
    }
}
