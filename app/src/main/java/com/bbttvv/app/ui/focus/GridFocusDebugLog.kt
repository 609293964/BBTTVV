package com.bbttvv.app.ui.focus

import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bbttvv.app.BuildConfig
import com.bbttvv.app.ui.home.HomeVideoCardAdapter

internal object GridFocusDebugLog {
    const val Tag = "BBTTVVGridFocus"

    inline fun d(message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(Tag, message())
        }
    }

    fun event(event: KeyEvent): String {
        return "keyCode=${event.keyCode} action=${event.action} repeatCount=${event.repeatCount} " +
            "eventTime=${event.eventTime}"
    }

    fun view(view: View?): String {
        if (view == null) return "currentFocus=null"
        val idName = if (view.id == View.NO_ID) {
            "NO_ID"
        } else {
            runCatching { view.resources.getResourceEntryName(view.id) }
                .getOrElse { view.id.toString() }
        }
        return "currentFocus=${view.javaClass.simpleName} id=$idName tag=${view.tag}"
    }

    fun recycler(recyclerView: RecyclerView?): String {
        if (recyclerView == null) return "recycler=null"
        val focusedView = recyclerView.rootView?.findFocus()
        val holder = focusedView?.let(recyclerView::findContainingViewHolder)
        val position = holder?.bindingAdapterPosition ?: RecyclerView.NO_POSITION
        val adapter = recyclerView.adapter
        val key = (adapter as? HomeVideoCardAdapter)?.keyAt(position)
        val layoutManager = recyclerView.layoutManager as? GridLayoutManager
        val firstVisible = layoutManager?.findFirstVisibleItemPosition()
            ?: RecyclerView.NO_POSITION
        val lastVisible = layoutManager?.findLastVisibleItemPosition()
            ?: RecyclerView.NO_POSITION
        return "${view(focusedView)} adapterPosition=$position itemKey=$key " +
            "scrollState=${recyclerView.scrollState} visible=$firstVisible..$lastVisible " +
            "itemCount=${adapter?.itemCount ?: 0}"
    }
}
