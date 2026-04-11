package com.android.purebilibili.feature.home.components

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.theme.AndroidNativeVariant
import com.android.purebilibili.core.theme.UiPreset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopTabStylePolicyTest {

    @Test
    fun `floating plus liquid uses liquid glass`() {
        val state = resolveTopTabStyle(
            isBottomBarFloating = true,
            isBottomBarBlurEnabled = true,
            isLiquidGlassEnabled = true
        )

        assertEquals(true, state.floating)
        assertEquals(TopTabMaterialMode.LIQUID_GLASS, state.materialMode)
    }

    @Test
    fun `floating without liquid but blur enabled uses blur`() {
        val state = resolveTopTabStyle(
            isBottomBarFloating = true,
            isBottomBarBlurEnabled = true,
            isLiquidGlassEnabled = false
        )

        assertEquals(true, state.floating)
        assertEquals(TopTabMaterialMode.BLUR, state.materialMode)
    }

    @Test
    fun `floating without blur and liquid uses plain`() {
        val state = resolveTopTabStyle(
            isBottomBarFloating = true,
            isBottomBarBlurEnabled = false,
            isLiquidGlassEnabled = false
        )

        assertEquals(true, state.floating)
        assertEquals(TopTabMaterialMode.PLAIN, state.materialMode)
    }

    @Test
    fun `docked with blur uses blur`() {
        val state = resolveTopTabStyle(
            isBottomBarFloating = false,
            isBottomBarBlurEnabled = true,
            isLiquidGlassEnabled = false
        )

        assertEquals(false, state.floating)
        assertEquals(TopTabMaterialMode.BLUR, state.materialMode)
    }

    @Test
    fun `docked with liquid downgrades to blur when blur enabled`() {
        val state = resolveTopTabStyle(
            isBottomBarFloating = false,
            isBottomBarBlurEnabled = true,
            isLiquidGlassEnabled = true
        )

        assertEquals(false, state.floating)
        assertEquals(TopTabMaterialMode.BLUR, state.materialMode)
    }

    @Test
    fun `docked without blur uses plain`() {
        val state = resolveTopTabStyle(
            isBottomBarFloating = false,
            isBottomBarBlurEnabled = false,
            isLiquidGlassEnabled = true
        )

        assertEquals(false, state.floating)
        assertEquals(TopTabMaterialMode.PLAIN, state.materialMode)
    }

    @Test
    fun `reduced interaction budget keeps home header tab material mode`() {
        assertEquals(
            TopTabMaterialMode.LIQUID_GLASS,
            resolveEffectiveHomeHeaderTabMaterialMode(
                materialMode = TopTabMaterialMode.LIQUID_GLASS,
                interactionBudget = HomeInteractionMotionBudget.REDUCED
            )
        )
        assertEquals(
            TopTabMaterialMode.BLUR,
            resolveEffectiveHomeHeaderTabMaterialMode(
                materialMode = TopTabMaterialMode.BLUR,
                interactionBudget = HomeInteractionMotionBudget.REDUCED
            )
        )
    }

    @Test
    fun `reduced interaction budget keeps top tab liquid glass enabled`() {
        assertTrue(
            resolveEffectiveTopTabLiquidGlassEnabled(
                isLiquidGlassEnabled = true,
                interactionBudget = HomeInteractionMotionBudget.REDUCED
            )
        )
    }

    @Test
    fun `ios top tab tuning uses bottom bar sized indicator footprint`() {
        val tuning = resolveTopTabVisualTuning(UiPreset.IOS)

        assertEquals(44f, tuning.nonFloatingIndicatorHeightDp, 0.001f)
        assertEquals(22f, tuning.nonFloatingIndicatorCornerDp, 0.001f)
        assertEquals(1.34f, tuning.nonFloatingIndicatorWidthRatio, 0.001f)
        assertEquals(90f, tuning.nonFloatingIndicatorMinWidthDp, 0.001f)
        assertEquals(0f, tuning.nonFloatingIndicatorHorizontalInsetDp, 0.001f)
        assertEquals(46f, tuning.floatingIndicatorHeightDp, 0.001f)
    }

    @Test
    fun `ios top tab keeps icon plus text scale stable inside large capsule`() {
        assertEquals(
            1f,
            resolveTopTabContentScale(
                selectionFraction = 1f,
                showIcon = true,
                showText = true,
                uiPreset = UiPreset.IOS
            ),
            0.001f
        )
        assertEquals(
            1.03f,
            resolveTopTabContentScale(
                selectionFraction = 1f,
                showIcon = true,
                showText = false,
                uiPreset = UiPreset.IOS
            ),
            0.001f
        )
    }

    @Test
    fun `md3 top tabs keep material typography spacing`() {
        val textSize = resolveTopTabLabelTextSizeSp(labelMode = 0)
        val lineHeight = resolveTopTabLabelLineHeightSp(labelMode = 0)

        assertEquals(13f, textSize, 0.001f)
        assertEquals(18f, lineHeight, 0.001f)
        assertTrue(lineHeight >= textSize)
    }

    @Test
    fun `md3 top tabs should use compact text first underline sizing`() {
        val spec = resolveMd3TopTabVisualSpec(isFloatingStyle = false)

        assertEquals(44.dp, spec.rowHeight)
        assertEquals(2.dp, spec.selectedCapsuleHeight)
        assertEquals(1.dp, spec.selectedCapsuleCornerRadius)
        assertEquals(16.dp, spec.iconSize)
        assertEquals(13.sp, spec.labelTextSize)
        assertEquals(18.sp, spec.labelLineHeight)
        assertEquals(0.dp, spec.iconLabelSpacing)
        assertEquals(12.dp, spec.itemHorizontalPadding)
        assertEquals(0.dp, spec.selectedCapsuleShadowElevation)
        assertEquals(0.dp, spec.selectedCapsuleTonalElevation)
    }

    @Test
    fun `md3 icon plus text top tabs reserve enough height`() {
        val spec = resolveMd3TopTabVisualSpec(
            isFloatingStyle = false,
            labelMode = 0
        )

        assertEquals(52.dp, spec.rowHeight)
        assertEquals(8.dp, spec.itemHorizontalPadding)
        assertEquals(1.dp, spec.iconLabelSpacing)
        assertTrue(spec.labelLineHeight >= spec.labelTextSize)
    }

    @Test
    fun `android native miuix top tabs should promote capsule selection styling`() {
        val spec = resolveMd3TopTabVisualSpec(
            isFloatingStyle = false,
            androidNativeVariant = AndroidNativeVariant.MIUIX
        )

        assertEquals(44.dp, spec.rowHeight)
        assertEquals(30.dp, spec.selectedCapsuleHeight)
        assertEquals(15.dp, spec.selectedCapsuleCornerRadius)
        assertEquals(12.dp, spec.itemHorizontalPadding)
        assertEquals(1.dp, spec.iconLabelSpacing)
        assertEquals(14.sp, spec.labelTextSize)
    }

    @Test
    fun `android native miuix text tabs should use native miuix row while icon modes stay shared`() {
        assertTrue(
            shouldUseNativeMiuixTopTabRow(
                androidNativeVariant = AndroidNativeVariant.MIUIX,
                labelMode = 2
            )
        )
        assertEquals(
            false,
            shouldUseNativeMiuixTopTabRow(
                androidNativeVariant = AndroidNativeVariant.MIUIX,
                labelMode = 0
            )
        )
        assertFalse(
            shouldUseNativeMiuixTopTabRow(
                androidNativeVariant = AndroidNativeVariant.MIUIX,
                labelMode = 1
            )
        )
    }

    @Test
    fun `md3 selected top tab should reuse material primary emphasis`() {
        val colorScheme = lightColorScheme(
            surface = Color.White,
            primary = Color(0xFF2D6A4F),
            secondaryContainer = Color(0xFFDCEFD8),
            onSecondaryContainer = Color(0xFF1A1C18),
            onSurface = Color(0xFF1B1C1F),
            onSurfaceVariant = Color(0xFF6A5E61)
        )

        assertEquals(colorScheme.primary, resolveMd3TopTabSelectedContainerColor(colorScheme))
        assertEquals(colorScheme.primary, resolveMd3TopTabSelectedIconColor(colorScheme))
        assertEquals(colorScheme.primary, resolveMd3TopTabSelectedLabelColor(colorScheme))
        assertEquals(colorScheme.onSurfaceVariant, resolveMd3TopTabUnselectedIconColor(colorScheme))
        assertEquals(colorScheme.onSurfaceVariant, resolveMd3TopTabUnselectedLabelColor(colorScheme))
    }

    @Test
    fun `android native miuix top tabs should use miuix secondary container emphasis`() {
        val colorScheme = lightColorScheme(
            primary = Color(0xFF2D6A4F),
            surfaceContainerHigh = Color(0xFFF4ECE1),
            secondaryContainer = Color(0xFFDCEFD8),
            onSecondaryContainer = Color(0xFF1A1C18),
            onSurface = Color(0xFF1E1B16),
            onSurfaceVariant = Color(0xFF6A5E61)
        )

        assertEquals(
            colorScheme.secondaryContainer,
            resolveMd3TopTabSelectedContainerColor(
                colorScheme = colorScheme,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
        assertEquals(
            colorScheme.onSecondaryContainer,
            resolveMd3TopTabSelectedIconColor(
                colorScheme = colorScheme,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
        assertEquals(
            colorScheme.onSecondaryContainer,
            resolveMd3TopTabSelectedLabelColor(
                colorScheme = colorScheme,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
    }

    @Test
    fun `md3 preset uses material tab indicator style`() {
        assertEquals(
            TopTabIndicatorStyle.MATERIAL,
            resolveTopTabIndicatorStyle(UiPreset.MD3)
        )
        assertEquals(
            TopTabIndicatorStyle.CAPSULE,
            resolveTopTabIndicatorStyle(UiPreset.IOS)
        )
    }

    @Test
    fun `md3 top tabs use underline row semantics and tighter action shape`() {
        assertEquals(
            "UNDERLINE_FIXED",
            resolveMd3TopTabRowVariant().name
        )
        assertEquals(16.dp, resolveMd3TopTabActionButtonCorner(isFloatingStyle = true))
        assertEquals(12.dp, resolveMd3TopTabActionButtonCorner(isFloatingStyle = false))
        assertEquals(44.dp, resolveMd3TopTabActionButtonSize(isFloatingStyle = true))
        assertEquals(36.dp, resolveMd3TopTabActionButtonSize(isFloatingStyle = false))
        assertEquals(20.dp, resolveMd3TopTabActionIconSize(isFloatingStyle = true))
        assertEquals(18.dp, resolveMd3TopTabActionIconSize(isFloatingStyle = false))
        assertEquals(4.dp, resolveMd3TopTabActionContentBottomPadding())
    }

    @Test
    fun `android native miuix top tabs should slightly enlarge action button chrome`() {
        assertEquals(
            18.dp,
            resolveMd3TopTabActionButtonCorner(
                isFloatingStyle = true,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
        assertEquals(
            14.dp,
            resolveMd3TopTabActionButtonCorner(
                isFloatingStyle = false,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
        assertEquals(
            46.dp,
            resolveMd3TopTabActionButtonSize(
                isFloatingStyle = true,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
        assertEquals(
            38.dp,
            resolveMd3TopTabActionButtonSize(
                isFloatingStyle = false,
                androidNativeVariant = AndroidNativeVariant.MIUIX
            )
        )
    }
}
