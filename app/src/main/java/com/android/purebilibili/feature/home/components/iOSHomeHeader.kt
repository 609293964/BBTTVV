// 文件路径: feature/home/components/iOSHomeHeader.kt
package com.android.purebilibili.feature.home.components

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance  //  状态栏亮度计算
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.iOSTapEffect
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.feature.home.UserState
import com.android.purebilibili.core.theme.iOSSystemGray
import com.android.purebilibili.core.store.LiquidGlassStyle
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.ui.blur.shouldAllowDirectHazeLiquidGlassFallback
import com.android.purebilibili.core.ui.blur.shouldAllowHomeChromeLiquidGlass
import com.android.purebilibili.core.ui.blur.resolveUnifiedBlurredEdgeTreatment
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.BlurStyles
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.rememberAppSettingsIcon
import com.android.purebilibili.core.store.HomeHeaderBlurMode
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.feature.home.resolveHomeTopCategories
import com.android.purebilibili.feature.home.HomeGlassResolvedColors
import com.android.purebilibili.feature.home.rememberHomeGlassChromeColors
import com.android.purebilibili.feature.home.rememberHomeGlassPillColors
import com.android.purebilibili.feature.home.resolveHomeGlassChromeStyle
import com.android.purebilibili.feature.home.resolveHomeGlassPillStyle
import com.android.purebilibili.core.store.resolveHomeHeaderBlurEnabled
import com.android.purebilibili.core.theme.AndroidNativeVariant
import com.android.purebilibili.core.theme.LocalAndroidNativeVariant
import com.android.purebilibili.core.theme.LocalUiPreset
import com.android.purebilibili.core.theme.UiPreset
import com.android.purebilibili.navigation.resolveAppNavigationAppearance

private const val HOME_HEADER_LIQUID_GLASS_ALPHA = 0.10f

internal data class HomeTopChromeMotionPolicy(
    val isScrolling: Boolean,
    val isTransitionRunning: Boolean
)

internal data class HomeTopSearchRefractionLayerPolicy(
    val captureContentLayer: Boolean,
    val useExportedBackdrop: Boolean,
    val overlayAlpha: Float,
    val visibleContentAlpha: Float,
    val exportTranslationMultiplier: Float
)

internal data class HomeTopLinkedBottomBarAppearance(
    val isFloating: Boolean,
    val blurEnabled: Boolean,
    val liquidGlassEnabled: Boolean
)

internal enum class HomeTopChromeRenderMode {
    PLAIN,
    BLUR,
    LIQUID_GLASS_HAZE,
    LIQUID_GLASS_BACKDROP
}

internal enum class HomeTopChromeSurfaceTreatment {
    STRUCTURED_GLASS,
    FLAT_GLASS
}

internal fun resolveHomeTopLinkedBottomBarAppearance(
    homeSettings: HomeSettings?,
    uiPreset: UiPreset,
    androidNativeVariant: AndroidNativeVariant
): HomeTopLinkedBottomBarAppearance {
    val resolvedHomeSettings = homeSettings ?: HomeSettings()
    val navigationAppearance = resolveAppNavigationAppearance(
        homeSettings = resolvedHomeSettings,
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant
    )
    return HomeTopLinkedBottomBarAppearance(
        isFloating = navigationAppearance.bottomBarFloating,
        blurEnabled = navigationAppearance.bottomBarBlurEnabled,
        // Top chrome has its own liquid-glass toggle now, so only reuse the
        // bottom shell posture here and keep material choice local to the top bar.
        liquidGlassEnabled = false
    )
}

internal fun resolveHomeTopChromeMaterialMode(
    isHeaderBlurEnabled: Boolean,
    isBottomBarBlurEnabled: Boolean,
    isLiquidGlassEnabled: Boolean,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): TopTabMaterialMode {
    if (androidNativeVariant == AndroidNativeVariant.MIUIX && (isHeaderBlurEnabled || isBottomBarBlurEnabled)) {
        return TopTabMaterialMode.BLUR
    }
    return when {
        isLiquidGlassEnabled -> TopTabMaterialMode.LIQUID_GLASS
        !isHeaderBlurEnabled && !isBottomBarBlurEnabled -> TopTabMaterialMode.PLAIN
        else -> TopTabMaterialMode.BLUR
    }
}

internal fun resolveHomeTopChromeRenderMode(
    materialMode: TopTabMaterialMode,
    isGlassSupported: Boolean,
    hasBackdrop: Boolean,
    hasHazeState: Boolean,
    allowHazeLiquidGlassFallback: Boolean = true
): HomeTopChromeRenderMode {
    return when (materialMode) {
        TopTabMaterialMode.PLAIN -> HomeTopChromeRenderMode.PLAIN
        TopTabMaterialMode.BLUR -> HomeTopChromeRenderMode.BLUR
        TopTabMaterialMode.LIQUID_GLASS -> when {
            isGlassSupported && hasBackdrop -> HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP
            isGlassSupported && hasHazeState && allowHazeLiquidGlassFallback ->
                HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
            hasHazeState -> HomeTopChromeRenderMode.BLUR
            else -> HomeTopChromeRenderMode.PLAIN
        }
    }
}

internal fun resolveHomeTopSearchRefractionLayerPolicy(
    renderMode: HomeTopChromeRenderMode,
    hasBackdrop: Boolean,
    searchRevealFraction: Float,
    isScrolling: Boolean,
    isTransitionRunning: Boolean
): HomeTopSearchRefractionLayerPolicy {
    return HomeTopSearchRefractionLayerPolicy(
        // Keep the search pill on a single stable glass layer.
        // The extra exported refraction overlay was causing visible flicker
        // when vertical scrolling started or stopped.
        captureContentLayer = false,
        useExportedBackdrop = false,
        overlayAlpha = 0f,
        visibleContentAlpha = 1f,
        exportTranslationMultiplier = 0f
    )
}

internal fun resolveHomeTopChromeSurfaceTreatment(
    renderMode: HomeTopChromeRenderMode,
    preferFlatGlass: Boolean
): HomeTopChromeSurfaceTreatment {
    if (!preferFlatGlass) return HomeTopChromeSurfaceTreatment.STRUCTURED_GLASS
    return when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> HomeTopChromeSurfaceTreatment.FLAT_GLASS
        HomeTopChromeRenderMode.BLUR,
        HomeTopChromeRenderMode.PLAIN -> HomeTopChromeSurfaceTreatment.STRUCTURED_GLASS
    }
}

internal fun resolveHomeHeaderSurfaceAlpha(
    isGlassEnabled: Boolean,
    blurEnabled: Boolean,
    blurIntensity: BlurIntensity
): Float {
    if (!blurEnabled) return 1f
    if (isGlassEnabled) return HOME_HEADER_LIQUID_GLASS_ALPHA
    return BlurStyles.getBackgroundAlpha(blurIntensity)
}

internal fun resolveHomeTopBlurContainerAlpha(
    blurIntensity: BlurIntensity
): Float = BlurStyles.getBackgroundAlpha(blurIntensity)

internal fun resolveHomeTopTabOverlayAlpha(
    materialMode: TopTabMaterialMode,
    isTabFloating: Boolean,
    containerAlpha: Float
): Float {
    return when (materialMode) {
        TopTabMaterialMode.PLAIN -> if (isTabFloating) containerAlpha else 1f
        TopTabMaterialMode.BLUR -> containerAlpha
        TopTabMaterialMode.LIQUID_GLASS -> containerAlpha
    }
}

internal fun resolveHomeTopTabVerticalPaddingDp(isTabFloating: Boolean): Float {
    return if (isTabFloating) 2f else 0f
}

internal fun resolveHomeTopTabYOffsetDp(isTabFloating: Boolean): Float {
    return if (isTabFloating) (-4f) else 0f
}

internal fun resolveHomeTopSearchBarHeight(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        50.dp
    } else if (uiPreset == UiPreset.MD3) {
        52.dp
    } else {
        48.dp
    }
}

internal data class HomeHeaderScrollLayout(
    val searchBarHeightPx: Float,
    val searchAlpha: Float,
    val tabRowHeightPx: Float,
    val tabAlpha: Float
)

internal fun resolveHomeTopSearchRevealDeadZone(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return if (uiPreset == UiPreset.MD3) {
        0.dp
    } else {
        8.dp
    }
}

internal fun resolveHomeTopVisibleSearchHeightPx(
    rawSearchHeightPx: Float,
    fullSearchHeightPx: Float,
    revealDeadZonePx: Float
): Float {
    if (fullSearchHeightPx <= 0f) return 0f
    val clampedRawHeight = rawSearchHeightPx.coerceIn(0f, fullSearchHeightPx)
    val clampedDeadZone = revealDeadZonePx.coerceIn(0f, fullSearchHeightPx - 0.5f)
    if (clampedDeadZone <= 0f) return clampedRawHeight
    if (clampedRawHeight <= clampedDeadZone) return 0f
    val normalizedFraction = (clampedRawHeight - clampedDeadZone) / (fullSearchHeightPx - clampedDeadZone)
    return (normalizedFraction * fullSearchHeightPx).coerceIn(0f, fullSearchHeightPx)
}

internal fun usesImmediateHomeTopSearchReveal(
    revealDeadZonePx: Float
): Boolean = revealDeadZonePx <= 0.01f

