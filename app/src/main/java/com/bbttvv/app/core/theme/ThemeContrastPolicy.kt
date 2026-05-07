package com.bbttvv.app.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private const val PRIMARY_TEXT_MIN_CONTRAST = 4.5f
private const val SECONDARY_TEXT_MIN_CONTRAST = 3.0f

internal fun calculateContrastRatio(
    foreground: Color,
    background: Color
): Float {
    val lighter = maxOf(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

internal fun resolveReadableTextColor(
    candidate: Color,
    background: Color,
    fallback: Color,
    minimumContrast: Float
): Color {
    return if (calculateContrastRatio(candidate, background) >= minimumContrast) {
        candidate
    } else {
        fallback
    }
}

internal fun resolveReadableThemeTextColor(
    candidate: Color,
    background: Color,
    fallbacks: List<Color>,
    minimumContrast: Float
): Color {
    if (calculateContrastRatio(candidate, background) >= minimumContrast) {
        return candidate
    }

    return fallbacks.firstOrNull { fallback ->
        calculateContrastRatio(fallback, background) >= minimumContrast
    } ?: candidate
}

internal fun enforceDynamicLightTextContrast(
    scheme: ColorScheme
): ColorScheme {
    val accentFallbacks = listOf(
        scheme.onSurface,
        scheme.onBackground,
        scheme.inverseOnSurface,
        scheme.scrim
    )
    val surfaceFallbacks = listOf(
        scheme.onBackground,
        scheme.onSurface,
        scheme.inverseOnSurface,
        scheme.scrim
    )
    val surfaceVariantFallbacks = listOf(
        scheme.onSurface,
        scheme.onBackground,
        scheme.inverseOnSurface,
        scheme.scrim
    )

    return scheme.copy(
        onBackground = resolveReadableThemeTextColor(
            candidate = scheme.onBackground,
            background = scheme.background,
            fallbacks = surfaceFallbacks,
            minimumContrast = PRIMARY_TEXT_MIN_CONTRAST
        ),
        onSurface = resolveReadableThemeTextColor(
            candidate = scheme.onSurface,
            background = scheme.surface,
            fallbacks = surfaceFallbacks,
            minimumContrast = PRIMARY_TEXT_MIN_CONTRAST
        ),
        onSurfaceVariant = resolveReadableThemeTextColor(
            candidate = scheme.onSurfaceVariant,
            background = scheme.surfaceVariant,
            fallbacks = surfaceVariantFallbacks,
            minimumContrast = SECONDARY_TEXT_MIN_CONTRAST
        ),
        onPrimary = resolveReadableThemeTextColor(
            candidate = scheme.onPrimary,
            background = scheme.primary,
            fallbacks = accentFallbacks,
            minimumContrast = PRIMARY_TEXT_MIN_CONTRAST
        ),
        onPrimaryContainer = resolveReadableThemeTextColor(
            candidate = scheme.onPrimaryContainer,
            background = scheme.primaryContainer,
            fallbacks = accentFallbacks,
            minimumContrast = PRIMARY_TEXT_MIN_CONTRAST
        ),
        onSecondary = resolveReadableThemeTextColor(
            candidate = scheme.onSecondary,
            background = scheme.secondary,
            fallbacks = accentFallbacks,
            minimumContrast = PRIMARY_TEXT_MIN_CONTRAST
        ),
        onSecondaryContainer = resolveReadableThemeTextColor(
            candidate = scheme.onSecondaryContainer,
            background = scheme.secondaryContainer,
            fallbacks = accentFallbacks,
            minimumContrast = PRIMARY_TEXT_MIN_CONTRAST
        )
    )
}

