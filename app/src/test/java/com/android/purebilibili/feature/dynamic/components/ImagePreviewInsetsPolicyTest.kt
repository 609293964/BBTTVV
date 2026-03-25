package com.android.purebilibili.feature.dynamic.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ImagePreviewInsetsPolicyTest {

    @Test
    fun `resolveImagePreviewOverlayPadding adds safe insets to base spacing`() {
        val padding = resolveImagePreviewOverlayPadding(
            safeInsetStart = 24.dp,
            safeInsetTop = 12.dp,
            safeInsetEnd = 32.dp,
            safeInsetBottom = 20.dp
        )

        assertEquals(40.dp, padding.start)
        assertEquals(28.dp, padding.top)
        assertEquals(48.dp, padding.end)
        assertEquals(36.dp, padding.bottom)
    }

    @Test
    fun `resolveImagePreviewOverlayPadding clamps negative values`() {
        val padding = resolveImagePreviewOverlayPadding(
            safeInsetStart = (-8).dp,
            safeInsetTop = (-4).dp,
            safeInsetEnd = (-12).dp,
            safeInsetBottom = (-2).dp,
            extraHorizontal = (-6).dp,
            extraVertical = (-10).dp
        )

        assertEquals(0.dp, padding.start)
        assertEquals(0.dp, padding.top)
        assertEquals(0.dp, padding.end)
        assertEquals(0.dp, padding.bottom)
    }
}
