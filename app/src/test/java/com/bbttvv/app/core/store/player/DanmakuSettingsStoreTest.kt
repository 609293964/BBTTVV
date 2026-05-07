package com.bbttvv.app.core.store.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuSettingsStoreTest {

    @Test
    fun `normalizeDanmakuSettings clamps values into supported ranges`() {
        val normalized = normalizeDanmakuSettings(
            DanmakuSettings(
                opacity = 0.91f,
                textSizeSp = 39,
                strokeWidthPx = 5,
                areaRatio = 0.58f,
                speedLevel = 99,
                aiShieldLevel = 0,
            )
        )

        assertEquals(0.9f, normalized.opacity, 0.0001f)
        assertEquals(38, normalized.textSizeSp)
        assertEquals(4, normalized.strokeWidthPx)
        assertEquals(0.6f, normalized.areaRatio, 0.0001f)
        assertEquals(10, normalized.speedLevel)
        assertEquals(1, normalized.aiShieldLevel)
    }

    @Test
    fun `toEngineConfig maps supported settings onto danmaku engine config`() {
        val config = DanmakuSettings(
            opacity = 0.75f,
            textSizeSp = 42,
            fontWeight = DanmakuFontWeightPreset.Bold,
            strokeWidthPx = 0,
            areaRatio = 2f / 3f,
            laneDensity = DanmakuLaneDensityPreset.Dense,
            speedLevel = 8
        ).toEngineConfig()

        assertEquals(0.75f, config.opacity, 0.0001f)
        assertEquals(1.0f, config.fontScale, 0.0001f)
        assertEquals(6, config.fontWeight)
        assertFalse(config.strokeEnabled)
        assertEquals(0f, config.strokeWidth, 0.0001f)
        assertEquals(2f / 3f, config.displayAreaRatio, 0.0001f)
        assertEquals(1.18f, config.lineHeight, 0.0001f)
        assertEquals(0.68f, config.speedFactor, 0.0001f)
    }

    @Test
    fun `shouldRenderDanmakuItem respects type and color switches`() {
        val settings = DanmakuSettings(
            allowScroll = false,
            allowTop = false,
            allowBottom = false,
            allowColor = false,
            allowSpecial = false,
        )

        assertFalse(shouldRenderDanmakuItem(type = 1, color = 0x00FFFFFF, settings = settings))
        assertFalse(shouldRenderDanmakuItem(type = 5, color = 0x00FFFFFF, settings = settings))
        assertFalse(shouldRenderDanmakuItem(type = 4, color = 0x00FFFFFF, settings = settings))
        assertFalse(shouldRenderDanmakuItem(type = 1, color = 0x0000FF00, settings = settings))
        assertFalse(shouldRenderDanmakuItem(type = 7, color = 0x00FFFFFF, settings = settings))
        assertFalse(shouldRenderDanmakuItem(type = 7, color = 0x00FFAA00, settings = settings))
    }

    @Test
    fun `shouldAllowDanmakuWeight respects ai shield settings`() {
        val enabled = DanmakuSettings(aiShieldEnabled = true, aiShieldLevel = 4)

        assertFalse(shouldAllowDanmakuWeight(weight = 3, settings = enabled))
        assertTrue(shouldAllowDanmakuWeight(weight = 4, settings = enabled))
        assertTrue(shouldAllowDanmakuWeight(weight = 1, settings = DanmakuSettings(aiShieldEnabled = false)))
    }
}
