package com.bbttvv.app.ui.home

import androidx.recyclerview.widget.RecyclerView

internal object HomeGridEntryFocusPolicy {
    fun targetPosition(
        itemCount: Int,
        spanCount: Int,
        firstVisiblePosition: Int,
        preferredIndex: Int?,
    ): Int {
        if (itemCount <= 0 || spanCount <= 0) return RecyclerView.NO_POSITION
        val preferredColumn = preferredIndex
            ?.coerceIn(0, spanCount - 1)
            ?: 0
        val firstVisible = firstVisiblePosition
            .takeIf { position -> position in 0 until itemCount }
            ?: 0
        val firstVisibleRowStart = (firstVisible / spanCount) * spanCount
        val firstRowTarget = firstVisibleRowStart + preferredColumn
        val targetRowStart = if (firstRowTarget < firstVisible) {
            firstVisibleRowStart + spanCount
        } else {
            firstVisibleRowStart
        }
        return (targetRowStart + preferredColumn)
            .coerceAtMost(itemCount - 1)
            .coerceAtLeast(0)
    }
}
