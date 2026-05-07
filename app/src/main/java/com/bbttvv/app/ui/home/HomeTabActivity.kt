package com.bbttvv.app.ui.home

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.recyclerview.widget.RecyclerView
import com.bbttvv.app.ui.focus.isSameOrDescendantOf

internal val LocalHomeTabActive = staticCompositionLocalOf { true }

internal fun RecyclerView.applyHomeTabActiveState(isActive: Boolean) {
    val wasActive = visibility == View.VISIBLE && isFocusable
    visibility = if (isActive) View.VISIBLE else View.INVISIBLE
    isFocusable = isActive
    if (isActive) {
        if (!wasActive || descendantFocusability != ViewGroup.FOCUS_BLOCK_DESCENDANTS) {
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
    } else {
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
    if (!isActive) {
        val focusedView = rootView?.findFocus()
        if (focusedView === this || focusedView?.isSameOrDescendantOf(this) == true) {
            clearFocus()
        }
    }
}
