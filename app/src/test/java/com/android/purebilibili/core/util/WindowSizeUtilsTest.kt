package com.android.purebilibili.core.util

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowSizeUtilsTest {

    @Test
    fun `responsive text scaling keeps unspecified units`() {
        val scaled = TextUnit.Unspecified.scaledIfSpecified(1.2f)

        assertTrue(scaled.isUnspecified)
    }

    @Test
    fun `responsive text scaling scales specified units`() {
        val scaled = 14.sp.scaledIfSpecified(1.2f)

        assertEquals(16.8f, scaled.value, 0.0001f)
    }
}
