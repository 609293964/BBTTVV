package com.android.purebilibili.feature.home.components

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.theme.AndroidNativeVariant
import com.android.purebilibili.core.theme.UiPreset

enum class TopTabMaterialMode {
    PLAIN,
    BLUR,
    LIQUID_GLASS
}

enum class TopTabIndicatorStyle {
    CAPSULE,
    MATERIAL
}

data class TopTabVisualTuning(
    val nonFloatingIndicatorHeightDp: Float = 31f,
    val nonFloatingIndicatorCornerDp: Float = 14f,
    val nonFloatingIndicatorWidthRatio: Float = 0.72f,
    val nonFloatingIndicatorMinWidthDp: Float = 44f,
    val nonFloatingIndicatorHorizontalInsetDp: Float = 18f,
    val floatingIndicatorWidthMultiplier: Float = 1.34f,
    val floatingIndicatorMinWidthDp: Float = 96f,
    val floatingIndicatorMaxWidthDp: Float = 126f,
    val floatingIndicatorMaxWidthToItemRatio: Float = 1.34f,
    val floatingIndicatorHeightDp: Float = 50f,
    val tabTextSizeSp: Float = 11.6f,
    val tabTextLineHeightSp: Float = 12f,
    val tabContentMinHeightDp: Float = 34f
)

data class TopTabVisualState(
    val floating: Boolean,
    val materialMode: TopTabMaterialMode
)

data class Md3TopTabVisualSpec(
    val rowHeight: Dp,
    val selectedCapsuleHeight: Dp,
    val selectedCapsuleCornerRadius: Dp,
    val selectedCapsuleTonalElevation: Dp,
    val selectedCapsuleShadowElevation: Dp,
    val itemHorizontalPadding: Dp,
    val iconSize: Dp,
    val labelTextSize: TextUnit,
    val labelLineHeight: TextUnit,
    val iconLabelSpacing: Dp
)

fun resolveTopTabVisualTuning(): TopTabVisualTuning = TopTabVisualTuning()

fun resolveTopTabVisualTuning(uiPreset: UiPreset): TopTabVisualTuning {
    return when (uiPreset) {
        UiPreset.IOS -> TopTabVisualTuning(
            nonFloatingIndicatorHeightDp = 44f,
            nonFloatingIndicatorCornerDp = 22f,
            nonFloatingIndicatorWidthRatio = 1.34f,
            nonFloatingIndicatorMinWidthDp = 90f,
            nonFloatingIndicatorHorizontalInsetDp = 0f,
            floatingIndicatorWidthMultiplier = 1.34f,
            floatingIndicatorMinWidthDp = 96f,
            floatingIndicatorMaxWidthDp = 126f,
            floatingIndicatorMaxWidthToItemRatio = 1.34f,
            floatingIndicatorHeightDp = 46f,
            tabTextSizeSp = 11.6f,
            tabTextLineHeightSp = 12f,
            tabContentMinHeightDp = 34f
        )
        UiPreset.MD3 -> resolveTopTabVisualTuning()
    }
}

internal fun resolveTopTabContentScale(
    selectionFraction: Float,
    showIcon: Boolean,
    showText: Boolean,
    uiPreset: UiPreset
): Float {
    if (showIcon && showText) return 1f

    val clampedFraction = selectionFraction.coerceIn(0f, 1f)
    val maxScale = when (uiPreset) {
        UiPreset.IOS -> 1.03f
        UiPreset.MD3 -> 1.04f
    }
    return 1f + ((maxScale - 1f) * clampedFraction)
}

internal fun resolveMd3TopTabVisualSpec(
    isFloatingStyle: Boolean,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3,
    labelMode: Int = 2
): Md3TopTabVisualSpec {
    val normalizedLabelMode = normalizeTopTabLabelMode(labelMode)
    val showIconAndText = normalizedLabelMode == 0
    if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
        return if (isFloatingStyle) {
            Md3TopTabVisualSpec(
                rowHeight = if (showIconAndText) 54.dp else 50.dp,
                selectedCapsuleHeight = 30.dp,
                selectedCapsuleCornerRadius = 15.dp,
                selectedCapsuleTonalElevation = 0.dp,
                selectedCapsuleShadowElevation = 0.dp,
                itemHorizontalPadding = 12.dp,
                iconSize = 18.dp,
                labelTextSize = 13.sp,
                labelLineHeight = 18.sp,
                iconLabelSpacing = 2.dp
            )
        } else {
            Md3TopTabVisualSpec(
                rowHeight = if (showIconAndText) 50.dp else 44.dp,
                selectedCapsuleHeight = 30.dp,
                selectedCapsuleCornerRadius = 15.dp,
                selectedCapsuleTonalElevation = 0.dp,
                selectedCapsuleShadowElevation = 0.dp,
                itemHorizontalPadding = 12.dp,
                iconSize = 16.dp,
                labelTextSize = 14.sp,
                labelLineHeight = 18.sp,
                iconLabelSpacing = 1.dp
            )
        }
    }

    return if (isFloatingStyle) {
        Md3TopTabVisualSpec(
            rowHeight = if (showIconAndText) 56.dp else 48.dp,
            selectedCapsuleHeight = 2.dp,
            selectedCapsuleCornerRadius = 1.dp,
            selectedCapsuleTonalElevation = 0.dp,
            selectedCapsuleShadowElevation = 0.dp,
            itemHorizontalPadding = if (showIconAndText) 8.dp else 14.dp,
            iconSize = 18.dp,
            labelTextSize = 13.sp,
            labelLineHeight = 18.sp,
            iconLabelSpacing = if (showIconAndText) 1.dp else 0.dp
        )
    } else {
        Md3TopTabVisualSpec(
            rowHeight = if (showIconAndText) 52.dp else 44.dp,
            selectedCapsuleHeight = 2.dp,
            selectedCapsuleCornerRadius = 1.dp,
            selectedCapsuleTonalElevation = 0.dp,
            selectedCapsuleShadowElevation = 0.dp,
            itemHorizontalPadding = if (showIconAndText) 8.dp else 12.dp,
            iconSize = 16.dp,
            labelTextSize = 13.sp,
            labelLineHeight = 18.sp,
            iconLabelSpacing = if (showIconAndText) 1.dp else 0.dp
        )
    }
}

