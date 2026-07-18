package com.bbttvv.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.bbttvv.app.core.theme.BiliTypography
import com.bbttvv.app.core.theme.enforceDynamicLightTextContrast
import com.bbttvv.app.core.store.SettingsManager

val LocalIsLightTheme = compositionLocalOf { false }

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
        LocalIsLightTheme provides isLightTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BiliTypography,
            content = content
        )
    }
}