internal fun resolveHomeTopSearchContentRevealFraction(
    searchRevealFraction: Float,
    usesImmediateReveal: Boolean
): Float {
    val clampedFraction = searchRevealFraction.coerceIn(0f, 1f)
    if (!usesImmediateReveal) return clampedFraction
    return (clampedFraction * (0.72f + 0.28f * clampedFraction)).coerceIn(0f, 1f)
}

internal fun resolveHomeTopSearchContentTranslationYPx(
    searchRevealFraction: Float,
    searchBarHeightPx: Float,
    usesImmediateReveal: Boolean
): Float {
    if (!usesImmediateReveal || searchBarHeightPx <= 0f) return 0f
    val clampedFraction = searchRevealFraction.coerceIn(0f, 1f)
    val maxShiftPx = minOf(searchBarHeightPx * 0.18f, 10f)
    return -maxShiftPx * (1f - clampedFraction)
}

internal fun resolveHomeHeaderScrollLayout(
    headerOffsetPx: Float,
    searchBarHeightPx: Float,
    searchCollapseDistancePx: Float,
    tabRowHeightPx: Float,
    isHeaderCollapseEnabled: Boolean,
    searchRevealDeadZonePx: Float = 0f,
    usesImmediateSearchReveal: Boolean = false
): HomeHeaderScrollLayout {
    if (!isHeaderCollapseEnabled) {
        return HomeHeaderScrollLayout(
            searchBarHeightPx = searchBarHeightPx,
            searchAlpha = 1f,
            tabRowHeightPx = tabRowHeightPx,
            tabAlpha = 1f
        )
    }
    val effectiveCollapseDistancePx = searchCollapseDistancePx.coerceAtLeast(searchBarHeightPx)
    val clampedOffsetPx = headerOffsetPx.coerceIn(-effectiveCollapseDistancePx, 0f)
    val currentSearchHeightPx = resolveHomeTopVisibleSearchHeightPx(
        rawSearchHeightPx = searchBarHeightPx + clampedOffsetPx,
        fullSearchHeightPx = searchBarHeightPx,
        revealDeadZonePx = searchRevealDeadZonePx
    )
    val rawSearchRevealFraction = if (searchBarHeightPx > 0f) {
        (currentSearchHeightPx / searchBarHeightPx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val searchAlpha = resolveHomeTopSearchContentRevealFraction(
        searchRevealFraction = rawSearchRevealFraction,
        usesImmediateReveal = usesImmediateSearchReveal
    )
    return HomeHeaderScrollLayout(
        searchBarHeightPx = currentSearchHeightPx,
        searchAlpha = searchAlpha,
        tabRowHeightPx = tabRowHeightPx,
        tabAlpha = 1f
    )
}

internal fun resolveHomeTopTabRowHeight(
    isTabFloating: Boolean,
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3,
    labelMode: Int = com.android.purebilibili.core.store.SettingsManager.TopTabLabelMode.TEXT_ONLY
): Dp {
    val showIconAndText = normalizeTopTabLabelMode(labelMode) ==
        com.android.purebilibili.core.store.SettingsManager.TopTabLabelMode.ICON_AND_TEXT
    if (uiPreset == UiPreset.MD3) {
        if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
            return if (showIconAndText) {
                if (isTabFloating) 54.dp else 50.dp
            } else {
                if (isTabFloating) 50.dp else 46.dp
            }
        }
        return if (showIconAndText) {
            if (isTabFloating) 56.dp else 52.dp
        } else {
            if (isTabFloating) 48.dp else 44.dp
        }
    }
    return if (isTabFloating) 56.dp else 46.dp
}

internal fun resolveHomeTopSearchRowHorizontalPadding(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        14.dp
    } else if (uiPreset == UiPreset.MD3) {
        16.dp
    } else {
        14.dp
    }
}

internal fun resolveHomeTopSearchPillHeight(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        46.dp
    } else if (uiPreset == UiPreset.MD3) {
        48.dp
    } else {
        34.dp
    }
}

internal fun resolveHomeTopSearchContentHorizontalPadding(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        14.dp
    } else if (uiPreset == UiPreset.MD3) {
        16.dp
    } else {
        12.dp
    }
}

internal fun resolveHomeTopSearchIconTextGap(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        8.dp
    } else if (uiPreset == UiPreset.MD3) {
        10.dp
    } else {
        8.dp
    }
}

internal fun resolveHomeTopSearchContainerShape(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Shape {
    return if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        RoundedCornerShape(22.dp)
    } else if (uiPreset == UiPreset.MD3) {
        RoundedCornerShape(24.dp)
    } else {
        RoundedCornerShape(18.dp)
    }
}

internal fun resolveHomeTopEdgeButtonShape(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Shape {
    return if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        RoundedCornerShape(18.dp)
    } else if (uiPreset == UiPreset.MD3) {
        RoundedCornerShape(16.dp)
    } else {
        CircleShape
    }
}

internal fun resolveHomeTopAvatarOuterSize(): Dp = 40.dp

internal fun resolveHomeTopAvatarInnerSize(): Dp = resolveHomeTopSettingsButtonSize()

internal fun resolveHomeTopSettingsButtonSize(): Dp = 40.dp

internal fun resolveHomeTopSettingsIconSize(): Dp = 20.dp

internal fun resolveHomeTopEdgeControlGap(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        7.dp
    } else if (uiPreset == UiPreset.MD3) {
        8.dp
    } else {
        6.dp
    }
}

internal fun shouldUseUnifiedHomeTopPanel(uiPreset: UiPreset = UiPreset.IOS): Boolean {
    return uiPreset == UiPreset.IOS || uiPreset == UiPreset.MD3
}

internal fun resolveHomeTopUnifiedPanelHorizontalPadding(uiPreset: UiPreset = UiPreset.IOS): Dp {
    return 0.dp
}

internal fun resolveHomeTopUnifiedPanelInnerPadding(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3,
    collapsedIntoStatusBar: Boolean = false
): Dp {
    if (collapsedIntoStatusBar) return 2.dp
    return if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        9.dp
    } else if (uiPreset == UiPreset.MD3) {
        10.dp
    } else {
        8.dp
    }
}

internal fun resolveHomeTopUnifiedPanelCornerRadius(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3,
    collapsedIntoStatusBar: Boolean = false
): Dp {
    if (collapsedIntoStatusBar) return 0.dp
    return if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        18.dp
    } else if (uiPreset == UiPreset.MD3) {
        16.dp
    } else {
        28.dp
    }
}

internal fun resolveHomeTopEmbeddedTabHorizontalPadding(uiPreset: UiPreset = UiPreset.IOS): Dp {
    return 0.dp
}

internal fun resolveHomeTopTabHorizontalPadding(
    isTabFloating: Boolean,
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return when {
        uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX && isTabFloating -> 8.dp
        uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX -> 2.dp
        uiPreset == UiPreset.MD3 && isTabFloating -> 10.dp
        uiPreset == UiPreset.MD3 -> 4.dp
        isTabFloating -> 14.dp
        else -> 0.dp
    }
}

internal fun resolveHomeTopSearchToTabsSpacing(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        4.dp
    } else {
        6.dp
    }
}

internal fun resolveHomeTopSearchCollapseExtraSpacing(uiPreset: UiPreset = UiPreset.IOS): Dp {
    return if (shouldUseUnifiedHomeTopPanel(uiPreset) && shouldShowUnifiedHomeTopPanelDivider(uiPreset)) {
        5.dp
    } else {
        0.dp
    }
}

internal fun resolveHomeTopSearchCollapseDistance(
    searchBarHeight: Dp,
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return searchBarHeight +
        resolveHomeTopSearchToTabsSpacing(uiPreset, androidNativeVariant) +
        resolveHomeTopSearchCollapseExtraSpacing(uiPreset)
}

internal fun shouldUseIntegratedCollapsedHomeTopBar(
    searchRevealFraction: Float,
    uiPreset: UiPreset = UiPreset.IOS
): Boolean {
    return uiPreset == UiPreset.IOS && searchRevealFraction <= 0.02f
}

internal fun resolveHomeTopContinuousSlabOverlap(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        20.dp
    } else if (uiPreset == UiPreset.MD3) {
        24.dp
    } else {
        0.dp
    }
}

internal fun resolveHomeTopContinuousSlabShape(uiPreset: UiPreset = UiPreset.IOS): Shape {
    return RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
}

internal fun resolveHomeTopReservedListPadding(
    statusBarHeight: Dp,
    searchBarHeight: Dp,
    tabRowHeight: Dp,
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    val useUnifiedPanel = shouldUseUnifiedHomeTopPanel(uiPreset)
    val chromeHeight = if (useUnifiedPanel) {
        searchBarHeight +
            tabRowHeight +
            (resolveHomeTopUnifiedPanelInnerPadding(uiPreset, androidNativeVariant) * 2) +
            resolveHomeTopSearchToTabsSpacing(uiPreset, androidNativeVariant) +
            5.dp
    } else {
        searchBarHeight + resolveHomeTopSearchToTabsSpacing(uiPreset, androidNativeVariant) + tabRowHeight
    }
    return statusBarHeight + chromeHeight
}

internal fun resolveHomeTopBlurContainerColors(
    colors: HomeGlassResolvedColors,
    surfaceColor: Color,
    blurIntensity: BlurIntensity
): HomeGlassResolvedColors {
    return colors.copy(
        containerColor = resolveBottomBarSurfaceColor(
            surfaceColor = surfaceColor,
            blurEnabled = true,
            blurIntensity = blurIntensity
        )
    )
}

