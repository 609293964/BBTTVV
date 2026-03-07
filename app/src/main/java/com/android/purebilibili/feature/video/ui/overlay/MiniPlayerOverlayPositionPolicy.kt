package com.android.purebilibili.feature.video.ui.overlay

import kotlin.math.abs

internal data class MiniPlayerOverlayOffset(
    val x: Float,
    val y: Float
)

internal enum class MiniPlayerContentDragIntent {
    UNDECIDED,
    SEEK,
    MOVE
}

internal fun clampMiniPlayerOverlayOffset(
    offsetX: Float,
    offsetY: Float,
    screenWidthPx: Float,
    screenHeightPx: Float,
    miniPlayerWidthPx: Float,
    miniPlayerHeightPx: Float,
    outerPaddingPx: Float,
    topInsetPx: Float,
    bottomInsetPx: Float
): MiniPlayerOverlayOffset {
    val minX = outerPaddingPx
    val maxX = (screenWidthPx - miniPlayerWidthPx - outerPaddingPx).coerceAtLeast(minX)
    val minY = outerPaddingPx + topInsetPx
    val maxY = (screenHeightPx - miniPlayerHeightPx - outerPaddingPx - bottomInsetPx).coerceAtLeast(minY)
    return MiniPlayerOverlayOffset(
        x = offsetX.coerceIn(minX, maxX),
        y = offsetY.coerceIn(minY, maxY)
    )
}

internal fun resolveMiniPlayerContentDragIntent(
    totalDragX: Float,
    totalDragY: Float,
    seekEnabled: Boolean,
    touchSlopPx: Float
): MiniPlayerContentDragIntent {
    val horizontal = abs(totalDragX)
    val vertical = abs(totalDragY)
    val dominant = maxOf(horizontal, vertical)
    if (dominant < touchSlopPx) return MiniPlayerContentDragIntent.UNDECIDED
    if (!seekEnabled) return MiniPlayerContentDragIntent.MOVE
    return if (horizontal > vertical * 1.35f) {
        MiniPlayerContentDragIntent.SEEK
    } else {
        MiniPlayerContentDragIntent.MOVE
    }
}
