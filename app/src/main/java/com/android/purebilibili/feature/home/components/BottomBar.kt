// 文件路径: feature/home/components/BottomBar.kt
package com.android.purebilibili.feature.home.components

// Duplicate import removed
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable  // [新增] 组合点击支持
import androidx.compose.foundation.ExperimentalFoundationApi // [新增]
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer  //  晃动动画
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.android.purebilibili.R
import com.android.purebilibili.feature.home.components.LiquidIndicator
import com.android.purebilibili.navigation.ScreenRoutes
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import com.android.purebilibili.core.ui.blur.shouldAllowDirectHazeLiquidGlassFallback
import com.android.purebilibili.core.ui.blur.shouldAllowHomeChromeLiquidGlass
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity
import com.android.purebilibili.core.ui.blur.BlurStyles
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.adaptive.MotionTier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.theme.iOSSystemGray
import com.android.purebilibili.core.theme.BottomBarColors  // 统一底栏颜色配置
import com.android.purebilibili.core.theme.BottomBarColorPalette  // 调色板
import com.android.purebilibili.core.theme.LocalCornerRadiusScale
import com.android.purebilibili.core.theme.LocalAndroidNativeVariant
import com.android.purebilibili.core.theme.LocalUiPreset
import com.android.purebilibili.core.theme.AndroidNativeVariant
import com.android.purebilibili.core.theme.UiPreset
import com.android.purebilibili.core.theme.iOSCornerRadius
import kotlinx.coroutines.launch  //  延迟导航
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import com.android.purebilibili.core.ui.animation.rememberDampedDragAnimationState
import com.android.purebilibili.core.ui.animation.horizontalDragGesture
import com.android.purebilibili.core.ui.motion.BottomBarMotionProfile
import com.android.purebilibili.core.ui.motion.resolveBottomBarMotionSpec
import dev.chrisbanes.haze.hazeEffect // [New]
import dev.chrisbanes.haze.HazeStyle   // [New]
// [LayerBackdrop] AndroidLiquidGlass library for real background refraction
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.foundation.shape.RoundedCornerShape as RoundedCornerShapeAlias
import androidx.compose.ui.Modifier.Companion.then
import dev.chrisbanes.haze.hazeSource
import com.android.purebilibili.core.ui.effect.liquidGlass
import com.android.purebilibili.core.store.LiquidGlassStyle // [New] Top-level enum
import com.android.purebilibili.core.store.LiquidGlassMode
import androidx.compose.foundation.isSystemInDarkTheme // [New] Theme detection for adaptive readability
import androidx.compose.animation.core.EaseOut
import kotlin.math.sign
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar as MiuixFloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem as MiuixFloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationDisplayMode as MiuixNavigationDisplayMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 底部导航项枚举 -  使用 iOS SF Symbols 风格图标
 * [HIG] 所有图标包含 contentDescription 用于无障碍访问
 */
enum class BottomNavItem(
    val label: String,
    @StringRes val labelRes: Int,
    @StringRes val contentDescriptionRes: Int,
    val legacyAliases: List<String> = emptyList(),
    val selectedIcon: @Composable () -> Unit,
    val unselectedIcon: @Composable () -> Unit,
    val route: String // [新增] 路由地址
) {
    HOME(
        "首页",
        R.string.bottom_nav_home,
        R.string.bottom_nav_home,
        emptyList(),
        { Icon(CupertinoIcons.Outlined.House, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.House, contentDescription = null) },
        ScreenRoutes.Home.route
    ),
    DYNAMIC(
        "动态",
        R.string.bottom_nav_dynamic,
        R.string.bottom_nav_dynamic,
        emptyList(),
        { Icon(CupertinoIcons.Outlined.Bell, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.Bell, contentDescription = null) },
        ScreenRoutes.Dynamic.route
    ),
    STORY(
        "短视频",
        R.string.bottom_nav_story,
        R.string.bottom_nav_story,
        emptyList(),
        { Icon(CupertinoIcons.Filled.PlayCircle, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.PlayCircle, contentDescription = null) },
        ScreenRoutes.Story.route
    ),
    HISTORY(
        "历史",
        R.string.bottom_nav_history,
        R.string.bottom_nav_history_desc,
        listOf("历史记录"),
        { Icon(CupertinoIcons.Filled.Clock, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.Clock, contentDescription = null) },
        ScreenRoutes.History.route
    ),
    PROFILE(
        "我的",
        R.string.bottom_nav_profile,
        R.string.bottom_nav_profile_desc,
        listOf("个人中心"),
        { Icon(CupertinoIcons.Outlined.Person, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.Person, contentDescription = null) },
        ScreenRoutes.Profile.route
    ),
    FAVORITE(
        "收藏",
        R.string.bottom_nav_favorite,
        R.string.bottom_nav_favorite_desc,
        listOf("收藏夹"),
        { Icon(CupertinoIcons.Filled.Star, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.Star, contentDescription = null) },
        ScreenRoutes.Favorite.route
    ),
    LIVE(
        "直播",
        R.string.bottom_nav_live,
        R.string.bottom_nav_live,
        emptyList(),
        { Icon(CupertinoIcons.Filled.Video, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.Video, contentDescription = null) },
        ScreenRoutes.LiveList.route
    ),
    WATCHLATER(
        "稍后看",
        R.string.bottom_nav_watch_later,
        R.string.bottom_nav_watch_later_desc,
        listOf("稍后再看"),
        { Icon(CupertinoIcons.Filled.Bookmark, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.Bookmark, contentDescription = null) },
        ScreenRoutes.WatchLater.route
    ),
    SETTINGS(
        "设置",
        R.string.bottom_nav_settings,
        R.string.bottom_nav_settings,
        emptyList(),
        { Icon(CupertinoIcons.Filled.Gearshape, contentDescription = null) },
        { Icon(CupertinoIcons.Default.Gearshape, contentDescription = null) },
        ScreenRoutes.Settings.route
    )
}

@Composable
internal fun resolveBottomNavItemLabel(item: BottomNavItem): String = stringResource(item.labelRes)

@Composable
internal fun resolveBottomNavItemContentDescription(item: BottomNavItem): String =
    stringResource(item.contentDescriptionRes)

internal fun resolveBottomNavItemLookupKeys(item: BottomNavItem): Set<String> {
    return linkedSetOf(
        item.name,
        item.name.lowercase(),
        item.name.uppercase(),
        item.route,
        item.route.lowercase(),
        item.route.uppercase(),
        item.label,
        item.label.lowercase(),
        *item.legacyAliases.toTypedArray()
    )
}

internal data class BottomBarLayoutPolicy(
    val horizontalPadding: Dp,
    val rowPadding: Dp,
    val maxBarWidth: Dp
)

internal enum class Md3BottomBarDisplayMode {
    IconAndText,
    IconOnly,
    TextOnly
}

internal data class Md3BottomBarFloatingChromeSpec(
    val cornerRadiusDp: Float,
    val horizontalOutsidePaddingDp: Float,
    val innerHorizontalPaddingDp: Float,
    val itemSpacingDp: Float,
    val shadowElevationDp: Float,
    val showDivider: Boolean
)

internal fun resolveMd3BottomBarFloatingChromeSpec(
    isFloating: Boolean
): Md3BottomBarFloatingChromeSpec {
    return if (isFloating) {
        Md3BottomBarFloatingChromeSpec(
            cornerRadiusDp = 50f,
            horizontalOutsidePaddingDp = 36f,
            innerHorizontalPaddingDp = 12f,
            itemSpacingDp = 12f,
            shadowElevationDp = 1f,
            showDivider = false
        )
    } else {
        Md3BottomBarFloatingChromeSpec(
            cornerRadiusDp = 0f,
            horizontalOutsidePaddingDp = 0f,
            innerHorizontalPaddingDp = 0f,
            itemSpacingDp = 0f,
            shadowElevationDp = 0f,
            showDivider = true
        )
    }
}

internal fun resolveMd3BottomBarDisplayMode(labelMode: Int): Md3BottomBarDisplayMode {
    return when (normalizeBottomBarLabelMode(labelMode)) {
        1 -> Md3BottomBarDisplayMode.IconOnly
        2 -> Md3BottomBarDisplayMode.TextOnly
        else -> Md3BottomBarDisplayMode.IconAndText
    }
}

internal data class AndroidNativeBottomBarTuning(
    val cornerRadiusDp: Float,
    val shellShadowElevationDp: Float,
    val shellBlurRadiusDp: Float,
    val shellSurfaceAlpha: Float,
    val outerHorizontalPaddingDp: Float,
    val innerHorizontalPaddingDp: Float,
    val indicatorHeightDp: Float,
    val indicatorLensRadiusDp: Float
)

private enum class SharedFloatingBottomBarIconStyle {
    MATERIAL,
    CUPERTINO
}

internal data class MiuixFloatingBottomBarTuning(
    val shellHeightDp: Float,
    val cornerRadiusDp: Float,
    val outerHorizontalPaddingDp: Float,
    val innerHorizontalPaddingDp: Float,
    val indicatorHeightDp: Float,
    val shellBlurRadiusDp: Float
)

internal data class AndroidNativeIndicatorSpec(
    val usesLens: Boolean,
    val captureTintedContentLayer: Boolean
)

internal fun resolveSharedBottomBarCapsuleShape(): androidx.compose.ui.graphics.Shape =
    RoundedCornerShape(percent = 50)

internal fun resolveAndroidNativeBottomBarTuning(
    blurEnabled: Boolean,
    darkTheme: Boolean
): AndroidNativeBottomBarTuning {
    return AndroidNativeBottomBarTuning(
        cornerRadiusDp = 32f,
        shellShadowElevationDp = if (darkTheme) 0.6f else 0.8f,
        shellBlurRadiusDp = if (blurEnabled) 12f else 0f,
        shellSurfaceAlpha = if (blurEnabled) 0.4f else 1f,
        outerHorizontalPaddingDp = 20f,
        innerHorizontalPaddingDp = 4f,
        indicatorHeightDp = 56f,
        indicatorLensRadiusDp = 24f
    )
}

internal fun resolveAndroidNativeBottomBarContainerColor(
    surfaceColor: Color,
    tuning: AndroidNativeBottomBarTuning,
    glassEnabled: Boolean
): Color {
    return if (glassEnabled) {
        surfaceColor.copy(alpha = if (surfaceColor.luminance() < 0.5f) 0.30f else 0.38f)
    } else {
        surfaceColor.copy(alpha = tuning.shellSurfaceAlpha)
    }
}

internal fun resolveAndroidNativeFloatingBottomBarContainerColor(
    surfaceColor: Color,
    tuning: AndroidNativeBottomBarTuning,
    glassEnabled: Boolean,
    blurEnabled: Boolean,
    blurIntensity: com.android.purebilibili.core.ui.blur.BlurIntensity
): Color {
    return if (glassEnabled) {
        resolveAndroidNativeBottomBarContainerColor(
            surfaceColor = surfaceColor,
            tuning = tuning,
            glassEnabled = true
        )
    } else {
        resolveBottomBarSurfaceColor(
            surfaceColor = surfaceColor,
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity
        )
    }
}

internal fun resolveMiuixFloatingBottomBarTuning(
    darkTheme: Boolean,
    glassEnabled: Boolean
): MiuixFloatingBottomBarTuning {
    return MiuixFloatingBottomBarTuning(
        shellHeightDp = 68f,
        cornerRadiusDp = 34f,
        outerHorizontalPaddingDp = 20f,
        innerHorizontalPaddingDp = 6f,
        indicatorHeightDp = 58f,
        shellBlurRadiusDp = if (glassEnabled) 12f else if (darkTheme) 8f else 6f
    )
}

internal fun resolveAndroidNativeBottomBarGlassEnabled(
    liquidGlassEnabled: Boolean,
    blurEnabled: Boolean
): Boolean = liquidGlassEnabled && !blurEnabled