internal fun resolveHomeTopBlurSurfaceType(
    renderMode: HomeTopChromeRenderMode
): BlurSurfaceType {
    return when (renderMode) {
        HomeTopChromeRenderMode.BLUR -> BlurSurfaceType.HEADER
        else -> BlurSurfaceType.HEADER
    }
}

internal fun resolveHomeTopContinuousSlabRenderMode(
    renderMode: HomeTopChromeRenderMode,
    uiPreset: UiPreset = UiPreset.IOS
): HomeTopChromeRenderMode {
    return when (renderMode) {
        HomeTopChromeRenderMode.BLUR -> HomeTopChromeRenderMode.BLUR
        else -> HomeTopChromeRenderMode.PLAIN
    }
}

internal fun resolveHomeTopContinuousSlabHeight(
    statusBarHeight: Dp,
    searchBarHeight: Dp,
    tabRowHeight: Dp,
    renderMode: HomeTopChromeRenderMode,
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    if (renderMode != HomeTopChromeRenderMode.BLUR) return 0.dp
    return if (uiPreset == UiPreset.MD3) {
        statusBarHeight + resolveHomeTopContinuousSlabOverlap(uiPreset, androidNativeVariant)
    } else {
        statusBarHeight + searchBarHeight + tabRowHeight
    }
}

internal fun resolveHomeTopContinuousSlabSurfaceColor(
    baseColor: Color,
    blurAlpha: Float,
    uiPreset: UiPreset = UiPreset.IOS,
    renderMode: HomeTopChromeRenderMode
): Color {
    if (renderMode == HomeTopChromeRenderMode.PLAIN) return Color.Transparent
    if (renderMode != HomeTopChromeRenderMode.BLUR) {
        return baseColor.copy(alpha = maxOf(baseColor.alpha, blurAlpha))
    }
    return if (uiPreset == UiPreset.MD3) {
        baseColor.copy(alpha = maxOf(baseColor.alpha, blurAlpha))
    } else {
        Color.Transparent
    }
}

internal fun resolveHomeTopPanelChromeRenderMode(
    renderMode: HomeTopChromeRenderMode,
    uiPreset: UiPreset = UiPreset.IOS,
    useUnifiedPanel: Boolean = false
): HomeTopChromeRenderMode {
    if (useUnifiedPanel && renderMode == HomeTopChromeRenderMode.BLUR && uiPreset == UiPreset.IOS) {
        return HomeTopChromeRenderMode.BLUR
    }
    return resolveHomeTopLocalChromeRenderMode(
        renderMode = renderMode,
        uiPreset = uiPreset
    )
}

internal fun resolveHomeTopSearchChromeRenderMode(
    renderMode: HomeTopChromeRenderMode,
    uiPreset: UiPreset = UiPreset.IOS,
    useUnifiedPanel: Boolean = false,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): HomeTopChromeRenderMode {
    if (useUnifiedPanel) {
        if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
            return when (renderMode) {
                HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
                HomeTopChromeRenderMode.LIQUID_GLASS_HAZE,
                HomeTopChromeRenderMode.BLUR -> renderMode
                HomeTopChromeRenderMode.PLAIN -> HomeTopChromeRenderMode.PLAIN
            }
        }
        return when (renderMode) {
            HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
            HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> renderMode
            HomeTopChromeRenderMode.BLUR,
            HomeTopChromeRenderMode.PLAIN -> HomeTopChromeRenderMode.PLAIN
        }
    }
    return resolveHomeTopLocalChromeRenderMode(
        renderMode = renderMode,
        uiPreset = uiPreset
    )
}

internal fun resolveHomeTopUnifiedTabChromeRenderMode(
    localTabChromeRenderMode: HomeTopChromeRenderMode,
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3,
    useUnifiedLiquidChrome: Boolean
): HomeTopChromeRenderMode {
    if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        return localTabChromeRenderMode
    }
    return if (useUnifiedLiquidChrome) {
        localTabChromeRenderMode
    } else {
        HomeTopChromeRenderMode.PLAIN
    }
}

internal fun resolveHomeTopUnifiedTabSurfaceColor(
    tabContainerColor: Color,
    tabOverlayAlpha: Float,
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3,
    useUnifiedLiquidChrome: Boolean
): Color {
    if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        return tabContainerColor.copy(alpha = tabOverlayAlpha)
    }
    return if (useUnifiedLiquidChrome) {
        tabContainerColor.copy(alpha = tabOverlayAlpha)
    } else {
        Color.Transparent
    }
}

internal fun resolveHomeTopUnifiedSearchContainerColor(
    isLightMode: Boolean
): Color {
    return if (isLightMode) {
        Color.White.copy(alpha = 0.34f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }
}

internal fun resolveHomeTopUnifiedSearchBorderColor(
    isLightMode: Boolean
): Color {
    return if (isLightMode) {
        Color.White.copy(alpha = 0.20f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }
}

internal fun resolveHomeTopUnifiedPanelReadabilityColor(
    isLightMode: Boolean,
    renderMode: HomeTopChromeRenderMode
): Color {
    val alpha = when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> 0.18f
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> 0.20f
        HomeTopChromeRenderMode.BLUR -> 0.16f
        HomeTopChromeRenderMode.PLAIN -> 0f
    }
    return if (isLightMode) {
        Color.White.copy(alpha = alpha)
    } else {
        Color.Black.copy(alpha = alpha)
    }
}

internal fun resolveHomeTopWideChromePreferFlatGlass(
    renderMode: HomeTopChromeRenderMode
): Boolean {
    return when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> false
        HomeTopChromeRenderMode.BLUR,
        HomeTopChromeRenderMode.PLAIN -> true
    }
}

internal fun resolveHomeTopLocalChromeRenderMode(
    renderMode: HomeTopChromeRenderMode,
    uiPreset: UiPreset = UiPreset.IOS
): HomeTopChromeRenderMode {
    if (uiPreset == UiPreset.MD3 && renderMode == HomeTopChromeRenderMode.BLUR) {
        return HomeTopChromeRenderMode.BLUR
    }
    return when (renderMode) {
        HomeTopChromeRenderMode.BLUR -> HomeTopChromeRenderMode.PLAIN
        else -> renderMode
    }
}

internal fun resolveHomeTopChromeMotionPolicy(
    renderMode: HomeTopChromeRenderMode,
    isScrolling: Boolean,
    isTransitionRunning: Boolean
): HomeTopChromeMotionPolicy {
    return if (renderMode == HomeTopChromeRenderMode.BLUR) {
        HomeTopChromeMotionPolicy(
            isScrolling = false,
            isTransitionRunning = false
        )
    } else {
        HomeTopChromeMotionPolicy(
            isScrolling = isScrolling,
            isTransitionRunning = isTransitionRunning
        )
    }
}

internal fun shouldEnableTopTabSecondaryBlur(
    hasHeaderBlur: Boolean,
    topTabMaterialMode: TopTabMaterialMode,
    isScrolling: Boolean,
    isTransitionRunning: Boolean
): Boolean {
    if (!hasHeaderBlur) return false
    if (topTabMaterialMode == TopTabMaterialMode.PLAIN) return false
    if (topTabMaterialMode == TopTabMaterialMode.LIQUID_GLASS && (isScrolling || isTransitionRunning)) {
        return false
    }
    return true
}

internal fun resolveHomeHeaderTabBorderAlpha(
    isTabFloating: Boolean,
    isTabGlassEnabled: Boolean
): Float {
    return 0f
}

internal fun resolveHomeTopChromeReadabilityAlpha(
    renderMode: HomeTopChromeRenderMode
): Float {
    return when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> 0.26f
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> 0.28f
        HomeTopChromeRenderMode.BLUR -> 0.30f
        HomeTopChromeRenderMode.PLAIN -> 0.16f
    }
}

internal fun resolveHomeTopSearchContentAlpha(
    renderMode: HomeTopChromeRenderMode
): Float {
    return when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> 0.88f
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> 0.90f
        HomeTopChromeRenderMode.BLUR -> 0.92f
        HomeTopChromeRenderMode.PLAIN -> 0.78f
    }
}

internal fun resolveHomeTopForegroundColor(
    isLightMode: Boolean
): Color {
    return if (isLightMode) {
        Color.Black
    } else {
        Color.White.copy(alpha = 0.92f)
    }
}

internal fun resolveHomeTopInnerUnderlayColor(
    isLightMode: Boolean,
    renderMode: HomeTopChromeRenderMode,
    softenWideChrome: Boolean = false
): Color {
    val alpha = resolveHomeTopTabContentUnderlayAlpha(
        renderMode = renderMode,
        softenWideChrome = softenWideChrome
    )
    return if (isLightMode) {
        Color.White.copy(alpha = alpha)
    } else {
        Color.Black.copy(alpha = (alpha * 0.72f).coerceAtLeast(0.05f))
    }
}

internal fun resolveHomeTopChromeHighlightOverlayColor(
    baseColor: Color,
    renderMode: HomeTopChromeRenderMode,
    softenWideChrome: Boolean
): Color {
    if (!softenWideChrome) return baseColor
    val alphaMultiplier = when (renderMode) {
        HomeTopChromeRenderMode.BLUR -> 0.42f
        else -> 1f
    }
    return baseColor.copy(alpha = baseColor.alpha * alphaMultiplier)
}