internal fun resolveMd3TopTabSelectedContainerColor(
    colorScheme: ColorScheme,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): androidx.compose.ui.graphics.Color = if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
    colorScheme.secondaryContainer
} else {
    colorScheme.primary
}

internal fun resolveMd3TopTabSelectedIconColor(
    colorScheme: ColorScheme,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): androidx.compose.ui.graphics.Color = if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
    colorScheme.onSecondaryContainer
} else {
    colorScheme.primary
}

internal fun resolveMd3TopTabSelectedLabelColor(
    colorScheme: ColorScheme,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): androidx.compose.ui.graphics.Color = if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
    colorScheme.onSecondaryContainer
} else {
    colorScheme.primary
}

internal fun resolveMd3TopTabUnselectedIconColor(
    colorScheme: ColorScheme
): androidx.compose.ui.graphics.Color = colorScheme.onSurfaceVariant

internal fun resolveMd3TopTabUnselectedLabelColor(
    colorScheme: ColorScheme
): androidx.compose.ui.graphics.Color = colorScheme.onSurfaceVariant

internal fun resolveMd3TopTabIconTint(
    selectionFraction: Float,
    colorScheme: ColorScheme,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
) = androidx.compose.ui.graphics.lerp(
    resolveMd3TopTabUnselectedIconColor(colorScheme),
    resolveMd3TopTabSelectedIconColor(colorScheme, androidNativeVariant),
    selectionFraction.coerceIn(0f, 1f)
)

internal fun resolveMd3TopTabLabelTint(
    selectionFraction: Float,
    colorScheme: ColorScheme,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
) = androidx.compose.ui.graphics.lerp(
    resolveMd3TopTabUnselectedLabelColor(colorScheme),
    resolveMd3TopTabSelectedLabelColor(colorScheme, androidNativeVariant),
    selectionFraction.coerceIn(0f, 1f)
)

internal fun resolveTopTabIndicatorStyle(uiPreset: UiPreset): TopTabIndicatorStyle {
    return if (uiPreset == UiPreset.MD3) {
        TopTabIndicatorStyle.MATERIAL
    } else {
        TopTabIndicatorStyle.CAPSULE
    }
}

fun resolveTopTabLabelTextSizeSp(labelMode: Int): Float {
    val tuning = resolveTopTabVisualTuning()
    return when (normalizeTopTabLabelMode(labelMode)) {
        0 -> resolveMd3TopTabVisualSpec(isFloatingStyle = false, labelMode = labelMode).labelTextSize.value
        2 -> tuning.tabTextSizeSp + 0.2f
        else -> tuning.tabTextSizeSp
    }
}

fun resolveTopTabLabelLineHeightSp(labelMode: Int): Float {
    return when (normalizeTopTabLabelMode(labelMode)) {
        0 -> resolveMd3TopTabVisualSpec(isFloatingStyle = false, labelMode = labelMode).labelLineHeight.value
        else -> {
            val tuning = resolveTopTabVisualTuning()
            val textSize = resolveTopTabLabelTextSizeSp(labelMode)
            maxOf(tuning.tabTextLineHeightSp, textSize)
        }
    }
}

fun resolveTopTabContentMinHeightDp(): Float {
    return resolveTopTabVisualTuning().tabContentMinHeightDp
}

fun resolveTopTabStyle(
    isBottomBarFloating: Boolean,
    isBottomBarBlurEnabled: Boolean,
    isLiquidGlassEnabled: Boolean
): TopTabVisualState {
    val materialMode = when {
        isBottomBarFloating && isLiquidGlassEnabled -> TopTabMaterialMode.LIQUID_GLASS
        isBottomBarBlurEnabled -> TopTabMaterialMode.BLUR
        else -> TopTabMaterialMode.PLAIN
    }

    return TopTabVisualState(
        floating = isBottomBarFloating,
        materialMode = materialMode
    )
}

internal fun resolveEffectiveHomeHeaderTabMaterialMode(
    materialMode: TopTabMaterialMode,
    interactionBudget: HomeInteractionMotionBudget
): TopTabMaterialMode {
    return materialMode
}

internal fun resolveEffectiveTopTabLiquidGlassEnabled(
    isLiquidGlassEnabled: Boolean,
    interactionBudget: HomeInteractionMotionBudget
): Boolean {
    return isLiquidGlassEnabled
}