internal fun shouldUseAndroidNativeFloatingHazeBlur(
    blurEnabled: Boolean,
    glassEnabled: Boolean,
    hasHazeState: Boolean
): Boolean = blurEnabled && !glassEnabled && hasHazeState

internal fun resolveAndroidNativeIndicatorSpec(
    isMoving: Boolean
): AndroidNativeIndicatorSpec {
    return AndroidNativeIndicatorSpec(
        usesLens = isMoving,
        captureTintedContentLayer = isMoving
    )
}

internal fun resolveAndroidNativeIndicatorColor(
    themeColor: Color,
    darkTheme: Boolean
): Color {
    val softened = androidx.compose.ui.graphics.lerp(
        start = themeColor,
        stop = Color.White,
        fraction = if (darkTheme) 0.58f else 0.82f
    )
    return softened.copy(alpha = if (darkTheme) 0.42f else 0.82f)
}

internal fun resolveAndroidNativeExportTintColor(
    themeColor: Color,
    darkTheme: Boolean
): Color {
    val toned = androidx.compose.ui.graphics.lerp(
        start = themeColor,
        stop = if (darkTheme) Color.White else Color.Black,
        fraction = if (darkTheme) 0.10f else 0.08f
    )
    return toned.copy(alpha = if (darkTheme) 0.32f else 0.38f)
}

internal fun resolveAndroidNativePanelOffsetFraction(
    position: Float,
    velocity: Float
): Float {
    val fractionalOffset = position - position.roundToInt().toFloat()
    if (abs(fractionalOffset) > 0.001f) {
        return fractionalOffset.coerceIn(-1f, 1f)
    }
    return (velocity / 2200f).coerceIn(-0.18f, 0.18f)
}

private fun Md3BottomBarDisplayMode.toMiuixNavigationDisplayMode(): MiuixNavigationDisplayMode {
    return when (this) {
        Md3BottomBarDisplayMode.IconAndText -> MiuixNavigationDisplayMode.IconAndText
        Md3BottomBarDisplayMode.IconOnly -> MiuixNavigationDisplayMode.IconOnly
        Md3BottomBarDisplayMode.TextOnly -> MiuixNavigationDisplayMode.TextOnly
    }
}

internal fun resolveBottomBarFloatingHeightDp(
    labelMode: Int,
    isTablet: Boolean
): Float {
    return when (labelMode) {
        0 -> if (isTablet) 72f else 66f
        2 -> if (isTablet) 54f else 52f
        else -> if (isTablet) 64f else 58f
    }
}

internal fun normalizeBottomBarLabelMode(requestedLabelMode: Int): Int = when (requestedLabelMode) {
    0, 1, 2 -> requestedLabelMode
    else -> 0
}

internal fun shouldShowBottomBarIcon(labelMode: Int): Boolean {
    return when (normalizeBottomBarLabelMode(labelMode)) {
        2 -> false
        else -> true
    }
}

internal fun shouldShowBottomBarText(labelMode: Int): Boolean {
    return when (normalizeBottomBarLabelMode(labelMode)) {
        1 -> false
        else -> true
    }
}

internal fun resolveBottomBarBottomPaddingDp(
    isFloating: Boolean,
    isTablet: Boolean
): Float {
    if (!isFloating) return 0f
    return if (isTablet) 18f else 12f
}

internal data class BottomBarIndicatorPolicy(
    val widthMultiplier: Float,
    val minWidthDp: Float,
    val maxWidthDp: Float,
    val maxWidthToItemRatio: Float,
    val clampToBounds: Boolean,
    val edgeInsetDp: Float
)

internal data class BottomBarIndicatorVisualPolicy(
    val isInMotion: Boolean,
    val shouldRefract: Boolean,
    val useNeutralTint: Boolean
)

internal data class BottomBarRefractionLayerPolicy(
    val captureTintedContentLayer: Boolean,
    val useCombinedBackdrop: Boolean
)

internal data class BottomBarRefractionMotionProfile(
    val progress: Float,
    val exportPanelOffsetFraction: Float,
    val indicatorPanelOffsetFraction: Float,
    val visiblePanelOffsetFraction: Float,
    val visibleSelectionEmphasis: Float,
    val exportSelectionEmphasis: Float,
    val exportCaptureWidthScale: Float,
    val forceChromaticAberration: Boolean,
    val indicatorLensAmountScale: Float,
    val indicatorLensHeightScale: Float
)

internal fun resolveBottomBarIndicatorVisualPolicy(
    position: Float,
    isDragging: Boolean,
    velocity: Float,
    useNeutralIndicatorTint: Boolean,
    motionSpec: com.android.purebilibili.core.ui.motion.BottomBarMotionSpec = resolveBottomBarMotionSpec()
): BottomBarIndicatorVisualPolicy {
    val isFractional = abs(position - position.roundToInt().toFloat()) > 0.001f
    val isInMotion = isDragging ||
        isFractional ||
        abs(velocity) > motionSpec.refraction.movingVelocityThresholdPxPerSecond
    return BottomBarIndicatorVisualPolicy(
        isInMotion = isInMotion,
        shouldRefract = isInMotion,
        useNeutralTint = isInMotion && useNeutralIndicatorTint
    )
}

internal fun resolveBottomBarRefractionLayerPolicy(
    isFloating: Boolean,
    isLiquidGlassEnabled: Boolean,
    indicatorVisualPolicy: BottomBarIndicatorVisualPolicy
): BottomBarRefractionLayerPolicy {
    val captureTintedContentLayer =
        isFloating && isLiquidGlassEnabled && indicatorVisualPolicy.shouldRefract
    return BottomBarRefractionLayerPolicy(
        captureTintedContentLayer = captureTintedContentLayer,
        useCombinedBackdrop = captureTintedContentLayer
    )
}

internal fun resolveBottomBarRefractionMotionProfile(
    position: Float,
    velocity: Float,
    isDragging: Boolean,
    motionSpec: com.android.purebilibili.core.ui.motion.BottomBarMotionSpec = resolveBottomBarMotionSpec()
): BottomBarRefractionMotionProfile {
    val signedFractionalOffset = position - position.roundToInt().toFloat()
    val fractionalProgress = (abs(signedFractionalOffset) * 2f).coerceIn(0f, 1f)
    val speedProgress = (abs(velocity) / motionSpec.refraction.speedProgressDivisorPxPerSecond)
        .coerceIn(0f, 1f)
    val baseProgress = fractionalProgress.coerceAtLeast(speedProgress)
    val rawProgress = when {
        isDragging -> baseProgress.coerceAtLeast(motionSpec.refraction.dragProgressFloor)
        baseProgress > motionSpec.refraction.motionDeadzone -> baseProgress
        else -> 0f
    }
    if (rawProgress <= 0f) {
        return BottomBarRefractionMotionProfile(
            progress = 0f,
            exportPanelOffsetFraction = 0f,
            indicatorPanelOffsetFraction = 0f,
            visiblePanelOffsetFraction = 0f,
            visibleSelectionEmphasis = 1f,
            exportSelectionEmphasis = 1f,
            exportCaptureWidthScale = 1f,
            forceChromaticAberration = false,
            indicatorLensAmountScale = 1f,
            indicatorLensHeightScale = 1f
        )
    }

    val progress = (rawProgress * rawProgress * (3f - 2f * rawProgress)).coerceIn(0f, 1f)
    val direction = when {
        abs(velocity) > 24f -> sign(velocity)
        abs(signedFractionalOffset) > 0.001f -> sign(signedFractionalOffset)
        else -> 0f
    }
    val panelOffsetFraction = direction * EaseOut.transform(progress)

    return BottomBarRefractionMotionProfile(
        progress = progress,
        exportPanelOffsetFraction = panelOffsetFraction * 0.5f,
        indicatorPanelOffsetFraction = panelOffsetFraction,
        visiblePanelOffsetFraction = panelOffsetFraction * 0.25f,
        visibleSelectionEmphasis = lerp(1f, 0.38f, progress),
        exportSelectionEmphasis = lerp(1f, 0.72f, progress),
        exportCaptureWidthScale = 1f,
        forceChromaticAberration = progress > 0.02f,
        indicatorLensAmountScale = lerp(1f, 1.18f, progress),
        indicatorLensHeightScale = lerp(1f, 1.08f, progress)
    )
}

internal fun resolveBottomBarMovingIndicatorSurfaceColor(isDarkTheme: Boolean): Color {
    return if (isDarkTheme) {
        Color(0xFFF6F8FB)
    } else {
        Color(0xFFFDFEFF)
    }
}

internal fun resolveBottomBarChromeMaterialMode(
    showGlassEffect: Boolean,
    hasBlur: Boolean,
    preferBlurWhenAvailable: Boolean = false
): TopTabMaterialMode {
    if (preferBlurWhenAvailable && hasBlur) return TopTabMaterialMode.BLUR
    return when {
        showGlassEffect -> TopTabMaterialMode.LIQUID_GLASS
        hasBlur -> TopTabMaterialMode.BLUR
        else -> TopTabMaterialMode.PLAIN
    }
}

internal fun resolveBottomBarIndicatorTintAlpha(
    shouldRefract: Boolean,
    liquidGlassProgress: Float,
    configuredAlpha: Float
): Float {
    if (shouldRefract) return configuredAlpha
    val minAlpha = lerp(
        start = 0.24f,
        stop = 0.32f,
        fraction = liquidGlassProgress.coerceIn(0f, 1f)
    )
    return configuredAlpha.coerceAtLeast(minAlpha)
}

internal fun resolveBottomBarIndicatorTintAlpha(
    shouldRefract: Boolean,
    liquidGlassMode: LiquidGlassMode,
    configuredAlpha: Float
): Float {
    return resolveBottomBarIndicatorTintAlpha(
        shouldRefract = shouldRefract,
        liquidGlassProgress = when (liquidGlassMode) {
            LiquidGlassMode.CLEAR -> 0f
            LiquidGlassMode.BALANCED -> 0.5f
            LiquidGlassMode.FROSTED -> 1f
        },
        configuredAlpha = configuredAlpha
    )
}

internal fun resolveBottomBarIndicatorPolicy(itemCount: Int): BottomBarIndicatorPolicy {
    val topTuning = resolveTopTabVisualTuning()
    return if (itemCount >= 5) {
        BottomBarIndicatorPolicy(
            widthMultiplier = topTuning.floatingIndicatorWidthMultiplier + 0.02f,
            minWidthDp = topTuning.floatingIndicatorMinWidthDp + 2f,
            maxWidthDp = topTuning.floatingIndicatorMaxWidthDp + 2f,
            maxWidthToItemRatio = topTuning.floatingIndicatorMaxWidthToItemRatio + 0.02f,
            clampToBounds = true,
            edgeInsetDp = 2f
        )
    } else {
        BottomBarIndicatorPolicy(
            widthMultiplier = topTuning.floatingIndicatorWidthMultiplier + 0.04f,
            minWidthDp = topTuning.floatingIndicatorMinWidthDp + 4f,
            maxWidthDp = topTuning.floatingIndicatorMaxWidthDp + 4f,
            maxWidthToItemRatio = topTuning.floatingIndicatorMaxWidthToItemRatio + 0.04f,
            clampToBounds = true,
            edgeInsetDp = 2f
        )
    }
}

internal fun resolveBottomIndicatorHeightDp(
    labelMode: Int,
    isTablet: Boolean,
    itemCount: Int
): Float {
    return when {
        labelMode == 0 && isTablet && itemCount >= 5 -> 56f
        labelMode == 0 && isTablet -> 60f
        labelMode == 0 && itemCount >= 5 -> 50f
        labelMode == 0 -> 58f
        else -> 54f
    }
}

