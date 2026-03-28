// 文件路径: core/theme/Theme.kt
package com.android.purebilibili.core.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- 扩展颜色定义 ---
private val LightSurfaceVariant = Color(0xFFF1F2F3)

//  [优化] 根据主题色索引生成配色方案
private fun createDarkColorScheme(primaryColor: Color) = darkColorScheme(
    primary = primaryColor,
    onPrimary = White,
    primaryContainer = primaryColor.copy(alpha = 0.3f), //  Container derived from primary
    onPrimaryContainer = primaryColor.copy(alpha = 1f), // Stronger primary for content
    secondary = primaryColor.copy(alpha = 0.85f),
    secondaryContainer = primaryColor.copy(alpha = 0.2f), //  Container derived from primary
    onSecondaryContainer = primaryColor.copy(alpha = 0.9f),
    background = DarkBackground, // iOS User Interface Black
    surface = DarkSurface, // iOS System Gray 6 (Dark)
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = DarkSurfaceElevated, // iOS System Gray 5 (Dark)
    outline = iOSSystemGray3Dark,
    outlineVariant = iOSSystemGray4Dark
)

private fun createAmoledDarkColorScheme(primaryColor: Color) = darkColorScheme(
    primary = primaryColor,
    onPrimary = White,
    primaryContainer = primaryColor.copy(alpha = 0.32f),
    onPrimaryContainer = primaryColor,
    secondary = primaryColor.copy(alpha = 0.9f),
    secondaryContainer = primaryColor.copy(alpha = 0.22f),
    onSecondaryContainer = primaryColor,
    background = Black,
    surface = Black,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF050505),
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = Color(0xFF090909),
    outline = Color(0xFF262626),
    outlineVariant = Color(0xFF1A1A1A)
)

internal fun resolveEffectiveDynamicColorEnabled(
    dynamicColorEnabled: Boolean,
    amoledDarkTheme: Boolean,
    uiPreset: UiPreset
): Boolean = dynamicColorEnabled

internal fun applyAmoledSurfaceOverrides(
    baseScheme: ColorScheme
): ColorScheme = baseScheme.copy(
    background = Black,
    surface = Black,
    surfaceVariant = Color(0xFF050505),
    surfaceContainer = Color(0xFF090909),
    outline = Color(0xFF262626),
    outlineVariant = Color(0xFF1A1A1A)
)

private fun createLightColorScheme(primaryColor: Color) = lightColorScheme(
    primary = primaryColor,
    onPrimary = White,
    primaryContainer = primaryColor.copy(alpha = 0.15f), //  Container derived from primary (ligther for light mode)
    onPrimaryContainer = primaryColor,
    secondary = primaryColor.copy(alpha = 0.8f),
    secondaryContainer = primaryColor.copy(alpha = 0.1f), //  Container derived from primary
    onSecondaryContainer = primaryColor,
    background = iOSSystemGray6, // Use iOS System Gray 6 for main background (grouped table view style)
    surface = White, // iOS cards are usually white
    onSurface = TextPrimary,
    surfaceVariant = iOSSystemGray5, // Separators / Higher groupings
    onSurfaceVariant = TextSecondary,
    surfaceContainer = iOSSystemGray5, // iOS System Gray 5 (Light)
    outline = iOSSystemGray3,
    outlineVariant = iOSSystemGray4
)

// 保留默认配色作为后备 (使用 iOS 系统蓝)
private val DarkColorScheme = createDarkColorScheme(iOSSystemBlue)
private val LightColorScheme = createLightColorScheme(iOSSystemBlue)

private fun createMd3DarkColorScheme(primaryColor: Color) = darkColorScheme(
    primary = primaryColor,
    onPrimary = White,
    primaryContainer = primaryColor.copy(alpha = 0.28f),
    onPrimaryContainer = White,
    secondary = primaryColor.copy(alpha = 0.82f),
    secondaryContainer = primaryColor.copy(alpha = 0.2f),
    onSecondaryContainer = White,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onSurface = White,
    surfaceVariant = Color(0xFF2B2B2B),
    onSurfaceVariant = Color(0xFFD1D1D1),
    surfaceContainer = Color(0xFF242424),
    outline = Color(0xFF8D8D8D),
    outlineVariant = Color(0xFF4C4C4C)
)

private fun createMd3LightColorScheme(primaryColor: Color) = lightColorScheme(
    primary = primaryColor,
    onPrimary = White,
    primaryContainer = primaryColor.copy(alpha = 0.14f),
    onPrimaryContainer = primaryColor,
    secondary = primaryColor.copy(alpha = 0.84f),
    secondaryContainer = primaryColor.copy(alpha = 0.1f),
    onSecondaryContainer = primaryColor,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainer = Color(0xFFF3EDF7),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0)
)

@Composable
fun PureBiliBiliTheme(
    uiPreset: UiPreset = UiPreset.IOS,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    amoledDarkTheme: Boolean = false,
    themeColorIndex: Int = 0, //  默认 0 = iOS 蓝色
    fontSizePreset: AppFontSizePreset = AppFontSizePreset.DEFAULT,
    content: @Composable () -> Unit
) {
    //  🚀 [修复] 强制监听配置变化 (如更换壁纸触发的资源刷新)
    // 即使 Activity 不重建，Configuration 也会变化，触发重组从而获取最新的 dynamicColorScheme
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    
    //  获取自定义主题色 (默认 iOS 蓝)
    val customPrimaryColor = ThemeColors.getOrElse(themeColorIndex) { iOSSystemBlue }
    
    val renderingProfile = resolveUiRenderingProfile(uiPreset)
    val isDynamicColorActive = resolveEffectiveDynamicColorEnabled(
        dynamicColorEnabled = dynamicColor,
        amoledDarkTheme = amoledDarkTheme,
        uiPreset = uiPreset
    ) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val shapes = if (renderingProfile.useMaterialChrome) {
        Shapes()
    } else {
        iOSShapes
    }
    
    val colorScheme = when {
        // 如果开启了动态取色 且 系统版本 >= Android 12 (S)
        isDynamicColorActive -> {
            val context = LocalContext.current
            if (darkTheme) {
                val dynamicDark = dynamicDarkColorScheme(context)
                if (amoledDarkTheme) {
                    applyAmoledSurfaceOverrides(dynamicDark)
                } else {
                    dynamicDark
                }
            } else {
                enforceDynamicLightTextContrast(dynamicLightColorScheme(context))
            }
        }
        darkTheme && amoledDarkTheme -> createAmoledDarkColorScheme(customPrimaryColor)
        darkTheme -> {
            if (renderingProfile.useMaterialChrome) {
                createMd3DarkColorScheme(customPrimaryColor)
            } else {
                createDarkColorScheme(customPrimaryColor)
            }
        }
        else -> {
            val lightScheme = if (renderingProfile.useMaterialChrome) {
                createMd3LightColorScheme(customPrimaryColor)
            } else {
                createLightColorScheme(customPrimaryColor)
            }
            enforceDynamicLightTextContrast(lightScheme)
        }
    }

    //  [新增] 动态设置状态栏图标颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 设置状态栏图标颜色：
            // - 深色模式：使用浅色图标 (isAppearanceLightStatusBars = false)
            // - 浅色模式：使用深色图标 (isAppearanceLightStatusBars = true)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalUiPreset provides uiPreset,
        LocalDynamicColorActive provides isDynamicColorActive,
        LocalCornerRadiusScale provides if (renderingProfile.useMaterialChrome) 0.9f else 1f
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BiliTypography.scaled(fontSizePreset.multiplier),
            shapes = shapes,
            content = content
        )
    }
}
