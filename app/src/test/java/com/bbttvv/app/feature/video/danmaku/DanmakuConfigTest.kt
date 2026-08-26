package com.bbttvv.app.feature.video.danmaku

import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuConfigTest {

    @Test
    fun `layout size follows large configured item size`() {
        assertEquals(96f, resolveDanmakuLayoutTextSize(96f), 0.0001f)
    }

    @Test
    fun `layout size keeps safe baseline for invalid or small values`() {
        assertEquals(42f, resolveDanmakuLayoutTextSize(32f), 0.0001f)
        assertEquals(42f, resolveDanmakuLayoutTextSize(Float.NaN), 0.0001f)
    }
}