internal fun tuneHomeTopGlassColors(
    colors: HomeGlassResolvedColors,
    isLightMode: Boolean,
    emphasized: Boolean
): HomeGlassResolvedColors {
    if (isLightMode) return colors
    return colors.copy(
        containerColor = colors.containerColor.copy(alpha = colors.containerColor.alpha * if (emphasized) 0.74f else 0.68f),
        borderColor = Color.White.copy(alpha = colors.borderColor.alpha * 0.48f),
        highlightColor = Color.White.copy(alpha = colors.highlightColor.alpha * 0.28f)
    )
}

internal fun resolveHomeTopContainerColors(
    uiPreset: UiPreset,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3,
    emphasized: Boolean,
    blurEnabled: Boolean,
    fallbackColors: HomeGlassResolvedColors,
    surfaceContainerColor: Color,
    surfaceContainerHighColor: Color,
    outlineVariantColor: Color
): HomeGlassResolvedColors {
    if (uiPreset != UiPreset.MD3) return fallbackColors
    if (blurEnabled) {
        val baseColor = if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
            surfaceContainerColor
        } else if (emphasized) {
            surfaceContainerHighColor
        } else {
            surfaceContainerColor
        }
        return HomeGlassResolvedColors(
            containerColor = baseColor.copy(alpha = fallbackColors.containerColor.alpha),
            borderColor = outlineVariantColor.copy(
                alpha = fallbackColors.borderColor.alpha.coerceAtLeast(
                    if (androidNativeVariant == AndroidNativeVariant.MIUIX) 0.16f else if (emphasized) 0.18f else 0.14f
                )
            ),
            highlightColor = Color.Transparent
        )
    }
    return HomeGlassResolvedColors(
        containerColor = if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
            surfaceContainerColor
        } else if (emphasized) {
            surfaceContainerHighColor
        } else {
            surfaceContainerColor
        },
        borderColor = outlineVariantColor.copy(
            alpha = if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
                if (emphasized) 0.44f else 0.34f
            } else if (emphasized) {
                0.55f
            } else {
                0.42f
            }
        ),
        highlightColor = Color.Transparent
    )
}

internal fun resolveHomeTopActionIconAlpha(
    renderMode: HomeTopChromeRenderMode
): Float {
    return when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> 0.86f
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> 0.88f
        HomeTopChromeRenderMode.BLUR -> 0.90f
        HomeTopChromeRenderMode.PLAIN -> 0.78f
    }
}

internal fun resolveHomeTopUnifiedPanelDividerAlpha(
    renderMode: HomeTopChromeRenderMode
): Float {
    return when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> 0.14f
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> 0.16f
        HomeTopChromeRenderMode.BLUR -> 0.18f
        HomeTopChromeRenderMode.PLAIN -> 0.12f
    }
}

internal fun shouldShowUnifiedHomeTopPanelDivider(uiPreset: UiPreset = UiPreset.IOS): Boolean {
    return uiPreset == UiPreset.MD3
}

internal fun resolveHomeTopTabContentUnderlayAlpha(
    renderMode: HomeTopChromeRenderMode,
    softenWideChrome: Boolean = false
): Float {
    val base = when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> 0.10f
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> 0.12f
        HomeTopChromeRenderMode.BLUR -> 0.14f
        HomeTopChromeRenderMode.PLAIN -> 0.08f
    }
    return if (softenWideChrome && renderMode == HomeTopChromeRenderMode.BLUR) {
        (base * 0.42f).coerceAtLeast(0.04f)
    } else {
        base
    }
}

internal fun resolveHomeTopChromeLensShape(shape: Shape): Shape? {
    return when {
        shape is CornerBasedShape -> shape
        shape === CircleShape -> RoundedCornerShape(50)
        shape === androidx.compose.ui.graphics.RectangleShape -> RoundedCornerShape(0.dp)
        else -> null
    }
}

internal fun Modifier.homeTopChromeSurface(
    renderMode: HomeTopChromeRenderMode,
    shape: Shape,
    surfaceColor: Color,
    hazeState: HazeState?,
    backdrop: LayerBackdrop?,
    liquidStyle: LiquidGlassStyle,
    liquidGlassTuning: LiquidGlassTuning? = null,
    motionTier: MotionTier,
    isScrolling: Boolean,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    preferFlatGlass: Boolean = false
): Modifier = composed {
    this.appChromeLiquidSurface(
        renderMode = renderMode,
        shape = shape,
        surfaceColor = surfaceColor,
        hazeState = hazeState,
        backdrop = backdrop,
        liquidStyle = liquidStyle,
        liquidGlassTuning = liquidGlassTuning,
        motionTier = motionTier,
        isScrolling = isScrolling,
        isTransitionRunning = isTransitionRunning,
        forceLowBlurBudget = forceLowBlurBudget,
        style = AppChromeLiquidSurfaceStyle(
            blurSurfaceType = resolveHomeTopBlurSurfaceType(renderMode),
            preferFlatGlass = preferFlatGlass,
            depthEffect = liquidGlassTuning?.depthEffectEnabled != false,
            refractionAmountScrollMultiplier = if (
                renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP ||
                renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
            ) {
                0.016f
            } else {
                0f
            },
            refractionAmountScrollCap = if (
                renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP ||
                renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
            ) {
                12f
            } else {
                0f
            },
            surfaceAlphaScrollMultiplier = if (
                renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP ||
                renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
            ) {
                0.00012f
            } else {
                0f
            },
            surfaceAlphaScrollCap = if (
                renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP ||
                renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
            ) {
                0.03f
            } else {
                0f
            },
            darkThemeWhiteOverlayMultiplier = if (
                renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP ||
                renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
            ) {
                0.86f
            } else {
                1f
            },
            useTuningSurfaceAlpha = renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP ||
                renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_HAZE,
            hazeBackgroundAlphaMultiplier = if (
                renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP ||
                renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
            ) {
                0.4f
            } else {
                1f
            }
        )
    )
}

/**
 *  简洁版首页头部 (带滚动隐藏/显示动画)
 * 
 *  [Refactor] 现在改为由外部通过 NestedScrollConnection 直接控制高度和透明度，
 *  实现了 1:1 的物理跟手效果，消除了漂浮感。
 */
