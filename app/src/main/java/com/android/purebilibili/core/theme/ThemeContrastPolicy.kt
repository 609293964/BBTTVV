package com.android.purebilibili.core.theme

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

internal fun enforceDynamicLightTextContrast(
    scheme: ColorScheme
): ColorScheme {
    return scheme.copy(
        onBackground = resolveReadableTextColor(
            candidate = scheme.onBackground,
            background = scheme.background,
            fallback = TextPrimary,
            minimumContrast = PRIMARY_TEXT_MIN_CONTRAST
        ),
        onSurface = resolveReadableTextColor(
            candidate = scheme.onSurface,
            background = scheme.surface,
            fallback = TextPrimary,
            minimumContrast = PRIMARY_TEXT_MIN_CONTRAST
        ),
        onSurfaceVariant = resolveReadableTextColor(
            candidate = scheme.onSurfaceVariant,
            background = scheme.surfaceVariant,
            fallback = TextSecondary,
            minimumContrast = SECONDARY_TEXT_MIN_CONTRAST
        )
    )
}
