package com.android.purebilibili.core.theme

import androidx.compose.ui.unit.isUnspecified
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.yukonga.miuix.kmp.theme.defaultTextStyles

class AppDisplayPolicyTest {

    @Test
    fun `font preset resolves expected multiplier`() {
        assertEquals(0.92f, AppFontSizePreset.SMALLER.multiplier)
        assertEquals(1.00f, AppFontSizePreset.DEFAULT.multiplier)
        assertEquals(1.08f, AppFontSizePreset.LARGER.multiplier)
    }

    @Test
    fun `ui scale preset resolves expected density multiplier`() {
        assertEquals(1.08f, AppUiScalePreset.COMPACT.densityMultiplier)
        assertEquals(1.00f, AppUiScalePreset.STANDARD.densityMultiplier)
        assertEquals(0.92f, AppUiScalePreset.LARGE.densityMultiplier)
    }

    @Test
    fun `dpi override takes precedence over ui scale preset`() {
        assertEquals(
            1.12f,
            resolveEffectiveDensityMultiplier(
                uiScalePreset = AppUiScalePreset.COMFORTABLE,
                dpiOverridePercent = 112
            )
        )
    }

    @Test
    fun `effective width shrinks when density multiplier grows`() {
        val snapshot = buildDisplayMetricsSnapshot(
            systemDensityDpi = 560,
            smallestScreenWidthDp = 347,
            uiScalePreset = AppUiScalePreset.COMPACT,
            fontSizePreset = AppFontSizePreset.DEFAULT,
            dpiOverridePercent = null
        )

        assertEquals(1.08f, snapshot.effectiveDensityMultiplier)
        assertEquals(605, snapshot.effectiveDensityDpi)
        assertEquals(321, snapshot.effectiveSmallestWidthDp)
        assertTrue(snapshot.isNarrowWidth)
    }

    @Test
    fun `system dpi is kept when override is disabled`() {
        val snapshot = buildDisplayMetricsSnapshot(
            systemDensityDpi = 560,
            smallestScreenWidthDp = 393,
            uiScalePreset = AppUiScalePreset.STANDARD,
            fontSizePreset = AppFontSizePreset.DEFAULT,
            dpiOverridePercent = null
        )

        assertEquals(393, snapshot.effectiveSmallestWidthDp)
        assertFalse(snapshot.isNarrowWidth)
    }

    @Test
    fun `miuix text styles keep unspecified units when scaled`() {
        val scaled = defaultTextStyles().scaled(AppFontSizePreset.LARGER.multiplier)

        assertEquals(17f * AppFontSizePreset.LARGER.multiplier, scaled.main.fontSize.value, 0.0001f)
        assertTrue(scaled.main.lineHeight.isUnspecified)
        assertTrue(scaled.main.letterSpacing.isUnspecified)
    }
}
