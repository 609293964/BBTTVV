// 文件路径: feature/home/components/iOSHomeHeader.kt
package com.android.purebilibili.feature.home.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance  //  状态栏亮度计算
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
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
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.BlurStyles
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.effect.liquidGlassBackground
import com.android.purebilibili.core.ui.effect.simpMusicLiquidGlass
import com.android.purebilibili.feature.home.LocalHomeScrollOffset
import com.android.purebilibili.feature.home.resolveHomeTopCategories
import com.android.purebilibili.feature.home.HomeGlassResolvedColors
import com.android.purebilibili.feature.home.rememberHomeGlassChromeColors
import com.android.purebilibili.feature.home.rememberHomeGlassPillColors
import com.android.purebilibili.feature.home.resolveHomeGlassChromeStyle
import com.android.purebilibili.feature.home.resolveHomeGlassPillStyle

private const val HOME_HEADER_LIQUID_GLASS_ALPHA = 0.10f

internal enum class HomeTopChromeRenderMode {
    PLAIN,
    BLUR,
    LIQUID_GLASS_HAZE,
    LIQUID_GLASS_BACKDROP
}

internal fun resolveHomeTopChromeMaterialMode(
    isBottomBarBlurEnabled: Boolean,
    isLiquidGlassEnabled: Boolean
): TopTabMaterialMode {
    return when {
        isLiquidGlassEnabled -> TopTabMaterialMode.LIQUID_GLASS
        isBottomBarBlurEnabled -> TopTabMaterialMode.BLUR
        else -> TopTabMaterialMode.PLAIN
    }
}

