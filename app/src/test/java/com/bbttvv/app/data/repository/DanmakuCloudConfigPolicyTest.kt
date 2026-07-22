package com.bbttvv.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuCloudConfigPolicyTest {
    @Test
    fun `cloud font size stays above bilibili exclusive floor`() {
        assertEquals(0.51f, mapDanmakuFontScaleToCloudFontSize(0.3f))
        assertEquals(0.51f, mapDanmakuFontScaleToCloudFontSize(0.5f))
        assertEquals(0.501f, mapDanmakuFontScaleToCloudFontSize(0.501f), 0.0001f)
        assertEquals(1.6f, mapDanmakuFontScaleToCloudFontSize(2f))
    }
}
