package com.android.purebilibili.feature.home.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import com.android.purebilibili.feature.home.HomeGlassResolvedColors
import com.android.purebilibili.core.ui.blur.BlurIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class iOSHomeHeaderVisualPolicyTest {

    @Test
    fun `top chrome uses liquid glass when liquid glass is enabled`() {
        assertEquals(
            TopTabMaterialMode.LIQUID_GLASS,
            resolveHomeTopChromeMaterialMode(
                isBottomBarBlurEnabled = true,
                isLiquidGlassEnabled = true
            )
        )
    }

    @Test
    fun `top chrome uses blur when only blur is enabled`() {
        assertEquals(
            TopTabMaterialMode.BLUR,
            resolveHomeTopChromeMaterialMode(
                isBottomBarBlurEnabled = true,
                isLiquidGlassEnabled = false
            )
        )
    }

    @Test
    fun `top chrome uses plain when blur and liquid glass are disabled`() {
        assertEquals(
            TopTabMaterialMode.PLAIN,
            resolveHomeTopChromeMaterialMode(
                isBottomBarBlurEnabled = false,
                isLiquidGlassEnabled = false
            )
        )
    }

    @Test
    fun `liquid glass top chrome prefers captured backdrop rendering`() {
        assertEquals(
            HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
            resolveHomeTopChromeRenderMode(
                materialMode = TopTabMaterialMode.LIQUID_GLASS,
                isGlassSupported = true,
                hasBackdrop = true,
                hasHazeState = true
            )
        )
    }

    @Test
    fun `liquid glass top chrome falls back to haze liquid glass when backdrop is unavailable`() {
        assertEquals(
            HomeTopChromeRenderMode.LIQUID_GLASS_HAZE,
            resolveHomeTopChromeRenderMode(
                materialMode = TopTabMaterialMode.LIQUID_GLASS,
                isGlassSupported = true,
                hasBackdrop = false,
                hasHazeState = true
            )
        )
    }

    @Test
    fun `unsupported liquid glass top chrome falls back to blur`() {
        assertEquals(
            HomeTopChromeRenderMode.BLUR,
            resolveHomeTopChromeRenderMode(
                materialMode = TopTabMaterialMode.LIQUID_GLASS,
                isGlassSupported = false,
                hasBackdrop = true,
                hasHazeState = true
            )
        )
    }

    @Test
    fun `circle top chrome shape resolves to a lens safe rounded shape`() {
        assertTrue(resolveHomeTopChromeLensShape(CircleShape) is CornerBasedShape)
    }

    @Test
    fun `rectangle top chrome shape resolves to a lens safe rounded shape`() {
        assertTrue(resolveHomeTopChromeLensShape(RectangleShape) is CornerBasedShape)
    }

    @Test
    fun `rounded top chrome shape is preserved for lens rendering`() {
        val shape = RoundedCornerShape(10)

        assertEquals(shape, resolveHomeTopChromeLensShape(shape))
    }

    @Test
    fun `liquid glass readability layer stays lighter than blur`() {
        val liquidAlpha = resolveHomeTopChromeReadabilityAlpha(HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP)
        val blurAlpha = resolveHomeTopChromeReadabilityAlpha(HomeTopChromeRenderMode.BLUR)

        assertTrue(liquidAlpha < blurAlpha)
    }

    @Test
    fun `blur readability layer stays stronger than plain`() {
        val blurAlpha = resolveHomeTopChromeReadabilityAlpha(HomeTopChromeRenderMode.BLUR)
        val plainAlpha = resolveHomeTopChromeReadabilityAlpha(HomeTopChromeRenderMode.PLAIN)

        assertTrue(blurAlpha > plainAlpha)
    }

    @Test
    fun `top search content alpha is strengthened for readability`() {
        assertTrue(
            resolveHomeTopSearchContentAlpha(HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP) > 0.72f
        )
        assertTrue(
            resolveHomeTopSearchContentAlpha(HomeTopChromeRenderMode.BLUR) >
                resolveHomeTopSearchContentAlpha(HomeTopChromeRenderMode.PLAIN)
        )
    }

    @Test
    fun `top action icon alpha is strengthened for readability`() {
        assertTrue(
            resolveHomeTopActionIconAlpha(HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP) > 0.7f
        )
    }

    @Test
    fun `top tab content underlay stays lighter than main readability layer`() {
        val underlay = resolveHomeTopTabContentUnderlayAlpha(HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP)
        val readability = resolveHomeTopChromeReadabilityAlpha(HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP)

        assertTrue(underlay < readability)
        assertTrue(underlay > 0f)
    }

    @Test
    fun `top tab unselected alpha is strengthened for readability`() {
        assertTrue(resolveTopTabUnselectedAlpha() > 0.65f)
    }

    @Test
    fun `light mode top search content uses black like bottom bar`() {
        assertEquals(
            Color.Black,
            resolveHomeTopForegroundColor(isLightMode = true)
        )
    }

    @Test
    fun `light mode top action icons use black like bottom bar`() {
        assertEquals(
            Color.Black,
            resolveHomeTopForegroundColor(isLightMode = true)
        )
    }

    @Test
    fun `light mode top tab unselected color uses black like bottom bar`() {
        assertEquals(
            Color.Black,
            resolveTopTabUnselectedColor(isLightMode = true)
        )
    }

    @Test
    fun `dark mode top foreground uses bright text`() {
        assertEquals(
            Color.White.copy(alpha = 0.92f),
            resolveHomeTopForegroundColor(isLightMode = false)
        )
    }

    @Test
    fun `dark mode top tab underlay uses dark tint instead of white`() {
        val underlay = resolveHomeTopInnerUnderlayColor(
            isLightMode = false,
            renderMode = HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP
        )

        assertNotEquals(Color.White, underlay)
        assertTrue(underlay.red < 0.2f && underlay.green < 0.2f && underlay.blue < 0.2f)
        assertTrue(underlay.alpha > 0f)
    }

    @Test
    fun `dark mode top glass accents are dimmed`() {
        val base = HomeGlassResolvedColors(
            containerColor = Color.White.copy(alpha = 0.28f),
            borderColor = Color.White.copy(alpha = 0.18f),
            highlightColor = Color.White.copy(alpha = 0.20f)
        )

        val tuned = tuneHomeTopGlassColors(
            colors = base,
            isLightMode = false,
            emphasized = true
        )

        assertTrue(tuned.borderColor.alpha < base.borderColor.alpha)
        assertTrue(tuned.highlightColor.alpha < base.highlightColor.alpha)
    }

    @Test
    fun `liquid glass header uses same base alpha as bottom bar`() {
        val alpha = resolveHomeHeaderSurfaceAlpha(
            isGlassEnabled = true,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THIN
        )

        assertEquals(0.10f, alpha, 0.0001f)
    }

    @Test
    fun `blur disabled header falls back to opaque`() {
        val alpha = resolveHomeHeaderSurfaceAlpha(
            isGlassEnabled = true,
            blurEnabled = false,
            blurIntensity = BlurIntensity.THIN
        )

        assertEquals(1f, alpha, 0.0001f)
    }

    @Test
    fun `non-glass header keeps tuned blur-based alpha`() {
        val alpha = resolveHomeHeaderSurfaceAlpha(
            isGlassEnabled = false,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THICK
        )
        val bottomBarAlpha = resolveBottomBarSurfaceColor(
            surfaceColor = Color.White,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THICK
        ).alpha

        assertEquals(bottomBarAlpha, alpha, 0.0001f)
    }

    @Test
    fun `docked blur top tabs use same overlay alpha as blur chrome container`() {
        assertEquals(
            0.4f,
            resolveHomeTopTabOverlayAlpha(
                materialMode = TopTabMaterialMode.BLUR,
                isTabFloating = false,
                containerAlpha = 0.4f
            ),
            0.0001f
        )
    }

    @Test
    fun `top tab secondary blur enabled only in static state`() {
        assertTrue(
            shouldEnableTopTabSecondaryBlur(
                hasHeaderBlur = true,
                topTabMaterialMode = TopTabMaterialMode.BLUR,
                isScrolling = false,
                isTransitionRunning = false
            )
        )
    }

    @Test
    fun `liquid glass top tab secondary blur disabled during motion to reduce duplicate blur passes`() {
        assertFalse(
            shouldEnableTopTabSecondaryBlur(
                hasHeaderBlur = true,
                topTabMaterialMode = TopTabMaterialMode.LIQUID_GLASS,
                isScrolling = true,
                isTransitionRunning = false
            )
        )
        assertFalse(
            shouldEnableTopTabSecondaryBlur(
                hasHeaderBlur = true,
                topTabMaterialMode = TopTabMaterialMode.LIQUID_GLASS,
                isScrolling = false,
                isTransitionRunning = true
            )
        )
    }

    @Test
    fun `blur mode keeps top tab secondary blur during motion`() {
        assertTrue(
            shouldEnableTopTabSecondaryBlur(
                hasHeaderBlur = true,
                topTabMaterialMode = TopTabMaterialMode.BLUR,
                isScrolling = true,
                isTransitionRunning = false
            )
        )
        assertTrue(
            shouldEnableTopTabSecondaryBlur(
                hasHeaderBlur = true,
                topTabMaterialMode = TopTabMaterialMode.BLUR,
                isScrolling = false,
                isTransitionRunning = true
            )
        )
    }

    @Test
    fun `floating top tabs no longer use highlighted border`() {
        assertEquals(
            0f,
            resolveHomeHeaderTabBorderAlpha(
                isTabFloating = true,
                isTabGlassEnabled = true
            ),
            0.0001f
        )
        assertEquals(
            0f,
            resolveHomeHeaderTabBorderAlpha(
                isTabFloating = true,
                isTabGlassEnabled = false
            ),
            0.0001f
        )
    }
}