internal fun resolveHomeTopChromeRenderMode(
    materialMode: TopTabMaterialMode,
    isGlassSupported: Boolean,
    hasBackdrop: Boolean,
    hasHazeState: Boolean
): HomeTopChromeRenderMode {
    return when (materialMode) {
        TopTabMaterialMode.PLAIN -> HomeTopChromeRenderMode.PLAIN
        TopTabMaterialMode.BLUR -> HomeTopChromeRenderMode.BLUR
        TopTabMaterialMode.LIQUID_GLASS -> when {
            isGlassSupported && hasBackdrop -> HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP
            isGlassSupported && hasHazeState -> HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
            hasHazeState -> HomeTopChromeRenderMode.BLUR
            else -> HomeTopChromeRenderMode.PLAIN
        }
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

internal fun resolveHomeTopBlurContainerColors(
    colors: HomeGlassResolvedColors,
    surfaceColor: Color,
    blurIntensity: BlurIntensity
): HomeGlassResolvedColors {
    return colors.copy(
        containerColor = surfaceColor.copy(alpha = resolveHomeTopBlurContainerAlpha(blurIntensity))
    )
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
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> 0.22f
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> 0.24f
        HomeTopChromeRenderMode.BLUR -> 0.30f
        HomeTopChromeRenderMode.PLAIN -> 0.16f
    }
}

internal fun resolveHomeTopSearchContentAlpha(
    renderMode: HomeTopChromeRenderMode
): Float {
    return when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> 0.84f
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> 0.86f
        HomeTopChromeRenderMode.BLUR -> 0.88f
        HomeTopChromeRenderMode.PLAIN -> 0.76f
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
    renderMode: HomeTopChromeRenderMode
): Color {
    val alpha = resolveHomeTopTabContentUnderlayAlpha(renderMode)
    return if (isLightMode) {
        Color.White.copy(alpha = alpha)
    } else {
        Color.Black.copy(alpha = (alpha * 0.72f).coerceAtLeast(0.05f))
    }
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

internal fun resolveHomeTopActionIconAlpha(
    renderMode: HomeTopChromeRenderMode
): Float {
    return when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> 0.82f
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> 0.84f
        HomeTopChromeRenderMode.BLUR -> 0.86f
        HomeTopChromeRenderMode.PLAIN -> 0.78f
    }
}

internal fun resolveHomeTopTabContentUnderlayAlpha(
    renderMode: HomeTopChromeRenderMode
): Float {
    return when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> 0.10f
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> 0.12f
        HomeTopChromeRenderMode.BLUR -> 0.14f
        HomeTopChromeRenderMode.PLAIN -> 0.08f
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
    motionTier: MotionTier,
    isScrolling: Boolean,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean
): Modifier = composed {
    val scrollState = LocalHomeScrollOffset.current
    val lensShape = resolveHomeTopChromeLensShape(shape)

    when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> {
            if (liquidStyle == LiquidGlassStyle.SIMP_MUSIC && backdrop != null && lensShape != null) {
                this.simpMusicLiquidGlass(
                    backdrop = backdrop,
                    shape = lensShape
                )
            } else if (backdrop != null && lensShape != null) {
                this.drawBackdrop(
                    backdrop = backdrop,
                    shape = { lensShape },
                    effects = {
                        lens(
                            refractionHeight = if (liquidStyle == LiquidGlassStyle.IOS26) 136f else 164f,
                            refractionAmount = if (liquidStyle == LiquidGlassStyle.IOS26) 32f else 56f,
                            depthEffect = liquidStyle != LiquidGlassStyle.IOS26,
                            chromaticAberration = liquidStyle == LiquidGlassStyle.CLASSIC
                        )
                    },
                    onDrawSurface = {
                        drawRect(surfaceColor)
                        drawRect(
                            Color.White.copy(
                                alpha = if (liquidStyle == LiquidGlassStyle.CLASSIC) 0.04f else 0.08f
                            )
                        )
                    }
                )
            } else if (backdrop != null) {
                this.drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = { blur(24f) },
                    onDrawSurface = {
                        drawRect(surfaceColor)
                        drawRect(Color.White.copy(alpha = 0.06f))
                    }
                )
            } else {
                this.background(surfaceColor)
            }
        }

        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> {
            if (hazeState != null) {
                this
                    .hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            tint = null,
                            blurRadius = 0.1.dp,
                            noiseFactor = 0f
                        )
                    )
                    .liquidGlassBackground(
                        refractIntensity = if (liquidStyle == LiquidGlassStyle.IOS26) 0.22f else 0.6f,
                        scrollOffsetProvider = {
                            if (liquidStyle == LiquidGlassStyle.IOS26) 0f else scrollState.floatValue
                        },
                        backgroundColor = surfaceColor
                    )
            } else {
                this.background(surfaceColor)
            }
        }

        HomeTopChromeRenderMode.BLUR -> {
            this
                .then(
                    if (hazeState != null) {
                        Modifier.unifiedBlur(
                            hazeState = hazeState,
                            surfaceType = BlurSurfaceType.HEADER,
                            motionTier = motionTier,
                            isScrolling = isScrolling,
                            isTransitionRunning = isTransitionRunning,
                            forceLowBudget = forceLowBlurBudget
                        )
                    } else {
                        Modifier
                    }
                )
                .background(surfaceColor)
        }

        HomeTopChromeRenderMode.PLAIN -> {
            this.background(surfaceColor)
        }
    }
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
    val haptic = rememberHapticFeedback()
    val density = LocalDensity.current

    // 状态栏高度
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    
    // [Feature] Liquid Glass Logic
    val topChromeMaterialMode = resolveHomeTopChromeMaterialMode(
        isBottomBarBlurEnabled = homeSettings?.isBottomBarBlurEnabled == true,
        isLiquidGlassEnabled = homeSettings?.isLiquidGlassEnabled == true
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
    val targetHeaderColor = MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha)
    
    // [UX优化] 平滑过渡顶部栏背景色 (Smooth Header Color Transition)
    // 注意：这里保留颜色动画是没问题的，因为它不影响布局
    // [UX优化] 平滑过渡顶部栏背景色 (Smooth Header Color Transition)
    // 注意：这里保留颜色动画是没问题的，因为它不影响布局
    val animatedHeaderColor by animateColorAsState(
        targetValue = targetHeaderColor,
        animationSpec = androidx.compose.animation.core.tween<androidx.compose.ui.graphics.Color>(300),
        label = "headerColor"
    )

    val topTabStyle = resolveTopTabStyle(
        isBottomBarFloating = homeSettings?.isBottomBarFloating == true,
        isBottomBarBlurEnabled = homeSettings?.isBottomBarBlurEnabled == true,
        isLiquidGlassEnabled = homeSettings?.isLiquidGlassEnabled == true
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
    val isGlassSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val liquidStyle = homeSettings?.liquidGlassStyle ?: LiquidGlassStyle.CLASSIC
    val topChromeRenderMode = resolveHomeTopChromeRenderMode(
        materialMode = topChromeMaterialMode,
        isGlassSupported = isGlassSupported,
        hasBackdrop = backdrop != null,
        hasHazeState = hazeState != null
    )
    val surfaceColor = MaterialTheme.colorScheme.surface
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
    val headerChromeColors = remember(rawHeaderChromeColors, isGlassEnabled, isTopChromeBlurEnabled, blurIntensity) {
        if (!isGlassEnabled && isTopChromeBlurEnabled) {
            resolveHomeTopBlurContainerColors(
                colors = rawHeaderChromeColors,
                surfaceColor = surfaceColor,
                blurIntensity = blurIntensity
            )
        } else {
            rawHeaderChromeColors
        }
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
    val searchPillColors = remember(rawSearchPillColors, isGlassEnabled, isTopChromeBlurEnabled, blurIntensity) {
        if (!isGlassEnabled && isTopChromeBlurEnabled) {
            resolveHomeTopBlurContainerColors(
                colors = rawSearchPillColors,
                surfaceColor = surfaceColor,
                blurIntensity = blurIntensity
            )
        } else {
            rawSearchPillColors
        }
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
    val tabChromeRenderMode = when (effectiveTabMaterialMode) {
        TopTabMaterialMode.LIQUID_GLASS -> resolveHomeTopChromeRenderMode(
            materialMode = effectiveTabMaterialMode,
            isGlassSupported = isGlassSupported,
            hasBackdrop = backdrop != null,
            hasHazeState = hazeState != null
        )
        TopTabMaterialMode.BLUR -> if (enableTopTabSecondaryBlur) {
            HomeTopChromeRenderMode.BLUR
        } else {
            HomeTopChromeRenderMode.PLAIN
        }
        TopTabMaterialMode.PLAIN -> HomeTopChromeRenderMode.PLAIN
    }

    // [Optimization] Calculate layout values LOCALLY using deferred state read
    // This prevents HomeScreen from recomposing when headerOffset changes
    val headerOffset by remember { derivedStateOf(headerOffsetProvider) }
    
    val searchBarHeightDp = 52.dp
    val tabRowHeightDp = if (isTabFloating) 62.dp else 48.dp
    val searchBarHeightPx = with(density) { searchBarHeightDp.toPx() }
    val tabRowHeightPx = with(density) { tabRowHeightDp.toPx() }

    // 1. Search Bar Collapse (First phase)
    val searchCollapseAmount = headerOffset.coerceAtLeast(-searchBarHeightPx)
    val currentSearchHeight = searchBarHeightDp + with(density) { searchCollapseAmount.toDp() }
    val searchAlpha = (1f + (searchCollapseAmount / searchBarHeightPx)).coerceIn(0f, 1f)
    
    // 2. Tab Row Collapse (Second phase, only if enabled)
    // Starts after Search Bar is fully collapsed (-52dp)
    val tabCollapseStart = -searchBarHeightPx
    val tabCollapseAmount = (headerOffset - tabCollapseStart).coerceAtMost(0f)
    
    val currentTabHeight = if (headerOffset < tabCollapseStart && isHeaderCollapseEnabled) {
         tabRowHeightDp + with(density) { tabCollapseAmount.toDp() }
    } else {
         tabRowHeightDp
    }
    val tabAlpha = if (headerOffset < tabCollapseStart && isHeaderCollapseEnabled) {
        (1f + (tabCollapseAmount / tabRowHeightPx)).coerceIn(0f, 1f)
    } else 1f

    val tabHorizontalPadding by animateDpAsState(
        targetValue = if (isTabFloating) 16.dp else 0.dp,
        animationSpec = tween(240),
        label = "tabHorizontalPadding"
    )
    val tabVerticalPadding by animateDpAsState(
        targetValue = if (isTabFloating) 4.dp else 0.dp,
        animationSpec = tween(240),
        label = "tabVerticalPadding"
    )
    val tabShadowElevation by animateDpAsState(
        targetValue = if (isTabFloating) 8.dp else 0.dp,
        animationSpec = tween(240),
        label = "tabShadowElevation"
    )
    val effectiveTabShadowElevation = if (interactionBudget == HomeInteractionMotionBudget.REDUCED) 0.dp else tabShadowElevation
    val tabOverlayAlpha by animateFloatAsState(
        targetValue = resolveHomeTopTabOverlayAlpha(
            materialMode = effectiveTabMaterialMode,
            isTabFloating = isTabFloating,
            containerAlpha = tabChromeColors.containerColor.alpha
        ),
        animationSpec = tween(220),
        label = "tabOverlayAlpha"
    )
    val tabContentAlpha by animateFloatAsState(
        targetValue = if (topTabsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "tabContentAlpha"
    )
    val tabBorderAlpha by animateFloatAsState(
        targetValue = if (isTabFloating) tabChromeStyle.borderAlpha else 0f,
        animationSpec = tween(220),
        label = "tabBorderAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(10f)
    ) {
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
                    .height(currentSearchHeight) // Use local derived value
                    .graphicsLayer { alpha = searchAlpha } // Use local derived value
                    .clip(androidx.compose.ui.graphics.RectangleShape) // Ensure content is clipped when shrinking
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp) // 内部内容保持原始高度，通过父容器裁剪实现收缩
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .iOSTapEffect { onAvatarClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .homeTopChromeSurface(
                                    renderMode = topChromeRenderMode,
                                    shape = CircleShape,
                                    surfaceColor = headerChromeColors.containerColor,
                                    hazeState = hazeState,
                                    backdrop = backdrop,
                                    liquidStyle = liquidStyle,
                                    motionTier = motionTier,
                                    isScrolling = isScrolling,
                                    isTransitionRunning = isTransitionRunning,
                                    forceLowBlurBudget = forceLowBlurBudget
                                )
                                .border(1.dp, headerChromeColors.borderColor, CircleShape)
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
                                    Modifier.fillMaxSize().background(headerChromeColors.containerColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("未", fontSize = 11.sp, fontWeight = FontWeight.Bold, 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Search Box
                    // [优化] 外层容器用于居中，内层容器限制最大宽度 (640dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .homeTopChromeSurface(
                                    renderMode = topChromeRenderMode,
                                    shape = RoundedCornerShape(10.dp),
                                    surfaceColor = searchPillColors.containerColor,
                                    hazeState = hazeState,
                                    backdrop = backdrop,
                                    liquidStyle = liquidStyle,
                                    motionTier = motionTier,
                                    isScrolling = isScrolling,
                                    isTransitionRunning = isTransitionRunning,
                                    forceLowBlurBudget = forceLowBlurBudget
                                )
                                .border(
                                    width = 0.8.dp,
                                    color = searchPillColors.borderColor,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { 
                                    haptic(HapticType.LIGHT)
                                    onSearchClick() 
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                searchPillColors.highlightColor,
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    CupertinoIcons.Default.MagnifyingGlass,
                                    contentDescription = "搜索",
                                    tint = if (isLightMode) {
                                        topForegroundColor
                                    } else {
                                        topForegroundColor.copy(alpha = topActionIconAlpha)
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // [优化] 响应式字体大小
                                val isTablet = com.android.purebilibili.core.util.LocalWindowSizeClass.current.isTablet
                                Text(
                                    text = "搜索视频、UP主...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = if (isTablet) 16.sp else 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = if (isLightMode) {
                                        topForegroundColor
                                    } else {
                                        topForegroundColor.copy(
                                            alpha = minOf(searchPillStyle.contentAlpha, topSearchContentAlpha)
                                        )
                                    },
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Settings Button
                    IconButton(
                        onClick = { 
                            haptic(HapticType.LIGHT)
                            onSettingsClick() 
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .homeTopChromeSurface(
                                renderMode = topChromeRenderMode,
                                shape = CircleShape,
                                surfaceColor = headerChromeColors.containerColor,
                                hazeState = hazeState,
                                backdrop = backdrop,
                                liquidStyle = liquidStyle,
                                motionTier = motionTier,
                                isScrolling = isScrolling,
                                isTransitionRunning = isTransitionRunning,
                                forceLowBlurBudget = forceLowBlurBudget
                            )
                            .border(0.8.dp, headerChromeColors.borderColor, CircleShape)
                    ) {
                        Icon(
                            CupertinoIcons.Default.Gearshape,
                            contentDescription = "设置",
                            tint = if (isLightMode) {
                                topForegroundColor
                            } else {
                                topForegroundColor.copy(alpha = topActionIconAlpha)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            HomeTopTabChrome(
                currentTabHeight = currentTabHeight,
                tabAlpha = tabAlpha,
                tabContentAlpha = tabContentAlpha,
                tabHorizontalPadding = tabHorizontalPadding,
                tabVerticalPadding = tabVerticalPadding,
                isTabFloating = isTabFloating,
                effectiveTabShadowElevation = effectiveTabShadowElevation,
                tabShape = tabShape,
                tabChromeRenderMode = tabChromeRenderMode,
                tabSurfaceColor = tabSurfaceColor.copy(alpha = tabOverlayAlpha),
                hazeState = hazeState,
                backdrop = backdrop,
                liquidStyle = liquidStyle,
                motionTier = motionTier,
                isScrolling = isScrolling,
                isTransitionRunning = isTransitionRunning,
                forceLowBlurBudget = forceLowBlurBudget,
                tabBorderAlpha = tabBorderAlpha,
                tabHighlightColor = tabChromeColors.highlightColor,
                tabContentUnderlayColor = resolveHomeTopInnerUnderlayColor(
                    isLightMode = isLightMode,
                    renderMode = tabChromeRenderMode
                )
            ) {
                if (tabContentAlpha > 0.01f) {
                    CategoryTabRow(
                        categories = topCategories,
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
                        isLiquidGlassEnabled = effectiveTabMaterialMode == TopTabMaterialMode.LIQUID_GLASS && isGlassSupported,
                        liquidGlassStyle = liquidStyle,
                        backdrop = backdrop,
                        isFloatingStyle = isTabFloating,
                        interactionBudget = interactionBudget
                    )
                }
            }
        }
    }
}
