package com.android.purebilibili.feature.home.components

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.ui.blur.BlurIntensity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomBarSurfaceColorPolicyTest {

    @Test
    fun `blur enabled follows blur style alpha`() {
        val color = resolveBottomBarSurfaceColor(
            surfaceColor = Color.White,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THIN
        )

        assertEquals(0.4f, color.alpha, 0.001f)
    }

    @Test
    fun `blur disabled keeps light theme surface color`() {
        val color = resolveBottomBarSurfaceColor(
            surfaceColor = Color.White,
            blurEnabled = false,
            blurIntensity = BlurIntensity.THIN
        )

        assertEquals(Color.White, color)
    }

    @Test
    fun `blur disabled keeps dark theme surface color`() {
        val color = resolveBottomBarSurfaceColor(
            surfaceColor = Color(0xFF121212),
            blurEnabled = false,
            blurIntensity = BlurIntensity.THIN
        )

        assertEquals(Color(0xFF121212), color)
    }

    @Test
    fun `frosted bottom bar shell keeps blur visibility when glass effect is enabled`() {
        val color = resolveBottomBarContainerColor(
            surfaceColor = Color.White,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THIN,
            liquidGlassProgress = 1f,
            isGlassEffectEnabled = true
        )

        assertTrue(color.alpha >= 0.36f)
    }

    @Test
    fun `clear bottom bar shell stays lighter than frosted shell`() {
        val clear = resolveBottomBarContainerColor(
            surfaceColor = Color.White,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THIN,
            liquidGlassProgress = 0f,
            isGlassEffectEnabled = true
        )
        val frosted = resolveBottomBarContainerColor(
            surfaceColor = Color.White,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THIN,
            liquidGlassProgress = 1f,
            isGlassEffectEnabled = true
        )

        assertTrue(clear.alpha < frosted.alpha)
    }

    @Test
    fun `android native floating shell is opaque when blur is disabled`() {
        val tuning = resolveAndroidNativeBottomBarTuning(
            blurEnabled = false,
            darkTheme = false
        )

        assertEquals(1f, tuning.shellSurfaceAlpha, 0.001f)
    }

    @Test
    fun `android native floating shell is translucent when ordinary blur is enabled`() {
        val tuning = resolveAndroidNativeBottomBarTuning(
            blurEnabled = true,
            darkTheme = false
        )
        val color = resolveAndroidNativeFloatingBottomBarContainerColor(
            surfaceColor = Color.White,
            tuning = tuning,
            glassEnabled = false,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THIN
        )

        assertEquals(0.4f, tuning.shellSurfaceAlpha, 0.001f)
        assertEquals(0.4f, color.alpha, 0.001f)
    }

    @Test
    fun `android native floating shell follows ordinary blur intensity when glass is off`() {
        val tuning = resolveAndroidNativeBottomBarTuning(
            blurEnabled = true,
            darkTheme = false
        )
        val color = resolveAndroidNativeFloatingBottomBarContainerColor(
            surfaceColor = Color.White,
            tuning = tuning,
            glassEnabled = false,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THICK
        )

        assertEquals(0.6f, color.alpha, 0.001f)
    }

    @Test
    fun `android native glass stays enabled when liquid glass is on even if blur toggle is off`() {
        assertTrue(
            resolveAndroidNativeBottomBarGlassEnabled(
                liquidGlassEnabled = true,
                blurEnabled = false
            )
        )
        assertFalse(
            resolveAndroidNativeBottomBarGlassEnabled(
                liquidGlassEnabled = false,
                blurEnabled = false
            )
        )
    }

    @Test
    fun `android native blur disables liquid glass surface treatment`() {
        assertFalse(
            resolveAndroidNativeBottomBarGlassEnabled(
                liquidGlassEnabled = false,
                blurEnabled = true
            )
        )
        assertFalse(
            resolveAndroidNativeBottomBarGlassEnabled(
                liquidGlassEnabled = true,
                blurEnabled = true
            )
        )
    }

    @Test
    fun `android native floating blur uses haze when available`() {
        assertTrue(
            shouldUseAndroidNativeFloatingHazeBlur(
                blurEnabled = true,
                glassEnabled = false,
                hasHazeState = true
            )
        )
        assertFalse(
            shouldUseAndroidNativeFloatingHazeBlur(
                blurEnabled = true,
                glassEnabled = true,
                hasHazeState = true
            )
        )
    }

    @Test
    fun `android native indicator keeps capsule static when idle`() {
        val spec = resolveAndroidNativeIndicatorSpec(isMoving = false)

        assertFalse(spec.usesLens)
        assertFalse(spec.captureTintedContentLayer)
    }

    @Test
    fun `android native indicator enables lens and tinted export while moving`() {
        val spec = resolveAndroidNativeIndicatorSpec(isMoving = true)

        assertTrue(spec.usesLens)
        assertTrue(spec.captureTintedContentLayer)
    }

    @Test
    fun `android native indicator color softens primary tint in light theme`() {
        val color = resolveAndroidNativeIndicatorColor(
            themeColor = Color(0xFF4F7CFF),
            darkTheme = false
        )

        assertTrue(color.alpha > 0.7f)
        assertTrue(color.red > 0.7f)
        assertTrue(color.green > 0.8f)
    }

    @Test
    fun `android native export layer tint keeps theme hue in dark theme`() {
        val color = resolveAndroidNativeExportTintColor(
            themeColor = Color(0xFF4F7CFF),
            darkTheme = true
        )

        assertTrue(color.alpha > 0.2f)
        assertTrue(color.blue >= color.red)
    }

    @Test
    fun `moving floating bottom bar staggers shell and indicator refraction offsets`() {
        val profile = resolveBottomBarRefractionMotionProfile(
            position = 1.35f,
            velocity = 900f,
            isDragging = true
        )

        assertTrue(profile.progress > 0f)
        assertTrue(profile.exportPanelOffsetFraction > 0f)
        assertTrue(profile.indicatorPanelOffsetFraction > 0f)
        assertTrue(profile.visiblePanelOffsetFraction > 0f)
        assertTrue(profile.exportPanelOffsetFraction < profile.indicatorPanelOffsetFraction)
        assertTrue(profile.visiblePanelOffsetFraction < profile.indicatorPanelOffsetFraction)
        assertTrue(profile.visibleSelectionEmphasis < 1f)
        assertTrue(profile.exportSelectionEmphasis < 1f)
        assertTrue(profile.forceChromaticAberration)
        assertTrue(profile.indicatorLensAmountScale > 1f)
        assertTrue(profile.indicatorLensHeightScale > 1f)
    }

    @Test
    fun `idle floating bottom bar refraction profile stays neutral`() {
        val profile = resolveBottomBarRefractionMotionProfile(
            position = 2f,
            velocity = 0f,
            isDragging = false
        )

        assertEquals(0f, profile.progress, 0.001f)
        assertEquals(0f, profile.exportPanelOffsetFraction, 0.001f)
        assertEquals(0f, profile.indicatorPanelOffsetFraction, 0.001f)
        assertEquals(0f, profile.visiblePanelOffsetFraction, 0.001f)
        assertEquals(1f, profile.visibleSelectionEmphasis, 0.001f)
        assertEquals(1f, profile.exportSelectionEmphasis, 0.001f)
    }

    @Test
    fun `moving floating indicator uses combined backdrop when tinted layer is exported`() {
        val policy = resolveBottomBarRefractionLayerPolicy(
            isFloating = true,
            isLiquidGlassEnabled = true,
            indicatorVisualPolicy = BottomBarIndicatorVisualPolicy(
                isInMotion = true,
                shouldRefract = true,
                useNeutralTint = false
            )
        )

        assertTrue(policy.captureTintedContentLayer)
        assertTrue(policy.useCombinedBackdrop)
    }

    @Test
    fun `idle floating indicator does not export tinted layer or combined backdrop`() {
        val policy = resolveBottomBarRefractionLayerPolicy(
            isFloating = true,
            isLiquidGlassEnabled = true,
            indicatorVisualPolicy = BottomBarIndicatorVisualPolicy(
                isInMotion = false,
                shouldRefract = false,
                useNeutralTint = false
            )
        )

        assertFalse(policy.captureTintedContentLayer)
        assertFalse(policy.useCombinedBackdrop)
    }
}