@Composable
fun iOSHomeHeader(
    headerOffsetProvider: () -> Float, // [Optimization] Defer state read to prevent parent recomposition
    isHeaderCollapseEnabled: Boolean = true,
    user: UserState,
    onAvatarClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    topCategories: List<String> = resolveHomeTopCategories().map { it.label },
    topCategoryKeys: List<String> = resolveHomeTopCategories().map { it.name },
    categoryIndex: Int,
    onCategorySelected: (Int) -> Unit,
    onPartitionClick: () -> Unit = {},  //  新增：分区按钮回调
    onLiveClick: () -> Unit = {},  // [新增] 直播分区点击回调
    hazeState: HazeState? = null,  // 保留参数兼容性，但不用于模糊
    onStatusBarDoubleTap: () -> Unit = {},
    //  [新增] 下拉刷新状态
    isRefreshing: Boolean = false,
    pullProgress: Float = 0f,  // 0.0 ~ 1.0+ 下拉进度
    pagerState: androidx.compose.foundation.pager.PagerState? = null, // [New] PagerState for sync
    // [New] LayerBackdrop for liquid glass effect
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
    homeSettings: com.android.purebilibili.core.store.HomeSettings? = null,
    topTabsVisible: Boolean = true,
    motionTier: MotionTier = MotionTier.Normal,
    isScrolling: Boolean = false,
    isTransitionRunning: Boolean = false,
    forceLowBlurBudget: Boolean = false,
    interactionBudget: HomeInteractionMotionBudget = HomeInteractionMotionBudget.FULL
) {
    val uiPreset = LocalUiPreset.current
    val androidNativeVariant = LocalAndroidNativeVariant.current
    val haptic = rememberHapticFeedback()
    val density = LocalDensity.current
    val resolvedHeaderBlurMode = homeSettings?.headerBlurMode ?: HomeHeaderBlurMode.FOLLOW_PRESET
    val isHeaderBlurEnabled = remember(resolvedHeaderBlurMode, uiPreset) {
        resolveHomeHeaderBlurEnabled(
            mode = resolvedHeaderBlurMode,
            uiPreset = uiPreset
        )
    }
    val linkedBottomBarAppearance = remember(homeSettings, uiPreset, androidNativeVariant) {
        resolveHomeTopLinkedBottomBarAppearance(
            homeSettings = homeSettings,
            uiPreset = uiPreset,
            androidNativeVariant = androidNativeVariant
        )
    }
    val edgeButtonShape = resolveHomeTopEdgeButtonShape(uiPreset, androidNativeVariant)
    val searchContainerShape = resolveHomeTopSearchContainerShape(uiPreset, androidNativeVariant)
    val searchIcon = if (uiPreset == UiPreset.MD3) Icons.Outlined.Search else CupertinoIcons.Default.MagnifyingGlass
    val settingsIcon = rememberAppSettingsIcon()

    // 状态栏高度
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    
    // [Feature] Liquid Glass Logic
    val topChromeMaterialMode = resolveHomeTopChromeMaterialMode(
        isHeaderBlurEnabled = isHeaderBlurEnabled,
        isBottomBarBlurEnabled = linkedBottomBarAppearance.blurEnabled,
        isLiquidGlassEnabled = homeSettings?.isTopBarLiquidGlassEnabled == true,
        androidNativeVariant = androidNativeVariant
    )
    val isGlassEnabled = topChromeMaterialMode == TopTabMaterialMode.LIQUID_GLASS
    val isTopChromeBlurEnabled = topChromeMaterialMode != TopTabMaterialMode.PLAIN

    //  读取当前模糊强度以确定背景透明度
    val blurIntensity = currentUnifiedBlurIntensity()
    val backgroundAlpha = resolveHomeHeaderSurfaceAlpha(
        isGlassEnabled = isGlassEnabled,
        blurEnabled = isTopChromeBlurEnabled,
        blurIntensity = blurIntensity
    )

    val topTabStyle = resolveTopTabStyle(
        isBottomBarFloating = linkedBottomBarAppearance.isFloating,
        isBottomBarBlurEnabled = isHeaderBlurEnabled,
        isLiquidGlassEnabled = homeSettings?.isTopBarLiquidGlassEnabled == true
    )
    val isTabFloating = topTabStyle.floating
    val isTabGlassEnabled = topChromeMaterialMode == TopTabMaterialMode.LIQUID_GLASS
    val isTabBlurEnabled = topChromeMaterialMode == TopTabMaterialMode.BLUR
    val enableTopTabSecondaryBlur = shouldEnableTopTabSecondaryBlur(
        hasHeaderBlur = hazeState != null,
        topTabMaterialMode = topChromeMaterialMode,
        isScrolling = isScrolling,
        isTransitionRunning = isTransitionRunning
    )
    val isGlassSupported = shouldAllowHomeChromeLiquidGlass(Build.VERSION.SDK_INT)
    val allowHazeLiquidGlassFallback = shouldAllowDirectHazeLiquidGlassFallback(Build.VERSION.SDK_INT)
    val liquidStyle = homeSettings?.liquidGlassStyle ?: LiquidGlassStyle.CLASSIC
    val liquidGlassTuning = remember(
        homeSettings?.liquidGlassProgress,
        liquidStyle
    ) {
        if (homeSettings != null) {
            resolveLiquidGlassTuning(progress = homeSettings.liquidGlassProgress)
        } else {
            resolveLiquidGlassTuning(liquidStyle)
        }
    }
    val topChromeRenderMode = resolveHomeTopChromeRenderMode(
        materialMode = topChromeMaterialMode,
        isGlassSupported = isGlassSupported,
        hasBackdrop = backdrop != null,
        hasHazeState = hazeState != null,
        allowHazeLiquidGlassFallback = allowHazeLiquidGlassFallback
    )
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerHighColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val tabShape = RoundedCornerShape(if (isTabFloating) 22.dp else 0.dp)
    val tabSurfaceColor = surfaceColor
    val isLightMode = surfaceColor.luminance() > 0.5f
    val effectiveTabMaterialMode = resolveEffectiveHomeHeaderTabMaterialMode(
        materialMode = topChromeMaterialMode,
        interactionBudget = interactionBudget
    )
    val rawHeaderChromeColors = tuneHomeTopGlassColors(
        colors = rememberHomeGlassChromeColors(
            glassEnabled = isGlassEnabled,
            blurEnabled = isTopChromeBlurEnabled
        ),
        isLightMode = isLightMode,
        emphasized = false
    )
    val headerChromeColors = remember(
        rawHeaderChromeColors,
        isGlassEnabled,
        isTopChromeBlurEnabled,
        blurIntensity,
        uiPreset,
        androidNativeVariant
    ) {
        val resolved = if (!isGlassEnabled && isTopChromeBlurEnabled) {
            resolveHomeTopBlurContainerColors(
                colors = rawHeaderChromeColors,
                surfaceColor = surfaceColor,
                blurIntensity = blurIntensity
            )
        } else {
            rawHeaderChromeColors
        }
        resolveHomeTopContainerColors(
            uiPreset = uiPreset,
            androidNativeVariant = androidNativeVariant,
            emphasized = false,
            blurEnabled = !isGlassEnabled && isTopChromeBlurEnabled,
            fallbackColors = resolved,
            surfaceContainerColor = surfaceContainerColor,
            surfaceContainerHighColor = surfaceContainerHighColor,
            outlineVariantColor = outlineVariantColor
        )
    }
    val rawSearchPillColors = tuneHomeTopGlassColors(
        colors = rememberHomeGlassPillColors(
            glassEnabled = isGlassEnabled,
            blurEnabled = isTopChromeBlurEnabled,
            emphasized = true,
            baseColor = MaterialTheme.colorScheme.surface
        ),
        isLightMode = isLightMode,
        emphasized = true
    )
    val searchPillColors = remember(
        rawSearchPillColors,
        isGlassEnabled,
        isTopChromeBlurEnabled,
        blurIntensity,
        uiPreset,
        androidNativeVariant
    ) {
        val resolved = if (!isGlassEnabled && isTopChromeBlurEnabled) {
            resolveHomeTopBlurContainerColors(
                colors = rawSearchPillColors,
                surfaceColor = surfaceColor,
                blurIntensity = blurIntensity
            )
        } else {
            rawSearchPillColors
        }
        resolveHomeTopContainerColors(
            uiPreset = uiPreset,
            androidNativeVariant = androidNativeVariant,
            emphasized = true,
            blurEnabled = !isGlassEnabled && isTopChromeBlurEnabled,
            fallbackColors = resolved,
            surfaceContainerColor = surfaceContainerColor,
            surfaceContainerHighColor = surfaceContainerHighColor,
            outlineVariantColor = outlineVariantColor
        )
    }
    val rawTabChromeColors = tuneHomeTopGlassColors(
        colors = rememberHomeGlassChromeColors(
            glassEnabled = effectiveTabMaterialMode == TopTabMaterialMode.LIQUID_GLASS,
            blurEnabled = enableTopTabSecondaryBlur || effectiveTabMaterialMode != TopTabMaterialMode.PLAIN
        ),
        isLightMode = isLightMode,
        emphasized = false
    )
    val tabChromeColors = remember(rawTabChromeColors, effectiveTabMaterialMode, blurIntensity) {
        if (effectiveTabMaterialMode == TopTabMaterialMode.BLUR) {
            resolveHomeTopBlurContainerColors(
                colors = rawTabChromeColors,
                surfaceColor = tabSurfaceColor,
                blurIntensity = blurIntensity
            )
        } else {
            rawTabChromeColors
        }
    }
    val searchPillStyle = remember(isGlassEnabled, isTopChromeBlurEnabled) {
        resolveHomeGlassPillStyle(
            glassEnabled = isGlassEnabled,
            blurEnabled = isTopChromeBlurEnabled,
            emphasized = true
        )
    }
    val tabChromeStyle = remember(effectiveTabMaterialMode, enableTopTabSecondaryBlur) {
        resolveHomeGlassChromeStyle(
            glassEnabled = effectiveTabMaterialMode == TopTabMaterialMode.LIQUID_GLASS,
            blurEnabled = enableTopTabSecondaryBlur || effectiveTabMaterialMode != TopTabMaterialMode.PLAIN
        )
    }
    val topForegroundColor = resolveHomeTopForegroundColor(isLightMode = isLightMode)
    val topSearchContentAlpha = resolveHomeTopSearchContentAlpha(topChromeRenderMode)
    val topActionIconAlpha = resolveHomeTopActionIconAlpha(topChromeRenderMode)
    val topChromeMotionPolicy = resolveHomeTopChromeMotionPolicy(
        renderMode = topChromeRenderMode,
        isScrolling = isScrolling,
        isTransitionRunning = isTransitionRunning
    )
    val tabChromeRenderMode = when (effectiveTabMaterialMode) {
        TopTabMaterialMode.LIQUID_GLASS -> resolveHomeTopChromeRenderMode(
            materialMode = effectiveTabMaterialMode,
            isGlassSupported = isGlassSupported,
            hasBackdrop = backdrop != null,
            hasHazeState = hazeState != null,
            allowHazeLiquidGlassFallback = allowHazeLiquidGlassFallback
        )
        TopTabMaterialMode.BLUR -> if (enableTopTabSecondaryBlur) {
            HomeTopChromeRenderMode.BLUR
        } else {
            HomeTopChromeRenderMode.PLAIN
        }
        TopTabMaterialMode.PLAIN -> HomeTopChromeRenderMode.PLAIN
    }
    val tabChromeMotionPolicy = resolveHomeTopChromeMotionPolicy(
        renderMode = tabChromeRenderMode,
        isScrolling = isScrolling,
        isTransitionRunning = isTransitionRunning
    )
    val useUnifiedTopPanel = shouldUseUnifiedHomeTopPanel(uiPreset)
    val topPanelChromeRenderMode = resolveHomeTopPanelChromeRenderMode(
        renderMode = topChromeRenderMode,
        uiPreset = uiPreset,
        useUnifiedPanel = useUnifiedTopPanel
    )
    val searchChromeRenderMode = resolveHomeTopSearchChromeRenderMode(
        renderMode = topChromeRenderMode,
        uiPreset = uiPreset,
        useUnifiedPanel = useUnifiedTopPanel,
        androidNativeVariant = androidNativeVariant
    )
    val searchContentBackdrop = rememberLayerBackdrop()
    val localTopChromeRenderMode = resolveHomeTopLocalChromeRenderMode(
        renderMode = topChromeRenderMode,
        uiPreset = uiPreset
    )
    val localTabChromeRenderMode = resolveHomeTopLocalChromeRenderMode(
        renderMode = tabChromeRenderMode,
        uiPreset = uiPreset
    )
    val continuousSlabRenderMode = resolveHomeTopContinuousSlabRenderMode(
        renderMode = topChromeRenderMode,
        uiPreset = uiPreset
    )

    // [Optimization] Calculate layout values LOCALLY using deferred state read
    // This prevents HomeScreen from recomposing when headerOffset changes
    val headerOffset by remember { derivedStateOf(headerOffsetProvider) }
    
    val searchBarHeightDp = resolveHomeTopSearchBarHeight(uiPreset, androidNativeVariant)
    val tabRowHeightDp = resolveHomeTopTabRowHeight(
        isTabFloating = isTabFloating,
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant,
        labelMode = homeSettings?.topTabLabelMode
            ?: com.android.purebilibili.core.store.SettingsManager.TopTabLabelMode.TEXT_ONLY
    )
    val searchCollapseDistanceDp = resolveHomeTopSearchCollapseDistance(
        searchBarHeight = searchBarHeightDp,
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant
    )
    val searchRevealDeadZoneDp = resolveHomeTopSearchRevealDeadZone(uiPreset, androidNativeVariant)
    val searchBarHeightPx = with(density) { searchBarHeightDp.toPx() }
    val searchCollapseDistancePx = with(density) { searchCollapseDistanceDp.toPx() }
    val searchRevealDeadZonePx = with(density) { searchRevealDeadZoneDp.toPx() }
    val tabRowHeightPx = with(density) { tabRowHeightDp.toPx() }

    val scrollLayout = remember(
        headerOffset,
        searchBarHeightPx,
        searchCollapseDistancePx,
        searchRevealDeadZonePx,
        tabRowHeightPx,
        isHeaderCollapseEnabled
    ) {
        resolveHomeHeaderScrollLayout(
            headerOffsetPx = headerOffset,
            searchBarHeightPx = searchBarHeightPx,
            searchCollapseDistancePx = searchCollapseDistancePx,
            tabRowHeightPx = tabRowHeightPx,
            isHeaderCollapseEnabled = isHeaderCollapseEnabled,
            searchRevealDeadZonePx = searchRevealDeadZonePx,
            usesImmediateSearchReveal = usesImmediateHomeTopSearchReveal(searchRevealDeadZonePx)
        )
    }
    val currentSearchHeight = with(density) { scrollLayout.searchBarHeightPx.toDp() }
    val searchAlpha = scrollLayout.searchAlpha
    val currentTabHeight = with(density) { scrollLayout.tabRowHeightPx.toDp() }
    val tabAlpha = scrollLayout.tabAlpha
    val searchRevealFraction = if (searchBarHeightPx > 0f) {
        (scrollLayout.searchBarHeightPx / searchBarHeightPx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val searchRefractionLayerPolicy = remember(
        searchChromeRenderMode,
        backdrop,
        searchRevealFraction,
        topChromeMotionPolicy.isScrolling,
        topChromeMotionPolicy.isTransitionRunning
    ) {
        resolveHomeTopSearchRefractionLayerPolicy(
            renderMode = searchChromeRenderMode,
            hasBackdrop = backdrop != null,
            searchRevealFraction = searchRevealFraction,
            isScrolling = topChromeMotionPolicy.isScrolling,
            isTransitionRunning = topChromeMotionPolicy.isTransitionRunning
        )
    }
    val usesImmediateSearchReveal = remember(searchRevealDeadZonePx) {
        usesImmediateHomeTopSearchReveal(searchRevealDeadZonePx)
    }
    val searchContentRevealFraction = remember(searchRevealFraction, usesImmediateSearchReveal) {
        resolveHomeTopSearchContentRevealFraction(
            searchRevealFraction = searchRevealFraction,
            usesImmediateReveal = usesImmediateSearchReveal
        )
    }
    val searchContentTranslationYPx = remember(
        searchRevealFraction,
        searchBarHeightPx,
        usesImmediateSearchReveal
    ) {
        resolveHomeTopSearchContentTranslationYPx(
            searchRevealFraction = searchRevealFraction,
            searchBarHeightPx = searchBarHeightPx,
            usesImmediateReveal = usesImmediateSearchReveal
        )
    }
    val integratedCollapsedTopBar = shouldUseIntegratedCollapsedHomeTopBar(
        searchRevealFraction = searchRevealFraction,
        uiPreset = uiPreset
    )
    val unifiedPanelCornerRadius = resolveHomeTopUnifiedPanelCornerRadius(
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant,
        collapsedIntoStatusBar = integratedCollapsedTopBar
    )
    val unifiedPanelShape = if (unifiedPanelCornerRadius == 0.dp) {
        androidx.compose.ui.graphics.RectangleShape
    } else {
        RoundedCornerShape(unifiedPanelCornerRadius)
    }
    val unifiedPanelHorizontalPadding = resolveHomeTopUnifiedPanelHorizontalPadding(uiPreset)
    val unifiedPanelInnerPadding = resolveHomeTopUnifiedPanelInnerPadding(
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant,
        collapsedIntoStatusBar = integratedCollapsedTopBar
    )
    val searchToTabsSpacing = resolveHomeTopSearchToTabsSpacing(uiPreset, androidNativeVariant)
    val currentSearchToTabsSpacing = searchToTabsSpacing * searchContentRevealFraction
    val currentUnifiedDividerBottomSpacing = 4.dp * searchContentRevealFraction

    val tabHorizontalPadding by animateDpAsState(
        targetValue = resolveHomeTopTabHorizontalPadding(
            isTabFloating = isTabFloating,
            uiPreset = uiPreset,
            androidNativeVariant = androidNativeVariant
        ),
        animationSpec = tween(240),
        label = "tabHorizontalPadding"
    )
    val tabVerticalPadding by animateDpAsState(
        targetValue = resolveHomeTopTabVerticalPaddingDp(isTabFloating).dp,
        animationSpec = tween(240),
        label = "tabVerticalPadding"
    )
    val tabVerticalOffset by animateDpAsState(
        targetValue = resolveHomeTopTabYOffsetDp(isTabFloating).dp,
        animationSpec = tween(240),
        label = "tabVerticalOffset"
    )
    val tabShadowElevation by animateDpAsState(
        targetValue = if (uiPreset == UiPreset.MD3) 0.dp else if (isTabFloating) 8.dp else 0.dp,
        animationSpec = tween(240),
        label = "tabShadowElevation"
    )
    val effectiveTabShadowElevation = if (interactionBudget == HomeInteractionMotionBudget.REDUCED) 0.dp else tabShadowElevation
    val tabOverlayAlpha = resolveHomeTopTabOverlayAlpha(
        materialMode = effectiveTabMaterialMode,
        isTabFloating = isTabFloating,
        containerAlpha = tabChromeColors.containerColor.alpha
    )
    val tabContentAlpha by animateFloatAsState(
        targetValue = if (topTabsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "tabContentAlpha"
    )
    val effectiveContinuousSlabRenderMode = if (integratedCollapsedTopBar) {
        topPanelChromeRenderMode
    } else {
        continuousSlabRenderMode
    }
    val continuousSlabHeight = if (integratedCollapsedTopBar) {
        statusBarHeight + currentTabHeight
    } else {
        resolveHomeTopContinuousSlabHeight(
            statusBarHeight = statusBarHeight,
            searchBarHeight = currentSearchHeight,
            tabRowHeight = currentTabHeight,
            renderMode = effectiveContinuousSlabRenderMode,
            uiPreset = uiPreset,
            androidNativeVariant = androidNativeVariant
        )
    }
    val effectiveTopPanelChromeRenderMode = if (integratedCollapsedTopBar) {
        HomeTopChromeRenderMode.PLAIN
    } else {
        topPanelChromeRenderMode
    }
    val useUnifiedLiquidChrome = useUnifiedTopPanel &&
        (
            effectiveTopPanelChromeRenderMode == HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP ||
                effectiveTopPanelChromeRenderMode == HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
        )
    val isTopTabViewportSyncEnabled = resolveHomeTopTabViewportSyncEnabled(
        currentTabHeightDp = currentTabHeight.value,
        tabAlpha = tabAlpha,
        tabContentAlpha = tabContentAlpha
    )
    val tabBorderAlpha = if (isTabFloating) tabChromeStyle.borderAlpha else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(10f)
    ) {
        if (effectiveContinuousSlabRenderMode != HomeTopChromeRenderMode.PLAIN) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(continuousSlabHeight)
                    .homeTopChromeSurface(
                        renderMode = effectiveContinuousSlabRenderMode,
                        shape = resolveHomeTopContinuousSlabShape(uiPreset),
                        surfaceColor = resolveHomeTopContinuousSlabSurfaceColor(
                            baseColor = headerChromeColors.containerColor,
                            blurAlpha = backgroundAlpha,
                            uiPreset = uiPreset,
                            renderMode = effectiveContinuousSlabRenderMode
                        ),
                        hazeState = hazeState,
                        backdrop = backdrop,
                        liquidStyle = liquidStyle,
                        liquidGlassTuning = liquidGlassTuning,
                        motionTier = motionTier,
                        isScrolling = topChromeMotionPolicy.isScrolling,
                        isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                        forceLowBlurBudget = forceLowBlurBudget
                    )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 0.dp) // Reset padding, controlled by spacer
        ) {
            // 1. Status Bar Placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptic(HapticType.LIGHT)
                                onStatusBarDoubleTap()
                            }
                        )
                    }
            )

            // 2. Search Bar + Avatar + Settings
            // 高度和透明度由外部直接控制，实现物理跟手
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (useUnifiedTopPanel) {
                            Modifier
                                .padding(horizontal = unifiedPanelHorizontalPadding)
                                .clip(unifiedPanelShape)
                                .homeTopChromeSurface(
                                    renderMode = effectiveTopPanelChromeRenderMode,
                                    shape = unifiedPanelShape,
                                    surfaceColor = headerChromeColors.containerColor,
                                    hazeState = hazeState,
                                    backdrop = backdrop,
                                    liquidStyle = liquidStyle,
                                    liquidGlassTuning = liquidGlassTuning,
                                    motionTier = motionTier,
                                    isScrolling = topChromeMotionPolicy.isScrolling,
                                    isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                    forceLowBlurBudget = forceLowBlurBudget,
                                    preferFlatGlass = resolveHomeTopWideChromePreferFlatGlass(
                                        effectiveTopPanelChromeRenderMode
                                    )
                                )
                                .then(
                                    if (integratedCollapsedTopBar) {
                                        Modifier
                                    } else if (useUnifiedLiquidChrome) {
                                        Modifier
                                    } else {
                                        Modifier.border(0.8.dp, headerChromeColors.borderColor, unifiedPanelShape)
                                    }
                                )
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (
                    useUnifiedTopPanel &&
                    !integratedCollapsedTopBar &&
                    !useUnifiedLiquidChrome
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                resolveHomeTopUnifiedPanelReadabilityColor(
                                    isLightMode = isLightMode,
                                    renderMode = effectiveTopPanelChromeRenderMode
                                )
                            )
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (useUnifiedTopPanel) {
                                Modifier.padding(
                                    horizontal = if (integratedCollapsedTopBar) 0.dp else unifiedPanelInnerPadding,
                                    vertical = unifiedPanelInnerPadding
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(currentSearchHeight)
                            .graphicsLayer {
                                alpha = searchAlpha
                                translationY = searchContentTranslationYPx
                            }
                            .clip(androidx.compose.ui.graphics.RectangleShape)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(searchBarHeightDp)
                                .padding(
                                    horizontal = if (useUnifiedTopPanel) {
                                        0.dp
                                    } else {
                                        resolveHomeTopSearchRowHorizontalPadding(uiPreset, androidNativeVariant)
                                    }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(resolveHomeTopAvatarOuterSize())
                                    .then(
                                        if (uiPreset == UiPreset.MD3) {
                                            Modifier.clickable {
                                                performHomeTopBarTap(haptic = haptic, onClick = onAvatarClick)
                                            }
                                        } else {
                                            Modifier.iOSTapEffect { onAvatarClick() }
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(resolveHomeTopAvatarInnerSize())
                                        .clip(edgeButtonShape)
                                        .then(
                                            if (useUnifiedTopPanel) {
                                                if (useUnifiedLiquidChrome) {
                                                    Modifier
                                                        .homeTopChromeSurface(
                                                            renderMode = localTopChromeRenderMode,
                                                            shape = edgeButtonShape,
                                                            surfaceColor = headerChromeColors.containerColor,
                                                            hazeState = hazeState,
                                                            backdrop = backdrop,
                                                            liquidStyle = liquidStyle,
                                                            liquidGlassTuning = liquidGlassTuning,
                                                            motionTier = motionTier,
                                                            isScrolling = topChromeMotionPolicy.isScrolling,
                                                            isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                                            forceLowBlurBudget = forceLowBlurBudget
                                                        )
                                                } else {
                                                    Modifier.border(
                                                        width = 0.8.dp,
                                                        color = headerChromeColors.borderColor.copy(alpha = 0.7f),
                                                        shape = edgeButtonShape
                                                    )
                                                }
                                            } else {
                                                Modifier
                                                    .homeTopChromeSurface(
                                                        renderMode = localTopChromeRenderMode,
                                                        shape = edgeButtonShape,
                                                        surfaceColor = headerChromeColors.containerColor,
                                                        hazeState = hazeState,
                                                        backdrop = backdrop,
                                                        liquidStyle = liquidStyle,
                                                        liquidGlassTuning = liquidGlassTuning,
                                                        motionTier = motionTier,
                                                        isScrolling = topChromeMotionPolicy.isScrolling,
                                                        isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                                        forceLowBlurBudget = forceLowBlurBudget
                                                    )
                                                    .border(1.dp, headerChromeColors.borderColor, edgeButtonShape)
                                            }
                                        )
                                ) {
                                    if (user.isLogin && user.face.isNotEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(FormatUtils.fixImageUrl(user.face))
                                                .crossfade(true).build(),
                                            contentDescription = "用户头像",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .background(
                                                    if (useUnifiedTopPanel) {
                                                        if (useUnifiedLiquidChrome) {
                                                            Color.Transparent
                                                        } else {
                                                            topForegroundColor.copy(alpha = 0.10f)
                                                        }
                                                    } else {
                                                        headerChromeColors.containerColor
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "未",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(resolveHomeTopEdgeControlGap(uiPreset, androidNativeVariant)))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(resolveHomeTopSearchPillHeight(uiPreset, androidNativeVariant)),
                                contentAlignment = Alignment.Center
                            ) {
                                val isTablet = com.android.purebilibili.core.util.LocalWindowSizeClass.current.isTablet
                                var searchPillWidthPx by remember { mutableFloatStateOf(0f) }
                                val searchIndicatorTintAlpha = resolveBottomBarIndicatorTintAlpha(
                                    shouldRefract = true,
                                    liquidGlassProgress = liquidGlassTuning.progress,
                                    configuredAlpha = liquidGlassTuning.indicatorTintAlpha
                                )
                                val searchIndicatorColor = resolveBottomBarMovingIndicatorSurfaceColor(
                                    isDarkTheme = !isLightMode
                                ).copy(alpha = searchIndicatorTintAlpha)
                                val stableSearchContentColor = if (uiPreset == UiPreset.MD3) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else if (isLightMode) {
                                    topForegroundColor
                                } else {
                                    Color.White.copy(alpha = 0.96f)
                                }
                                val searchPillContent: @Composable () -> Unit = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            searchIcon,
                                            contentDescription = "搜索",
                                            tint = stableSearchContentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(resolveHomeTopSearchIconTextGap(uiPreset, androidNativeVariant)))
                                        Text(
                                            text = "搜索视频、UP主...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = if (uiPreset == UiPreset.MD3) {
                                                if (isTablet) 15.sp else 14.sp
                                            } else {
                                                if (isTablet) 16.sp else 15.sp
                                            },
                                            fontWeight = if (uiPreset == UiPreset.MD3) FontWeight.Normal else FontWeight.Normal,
                                            color = stableSearchContentColor,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 640.dp)
                                        .fillMaxWidth()
                                        .height(resolveHomeTopSearchPillHeight(uiPreset, androidNativeVariant))
                                        .onSizeChanged { searchPillWidthPx = it.width.toFloat() }
                                        .clip(searchContainerShape)
                                        .homeTopChromeSurface(
                                            renderMode = searchChromeRenderMode,
                                            shape = searchContainerShape,
                                            surfaceColor = if (useUnifiedTopPanel) {
                                                if (useUnifiedLiquidChrome) {
                                                    searchPillColors.containerColor
                                                } else {
                                                    resolveHomeTopUnifiedSearchContainerColor(isLightMode = isLightMode)
                                                }
                                            } else {
                                                searchPillColors.containerColor
                                            },
                                            hazeState = hazeState,
                                            backdrop = backdrop,
                                            liquidStyle = liquidStyle,
                                            liquidGlassTuning = liquidGlassTuning,
                                            motionTier = motionTier,
                                            isScrolling = topChromeMotionPolicy.isScrolling,
                                            isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                            forceLowBlurBudget = forceLowBlurBudget,
                                            preferFlatGlass = resolveHomeTopWideChromePreferFlatGlass(
                                                searchChromeRenderMode
                                            )
                                        )
                                        .border(
                                            width = 0.8.dp,
                                            color = if (useUnifiedTopPanel) {
                                                if (useUnifiedLiquidChrome) {
                                                    Color.Transparent
                                                } else {
                                                    resolveHomeTopUnifiedSearchBorderColor(isLightMode = isLightMode)
                                                }
                                            } else {
                                                searchPillColors.borderColor
                                            },
                                            shape = searchContainerShape
                                        )
                                        .clickable {
                                            haptic(HapticType.LIGHT)
                                            onSearchClick()
                                        }
                                        .padding(horizontal = resolveHomeTopSearchContentHorizontalPadding(uiPreset, androidNativeVariant)),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (uiPreset == UiPreset.IOS && !useUnifiedTopPanel) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(14.dp)
                                                .align(Alignment.TopCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            resolveHomeTopChromeHighlightOverlayColor(
                                                                baseColor = searchPillColors.highlightColor,
                                                                renderMode = topChromeRenderMode,
                                                                softenWideChrome = true
                                                            ),
                                                            Color.Transparent
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                    if (searchRefractionLayerPolicy.captureContentLayer) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clearAndSetSemantics {}
                                                .alpha(0f)
                                                .layerBackdrop(searchContentBackdrop),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(
                                                    horizontal = resolveHomeTopSearchContentHorizontalPadding(uiPreset, androidNativeVariant)
                                                ),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                searchPillContent()
                                            }
                                        }
                                    }
                                    Box(
                                        modifier = Modifier.alpha(searchRefractionLayerPolicy.visibleContentAlpha)
                                    ) {
                                        searchPillContent()
                                    }
                                    if (
                                        searchRefractionLayerPolicy.overlayAlpha > 0f &&
                                        searchPillWidthPx > 0f
                                    ) {
                                        SimpleLiquidIndicator(
                                            position = 0f,
                                            itemWidthPx = searchPillWidthPx,
                                            isDragging = true,
                                            velocityPxPerSecond = 0f,
                                            isLiquidGlassEnabled = true,
                                            liquidGlassStyle = liquidStyle,
                                            liquidGlassTuning = liquidGlassTuning,
                                            backdrop = searchContentBackdrop,
                                            indicatorColor = searchIndicatorColor,
                                            indicatorHeight = resolveHomeTopSearchPillHeight(uiPreset, androidNativeVariant),
                                            cornerRadius = resolveHomeTopSearchPillHeight(uiPreset, androidNativeVariant) / 2,
                                            widthRatio = 1f,
                                            minWidth = with(density) { searchPillWidthPx.toDp() },
                                            horizontalInset = 0.dp,
                                            modifier = Modifier
                                                .matchParentSize()
                                                .alpha(searchRefractionLayerPolicy.overlayAlpha)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(resolveHomeTopEdgeControlGap(uiPreset, androidNativeVariant)))

                            Box(
                                modifier = Modifier
                                    .size(resolveHomeTopSettingsButtonSize())
                                    .clip(edgeButtonShape)
                                    .then(
                                        if (useUnifiedTopPanel) {
                                            if (useUnifiedLiquidChrome) {
                                                Modifier
                                                    .homeTopChromeSurface(
                                                        renderMode = localTopChromeRenderMode,
                                                        shape = edgeButtonShape,
                                                        surfaceColor = headerChromeColors.containerColor,
                                                        hazeState = hazeState,
                                                        backdrop = backdrop,
                                                        liquidStyle = liquidStyle,
                                                        liquidGlassTuning = liquidGlassTuning,
                                                        motionTier = motionTier,
                                                        isScrolling = topChromeMotionPolicy.isScrolling,
                                                        isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                                        forceLowBlurBudget = forceLowBlurBudget
                                                    )
                                            } else {
                                                Modifier.background(
                                                    topForegroundColor.copy(alpha = if (isLightMode) 0.06f else 0.10f)
                                                )
                                            }
                                        } else {
                                            Modifier
                                                .homeTopChromeSurface(
                                                    renderMode = localTopChromeRenderMode,
                                                    shape = edgeButtonShape,
                                                    surfaceColor = headerChromeColors.containerColor,
                                                    hazeState = hazeState,
                                                    backdrop = backdrop,
                                                    liquidStyle = liquidStyle,
                                                    liquidGlassTuning = liquidGlassTuning,
                                                    motionTier = motionTier,
                                                    isScrolling = topChromeMotionPolicy.isScrolling,
                                                    isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                                    forceLowBlurBudget = forceLowBlurBudget
                                                )
                                                .border(0.8.dp, headerChromeColors.borderColor, edgeButtonShape)
                                        }
                                    )
                                    .then(
                                        if (uiPreset == UiPreset.MD3) {
                                            Modifier.clickable {
                                                performHomeTopBarTap(haptic = haptic, onClick = onSettingsClick)
                                            }
                                        } else {
                                            Modifier.iOSTapEffect {
                                                haptic(HapticType.LIGHT)
                                                onSettingsClick()
                                            }
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    settingsIcon,
                                    contentDescription = "设置",
                                    tint = if (isLightMode) {
                                        topForegroundColor
                                    } else {
                                        topForegroundColor.copy(alpha = topActionIconAlpha)
                                    },
                                    modifier = Modifier.size(resolveHomeTopSettingsIconSize())
                                )
                            }
                        }
                    }

                    if (
                        useUnifiedTopPanel &&
                        shouldShowUnifiedHomeTopPanelDivider(uiPreset) &&
                        currentTabHeight > 0.dp &&
                        tabAlpha * tabContentAlpha > 0f &&
                        searchRevealFraction > 0f
                    ) {
                        Spacer(modifier = Modifier.height(currentSearchToTabsSpacing))
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = headerChromeColors.borderColor.copy(
                                alpha = resolveHomeTopUnifiedPanelDividerAlpha(topChromeRenderMode) *
                                    searchRevealFraction
                            )
                        )
                        Spacer(modifier = Modifier.height(currentUnifiedDividerBottomSpacing))
                    } else {
                        Spacer(modifier = Modifier.height(currentSearchToTabsSpacing))
                    }

                    HomeTopTabChrome(
                        currentTabHeight = currentTabHeight,
                        tabAlpha = tabAlpha,
                        tabContentAlpha = tabContentAlpha,
                        containerZIndex = if (useUnifiedTopPanel) 0f else -1f,
                        tabHorizontalPadding = if (useUnifiedTopPanel) {
                            resolveHomeTopEmbeddedTabHorizontalPadding(uiPreset)
                        } else {
                            tabHorizontalPadding
                        },
                        tabVerticalPadding = if (useUnifiedTopPanel) 0.dp else tabVerticalPadding,
                        tabVerticalOffset = if (useUnifiedTopPanel) 0.dp else tabVerticalOffset,
                        isTabFloating = if (useUnifiedTopPanel) false else isTabFloating,
                        effectiveTabShadowElevation = if (useUnifiedTopPanel) 0.dp else effectiveTabShadowElevation,
                        tabShape = if (useUnifiedTopPanel) {
                            RoundedCornerShape(18.dp)
                        } else {
                            tabShape
                        },
                        tabChromeRenderMode = if (useUnifiedTopPanel) {
                            resolveHomeTopUnifiedTabChromeRenderMode(
                                localTabChromeRenderMode = localTabChromeRenderMode,
                                uiPreset = uiPreset,
                                androidNativeVariant = androidNativeVariant,
                                useUnifiedLiquidChrome = useUnifiedLiquidChrome
                            )
                        } else {
                            localTabChromeRenderMode
                        },
                        tabSurfaceColor = if (useUnifiedTopPanel) {
                            resolveHomeTopUnifiedTabSurfaceColor(
                                tabContainerColor = tabChromeColors.containerColor,
                                tabOverlayAlpha = tabOverlayAlpha,
                                uiPreset = uiPreset,
                                androidNativeVariant = androidNativeVariant,
                                useUnifiedLiquidChrome = useUnifiedLiquidChrome
                            )
                        } else {
                            tabSurfaceColor.copy(alpha = tabOverlayAlpha)
                        },
                        hazeState = hazeState,
                        backdrop = backdrop,
                        liquidStyle = liquidStyle,
                        liquidGlassTuning = liquidGlassTuning,
                        motionTier = motionTier,
                        isScrolling = tabChromeMotionPolicy.isScrolling,
                        isTransitionRunning = tabChromeMotionPolicy.isTransitionRunning,
                        forceLowBlurBudget = forceLowBlurBudget,
                        preferFlatGlass = !useUnifiedTopPanel,
                        tabBorderAlpha = if (useUnifiedTopPanel) {
                            0f
                        } else {
                            tabBorderAlpha
                        },
                        tabHighlightColor = if (useUnifiedTopPanel) {
                            Color.Transparent
                        } else {
                            resolveHomeTopChromeHighlightOverlayColor(
                                baseColor = tabChromeColors.highlightColor,
                                renderMode = tabChromeRenderMode,
                                softenWideChrome = true
                            )
                        },
                        tabContentUnderlayColor = if (useUnifiedTopPanel) {
                            Color.Transparent
                        } else {
                            resolveHomeTopInnerUnderlayColor(
                                isLightMode = isLightMode,
                                renderMode = tabChromeRenderMode,
                                softenWideChrome = true
                            )
                        }
                    ) {
                        CategoryTabRow(
                            categories = topCategories,
                            categoryKeys = topCategoryKeys,
                            selectedIndex = categoryIndex,
                            onCategorySelected = { index ->
                                if (topTabsVisible) onCategorySelected(index)
                            },
                            onPartitionClick = {
                                if (topTabsVisible) onPartitionClick()
                            },
                            onLiveClick = {
                                if (topTabsVisible) onLiveClick()
                            },
                            pagerState = pagerState,
                            labelMode = homeSettings?.topTabLabelMode
                                ?: com.android.purebilibili.core.store.SettingsManager.TopTabLabelMode.TEXT_ONLY,
                            isLiquidGlassEnabled =
                                effectiveTabMaterialMode == TopTabMaterialMode.LIQUID_GLASS &&
                                isGlassSupported,
                            liquidGlassStyle = liquidStyle,
                            liquidGlassTuning = liquidGlassTuning,
                            backdrop = backdrop,
                            isFloatingStyle = if (useUnifiedTopPanel) false else isTabFloating,
                            edgeToEdge = integratedCollapsedTopBar,
                            interactionBudget = interactionBudget,
                            isViewportSyncEnabled = isTopTabViewportSyncEnabled
                        )
                    }
                }
            }
        }
    }
}
