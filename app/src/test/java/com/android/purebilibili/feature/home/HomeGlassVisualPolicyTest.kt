package com.android.purebilibili.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeGlassVisualPolicyTest {

    @Test
    fun prefersLightStructuralTintWhenGlassAndBlurAreEnabled() {
        val style = resolveHomeGlassChromeStyle(
            glassEnabled = true,
            blurEnabled = true
        )

        assertEquals(0.16f, style.containerAlpha)
        assertEquals(0.22f, style.highlightAlpha)
        assertEquals(0.18f, style.borderAlpha)
    }

    @Test
    fun fallsBackToDenserChromeWhenBlurIsDisabled() {
        val style = resolveHomeGlassChromeStyle(
            glassEnabled = true,
            blurEnabled = false
        )

        assertTrue(style.containerAlpha > 0.8f)
        assertEquals(0.08f, style.highlightAlpha)
        assertEquals(0.10f, style.borderAlpha)
    }

    @Test
    fun givesPillsStrongerFillThanChromeToProtectReadability() {
        val chromeStyle = resolveHomeGlassChromeStyle(
            glassEnabled = true,
            blurEnabled = true
        )
        val pillStyle = resolveHomeGlassPillStyle(
            glassEnabled = true,
            blurEnabled = true,
            emphasized = false
        )

        assertTrue(pillStyle.containerAlpha > chromeStyle.containerAlpha)
        assertEquals(0.24f, pillStyle.containerAlpha)
        assertEquals(0.16f, pillStyle.borderAlpha)
    }

    @Test
    fun emphasizedPillsGetSlightlyStrongerHighlight() {
        val normal = resolveHomeGlassPillStyle(
            glassEnabled = true,
            blurEnabled = true,
            emphasized = false
        )
        val emphasized = resolveHomeGlassPillStyle(
            glassEnabled = true,
            blurEnabled = true,
            emphasized = true
        )

        assertTrue(emphasized.highlightAlpha > normal.highlightAlpha)
        assertEquals(0.20f, emphasized.highlightAlpha)
    }
}
