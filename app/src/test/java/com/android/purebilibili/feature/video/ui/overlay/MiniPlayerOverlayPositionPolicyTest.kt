package com.android.purebilibili.feature.video.ui.overlay

import kotlin.test.Test
import kotlin.test.assertEquals

class MiniPlayerOverlayPositionPolicyTest {

    @Test
    fun initialOffset_isClampedInsideVisibleBounds() {
        val result = clampMiniPlayerOverlayOffset(
            offsetX = 980f,
            offsetY = 40f,
            screenWidthPx = 1080f,
            screenHeightPx = 2400f,
            miniPlayerWidthPx = 420f,
            miniPlayerHeightPx = 240f,
            outerPaddingPx = 24f,
            topInsetPx = 50f,
            bottomInsetPx = 100f
        )

        assertEquals(636f, result.x, 0.001f)
        assertEquals(74f, result.y, 0.001f)
    }

    @Test
    fun offsetClamp_preservesInBoundsPosition() {
        val result = clampMiniPlayerOverlayOffset(
            offsetX = 120f,
            offsetY = 320f,
            screenWidthPx = 1080f,
            screenHeightPx = 2400f,
            miniPlayerWidthPx = 420f,
            miniPlayerHeightPx = 240f,
            outerPaddingPx = 24f,
            topInsetPx = 50f,
            bottomInsetPx = 100f
        )

        assertEquals(120f, result.x, 0.001f)
        assertEquals(320f, result.y, 0.001f)
    }

    @Test
    fun dragIntent_prefersSeekOnlyForClearlyHorizontalDrags() {
        assertEquals(
            MiniPlayerContentDragIntent.SEEK,
            resolveMiniPlayerContentDragIntent(
                totalDragX = 48f,
                totalDragY = 8f,
                seekEnabled = true,
                touchSlopPx = 12f
            )
        )
        assertEquals(
            MiniPlayerContentDragIntent.MOVE,
            resolveMiniPlayerContentDragIntent(
                totalDragX = 26f,
                totalDragY = 22f,
                seekEnabled = true,
                touchSlopPx = 12f
            )
        )
    }

    @Test
    fun dragIntent_defaultsToMoveWhenSeekUnavailable() {
        assertEquals(
            MiniPlayerContentDragIntent.MOVE,
            resolveMiniPlayerContentDragIntent(
                totalDragX = 40f,
                totalDragY = 2f,
                seekEnabled = false,
                touchSlopPx = 12f
            )
        )
    }

    @Test
    fun dragIntent_waitsUntilMotionPassesTouchSlop() {
        assertEquals(
            MiniPlayerContentDragIntent.UNDECIDED,
            resolveMiniPlayerContentDragIntent(
                totalDragX = 6f,
                totalDragY = 5f,
                seekEnabled = true,
                touchSlopPx = 12f
            )
        )
    }
}
