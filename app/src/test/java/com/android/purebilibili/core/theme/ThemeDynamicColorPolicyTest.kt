package com.android.purebilibili.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeDynamicColorPolicyTest {

    @Test
    fun `amoled mode keeps dynamic color enabled when user turns monet on`() {
        assertTrue(
            resolveEffectiveDynamicColorEnabled(
                dynamicColorEnabled = true,
                amoledDarkTheme = true,
                uiPreset = UiPreset.MD3
            )
        )
    }

    @Test
    fun `ios preset should also allow monet when user enables dynamic color`() {
        assertEquals(
            true,
            resolveEffectiveDynamicColorEnabled(
                dynamicColorEnabled = true,
                amoledDarkTheme = false,
                uiPreset = UiPreset.IOS
            )
        )
    }

    @Test
    fun `amoled overrides keep monet accents while forcing black surfaces`() {
        val monetScheme = darkColorScheme(
            primary = Color(0xFF84F2A4),
            secondary = Color(0xFF79D7FF),
            tertiary = Color(0xFFFFB3C1),
            background = Color(0xFF101414),
            surface = Color(0xFF161B1A),
            surfaceVariant = Color(0xFF29312E),
            surfaceContainer = Color(0xFF1E2523),
            outline = Color(0xFF6F7975),
            outlineVariant = Color(0xFF414946)
        )

        val result = applyAmoledSurfaceOverrides(monetScheme)

        assertEquals(monetScheme.primary, result.primary)
        assertEquals(monetScheme.secondary, result.secondary)
        assertEquals(monetScheme.tertiary, result.tertiary)
        assertEquals(Color.Black, result.background)
        assertEquals(Color.Black, result.surface)
        assertEquals(Color(0xFF050505), result.surfaceVariant)
        assertEquals(Color(0xFF090909), result.surfaceContainer)
    }
}
