package com.bbttvv.app.ui.home

import androidx.recyclerview.widget.RecyclerView

internal object HomeGridDataSetFocusPolicy {
    fun shouldKeepFocusedChild(
        focusIsRecyclerContainer: Boolean,
        focusedKey: String?,
        nextPositionForKey: Int?,
    ): Boolean {
        return !focusIsRecyclerContainer &&
            focusedKey != null &&
            nextPositionForKey != null
    }

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
    ): Int {
        if (itemCount <= 0) return RecyclerView.NO_POSITION
        return keyPosition?.takeIf { it in 0 until itemCount }
            ?: fallbackPosition.coerceIn(0, itemCount - 1)
    }

    private fun Int?.coerceIntoNextItems(itemCount: Int): Int? {
        val position = this ?: return null
        if (position == RecyclerView.NO_POSITION) return null
        return position.coerceIn(0, itemCount - 1)
    }
}