internal fun resolveBottomBarLayoutPolicy(
    containerWidth: Dp,
    itemCount: Int,
    isTablet: Boolean,
    labelMode: Int,
    isFloating: Boolean
): BottomBarLayoutPolicy {
    if (!isFloating) {
        return BottomBarLayoutPolicy(
            horizontalPadding = 0.dp,
            rowPadding = 20.dp,
            maxBarWidth = containerWidth
        )
    }

    val safeItemCount = itemCount.coerceAtLeast(1)
    val rowPadding = when {
        isTablet && safeItemCount >= 6 -> 16.dp
        isTablet -> 18.dp
        safeItemCount >= 5 -> 12.dp
        else -> 16.dp
    }
    val normalizedLabelMode = when (labelMode) {
        0, 1, 2 -> labelMode
        else -> 0
    }
    val minItemWidth = when (normalizedLabelMode) {
        0 -> if (isTablet) 62.dp else 52.dp
        2 -> if (isTablet) 60.dp else 52.dp
        else -> if (isTablet) 58.dp else 50.dp
    }
    val preferredItemWidth = when (normalizedLabelMode) {
        0 -> if (isTablet) 84.dp else 80.dp
        2 -> if (isTablet) 80.dp else 74.dp
        else -> if (isTablet) 76.dp else 72.dp
    }
    val minBarWidth = (rowPadding * 2) + (minItemWidth * safeItemCount)
    val preferredBarWidth = (rowPadding * 2) + (preferredItemWidth * safeItemCount)

    val phoneRatio = when {
        safeItemCount >= 6 -> 0.84f
        safeItemCount == 5 -> 0.88f
        safeItemCount == 4 -> 0.92f
        else -> 0.93f
    }
    val widthRatio = if (isTablet) 0.86f else phoneRatio
    val visualCap = containerWidth * widthRatio
    val hardCap = if (isTablet) 640.dp else 432.dp
    val minEdgePadding = if (isTablet) 16.dp else 10.dp
    val containerCap = (containerWidth - (minEdgePadding * 2)).coerceAtLeast(0.dp)
    val maxAllowed = minOf(hardCap, visualCap, containerCap)

    val resolvedBarWidth = maxOf(
        minBarWidth,
        minOf(preferredBarWidth, maxAllowed)
    ).coerceAtMost(containerWidth)

    val horizontalPadding = ((containerWidth - resolvedBarWidth) / 2).coerceAtLeast(0.dp)
    return BottomBarLayoutPolicy(
        horizontalPadding = horizontalPadding,
        rowPadding = rowPadding,
        maxBarWidth = resolvedBarWidth
    )
}

