package com.bbttvv.app.ui.home

import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

internal fun firstVisiblePosition(recycler: RecyclerView): Int {
    return (recycler.layoutManager as? GridLayoutManager)
        ?.findFirstVisibleItemPosition()
        ?.takeIf { it != RecyclerView.NO_POSITION }
        ?: RecyclerView.NO_POSITION
}

internal fun isPositionVisible(recycler: RecyclerView, position: Int): Boolean {
    val layoutManager = recycler.layoutManager as? GridLayoutManager ?: return false
    val first = layoutManager.findFirstVisibleItemPosition()
    val last = layoutManager.findLastVisibleItemPosition()
    return first != RecyclerView.NO_POSITION &&
        last != RecyclerView.NO_POSITION &&
        position in first..last
}

internal fun attachedRecyclerView(recyclerView: RecyclerView?): RecyclerView? {
    return recyclerView?.takeIf { it.isValidFocusTarget() }
}

internal fun findFocusedAdapterPosition(
    recycler: RecyclerView,
    focusedView: View?,
    itemCount: Int,
): Int? {
    return focusedView
        ?.takeUnless { it === recycler }
        ?.let(recycler::findContainingViewHolder)
        ?.bindingAdapterPosition
        ?.takeIf { it in 0 until itemCount }
}
