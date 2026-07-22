package com.bbttvv.app.ui.player

import com.bbttvv.app.core.store.player.DanmakuFontWeightPreset
import com.bbttvv.app.core.store.player.DanmakuLaneDensityPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuSettingsUiPolicyTest {
    @Test
    fun `formats shared danmaku labels`() {
        assertEquals("75%", formatDanmakuOpacity(0.75f))
        assertEquals("不限", formatDanmakuAreaRatio(1f))
        assertEquals("1/2", formatDanmakuAreaRatio(0.5f))
        assertEquals("加粗", formatDanmakuFontWeight(DanmakuFontWeightPreset.Bold))
        assertEquals("密集", formatDanmakuLaneDensity(DanmakuLaneDensityPreset.Dense))
    }

    @Test
    fun `next option advances and wraps`() {
        val options = listOf(0.5f, 0.75f, 1f)

        assertEquals(0.75f, nextTvOption(options, 0.5f))
        assertEquals(0.5f, nextTvOption(options, 1f))
        assertEquals(0.5f, nextTvOption(options, 0.25f))
    }
}