/**
 *  iOS 风格磨砂玻璃底部导航栏
 * 
 * 特性：
 * - 实时磨砂玻璃效果 (使用 Haze 库)
 * - 悬浮圆角设计
 * - 自动适配深色/浅色模式
 * -  点击触觉反馈
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun FrostedBottomBar(
    currentItem: BottomNavItem = BottomNavItem.HOME,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    isFloating: Boolean = true,
    labelMode: Int = 1,
    homeSettings: com.android.purebilibili.core.store.HomeSettings = com.android.purebilibili.core.store.HomeSettings(),
    onHomeDoubleTap: () -> Unit = {},
    onDynamicDoubleTap: () -> Unit = {},
    visibleItems: List<BottomNavItem> = listOf(BottomNavItem.HOME, BottomNavItem.DYNAMIC, BottomNavItem.HISTORY, BottomNavItem.PROFILE),
    itemColorIndices: Map<String, Int> = emptyMap(),
    onToggleSidebar: (() -> Unit)? = null,
    // [NEW] Scroll offset for liquid glass refraction effect
    scrollOffset: Float = 0f,
    // [NEW] LayerBackdrop for real background refraction (captures content behind the bar)
    backdrop: LayerBackdrop? = null,
    motionTier: MotionTier = MotionTier.Normal,
    isTransitionRunning: Boolean = false,
    forceLowBlurBudget: Boolean = false
) {
    if (LocalUiPreset.current == UiPreset.MD3) {
        val androidNativeVariant = LocalAndroidNativeVariant.current
        if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
            MiuixBottomBar(
                currentItem = currentItem,
                onItemClick = onItemClick,
                modifier = modifier,
                visibleItems = visibleItems,
                onToggleSidebar = onToggleSidebar,
                isFloating = isFloating,
                isTablet = com.android.purebilibili.core.util.LocalWindowSizeClass.current.isTablet,
                labelMode = labelMode,
                blurEnabled = hazeState != null,
                hazeState = hazeState,
                backdrop = backdrop,
                homeSettings = homeSettings,
                motionTier = motionTier,
                isTransitionRunning = isTransitionRunning,
                forceLowBlurBudget = forceLowBlurBudget
            )
        } else {
            MaterialBottomBar(
                currentItem = currentItem,
                onItemClick = onItemClick,
                modifier = modifier,
                visibleItems = visibleItems,
                onToggleSidebar = onToggleSidebar,
                isFloating = isFloating,
                isTablet = com.android.purebilibili.core.util.LocalWindowSizeClass.current.isTablet,
                labelMode = labelMode,
                blurEnabled = hazeState != null,
                hazeState = hazeState,
                backdrop = backdrop,
                homeSettings = homeSettings,
                motionTier = motionTier,
                isTransitionRunning = isTransitionRunning,
                forceLowBlurBudget = forceLowBlurBudget
            )
        }
        return
    }

    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.5f // Simple darkness check
    val haptic = rememberHapticFeedback()
    
    // 🔒 [防抖]
    var lastClickTime by remember { mutableStateOf(0L) }
    var lastClickedItem by remember { mutableStateOf<BottomNavItem?>(null) }
    val debounceClick: (BottomNavItem, () -> Unit) -> Unit = remember {
        { item, action ->
            val currentTime = System.currentTimeMillis()
            if (
                shouldAcceptBottomBarTap(
                    tappedItem = item,
                    lastTappedItem = lastClickedItem,
                    currentTimeMillis = currentTime,
                    lastTapTimeMillis = lastClickTime,
                    debounceWindowMillis = 200L
                )
            ) {
                lastClickTime = currentTime
                lastClickedItem = item
                action()
            }
        }
    }
    
    // 📐 [平板适配]
    val windowSizeClass = com.android.purebilibili.core.util.LocalWindowSizeClass.current
    val isTablet = windowSizeClass.isTablet
    val bottomBarMotionSpec = remember(isFloating) {
        resolveBottomBarMotionSpec(
            profile = if (isFloating) {
                BottomBarMotionProfile.IOS_FLOATING
            } else {
                BottomBarMotionProfile.DEFAULT
            }
        )
    }
    
    // 背景颜色
    val blurIntensity = com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity()
    val isActivelyScrolling = kotlin.math.abs(scrollOffset) >= 6f
    
    // 📐 高度计算
    val floatingHeight = resolveBottomBarFloatingHeightDp(
        labelMode = labelMode,
        isTablet = isTablet
    ).dp
    val dockedHeight = when (labelMode) {
        0 -> if (isTablet) 72.dp else 72.dp
        2 -> if (isTablet) 52.dp else 56.dp
        else -> if (isTablet) 64.dp else 64.dp
    }
    
    // 📐 这里把 BoxWithConstraints 提到顶层，以便计算 itemWidth 和 indicator 参数
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
    ) {
        val totalWidth = maxWidth
        // 📐 下边距
        val barBottomPadding = resolveBottomBarBottomPaddingDp(
            isFloating = isFloating,
            isTablet = isTablet
        ).dp
        
        // [平板适配] 侧边栏按钮也算作一个 Item，确保指示器宽度与内容一致。
        val sidebarCount = if (isTablet && onToggleSidebar != null) 1 else 0
        val itemCount = visibleItems.size + sidebarCount
        val layoutPolicy = resolveBottomBarLayoutPolicy(
            containerWidth = totalWidth,
            itemCount = itemCount,
            isTablet = isTablet,
            labelMode = labelMode,
            isFloating = isFloating
        )
        val barHorizontalPadding = layoutPolicy.horizontalPadding
        val rowPadding = layoutPolicy.rowPadding
        val targetMaxWidth = layoutPolicy.maxBarWidth
        
        // 内容宽度需减去 padding
        // 注意：isFloating 时 padding 在 Box 上，docked 时无 padding
        // 但这里我们是在 BoxWithConstraints 内部计算，TotalWidth 是包含 padding 的吗？
        // Modifier 传给了 BottomBar，BoxWithConstraints 用了 modifier。
        // 如果 modifier 有 padding，maxWidth 会减小。
        // 原逻辑是在 internal Box 计算 padding。
        
        // 重新计算可用宽度
        val availableWidth = if (isFloating) {
             totalWidth - (barHorizontalPadding * 2)
        } else {
             totalWidth
        }
        val renderedBarWidth = if (isFloating) minOf(availableWidth, targetMaxWidth) else availableWidth
        val contentWidth = (renderedBarWidth - (rowPadding * 2)).coerceAtLeast(0.dp)
        val itemWidth = if (itemCount > 0) contentWidth / itemCount else 0.dp
        
        // 📐 状态提升：DampedDragAnimationState
        val selectedIndex = visibleItems.indexOf(currentItem)
        val dampedDragState = rememberDampedDragAnimationState(
            initialIndex = if (selectedIndex >= 0) selectedIndex else 0,
            itemCount = itemCount,
            motionSpec = bottomBarMotionSpec,
            onIndexChanged = { index -> 
                if (index in visibleItems.indices) {
                    onItemClick(visibleItems[index])
                } else if (isTablet && onToggleSidebar != null && index == visibleItems.size) {
                    // [Feature] Slide to trigger sidebar
                    onToggleSidebar()
                }
            }
        )
        
        val isValidSelection = selectedIndex >= 0
        val indicatorAlpha by animateFloatAsState(
            targetValue = if (isValidSelection) 1f else 0f,
            label = "indicatorAlpha"
        )
        
        LaunchedEffect(selectedIndex) {
            if (isValidSelection) {
                dampedDragState.updateIndex(selectedIndex)
            }
        }
        
        val density = LocalDensity.current
        val isDarkTheme = isSystemInDarkTheme()

        // 圆角
        val cornerRadiusScale = com.android.purebilibili.core.theme.LocalCornerRadiusScale.current
        val floatingCornerRadius = com.android.purebilibili.core.theme.iOSCornerRadius.Floating * cornerRadiusScale
        val barShape = if (isFloating) RoundedCornerShape(floatingCornerRadius + 8.dp) else RoundedCornerShape(0.dp)
        
        // 垂直偏移
        // 统一对齐策略：所有模式使用同一基线，避免图标与文字上下错位
        val contentVerticalOffset = 0.dp

    // [Fix] 确保指示器互斥显示的最终逻辑
    // 当底栏停靠时，强制禁用液态玻璃（Liquid Glass），仅使用标准磨砂（Frosted Glass）
    val showGlassEffect = homeSettings.isBottomBarLiquidGlassEnabled && isFloating
    val liquidGlassTuning = remember(
        homeSettings.liquidGlassProgress,
        homeSettings.liquidGlassStyle
    ) {
        resolveLiquidGlassTuning(progress = homeSettings.liquidGlassProgress)
    }
    val contentLuminance = remember(showGlassEffect, liquidGlassTuning.progress, isDarkTheme) {
        if (showGlassEffect && liquidGlassTuning.progress > 0.72f) {
            if (isDarkTheme) 0.18f else 0.82f
        } else 0f
    }
    val isGlassSupported = remember { shouldAllowHomeChromeLiquidGlass(android.os.Build.VERSION.SDK_INT) }
    val allowHazeLiquidGlassFallback = remember {
        shouldAllowDirectHazeLiquidGlassFallback(android.os.Build.VERSION.SDK_INT)
    }
    val bottomChromeMaterialMode = resolveBottomBarChromeMaterialMode(
        showGlassEffect = showGlassEffect,
        hasBlur = hazeState != null,
        preferBlurWhenAvailable = false
    )
    val barColor = resolveBottomBarContainerColor(
        surfaceColor = MaterialTheme.colorScheme.surface,
        blurEnabled = hazeState != null,
        blurIntensity = blurIntensity,
        liquidGlassProgress = liquidGlassTuning.progress,
        isGlassEffectEnabled = showGlassEffect
    )
    val bottomChromeRenderMode = remember(
        bottomChromeMaterialMode,
        isGlassSupported,
        backdrop,
        hazeState,
        allowHazeLiquidGlassFallback
    ) {
        resolveHomeTopChromeRenderMode(
            materialMode = bottomChromeMaterialMode,
            isGlassSupported = isGlassSupported,
            hasBackdrop = backdrop != null,
            hasHazeState = hazeState != null,
            allowHazeLiquidGlassFallback = allowHazeLiquidGlassFallback
        )
    }
    // [Refraction] 图标+文字模式下，提高镜片高度并轻微下移，让标签文字稳定进入折射区域
    val bottomIndicatorHeight = resolveBottomIndicatorHeightDp(
        labelMode = labelMode,
        isTablet = isTablet,
        itemCount = itemCount
    ).dp
    // Keep indicator vertically centered; avoid extra offset that breaks top/bottom spacing.
    val bottomIndicatorYOffset = 0.dp
    
    // 🟢 最外层容器
    Box(
        modifier = Modifier
            .fillMaxWidth() // [Fix] Ensure container fills width so Alignment.BottomCenter works
            .padding(horizontal = barHorizontalPadding)
            .padding(bottom = barBottomPadding)
            .then(if (isFloating) Modifier.navigationBarsPadding() else Modifier),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 🟢 Haze 背景容器 (也是 Liquid Glass 的应用目标)
        // 这里的 Modifier 顺序很重要
        Box(
            modifier = Modifier
                .then(
                    if (isFloating) {
                         Modifier
                            .widthIn(max = targetMaxWidth)
                            .shadow(8.dp, barShape, ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            .height(floatingHeight)
                    } else {
                        Modifier
                    }
                )
                .fillMaxWidth()
                .clip(barShape)
                // [Refactor] Removed background modifiers from here to separate layers
        ) {
            // [Layer 1] Glass Background Layer
            // Uses LayerBackdrop to capture and refract background content
            // This creates real refraction of video covers/text when scrolling
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .appChromeLiquidSurface(
                        renderMode = bottomChromeRenderMode,
                        shape = barShape,
                        surfaceColor = barColor,
                        hazeState = hazeState,
                        backdrop = backdrop,
                        liquidStyle = homeSettings.liquidGlassStyle,
                        liquidGlassTuning = liquidGlassTuning,
                        motionTier = motionTier,
                        isScrolling = isActivelyScrolling,
                        isTransitionRunning = isTransitionRunning,
                        forceLowBlurBudget = forceLowBlurBudget,
                        style = AppChromeLiquidSurfaceStyle(
                            blurSurfaceType = BlurSurfaceType.BOTTOM_BAR,
                            depthEffect = isFloating,
                            refractionAmountScrollMultiplier = 0.02f,
                            refractionAmountScrollCap = 14f,
                            surfaceAlphaScrollMultiplier = 0.00015f,
                            surfaceAlphaScrollCap = 0.04f,
                            darkThemeWhiteOverlayMultiplier = 0.86f,
                            useTuningSurfaceAlpha = true,
                            hazeBackgroundAlphaMultiplier = 0.4f
                        )
                    )
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent,
                shape = barShape,
                shadowElevation = 0.dp,
                border = if (!isFloating) {
                    null
                } else {
                    androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                    )
                }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 内容容器 (用于占位高度) - 应用 liquidGlass 效果在这里
                    val isSupported = shouldAllowHomeChromeLiquidGlass(android.os.Build.VERSION.SDK_INT)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isFloating) Modifier.fillMaxHeight() else Modifier.height(dockedHeight))
                            // liquidGlass refracts the icons/text around indicator during horizontal swipe
                            // liquidGlass removed: Refraction now handled by LiquidIndicator using LayerBackdrop
                    ) {
                        // 关键修复：
                        // 1) 移动态时导出一层隐藏的 tint 内容给 capsule 折射
                        // 2) capsule 使用页面 backdrop + tint 内容的 combined backdrop
                        val tintedContentBackdrop = rememberLayerBackdrop()
                        val isDark = isSystemInDarkTheme()
                        val indicatorPosition = dampedDragState.value
                        val indicatorVisualPolicy = resolveBottomBarIndicatorVisualPolicy(
                            position = indicatorPosition,
                            isDragging = dampedDragState.isDragging,
                            velocity = dampedDragState.velocity,
                            useNeutralIndicatorTint = liquidGlassTuning.useNeutralIndicatorTint,
                            motionSpec = bottomBarMotionSpec
                        )
                        val refractionMotionProfile = resolveBottomBarRefractionMotionProfile(
                            position = indicatorPosition,
                            velocity = dampedDragState.velocity,
                            isDragging = dampedDragState.isDragging,
                            motionSpec = bottomBarMotionSpec
                        )
                        val panelOffsetPx = with(density) {
                            bottomBarMotionSpec.refraction.panelOffsetMaxDp.dp.toPx()
                        }
                        val exportPanelOffsetPx = panelOffsetPx *
                            refractionMotionProfile.exportPanelOffsetFraction
                        val indicatorPanelOffsetPx = panelOffsetPx *
                            refractionMotionProfile.indicatorPanelOffsetFraction
                        val visiblePanelOffsetPx = panelOffsetPx *
                            refractionMotionProfile.visiblePanelOffsetFraction
                        val indicatorPolicy = remember(itemCount) {
                            resolveBottomBarIndicatorPolicy(itemCount = itemCount)
                        }
                        val indicatorColor = when {
                            indicatorVisualPolicy.shouldRefract ->
                                resolveBottomBarMovingIndicatorSurfaceColor(isDarkTheme = isDark)
                            indicatorVisualPolicy.useNeutralTint ->
                                resolveIos26BottomIndicatorGrayColor(isDarkTheme = isDark)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val indicatorTintAlpha = resolveBottomBarIndicatorTintAlpha(
                            shouldRefract = indicatorVisualPolicy.shouldRefract,
                            liquidGlassProgress = liquidGlassTuning.progress,
                            configuredAlpha = liquidGlassTuning.indicatorTintAlpha
                        )
                        val refractionLayerPolicy = resolveBottomBarRefractionLayerPolicy(
                            isFloating = isFloating,
                            isLiquidGlassEnabled = showGlassEffect,
                            indicatorVisualPolicy = indicatorVisualPolicy
                        )
                        val combinedIndicatorBackdrop = when {
                            refractionLayerPolicy.useCombinedBackdrop && backdrop != null ->
                                rememberCombinedBackdrop(backdrop, tintedContentBackdrop)
                            else -> null
                        }
                        val indicatorBackdrop: Backdrop? = when {
                            !indicatorVisualPolicy.shouldRefract -> null
                            combinedIndicatorBackdrop != null -> combinedIndicatorBackdrop
                            refractionLayerPolicy.captureTintedContentLayer -> tintedContentBackdrop
                            else -> backdrop
                        }
                        LiquidIndicator(
                            position = indicatorPosition,
                            itemWidth = itemWidth,
                            itemCount = itemCount,
                            // Keep refraction active during in-flight horizontal motion (drag + settle).
                            isDragging = indicatorVisualPolicy.isInMotion,
                            velocity = dampedDragState.velocity,
                            startPadding = rowPadding,
                            viewportShiftPx = indicatorPanelOffsetPx,
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(y = bottomIndicatorYOffset)
                                .alpha(indicatorAlpha),
                            clampToBounds = indicatorPolicy.clampToBounds,
                            edgeInset = indicatorPolicy.edgeInsetDp.dp,
                            indicatorWidthMultiplier = indicatorPolicy.widthMultiplier,
                            indicatorMinWidth = indicatorPolicy.minWidthDp.dp,
                            indicatorMaxWidth = indicatorPolicy.maxWidthDp.dp,
                            maxWidthToItemRatio = indicatorPolicy.maxWidthToItemRatio,
                            indicatorHeight = bottomIndicatorHeight,
                            isLiquidGlassEnabled = showGlassEffect,
                            lensIntensityBoost = liquidGlassTuning.indicatorLensBoost,
                            edgeWarpBoost = liquidGlassTuning.indicatorEdgeWarpBoost,
                            chromaticBoost = liquidGlassTuning.indicatorChromaticBoost,
                            liquidGlassStyle = homeSettings.liquidGlassStyle, // [New] Pass style
                            liquidGlassTuning = liquidGlassTuning,
                            motionSpec = bottomBarMotionSpec,
                            // Dynamic refraction: moving -> refract icons/text/cover, static -> keep pure color.
                            backdrop = indicatorBackdrop,
                            lensAmountScale = refractionMotionProfile.indicatorLensAmountScale,
                            lensHeightScale = refractionMotionProfile.indicatorLensHeightScale,
                            forceChromaticAberration = refractionLayerPolicy.useCombinedBackdrop &&
                                refractionMotionProfile.forceChromaticAberration,
                            color = indicatorColor.copy(alpha = indicatorTintAlpha)
                        )

                        if (refractionLayerPolicy.captureTintedContentLayer) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clearAndSetSemantics {}
                                    .alpha(0f)
                                    .layerBackdrop(tintedContentBackdrop)
                                    .graphicsLayer {
                                        this.translationX = exportPanelOffsetPx
                                        this.scaleX = refractionMotionProfile.exportCaptureWidthScale
                                    }
                            ) {
                                BottomBarContent(
                                    visibleItems = visibleItems,
                                    selectedIndex = selectedIndex,
                                    itemColorIndices = itemColorIndices,
                                    onItemClick = onItemClick,
                                    onToggleSidebar = onToggleSidebar,
                                    isTablet = isTablet,
                                    labelMode = labelMode,
                                    hazeState = hazeState,
                                    haptic = haptic,
                                    debounceClick = debounceClick,
                                    onHomeDoubleTap = onHomeDoubleTap,
                                    onDynamicDoubleTap = onDynamicDoubleTap,
                                    itemWidth = itemWidth,
                                    rowPadding = rowPadding,
                                    contentVerticalOffset = contentVerticalOffset,
                                    isInteractive = false,
                                    currentPosition = indicatorPosition,
                                    contentLuminance = contentLuminance,
                                    liquidGlassStyle = homeSettings.liquidGlassStyle,
                                    liquidGlassTuning = liquidGlassTuning,
                                    selectionEmphasis = refractionMotionProfile.exportSelectionEmphasis
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { this.translationX = visiblePanelOffsetPx }
                        ) {
                            BottomBarContent(
                                visibleItems = visibleItems,
                                selectedIndex = selectedIndex,
                                itemColorIndices = itemColorIndices,
                                onItemClick = onItemClick,
                                onToggleSidebar = onToggleSidebar,
                                isTablet = isTablet,
                                labelMode = labelMode,
                                hazeState = hazeState,
                                haptic = haptic,
                                debounceClick = debounceClick,
                                onHomeDoubleTap = onHomeDoubleTap,
                                onDynamicDoubleTap = onDynamicDoubleTap,
                                itemWidth = itemWidth,
                                rowPadding = rowPadding,
                                contentVerticalOffset = contentVerticalOffset,
                                isInteractive = true,
                                currentPosition = dampedDragState.value,
                                dragModifier = Modifier.horizontalDragGesture(
                                    dragState = dampedDragState,
                                    itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }
                                ),
                                // [New] Param for adaptive text color
                                contentLuminance = contentLuminance,
                                liquidGlassStyle = homeSettings.liquidGlassStyle,
                                liquidGlassTuning = liquidGlassTuning,
                                selectionEmphasis = refractionMotionProfile.visibleSelectionEmphasis
                            )
                        }
                    }
                        
                        if (!isFloating) {
                             Spacer(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialBottomBar(
    currentItem: BottomNavItem,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    visibleItems: List<BottomNavItem>,
    onToggleSidebar: (() -> Unit)?,
    isFloating: Boolean,
    isTablet: Boolean,
    labelMode: Int,
    blurEnabled: Boolean,
    hazeState: HazeState?,
    backdrop: LayerBackdrop?,
    homeSettings: com.android.purebilibili.core.store.HomeSettings,
    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean
) {
    val haptic = rememberHapticFeedback()
    val normalizedLabelMode = normalizeBottomBarLabelMode(labelMode)
    val showIcon = shouldShowBottomBarIcon(normalizedLabelMode)
    val showText = shouldShowBottomBarText(normalizedLabelMode)
    val glassEnabled = resolveAndroidNativeBottomBarGlassEnabled(
        liquidGlassEnabled = homeSettings.isBottomBarLiquidGlassEnabled,
        blurEnabled = blurEnabled
    )
    val androidNativeTuning = resolveAndroidNativeBottomBarTuning(
        blurEnabled = glassEnabled || blurEnabled,
        darkTheme = isSystemInDarkTheme()
    )
    val blurIntensity = currentUnifiedBlurIntensity()
    val baseSurfaceColor = if (isFloating) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val containerColor = if (isFloating) {
        resolveAndroidNativeFloatingBottomBarContainerColor(
            surfaceColor = baseSurfaceColor,
            tuning = androidNativeTuning,
            glassEnabled = glassEnabled,
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity
        )
    } else {
        resolveBottomBarSurfaceColor(
            surfaceColor = baseSurfaceColor,
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity
        )
    }

    if (isFloating) {
        KernelSuAlignedBottomBar(
            currentItem = currentItem,
            onItemClick = onItemClick,
            modifier = modifier,
            visibleItems = visibleItems,
            onToggleSidebar = onToggleSidebar,
            isTablet = isTablet,
            showIcon = showIcon,
            showText = showText,
            blurEnabled = blurEnabled,
            backdrop = backdrop,
            containerColor = containerColor,
            tuning = androidNativeTuning,
            glassEnabled = glassEnabled,
            haptic = haptic
        )
        return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (blurEnabled && hazeState != null) {
                    Modifier.unifiedBlur(
                        hazeState = hazeState,
                        surfaceType = BlurSurfaceType.BOTTOM_BAR,
                        motionTier = motionTier,
                        isScrolling = false,
                        isTransitionRunning = isTransitionRunning,
                        forceLowBudget = forceLowBlurBudget
                    )
                } else {
                    Modifier
                }
            ),
        tonalElevation = if (blurEnabled) 0.dp else 3.dp,
        shadowElevation = 0.dp,
        color = containerColor
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            visibleItems.forEach { item ->
                val itemLabel = resolveBottomNavItemLabel(item)
                val itemContentDescription = resolveBottomNavItemContentDescription(item)
                NavigationBarItem(
                    selected = currentItem == item,
                    onClick = {
                        performMaterialBottomBarTap(
                            haptic = haptic,
                            onClick = { onItemClick(item) }
                        )
                    },
                    icon = {
                        if (showIcon) {
                            Icon(
                                imageVector = resolveMaterialBottomBarIcon(item = item, selected = currentItem == item),
                                contentDescription = itemContentDescription
                            )
                        } else {
                            Spacer(modifier = Modifier.size(0.dp))
                        }
                    },
                    label = if (showText) {
                        { Text(itemLabel) }
                    } else {
                        null
                    },
                    alwaysShowLabel = showText,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            if (isTablet && onToggleSidebar != null) {
                val sidebarLabel = stringResource(R.string.sidebar_toggle)
                NavigationBarItem(
                    selected = false,
                    onClick = {
                        performMaterialBottomBarTap(
                            haptic = haptic,
                            onClick = onToggleSidebar
                        )
                    },
                    icon = {
                        if (showIcon) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.MenuOpen,
                                contentDescription = sidebarLabel
                            )
                        } else {
                            Spacer(modifier = Modifier.size(0.dp))
                        }
                    },
                    label = if (showText) {
                        { Text(sidebarLabel) }
                    } else {
                        null
                    },
                    alwaysShowLabel = showText,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun MiuixBottomBar(
    currentItem: BottomNavItem,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    visibleItems: List<BottomNavItem>,
    onToggleSidebar: (() -> Unit)?,
    isFloating: Boolean,
    isTablet: Boolean,
    labelMode: Int,
    blurEnabled: Boolean,
    hazeState: HazeState?,
    backdrop: LayerBackdrop?,
    homeSettings: com.android.purebilibili.core.store.HomeSettings,
    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean
) {
    val haptic = rememberHapticFeedback()
    val normalizedLabelMode = normalizeBottomBarLabelMode(labelMode)
    val showIcon = shouldShowBottomBarIcon(normalizedLabelMode)
    val showText = shouldShowBottomBarText(normalizedLabelMode)
    val displayMode = resolveMd3BottomBarDisplayMode(labelMode).toMiuixNavigationDisplayMode()
    val glassEnabled = resolveAndroidNativeBottomBarGlassEnabled(
        liquidGlassEnabled = homeSettings.isBottomBarLiquidGlassEnabled,
        blurEnabled = blurEnabled
    )
    val tuning = resolveAndroidNativeBottomBarTuning(
        blurEnabled = glassEnabled || blurEnabled,
        darkTheme = isSystemInDarkTheme()
    )
    val blurIntensity = currentUnifiedBlurIntensity()
    val baseSurfaceColor = if (isFloating) {
        MiuixTheme.colorScheme.surfaceContainer
    } else {
        MiuixTheme.colorScheme.surface
    }
    val containerColor = if (isFloating) {
        resolveAndroidNativeFloatingBottomBarContainerColor(
            surfaceColor = baseSurfaceColor,
            tuning = tuning,
            glassEnabled = glassEnabled,
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity
        )
    } else {
        resolveBottomBarSurfaceColor(
            surfaceColor = baseSurfaceColor,
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity
        )
    }
    val floatingChromeSpec = resolveMd3BottomBarFloatingChromeSpec(isFloating = true)

    if (isFloating) {
        KernelSuAlignedBottomBar(
            currentItem = currentItem,
            onItemClick = onItemClick,
            modifier = modifier,
            visibleItems = visibleItems,
            onToggleSidebar = onToggleSidebar,
            isTablet = isTablet,
            showIcon = showIcon,
            showText = showText,
            blurEnabled = blurEnabled,
            backdrop = backdrop,
            containerColor = containerColor,
            tuning = tuning,
            glassEnabled = glassEnabled,
            iconStyle = SharedFloatingBottomBarIconStyle.CUPERTINO,
            haptic = haptic,
            hazeState = hazeState,
            motionTier = motionTier,
            isTransitionRunning = isTransitionRunning,
            forceLowBlurBudget = forceLowBlurBudget
        )
        return
    }

    val barModifier = modifier
        .fillMaxWidth()
        .then(
            if (blurEnabled && hazeState != null) {
                Modifier.unifiedBlur(
                    hazeState = hazeState,
                    surfaceType = BlurSurfaceType.BOTTOM_BAR,
                    motionTier = motionTier,
                    isScrolling = false,
                    isTransitionRunning = isTransitionRunning,
                    forceLowBudget = forceLowBlurBudget
                )
            } else {
                Modifier
            }
        )

    MiuixFloatingNavigationBar(
        modifier = barModifier,
        color = containerColor,
        cornerRadius = if (isFloating) floatingChromeSpec.cornerRadiusDp.dp else 0.dp,
        horizontalAlignment = Alignment.CenterHorizontally,
        horizontalOutSidePadding = if (isFloating) {
            floatingChromeSpec.horizontalOutsidePaddingDp.dp
        } else {
            0.dp
        },
        shadowElevation = if (isFloating) floatingChromeSpec.shadowElevationDp.dp else 0.dp,
        showDivider = false,
        defaultWindowInsetsPadding = true,
        mode = displayMode
    ) {
        visibleItems.forEach { item ->
            val itemLabel = resolveBottomNavItemLabel(item)
            MiuixBottomBarItemShell(
                selected = currentItem == item,
                label = itemLabel
            ) {
                MiuixFloatingNavigationBarItem(
                    selected = currentItem == item,
                    onClick = {
                        performMaterialBottomBarTap(
                            haptic = haptic,
                            onClick = { onItemClick(item) }
                        )
                    },
                    icon = resolveMaterialBottomBarIcon(item, currentItem == item),
                    label = itemLabel
                )
            }
        }

        if (isTablet && onToggleSidebar != null) {
            val sidebarLabel = stringResource(R.string.sidebar_toggle)
            MiuixBottomBarItemShell(
                selected = false,
                label = sidebarLabel
            ) {
                MiuixFloatingNavigationBarItem(
                    selected = false,
                    onClick = {
                        performMaterialBottomBarTap(
                            haptic = haptic,
                            onClick = onToggleSidebar
                        )
                    },
                    icon = Icons.AutoMirrored.Outlined.MenuOpen,
                    label = sidebarLabel
                )
            }
        }
    }
}

@Composable
private fun MiuixFloatingCapsuleBottomBar(
    currentItem: BottomNavItem,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    visibleItems: List<BottomNavItem>,
    onToggleSidebar: (() -> Unit)?,
    isTablet: Boolean,
    showIcon: Boolean,
    showText: Boolean,
    blurEnabled: Boolean,
    backdrop: Backdrop?,
    containerColor: Color,
    tuning: AndroidNativeBottomBarTuning,
    glassEnabled: Boolean,
    haptic: (HapticType) -> Unit
) {
    val density = LocalDensity.current
    val darkTheme = isSystemInDarkTheme()
    val bottomBarMotionSpec = remember {
        resolveBottomBarMotionSpec(profile = BottomBarMotionProfile.MIUI_FLOATING)
    }
    val visualTuning = remember(darkTheme, glassEnabled) {
        resolveMiuixFloatingBottomBarTuning(
            darkTheme = darkTheme,
            glassEnabled = glassEnabled
        )
    }
    val shellShape = RoundedCornerShape(visualTuning.cornerRadiusDp.dp)
    val allItems = remember(visibleItems, isTablet, onToggleSidebar) {
        buildList {
            addAll(visibleItems)
            if (isTablet && onToggleSidebar != null) add(null)
        }
    }
    val totalItems = allItems.size.coerceAtLeast(1)
    val selectedIndex = visibleItems.indexOf(currentItem).coerceAtLeast(0)
    val dampedDragState = rememberDampedDragAnimationState(
        initialIndex = selectedIndex,
        itemCount = totalItems,
        motionSpec = bottomBarMotionSpec,
        onIndexChanged = { index ->
            when {
                index in visibleItems.indices -> onItemClick(visibleItems[index])
                isTablet && onToggleSidebar != null && index == visibleItems.size -> onToggleSidebar()
            }
        }
    )
    LaunchedEffect(selectedIndex) {
        dampedDragState.updateIndex(selectedIndex)
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val selectedAccent = remember(primaryColor, darkTheme) {
        resolveAndroidNativeIndicatorColor(
            themeColor = primaryColor,
            darkTheme = darkTheme
        ).copy(alpha = if (darkTheme) 0.78f else 0.70f)
    }
    val selectedColor = primaryColor
    val unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = visualTuning.outerHorizontalPaddingDp.dp,
                    end = visualTuning.outerHorizontalPaddingDp.dp,
                    bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
        ) {
            val shellHeight = visualTuning.shellHeightDp.dp
            val indicatorWidth = (maxWidth - (visualTuning.innerHorizontalPaddingDp.dp * 2)) / totalItems
            val itemWidthPx = with(density) { indicatorWidth.toPx() }.coerceAtLeast(1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(shellHeight)
                    .run {
                        if (backdrop != null) {
                            drawBackdrop(
                                backdrop = backdrop,
                                shape = { shellShape },
                                effects = {
                                    if (glassEnabled || blurEnabled) {
                                        vibrancy()
                                        blur(visualTuning.shellBlurRadiusDp.dp.toPx())
                                    }
                                },
                                highlight = {
                                    Highlight.Default.copy(alpha = if (glassEnabled) 0.9f else 0.18f)
                                },
                                shadow = {
                                    Shadow.Default.copy(
                                        color = Color.Black.copy(alpha = if (darkTheme) 0.26f else 0.14f)
                                    )
                                },
                                onDrawSurface = {
                                    drawRect(containerColor)
                                }
                            )
                        } else {
                            background(containerColor, shellShape)
                        }
                    }
                    .clip(shellShape)
                    .border(
                        width = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (darkTheme) 0.26f else 0.18f),
                        shape = shellShape
                    )
                    .padding(horizontal = visualTuning.innerHorizontalPaddingDp.dp)
            ) {
                if (selectedIndex in visibleItems.indices) {
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorWidth * dampedDragState.value)
                            .width(indicatorWidth)
                            .fillMaxHeight()
                            .padding(vertical = ((shellHeight - visualTuning.indicatorHeightDp.dp) / 2))
                            .graphicsLayer {
                                val fraction = (dampedDragState.dragOffset / itemWidthPx).coerceIn(-1f, 1f)
                                scaleX = 1f + (
                                    abs(fraction) *
                                        bottomBarMotionSpec.indicator.railFractionStretchMultiplier
                                    )
                            }
                            .background(selectedAccent, shellShape)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalDragGesture(
                            dragState = dampedDragState,
                            itemWidthPx = itemWidthPx
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleItems.forEach { item ->
                        MiuixFloatingBottomBarItem(
                            item = item,
                            selected = currentItem == item,
                            showIcon = showIcon,
                            showText = showText,
                            isTablet = isTablet,
                            selectedColor = selectedColor,
                            unselectedColor = unselectedColor,
                            onClick = {
                                performMaterialBottomBarTap(
                                    haptic = haptic,
                                    onClick = { onItemClick(item) }
                                )
                            },
                            interactive = true
                        )
                    }

                    if (isTablet && onToggleSidebar != null) {
                        MiuixFloatingBottomBarItem(
                            item = null,
                            labelOverride = stringResource(R.string.sidebar_toggle),
                            selected = false,
                            showIcon = showIcon,
                            showText = showText,
                            isTablet = isTablet,
                            selectedColor = selectedColor,
                            unselectedColor = unselectedColor,
                            onClick = {
                                performMaterialBottomBarTap(
                                    haptic = haptic,
                                    onClick = onToggleSidebar
                                )
                            },
                            interactive = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KernelSuAlignedBottomBar(
    currentItem: BottomNavItem,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    visibleItems: List<BottomNavItem>,
    onToggleSidebar: (() -> Unit)?,
    isTablet: Boolean,
    showIcon: Boolean,
    showText: Boolean,
    blurEnabled: Boolean,
    backdrop: Backdrop?,
    containerColor: Color,
    tuning: AndroidNativeBottomBarTuning,
    glassEnabled: Boolean,
    iconStyle: SharedFloatingBottomBarIconStyle = SharedFloatingBottomBarIconStyle.MATERIAL,
    haptic: (HapticType) -> Unit,
    hazeState: HazeState? = null,
    motionTier: MotionTier = MotionTier.Normal,
    isTransitionRunning: Boolean = false,
    forceLowBlurBudget: Boolean = false
) {
    val shellShape = resolveSharedBottomBarCapsuleShape()
    val tintedContentBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val bottomBarMotionSpec = remember {
        resolveBottomBarMotionSpec(profile = BottomBarMotionProfile.ANDROID_NATIVE_FLOATING)
    }
    val allItems = remember(visibleItems, isTablet, onToggleSidebar) {
        buildList {
            addAll(visibleItems)
            if (isTablet && onToggleSidebar != null) add(null)
        }
    }
    val selectedIndex = visibleItems.indexOf(currentItem).coerceAtLeast(0)
    val isDarkTheme = isSystemInDarkTheme()
    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val indicatorColor = remember(selectedColor, isDarkTheme) {
        resolveAndroidNativeIndicatorColor(
            themeColor = selectedColor,
            darkTheme = isDarkTheme
        )
    }
    val exportTintColor = remember(selectedColor, isDarkTheme) {
        resolveAndroidNativeExportTintColor(
            themeColor = selectedColor,
            darkTheme = isDarkTheme
        )
    }
    val totalItems = allItems.size.coerceAtLeast(1)
    val dampedDragState = rememberDampedDragAnimationState(
        initialIndex = selectedIndex,
        itemCount = totalItems,
        motionSpec = bottomBarMotionSpec,
        onIndexChanged = { index ->
            when {
                index in visibleItems.indices -> onItemClick(visibleItems[index])
                isTablet && onToggleSidebar != null && index == visibleItems.size -> onToggleSidebar()
            }
        }
    )
    LaunchedEffect(selectedIndex) {
        dampedDragState.updateIndex(selectedIndex)
    }
    val indicatorVisualPolicy by remember {
        derivedStateOf {
            resolveBottomBarIndicatorVisualPolicy(
                position = dampedDragState.value,
                isDragging = dampedDragState.isDragging,
                velocity = dampedDragState.velocity,
                useNeutralIndicatorTint = false,
                motionSpec = bottomBarMotionSpec
            )
        }
    }
    val refractionLayerPolicy by remember {
        derivedStateOf {
            resolveBottomBarRefractionLayerPolicy(
                isFloating = true,
                isLiquidGlassEnabled = glassEnabled,
                indicatorVisualPolicy = indicatorVisualPolicy
            )
        }
    }
    val refractionMotionProfile by remember {
        derivedStateOf {
            resolveBottomBarRefractionMotionProfile(
                position = dampedDragState.value,
                velocity = dampedDragState.velocity,
                isDragging = dampedDragState.isDragging,
                motionSpec = bottomBarMotionSpec
            )
        }
    }
    val contentBackdrop = when {
        refractionLayerPolicy.useCombinedBackdrop && backdrop != null ->
            rememberCombinedBackdrop(backdrop, tintedContentBackdrop)
        refractionLayerPolicy.captureTintedContentLayer -> tintedContentBackdrop
        else -> backdrop
    }
    // Offset values will be calculated inside BoxWithConstraints
    val motionProgress by remember {
        derivedStateOf { refractionMotionProfile.progress }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = tuning.outerHorizontalPaddingDp.dp,
                    end = tuning.outerHorizontalPaddingDp.dp,
                    bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
        ) {
            val shellHeight = 64.dp
            val indicatorWidth = (maxWidth - (tuning.innerHorizontalPaddingDp.dp * 2)) / totalItems
            val itemWidthPx = with(density) { indicatorWidth.toPx() }.coerceAtLeast(1f)
            val useHazeBlur = shouldUseAndroidNativeFloatingHazeBlur(
                blurEnabled = blurEnabled,
                glassEnabled = glassEnabled,
                hasHazeState = hazeState != null
            )

            val exportPanelOffsetPx by remember(density, itemWidthPx) {
                derivedStateOf {
                    with(density) { bottomBarMotionSpec.refraction.panelOffsetMaxDp.dp.toPx() } *
                        refractionMotionProfile.exportPanelOffsetFraction
                }
            }
            val indicatorPanelOffsetPx by remember(density, itemWidthPx) {
                derivedStateOf {
                    with(density) { bottomBarMotionSpec.refraction.panelOffsetMaxDp.dp.toPx() } *
                        refractionMotionProfile.indicatorPanelOffsetFraction
                }
            }
            val visiblePanelOffsetPx by remember(density, itemWidthPx) {
                derivedStateOf {
                    with(density) { bottomBarMotionSpec.refraction.panelOffsetMaxDp.dp.toPx() } *
                        refractionMotionProfile.visiblePanelOffsetFraction
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(shellHeight)
                    .then(
                        if (useHazeBlur && hazeState != null) {
                            Modifier.unifiedBlur(
                                hazeState = hazeState,
                                shape = shellShape,
                                surfaceType = BlurSurfaceType.BOTTOM_BAR,
                                motionTier = motionTier,
                                isScrolling = false,
                                isTransitionRunning = isTransitionRunning,
                                forceLowBudget = forceLowBlurBudget
                            )
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer {
                        val progress = dampedDragState.pressProgress
                        val bumpScale = androidx.compose.ui.util.lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = bumpScale
                        scaleY = bumpScale
                    }
                    .run {
                        if (backdrop != null && !useHazeBlur) {
                            drawBackdrop(
                                backdrop = backdrop,
                                shape = { shellShape },
                                effects = {
                                    if (glassEnabled || (blurEnabled && !useHazeBlur)) {
                                        vibrancy()
                                        blur(tuning.shellBlurRadiusDp.dp.toPx())
                                    }
                                },
                                highlight = {
                                    Highlight.Default.copy(
                                        alpha = if (glassEnabled) {
                                            1f
                                        } else if (blurEnabled && !useHazeBlur) {
                                            0.18f
                                        } else {
                                            0f
                                        }
                                    )
                                },
                                shadow = {
                                    Shadow.Default.copy(
                                        color = Color.Black.copy(alpha = tuning.shellShadowElevationDp)
                                    )
                                },
                                onDrawSurface = {
                                    drawRect(containerColor)
                                }
                            )
                        } else {
                            background(containerColor, shellShape)
                        }
                    }
                    .clip(shellShape)
                    .padding(tuning.innerHorizontalPaddingDp.dp)
            ) {
                if (refractionLayerPolicy.captureTintedContentLayer) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clearAndSetSemantics {}
                            .alpha(0f)
                            .layerBackdrop(tintedContentBackdrop)
                            .graphicsLayer {
                                this.translationX = exportPanelOffsetPx
                                this.scaleX = refractionMotionProfile.exportCaptureWidthScale
                            }
                            .run {
                                if (backdrop != null) {
                                    drawBackdrop(
                                        backdrop = backdrop,
                                        shape = { shellShape },
                                        effects = {
                                            if (glassEnabled) {
                                                vibrancy()
                                                blur(8.dp.toPx())
                                                lens(
                                                    24.dp.toPx() * motionProgress,
                                                    24.dp.toPx() * motionProgress
                                                )
                                            }
                                        },
                                        highlight = {
                                            Highlight.Default.copy(alpha = motionProgress)
                                        },
                                        onDrawSurface = {
                                            drawRect(containerColor)
                                        }
                                    )
                                } else {
                                    this
                                }
                            }
                            .graphicsLayer(colorFilter = ColorFilter.tint(exportTintColor))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            visibleItems.forEach { item ->
                                AndroidNativeBottomBarItem(
                                    item = item,
                                    label = resolveBottomNavItemLabel(item),
                                    selected = currentItem == item,
                                    showIcon = showIcon,
                                    showText = showText,
                                    selectedColor = selectedColor,
                                    unselectedColor = unselectedColor,
                                    iconStyle = iconStyle,
                                    onClick = {},
                                    interactive = false
                                )
                            }

                            if (isTablet && onToggleSidebar != null) {
                                AndroidNativeBottomBarItem(
                                    item = null,
                                    label = stringResource(R.string.sidebar_toggle),
                                    selected = false,
                                    showIcon = showIcon,
                                    showText = showText,
                                    selectedColor = selectedColor,
                                    unselectedColor = unselectedColor,
                                    iconStyle = iconStyle,
                                    onClick = {},
                                    interactive = false
                                )
                            }
                        }
                    }
                }

                if (selectedIndex in visibleItems.indices) {
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorWidth * dampedDragState.value)
                            .graphicsLayer { 
                                this.translationX = indicatorPanelOffsetPx 
                                val normalizedVelocity =
                                    dampedDragState.velocity /
                                        bottomBarMotionSpec.indicator.capsuleVelocityNormalizationDivisor
                                val maxDelta = bottomBarMotionSpec.indicator.capsuleVelocityClamp
                                this.scaleX = 1f / (
                                    1f - (
                                        normalizedVelocity *
                                            bottomBarMotionSpec.indicator.capsuleVelocityScaleXMultiplier
                                        ).coerceIn(-maxDelta, maxDelta)
                                    )
                                this.scaleY = 1f * (
                                    1f - (
                                        normalizedVelocity *
                                            bottomBarMotionSpec.indicator.capsuleVelocityScaleYMultiplier
                                        ).coerceIn(-maxDelta, maxDelta)
                                    )
                            }
                            .width(indicatorWidth)
                            .fillMaxHeight()
                            .padding(vertical = ((shellHeight - tuning.indicatorHeightDp.dp) / 2))
                            .background(indicatorColor, shellShape)
                            .run {
                                if (glassEnabled && contentBackdrop != null) {
                                    drawBackdrop(
                                        backdrop = contentBackdrop,
                                        shape = { shellShape },
                                        effects = {
                                            blur(tuning.shellBlurRadiusDp.dp.toPx())
                                            lens(
                                                tuning.indicatorLensRadiusDp.dp.toPx() *
                                                    motionProgress.coerceAtLeast(0.16f) *
                                                    refractionMotionProfile.indicatorLensHeightScale.coerceAtLeast(0.18f),
                                                14.dp.toPx() *
                                                    motionProgress.coerceAtLeast(0.16f) *
                                                    refractionMotionProfile.indicatorLensAmountScale.coerceAtLeast(0.22f),
                                                true,
                                                chromaticAberration = true
                                            )
                                        },
                                        highlight = {
                                            Highlight.Default.copy(alpha = motionProgress)
                                        },
                                        onDrawSurface = {
                                            drawRect(indicatorColor)
                                        }
                                    )
                                } else {
                                    this
                                }
                            }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { this.translationX = visiblePanelOffsetPx }
                        .horizontalDragGesture(
                            dragState = dampedDragState,
                            itemWidthPx = itemWidthPx
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleItems.forEach { item ->
                        AndroidNativeBottomBarItem(
                            item = item,
                            label = resolveBottomNavItemLabel(item),
                            selected = currentItem == item,
                            showIcon = showIcon,
                            showText = showText,
                            selectedColor = selectedColor,
                            unselectedColor = unselectedColor,
                            iconStyle = iconStyle,
                            onClick = {
                                performMaterialBottomBarTap(
                                    haptic = haptic,
                                    onClick = { onItemClick(item) }
                                )
                            },
                            interactive = true
                        )
                    }

                    if (isTablet && onToggleSidebar != null) {
                        AndroidNativeBottomBarItem(
                            item = null,
                            label = stringResource(R.string.sidebar_toggle),
                            selected = false,
                            showIcon = showIcon,
                            showText = showText,
                            selectedColor = selectedColor,
                            unselectedColor = unselectedColor,
                            iconStyle = iconStyle,
                            onClick = {
                                performMaterialBottomBarTap(
                                    haptic = haptic,
                                    onClick = onToggleSidebar
                                )
                            },
                            interactive = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.AndroidNativeBottomBarItem(
    item: BottomNavItem?,
    label: String,
    selected: Boolean,
    showIcon: Boolean,
    showText: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    iconStyle: SharedFloatingBottomBarIconStyle,
    onClick: () -> Unit,
    interactive: Boolean
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        label = "${label}_android_native_bottom_bar_color"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(resolveSharedBottomBarCapsuleShape())
            .then(
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (showIcon) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    when {
                        item == null && iconStyle == SharedFloatingBottomBarIconStyle.CUPERTINO -> {
                            Icon(
                                imageVector = CupertinoIcons.Outlined.SidebarLeft,
                                contentDescription = label
                            )
                        }
                        item == null -> {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.MenuOpen,
                                contentDescription = label
                            )
                        }
                        iconStyle == SharedFloatingBottomBarIconStyle.CUPERTINO -> {
                            if (selected) item.selectedIcon() else item.unselectedIcon()
                        }
                        else -> {
                            Icon(
                                imageVector = resolveMaterialBottomBarIcon(item, selected),
                                contentDescription = label
                            )
                        }
                    }
                }
            }
            if (showText) {
                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun MiuixBottomBarItemShell(
    selected: Boolean,
    label: String,
    content: @Composable () -> Unit
) {
    val capsuleColor by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)
        } else {
            Color.Transparent
        },
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
        label = "${label}_capsule_color"
    )
    val capsuleScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
        label = "${label}_capsule_scale"
    )
    val capsuleElevation by animateDpAsState(
        targetValue = if (selected) 6.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
        label = "${label}_capsule_elevation"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = capsuleScale
                scaleY = capsuleScale
            }
            .shadow(
                elevation = capsuleElevation,
                shape = RoundedCornerShape(28.dp),
                clip = false
            )
            .clip(RoundedCornerShape(28.dp))
            .background(capsuleColor)
            .padding(horizontal = 4.dp)
    ) {
        content()
    }
}

@Composable
private fun RowScope.MiuixFloatingBottomBarItem(
    item: BottomNavItem?,
    selected: Boolean,
    showIcon: Boolean,
    showText: Boolean,
    isTablet: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    onClick: () -> Unit,
    interactive: Boolean,
    labelOverride: String? = null
) {
    val label = labelOverride ?: item?.let { resolveBottomNavItemLabel(it) }.orEmpty()
    val contentColor by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        label = "${label}_miuix_floating_bottom_bar_color"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(resolveSharedBottomBarCapsuleShape())
            .then(
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (showIcon) {
                Box(
                    modifier = Modifier
                        .size(if (showText) 22.dp else 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColor) {
                        when {
                            item == null -> Icon(
                                imageVector = CupertinoIcons.Outlined.SidebarLeft,
                                contentDescription = label,
                                modifier = Modifier.fillMaxSize()
                            )
                            selected -> item.selectedIcon()
                            else -> item.unselectedIcon()
                        }
                    }
                }
            }
            if (showText) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = if (isTablet) 12.sp else 11.sp,
                    lineHeight = if (isTablet) 12.sp else 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

private fun resolveMaterialBottomBarIcon(
    item: BottomNavItem,
    selected: Boolean
): ImageVector = when (item) {
    BottomNavItem.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    BottomNavItem.DYNAMIC -> if (selected) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone
    BottomNavItem.STORY -> if (selected) Icons.Filled.PlayCircle else Icons.Outlined.PlayCircleOutline
    BottomNavItem.HISTORY -> if (selected) Icons.Filled.History else Icons.Outlined.History
    BottomNavItem.PROFILE -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
    BottomNavItem.FAVORITE -> if (selected) Icons.Filled.CollectionsBookmark else Icons.Outlined.CollectionsBookmark
    BottomNavItem.LIVE -> if (selected) Icons.Filled.LiveTv else Icons.Outlined.LiveTv
    BottomNavItem.WATCHLATER -> if (selected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder
    BottomNavItem.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}

internal fun resolveBottomBarSurfaceColor(
    surfaceColor: Color,
    blurEnabled: Boolean,
    blurIntensity: com.android.purebilibili.core.ui.blur.BlurIntensity
): Color {
    val alpha = if (blurEnabled) {
        BlurStyles.getBackgroundAlpha(blurIntensity)
    } else {
        return surfaceColor
    }
    return surfaceColor.copy(alpha = alpha)
}

internal fun resolveBottomBarContainerColor(
    surfaceColor: Color,
    blurEnabled: Boolean,
    blurIntensity: com.android.purebilibili.core.ui.blur.BlurIntensity,
    liquidGlassProgress: Float,
    isGlassEffectEnabled: Boolean
): Color {
    val base = resolveBottomBarSurfaceColor(
        surfaceColor = surfaceColor,
        blurEnabled = blurEnabled,
        blurIntensity = blurIntensity
    )
    if (!isGlassEffectEnabled) return base

    val minAlpha = lerp(
        start = 0.18f,
        stop = 0.36f,
        fraction = liquidGlassProgress.coerceIn(0f, 1f)
    )
    val liftedAlpha = base.alpha.coerceAtLeast(minAlpha) + (0.06f * liquidGlassProgress.coerceIn(0f, 1f))
    return base.copy(alpha = liftedAlpha.coerceAtMost(1f))
}

internal fun resolveBottomBarContainerColor(
    surfaceColor: Color,
    blurEnabled: Boolean,
    blurIntensity: com.android.purebilibili.core.ui.blur.BlurIntensity,
    liquidGlassMode: LiquidGlassMode,
    isGlassEffectEnabled: Boolean
): Color {
    return resolveBottomBarContainerColor(
        surfaceColor = surfaceColor,
        blurEnabled = blurEnabled,
        blurIntensity = blurIntensity,
        liquidGlassProgress = when (liquidGlassMode) {
            LiquidGlassMode.CLEAR -> 0f
            LiquidGlassMode.BALANCED -> 0.5f
            LiquidGlassMode.FROSTED -> 1f
        },
        isGlassEffectEnabled = isGlassEffectEnabled
    )
}

internal fun shouldUseHomeCombinedClickable(
    item: BottomNavItem,
    isSelected: Boolean
): Boolean {
    return item == BottomNavItem.HOME && isSelected
}

internal enum class BottomBarPrimaryTapAction {
    Navigate,
    HomeReselect
}

internal fun resolveBottomBarPrimaryTapAction(
    item: BottomNavItem,
    isSelected: Boolean
): BottomBarPrimaryTapAction {
    return if (item == BottomNavItem.HOME && isSelected) {
        BottomBarPrimaryTapAction.HomeReselect
    } else {
        BottomBarPrimaryTapAction.Navigate
    }
}

internal fun performBottomBarPrimaryTap(
    item: BottomNavItem,
    isSelected: Boolean,
    haptic: (HapticType) -> Unit,
    onNavigate: () -> Unit,
    onHomeReselect: () -> Unit
) {
    haptic(HapticType.LIGHT)
    when (resolveBottomBarPrimaryTapAction(item, isSelected)) {
        BottomBarPrimaryTapAction.Navigate -> onNavigate()
        BottomBarPrimaryTapAction.HomeReselect -> onHomeReselect()
    }
}

internal fun performMaterialBottomBarTap(
    haptic: (HapticType) -> Unit,
    onClick: () -> Unit
) {
    haptic(HapticType.LIGHT)
    onClick()
}

internal fun shouldAcceptBottomBarTap(
    tappedItem: BottomNavItem,
    lastTappedItem: BottomNavItem?,
    currentTimeMillis: Long,
    lastTapTimeMillis: Long,
    debounceWindowMillis: Long
): Boolean {
    if (lastTappedItem == null) return true
    if (tappedItem != lastTappedItem) return true
    return currentTimeMillis - lastTapTimeMillis > debounceWindowMillis
}

internal fun shouldUseBottomReselectCombinedClickable(
    item: BottomNavItem,
    isSelected: Boolean
): Boolean {
    return isSelected && item == BottomNavItem.DYNAMIC
}

internal data class BottomBarItemColorBinding(
    val colorIndex: Int,
    val hasCustomAccent: Boolean
)

internal fun resolveBottomBarItemColorBinding(
    item: BottomNavItem,
    itemColorIndices: Map<String, Int>
): BottomBarItemColorBinding {
    if (itemColorIndices.isEmpty()) {
        return BottomBarItemColorBinding(colorIndex = 0, hasCustomAccent = false)
    }

    val match = resolveBottomNavItemLookupKeys(item).firstNotNullOfOrNull { key ->
        itemColorIndices[key]
    }
    return if (match != null) {
        BottomBarItemColorBinding(colorIndex = match, hasCustomAccent = true)
    } else {
        BottomBarItemColorBinding(colorIndex = 0, hasCustomAccent = false)
    }
}

internal fun resolveBottomBarReadableContentColor(
    isLightMode: Boolean,
    liquidGlassProgress: Float,
    contentLuminance: Float
): Color {
    if (isLightMode) {
        return Color.Black
    }
    val shouldUseDarkForeground = liquidGlassProgress >= 0.62f && contentLuminance > 0.6f
    return if (shouldUseDarkForeground) {
        Color.Black.copy(alpha = 0.82f)
    } else {
        Color.White.copy(
            alpha = if (liquidGlassProgress < 0.35f) 0.97f else 0.95f
        )
    }
}

@Composable
private fun BottomBarContent(
    visibleItems: List<BottomNavItem>,
    selectedIndex: Int,
    itemColorIndices: Map<String, Int>,
    onItemClick: (BottomNavItem) -> Unit,
    onToggleSidebar: (() -> Unit)?,
    isTablet: Boolean,
    labelMode: Int,
    hazeState: HazeState?,
    haptic: (HapticType) -> Unit,
    debounceClick: (BottomNavItem, () -> Unit) -> Unit,
    onHomeDoubleTap: () -> Unit,
    onDynamicDoubleTap: () -> Unit,
    itemWidth: Dp,
    rowPadding: Dp,
    contentVerticalOffset: Dp,
    isInteractive: Boolean,
    currentPosition: Float, // [新增] 当前指示器位置，用于动态插值
    dragModifier: Modifier = Modifier,
    contentLuminance: Float = 0f, // [New]
    liquidGlassStyle: LiquidGlassStyle = LiquidGlassStyle.CLASSIC, // [New]
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(liquidGlassStyle),
    selectionEmphasis: Float = 1f
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier
            .fillMaxSize()
            .then(dragModifier)
            .padding(horizontal = rowPadding),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // [平板适配] ... (保持不变，省略以简化 diff，实际需完整保留)
        // 为保持 diff 简洁且正确，这里只修改 visibleItems 循环部分
        // 平板侧边栏按钮逻辑可以保持现状，因为它不参与 currentPosition 计算（它是额外的）
        // 但为了完整性，我们需要确保 BottomBarContent 的完整代码。
        
        // 由于 multi_replace 限制，我必须提供完整的 BottomBarContent。
        // ... (平板按钮代码) 
        visibleItems.forEachIndexed { index, item ->
            val isSelected = selectedIndex == index
            val colorBinding = resolveBottomBarItemColorBinding(
                item = item,
                itemColorIndices = itemColorIndices
            )
            
            // [核心逻辑] 计算每个 Item 的选中分数 (0f..1f)
            // 根据当前位置 currentPosition 和 item index 的距离计算
            // 距离 < 1 时开始变色，距离 0 时完全变色
            val distance = abs(currentPosition - index)
            val selectionFraction = (1f - distance).coerceIn(0f, 1f)
            
            BottomBarItem(
                item = item,
                isSelected = isSelected, // 仅用于点击逻辑判断
                selectionFraction = selectionFraction, // [新增] 用于驱动样式
                onClick = { if (isInteractive) onItemClick(item) },
                labelMode = labelMode,
                colorIndex = colorBinding.colorIndex,
                hasCustomAccent = colorBinding.hasCustomAccent,
                iconSize = if (labelMode == 0) 20.dp else 24.dp,
                contentVerticalOffset = contentVerticalOffset,
                modifier = Modifier.weight(1f),
                hazeState = hazeState,
                haptic = haptic,
                debounceClick = debounceClick,
                onHomeDoubleTap = onHomeDoubleTap,
                onDynamicDoubleTap = onDynamicDoubleTap,
                isTablet = isTablet,
                contentLuminance = contentLuminance, // [New]
                liquidGlassStyle = liquidGlassStyle, // [New]
                liquidGlassTuning = liquidGlassTuning,
                selectionEmphasis = selectionEmphasis
            )
        }

        if (isTablet && onToggleSidebar != null) {
            // ... (复制原有逻辑)
            // 简单复制：
            val sidebarLabel = stringResource(R.string.sidebar_toggle)
            var isPending by remember { mutableStateOf(false) }
            val primaryColor = MaterialTheme.colorScheme.primary
            val unselectedColor = if (hazeState != null) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            } else {
                BottomBarColors.UNSELECTED
            }
            val iconColor by animateColorAsState(targetValue = if (isPending) primaryColor else unselectedColor, label = "iconColor")

            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().offset(y = contentVerticalOffset)
                    .then(
                        if (isInteractive) {
                            Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    isPending = true
                                    haptic(HapticType.LIGHT)
                                    scope.launch {
                                        kotlinx.coroutines.delay(100)
                                        onToggleSidebar()
                                        isPending = false
                                    }
                                }
                        } else {
                            Modifier
                        }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.size(26.dp)) {
                    Icon(imageVector = CupertinoIcons.Outlined.SidebarLeft, contentDescription = sidebarLabel, tint = iconColor, modifier = Modifier.fillMaxSize())
                }
                if (labelMode == 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sidebarLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = iconColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = if (isTablet) 12.sp else 10.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BottomBarItem(
    item: BottomNavItem,
    isSelected: Boolean,
    selectionFraction: Float, // [新增] 0f..1f
    onClick: () -> Unit,
    labelMode: Int,
    colorIndex: Int,
    hasCustomAccent: Boolean,
    iconSize: androidx.compose.ui.unit.Dp,
    contentVerticalOffset: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    hazeState: HazeState?,
    haptic: (HapticType) -> Unit,
    debounceClick: (BottomNavItem, () -> Unit) -> Unit,
    onHomeDoubleTap: () -> Unit,
    onDynamicDoubleTap: () -> Unit,
    isTablet: Boolean,
    contentLuminance: Float = 0f, // [New]
    liquidGlassStyle: LiquidGlassStyle = LiquidGlassStyle.CLASSIC, // [New]
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(liquidGlassStyle),
    selectionEmphasis: Float = 1f
) {
    val scope = rememberCoroutineScope()
    var isPending by remember { mutableStateOf(false) }
    val itemLabel = resolveBottomNavItemLabel(item)
    val itemContentDescription = resolveBottomNavItemContentDescription(item)
    
    val primaryColor = MaterialTheme.colorScheme.primary
    
    // [Adaptive] High Contrast Scheme for Glass Readability
    // Light Mode: Black text/icons (to stand out against white-ish glass)
    // Dark Mode: White text/icons (to stand out against dark glass)
    // [SimpMusic Style]: Adaptive based on luminance
    // [Fix] Reliably detect Light Mode using surface luminance
    // This handles cases where app theme overrides system theme
    val isLightMode = MaterialTheme.colorScheme.surface.luminance() > 0.5f

    val unselectedColor = resolveBottomBarReadableContentColor(
        isLightMode = isLightMode,
        liquidGlassProgress = liquidGlassTuning.progress,
        contentLuminance = contentLuminance
    )
    
    val selectedAccent = if (hasCustomAccent) {
        BottomBarColors.getColorByIndex(colorIndex)
    } else {
        primaryColor
    }
    val emphasizedSelectionFraction = (selectionFraction * selectionEmphasis).coerceIn(0f, 1f)

    // [修改] 颜色插值：根据 selectionFraction 在 unselected 和 selected 之间混合
    // 还要考虑 isPending (点击态)
    val targetIconColor = androidx.compose.ui.graphics.lerp(
        unselectedColor, 
        selectedAccent, 
        if (isPending) 1f else emphasizedSelectionFraction
    )
    
    // 仍然使用 animateColorAsState 但目标值现在是动态插值的
    // 使用较快的动画以跟手，或者直接使用 lerp 结果如果非常平滑
    // 为了平滑过渡，这里使用 FastOutSlowIn 且时间短
    val iconColor by animateColorAsState(
        targetValue = targetIconColor,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 100), // 快速响应
        label = "iconColor"
    )
    
    // [修改] 缩放插值 - 跃动效果
    // selectionFraction: 0f (未选中) -> 1f (完全选中)
    // 这里的逻辑是：当指示器经过时 (0.5f) 图标最大，两端 (0f/1f) 恢复正常
    // 使用 sin(x * PI) 曲线：sin(0)=0, sin(0.5PI)=1, sin(PI)=0
    // 基础大小 1.0f，最大放大 1.4f (增强版)
    val scaleMultiplier = 0.4f
    val bumpScale = 1.0f + (
        scaleMultiplier * kotlin.math.sin(emphasizedSelectionFraction * Math.PI)
    ).toFloat()
    
    // 直接使用计算出的 bumpScale 作为 scale，因为 selectionFraction 本身已经是平滑动画的值 (由 dampedDragState 驱动)
    // 这样可以保证图标缩放绝对跟随手指/指示器位置，没有任何滞后
    val scale = bumpScale
    
    // [修改] Y轴位移插值
    val targetBounceY = androidx.compose.ui.util.lerp(0f, 0f, emphasizedSelectionFraction)
    val bounceY by animateFloatAsState(
        targetValue = targetBounceY,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "bounceY"
    )
    
    //  晃动角度 (保持不变)
    var wobbleAngle by remember { mutableFloatStateOf(0f) }
    val animatedWobble by animateFloatAsState(
        targetValue = wobbleAngle,
        animationSpec = spring(dampingRatio = 0.2f, stiffness = 600f),
        label = "wobble"
    )
    
    LaunchedEffect(wobbleAngle) {
        if (wobbleAngle != 0f) {
            kotlinx.coroutines.delay(50)
            wobbleAngle = 0f
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxHeight()
            .offset(y = contentVerticalOffset)
            .clearAndSetSemantics {
                contentDescription = itemContentDescription
            }
            .then(
                // 仅当“当前已在首页”时保留双击手势，避免从其他页切首页产生点击延迟
                if (shouldUseBottomReselectCombinedClickable(item, isSelected)) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            debounceClick(item) {
                                performBottomBarPrimaryTap(
                                    item = item,
                                    isSelected = isSelected,
                                    haptic = haptic,
                                    onNavigate = onClick,
                                    onHomeReselect = onHomeDoubleTap
                                )
                                
                                // 2. 视觉反馈 (Visual Feedback)
                                isPending = true
                                scope.launch {
                                    // 晃动动画与导航并行执行
                                    wobbleAngle = 15f
                                    kotlinx.coroutines.delay(200) // 等待动画完成
                                    isPending = false
                                }
                            }
                        },
                        onDoubleClick = {
                            haptic(HapticType.MEDIUM)
                            when (item) {
                                BottomNavItem.HOME -> onHomeDoubleTap()
                                BottomNavItem.DYNAMIC -> onDynamicDoubleTap()
                                else -> Unit
                            }
                        }
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { 
                        debounceClick(item) {
                            performBottomBarPrimaryTap(
                                item = item,
                                isSelected = isSelected,
                                haptic = haptic,
                                onNavigate = onClick,
                                onHomeReselect = onHomeDoubleTap
                            )
                            
                            // 2. 视觉反馈 (Visual Feedback)
                            isPending = true
                            scope.launch {
                                // 晃动动画与导航并行执行
                                wobbleAngle = 15f
                                kotlinx.coroutines.delay(200) // 等待动画完成
                                isPending = false
                            }
                        }
                    }
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) { // ... (Icon/Text rendering 保持不变，使用 iconColor/scale 等变量)
        when (labelMode) {
            0 -> { // Icon + Text
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .offset(y = (-0.5).dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            rotationZ = animatedWobble
                            translationY = bounceY
                        },
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides iconColor) {
                        if (isSelected) item.selectedIcon() else item.unselectedIcon()
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = itemLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = iconColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = if (isTablet) 12.sp else 11.sp,
                    lineHeight = if (isTablet) 12.sp else 11.sp,
                    maxLines = 1
                )
            }
            2 -> { // Text Only
                Text(
                    text = itemLabel,
                    fontSize = if (isTablet) 16.sp else 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = iconColor,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        rotationZ = animatedWobble
                        translationY = bounceY
                    }
                )
            }
            else -> { // Icon Only
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            rotationZ = animatedWobble
                            translationY = bounceY
                        },
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides iconColor) {
                        if (isSelected) item.selectedIcon() else item.unselectedIcon()
                    }
                }
            }
        }
    }
}

internal fun resolveIos26BottomIndicatorGrayColor(isDarkTheme: Boolean): Color {
    return if (isDarkTheme) {
        // Dark mode: brighter neutral gray to float above dark glass.
        Color(0xFFC8CDD6)
    } else {
        // Light mode: deeper neutral gray to stay visible on bright background.
        Color(0xFF9BA5B4)
    }
}
