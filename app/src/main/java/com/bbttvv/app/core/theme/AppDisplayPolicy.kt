package com.bbttvv.app.core.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToInt

private const val DISPLAY_NARROW_WIDTH_THRESHOLD_DP = 360
private const val DISPLAY_DPI_OVERRIDE_PERCENT_MIN = 85
private const val DISPLAY_DPI_OVERRIDE_PERCENT_MAX = 115

enum class AppFontSizePreset(
    val value: Int,
    val label: String,
    val multiplier: Float
) {
    SMALLER(0, "更小", 0.92f),
    SMALL(1, "偏小", 0.96f),
    DEFAULT(2, "默认", 1.00f),
    LARGE(3, "偏大", 1.04f),
    LARGER(4, "更大", 1.08f);

    companion object {
        fun fromValue(value: Int): AppFontSizePreset {
            return entries.find { it.value == value } ?: DEFAULT
        }
    }
}

enum class AppUiScalePreset(
    val value: Int,
    val label: String,
    val densityMultiplier: Float
) {
    COMPACT(0, "紧凑", 1.08f),
    STANDARD(1, "标准", 1.00f),
    COMFORTABLE(2, "舒适", 0.96f),
    LARGE(3, "更大", 0.92f);

    companion object {
        fun fromValue(value: Int): AppUiScalePreset {
            return entries.find { it.value == value } ?: STANDARD
        }
    }
}

data class DisplayMetricsSnapshot(
    val systemDensityDpi: Int,
    val systemSmallestWidthDp: Int,
    val fontSizePreset: AppFontSizePreset,
    val uiScalePreset: AppUiScalePreset,
    val dpiOverridePercent: Int?,
    val effectiveDensityMultiplier: Float,
    val effectiveDensityDpi: Int,
    val effectiveSmallestWidthDp: Int,
    val isNarrowWidth: Boolean
)

val LocalDisplayMetricsSnapshot = staticCompositionLocalOf {
    DisplayMetricsSnapshot(
        systemDensityDpi = 440,
        systemSmallestWidthDp = 360,
        fontSizePreset = AppFontSizePreset.DEFAULT,
        uiScalePreset = AppUiScalePreset.STANDARD,
        dpiOverridePercent = null,
        effectiveDensityMultiplier = 1f,
        effectiveDensityDpi = 440,
        effectiveSmallestWidthDp = 360,
        isNarrowWidth = false
    )
}

fun resolveEffectiveDensityMultiplier(
    uiScalePreset: AppUiScalePreset,
    dpiOverridePercent: Int?
): Float {
    val normalizedOverride = dpiOverridePercent
        ?.coerceIn(DISPLAY_DPI_OVERRIDE_PERCENT_MIN, DISPLAY_DPI_OVERRIDE_PERCENT_MAX)
    return normalizedOverride?.div(100f) ?: uiScalePreset.densityMultiplier
}

fun resolveEffectiveSmallestWidthDp(
    smallestScreenWidthDp: Int,
    densityMultiplier: Float
): Int {
    if (smallestScreenWidthDp <= 0) return 0
    return (smallestScreenWidthDp / densityMultiplier)
        .roundToInt()
        .coerceAtLeast(1)
}

fun buildDisplayMetricsSnapshot(
    systemDensityDpi: Int,
    smallestScreenWidthDp: Int,
    uiScalePreset: AppUiScalePreset,
    fontSizePreset: AppFontSizePreset,
    dpiOverridePercent: Int?
): DisplayMetricsSnapshot {
    val effectiveDensityMultiplier = resolveEffectiveDensityMultiplier(
        uiScalePreset = uiScalePreset,
        dpiOverridePercent = dpiOverridePercent
    )
    val effectiveSmallestWidthDp = resolveEffectiveSmallestWidthDp(
        smallestScreenWidthDp = smallestScreenWidthDp,
        densityMultiplier = effectiveDensityMultiplier
    )
    return DisplayMetricsSnapshot(
        systemDensityDpi = systemDensityDpi,
        systemSmallestWidthDp = smallestScreenWidthDp,
        fontSizePreset = fontSizePreset,
        uiScalePreset = uiScalePreset,
        dpiOverridePercent = dpiOverridePercent,
        effectiveDensityMultiplier = effectiveDensityMultiplier,
        effectiveDensityDpi = (systemDensityDpi * effectiveDensityMultiplier).roundToInt(),
        effectiveSmallestWidthDp = effectiveSmallestWidthDp,
        isNarrowWidth = effectiveSmallestWidthDp < DISPLAY_NARROW_WIDTH_THRESHOLD_DP
    )
}

private fun TextStyle.scaled(multiplier: Float): TextStyle {
    return copy(
        fontSize = fontSize * multiplier,
        lineHeight = lineHeight * multiplier,
        letterSpacing = letterSpacing * multiplier
    )
}

fun Typography.scaled(multiplier: Float): Typography {
    if (multiplier == 1f) return this
    return copy(
        displayLarge = displayLarge.scaled(multiplier),
        displayMedium = displayMedium.scaled(multiplier),
        displaySmall = displaySmall.scaled(multiplier),
        headlineLarge = headlineLarge.scaled(multiplier),
        headlineMedium = headlineMedium.scaled(multiplier),
        headlineSmall = headlineSmall.scaled(multiplier),
        titleLarge = titleLarge.scaled(multiplier),
        titleMedium = titleMedium.scaled(multiplier),
        titleSmall = titleSmall.scaled(multiplier),
        bodyLarge = bodyLarge.scaled(multiplier),
        bodyMedium = bodyMedium.scaled(multiplier),
        bodySmall = bodySmall.scaled(multiplier),
        labelLarge = labelLarge.scaled(multiplier),
        labelMedium = labelMedium.scaled(multiplier),
        labelSmall = labelSmall.scaled(multiplier)
    )
}

