package com.bbttvv.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.bbttvv.app.core.theme.BiliTypography
import com.bbttvv.app.core.theme.enforceDynamicLightTextContrast
import com.bbttvv.app.core.store.SettingsManager

val LocalIsLightTheme = compositionLocalOf { false }

@Immutable
data class TvOverlayPalette(
    val scrim: Color,
    val dialogContainer: Color,
    val contextMenuContainer: Color,
    val dialogBorder: Color,
    val contextMenuBorder: Color,
    val title: Color,
)

private val DarkTvOverlayPalette = TvOverlayPalette(
    scrim = Color(0x99000000),
    dialogContainer = Color(0xF21A2028),
    contextMenuContainer = Color(0xF51A2028),
    dialogBorder = Color.White.copy(alpha = 0.14f),
    contextMenuBorder = Color.White.copy(alpha = 0.16f),
    title = Color.White,
)

private val LightTvOverlayPalette = TvOverlayPalette(
    scrim = Color(0x99000000),
    dialogContainer = Color(0xFFF7F8FA),
    contextMenuContainer = Color(0xF7F7F8FA),
    dialogBorder = Color.Black.copy(alpha = 0.08f),
    contextMenuBorder = Color.Black.copy(alpha = 0.08f),
    title = Color(0xFF18191C),
)

val LocalTvOverlayPalette = compositionLocalOf { DarkTvOverlayPalette }

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFFB7299),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = androidx.compose.ui.graphics.Color(0xFF03A9F4),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color(0xFF0F1014),
    onBackground = androidx.compose.ui.graphics.Color.White,
    surface = androidx.compose.ui.graphics.Color(0xFF1B1D23),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE8EAED)
)

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFFB7299),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = androidx.compose.ui.graphics.Color(0xFF03A9F4),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color(0xFFF0F1F5),
    onBackground = androidx.compose.ui.graphics.Color(0xFF18191C),
    surface = androidx.compose.ui.graphics.Color(0xFFF7F8FA),
    onSurface = androidx.compose.ui.graphics.Color(0xFF18191C)
)

@Composable
fun AppTheme(
    themeMode: SettingsManager.ThemeMode = SettingsManager.ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isLightTheme = themeMode == SettingsManager.ThemeMode.LIGHT
    val colorScheme = if (isLightTheme) {
        enforceDynamicLightTextContrast(LightColorScheme)
    } else {
        DarkColorScheme
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalIsLightTheme provides isLightTheme,
        LocalTvOverlayPalette provides if (isLightTheme) LightTvOverlayPalette else DarkTvOverlayPalette,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BiliTypography,
            content = content
        )
    }
}
