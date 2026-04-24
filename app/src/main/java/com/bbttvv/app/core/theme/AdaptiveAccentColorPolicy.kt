package com.bbttvv.app.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal data class AdaptiveAccentColors(
    val backgroundColor: Color,
    val contentColor: Color
)

internal fun resolveAdaptiveAccentColors(
    accentBackground: Color,
    accentContent: Color,
    containerBackground: Color,
    containerContent: Color,
    surface: Color,
    minimumContrast: Float = 4.5f
): AdaptiveAccentColors {
    val accentContrast = calculateContrastRatio(accentContent, accentBackground)
    val shouldAvoidPureBrightAccent = surface.luminance() < 0.35f &&
        accentBackground.luminance() > 0.88f
    val useContainerColors = shouldAvoidPureBrightAccent || accentContrast < minimumContrast

    return if (useContainerColors) {
        AdaptiveAccentColors(
            backgroundColor = containerBackground,
            contentColor = containerContent
        )
    } else {
        AdaptiveAccentColors(
            backgroundColor = accentBackground,
            contentColor = accentContent
        )
    }
}

internal fun resolveAdaptivePrimaryAccentColors(
    colorScheme: ColorScheme,
    minimumContrast: Float = 4.5f
): AdaptiveAccentColors = resolveAdaptiveAccentColors(
    accentBackground = colorScheme.primary,
    accentContent = colorScheme.onPrimary,
    containerBackground = colorScheme.primaryContainer,
    containerContent = colorScheme.onPrimaryContainer,
    surface = colorScheme.surface,
    minimumContrast = minimumContrast
)

internal fun resolveAdaptiveTertiaryAccentColors(
    colorScheme: ColorScheme,
    minimumContrast: Float = 4.5f
): AdaptiveAccentColors = resolveAdaptiveAccentColors(
    accentBackground = colorScheme.tertiary,
    accentContent = colorScheme.onTertiary,
    containerBackground = colorScheme.tertiaryContainer,
    containerContent = colorScheme.onTertiaryContainer,
    surface = colorScheme.surface,
    minimumContrast = minimumContrast
)

