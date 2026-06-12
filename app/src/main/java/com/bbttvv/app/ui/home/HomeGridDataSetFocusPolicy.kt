package com.bbttvv.app.ui.home

import androidx.recyclerview.widget.RecyclerView

internal object HomeGridDataSetFocusPolicy {
    fun pendingPosition(
        nextItemCount: Int,
        nextPositionForKey: Int?,
        focusedPosition: Int?,
        rememberedPosition: Int,
        firstVisiblePosition: Int,
    ): Int {
        if (nextItemCount <= 0) return RecyclerView.NO_POSITION
        return nextPositionForKey
            ?: focusedPosition.coerceIntoNextItems(nextItemCount)
            ?: rememberedPosition.coerceIntoNextItems(nextItemCount)
            ?: firstVisiblePosition.coerceIntoNextItems(nextItemCount)
            ?: 0
    }

    fun resolvePendingTarget(
        itemCount: Int,
        keyPosition: Int?,
        fallbackPosition: Int,
        preferFallbackPosition: Boolean = false,
    ): Int {
        if (itemCount <= 0) return RecyclerView.NO_POSITION
        if (preferFallbackPosition) {
            return fallbackPosition.coerceIn(0, itemCount - 1)
        }
        return keyPosition?.takeIf { it in 0 until itemCount }
            ?: fallbackPosition.coerceIn(0, itemCount - 1)
    }

    fun menuRefreshFocusPosition(itemCount: Int): Int {
        return if (itemCount > 0) 0 else RecyclerView.NO_POSITION
    }

    private fun Int?.coerceIntoNextItems(itemCount: Int): Int? {
        val position = this ?: return null
        if (position == RecyclerView.NO_POSITION) return null
        return position.coerceIn(0, itemCount - 1)
    }
}
