package com.bbttvv.app.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastPolicyTest {
    @Test
    fun `readable candidate is preserved`() {
        val resolved = resolveReadableTextColor(
            candidate = Color.Black,
            background = Color.White,
            fallback = Color.Red,
            minimumContrast = 4.5f,
        )

        assertEquals(Color.Black, resolved)
    }

    @Test
    fun `low contrast candidate uses readable fallback`() {
        val resolved = resolveReadableTextColor(
            candidate = Color(0xFFEFEFEF),
            background = Color.White,
            fallback = Color.Black,
            minimumContrast = 4.5f,
        )

        assertEquals(Color.Black, resolved)
        assertTrue(calculateContrastRatio(resolved, Color.White) >= 4.5f)
    }
}
