package com.bbttvv.app.feature.settings

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsChoicePopupPositionProviderTest {
    @Test
    fun popupUsesRowRightEdgeAndOpensBelowWhenThereIsRoom() {
        val provider = SettingsChoicePopupPositionProvider(
            targetBounds = Rect(100f, 100f, 500f, 180f),
            gapPx = 12,
            edgePaddingPx = 36,
        )

        val position = provider.calculatePosition(
            anchorBounds = IntRect.Zero,
            windowSize = IntSize(1920, 1080),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(400, 300),
        )

        assertEquals(IntOffset(100, 192), position)
    }

    @Test
    fun popupOpensAboveWhenThereIsNotEnoughRoomBelow() {
        val provider = SettingsChoicePopupPositionProvider(
            targetBounds = Rect(1000f, 800f, 1800f, 900f),
            gapPx = 12,
            edgePaddingPx = 36,
        )

        val position = provider.calculatePosition(
            anchorBounds = IntRect.Zero,
            windowSize = IntSize(1920, 1080),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(500, 300),
        )

        assertEquals(IntOffset(1300, 488), position)
    }
}
