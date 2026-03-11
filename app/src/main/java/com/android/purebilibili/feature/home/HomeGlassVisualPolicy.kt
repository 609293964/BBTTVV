package com.android.purebilibili.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

data class HomeGlassChromeStyle(
    val containerAlpha: Float,
    val borderAlpha: Float,
    val highlightAlpha: Float,
    val shadowAlpha: Float
)

data class HomeGlassPillStyle(
    val containerAlpha: Float,
    val borderAlpha: Float,
    val highlightAlpha: Float,
    val contentAlpha: Float
)

data class HomeGlassResolvedColors(
    val containerColor: Color,
    val borderColor: Color,
    val highlightColor: Color
)

internal fun resolveHomeGlassChromeStyle(
    glassEnabled: Boolean,
    blurEnabled: Boolean
): HomeGlassChromeStyle {
    return when {
        !blurEnabled -> HomeGlassChromeStyle(
            containerAlpha = 0.88f,
            borderAlpha = 0.10f,
            highlightAlpha = 0.08f,
            shadowAlpha = 0.08f
        )

        glassEnabled -> HomeGlassChromeStyle(
            containerAlpha = 0.16f,
            borderAlpha = 0.18f,
            highlightAlpha = 0.22f,
            shadowAlpha = 0.14f
        )

        else -> HomeGlassChromeStyle(
            containerAlpha = 0.72f,
            borderAlpha = 0.12f,
            highlightAlpha = 0.10f,
            shadowAlpha = 0.10f
        )
    }
}

internal fun resolveHomeGlassPillStyle(
    glassEnabled: Boolean,
    blurEnabled: Boolean,
    emphasized: Boolean
): HomeGlassPillStyle {
    return when {
        !blurEnabled -> HomeGlassPillStyle(
            containerAlpha = if (emphasized) 0.92f else 0.88f,
            borderAlpha = 0.12f,
            highlightAlpha = if (emphasized) 0.12f else 0.08f,
            contentAlpha = 0.96f
        )

        glassEnabled -> HomeGlassPillStyle(
            containerAlpha = if (emphasized) 0.28f else 0.24f,
            borderAlpha = 0.16f,
            highlightAlpha = if (emphasized) 0.20f else 0.16f,
            contentAlpha = 1f
        )

        else -> HomeGlassPillStyle(
            containerAlpha = if (emphasized) 0.64f else 0.58f,
            borderAlpha = 0.14f,
            highlightAlpha = if (emphasized) 0.12f else 0.08f,
            contentAlpha = 0.98f
        )
    }
}

@Composable
internal fun rememberHomeGlassChromeColors(
    glassEnabled: Boolean,
    blurEnabled: Boolean,
    baseColor: Color = MaterialTheme.colorScheme.surface
): HomeGlassResolvedColors {
    val style = remember(glassEnabled, blurEnabled) {
        resolveHomeGlassChromeStyle(
            glassEnabled = glassEnabled,
            blurEnabled = blurEnabled
        )
    }
    return remember(style, baseColor) {
        HomeGlassResolvedColors(
            containerColor = baseColor.copy(alpha = style.containerAlpha),
            borderColor = Color.White.copy(alpha = style.borderAlpha),
            highlightColor = Color.White.copy(alpha = style.highlightAlpha)
        )
    }
}

@Composable
internal fun rememberHomeGlassPillColors(
    glassEnabled: Boolean,
    blurEnabled: Boolean,
    emphasized: Boolean,
    baseColor: Color
): HomeGlassResolvedColors {
    val style = remember(glassEnabled, blurEnabled, emphasized) {
        resolveHomeGlassPillStyle(
            glassEnabled = glassEnabled,
            blurEnabled = blurEnabled,
            emphasized = emphasized
        )
    }
    return remember(style, baseColor) {
        HomeGlassResolvedColors(
            containerColor = baseColor.copy(alpha = style.containerAlpha),
            borderColor = Color.White.copy(alpha = style.borderAlpha),
            highlightColor = Color.White.copy(alpha = style.highlightAlpha)
        )
    }
}
