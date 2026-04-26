package com.bbttvv.app.ui.components

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.bbttvv.app.R
import com.bbttvv.app.ui.home.HomeFocusCoordinator
import com.bbttvv.app.ui.home.HomeFocusTarget
import com.bbttvv.app.ui.home.isSameOrDescendantOf

@Composable
internal fun AppTopBar(
    tabs: List<AppTopLevelTab>,
    selectedTab: AppTopLevelTab?,
    onTabSelected: (AppTopLevelTab) -> Unit,
    onSelectedTabConfirmed: (AppTopLevelTab) -> Unit = {},
    updateSelectedTabOnFocus: Boolean,
    onDpadDown: () -> Boolean,
    focusCoordinator: HomeFocusCoordinator? = null,
    onTopBarFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tabsKey = remember(tabs) {
        tabs.joinToString(separator = "|") { tab -> tab.name }
    }
    val focusTargetTab = selectedTab?.takeIf { it in tabs }
    val controller = remember { AppTopBarController() }
    val latestOnTabSelected by rememberUpdatedState(onTabSelected)
    val latestOnSelectedTabConfirmed by rememberUpdatedState(onSelectedTabConfirmed)
    val latestOnDpadDown by rememberUpdatedState(onDpadDown)
    val latestOnTopBarFocusChanged by rememberUpdatedState(onTopBarFocusChanged)
    val latestFocusTargetTab by rememberUpdatedState(focusTargetTab)

    DisposableEffect(focusCoordinator, controller, tabsKey) {
        val registration = focusCoordinator?.registerTopBarTarget(
            object : HomeFocusTarget {
                override fun tryRequestFocus(): Boolean {
                    return controller.tryRequestFocus(latestFocusTargetTab)
                }

                override fun hasFocus(): Boolean {
                    return controller.hasFocus()
                }

                override fun hasFocusOnRequestedTarget(): Boolean {
                    return controller.hasFocus(latestFocusTargetTab)
                }
            }
        )
        onDispose {
            registration?.unregister()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { rawContext ->
            val context = ContextThemeWrapper(rawContext, R.style.Theme_BBTTVV)
            val adapter = AppTopBarAdapter()
            val recyclerView = object : RecyclerView(context) {
                override fun focusSearch(focused: View?, direction: Int): View? {
                    val next = super.focusSearch(focused, direction)
                    if (next == null && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)) {
                        return focused
                    }
                    return next
                }
            }.apply {
                clipChildren = false
                clipToPadding = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                setPadding(dp(8), dp(4), dp(8), dp(4))
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                this.adapter = adapter
                (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            }

            FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false
                addView(
                    recyclerView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )
                controller.attach(recyclerView, adapter)
            }
        },
        update = {
            controller.adapter?.updateCallbacks(
                updateSelectedTabOnFocus = updateSelectedTabOnFocus,
                onTabSelected = latestOnTabSelected,
                onSelectedTabConfirmed = latestOnSelectedTabConfirmed,
                onDpadDown = latestOnDpadDown,
                onTopBarFocusChanged = latestOnTopBarFocusChanged
            )
            controller.adapter?.submitTabs(tabs, selectedTab)
            focusCoordinator?.drainPendingFocus()
        }
    )
}

private class AppTopBarController {
    var recyclerView: RecyclerView? = null
        private set
    var adapter: AppTopBarAdapter? = null
        private set
    private var focusRequestToken: Int = 0

    fun attach(recyclerView: RecyclerView, adapter: AppTopBarAdapter) {
        this.recyclerView = recyclerView
        this.adapter = adapter
    }

    fun tryRequestFocus(tab: AppTopLevelTab?): Boolean {
        val recycler = recyclerView ?: return false
        val topBarAdapter = adapter ?: return false
        val targetTab = topBarAdapter.focusTargetOrFallback(tab) ?: return false
        val expectedSelectedTab = topBarAdapter.currentSelectedTab()
        val requestToken = ++focusRequestToken
        return requestFocus(
            recycler = recycler,
            topBarAdapter = topBarAdapter,
            targetTab = targetTab,
            expectedSelectedTab = expectedSelectedTab,
            requestToken = requestToken
        )
    }

    private fun requestFocus(
        recycler: RecyclerView,
        topBarAdapter: AppTopBarAdapter,
        targetTab: AppTopLevelTab,
        expectedSelectedTab: AppTopLevelTab?,
        requestToken: Int
    ): Boolean {
        val position = topBarAdapter.positionOf(targetTab).takeIf { it != RecyclerView.NO_POSITION }
            ?: return false
        val holder = recycler.findViewHolderForAdapterPosition(position)
        if (holder?.itemView?.requestFocus() == true) {
            return true
        }
        scrollToFocusablePosition(recycler, position)
        recycler.post {
            if (focusRequestToken != requestToken) return@post
            if (recyclerView !== recycler || adapter !== topBarAdapter) return@post
            if (topBarAdapter.currentSelectedTab() != expectedSelectedTab) return@post
            val latestTargetTab = topBarAdapter.focusTargetOrFallback(targetTab) ?: return@post
            val latestPosition = topBarAdapter.positionOf(latestTargetTab)
                .takeIf { it != RecyclerView.NO_POSITION }
                ?: return@post
            if (latestPosition != position) {
                scrollToFocusablePosition(recycler, latestPosition)
            }
            recycler.findViewHolderForAdapterPosition(latestPosition)?.itemView?.requestFocus()
        }
        return false
    }

    private fun scrollToFocusablePosition(recycler: RecyclerView, position: Int) {
        val layoutManager = recycler.layoutManager as? LinearLayoutManager
        if (layoutManager == null) {
            recycler.scrollToPosition(position)
            return
        }
        layoutManager.scrollToPositionWithOffset(
            position,
            centeredTabOffset(recycler, position)
        )
    }

    private fun centeredTabOffset(recycler: RecyclerView, position: Int): Int {
        val itemCount = recycler.adapter?.itemCount ?: 0
        val availableWidth = recycler.width - recycler.paddingLeft - recycler.paddingRight
        if (itemCount <= 1 || availableWidth <= 0) return 0
        val childWidth = measuredChildWidth(recycler, position)
            ?: (availableWidth / 3).takeIf { it > 0 }
            ?: return 0
        return when {
            position <= 0 -> 0
            position >= itemCount - 1 -> (availableWidth - childWidth).coerceAtLeast(0)
            else -> ((availableWidth - childWidth) / 2).coerceAtLeast(0)
        }
    }

    private fun measuredChildWidth(recycler: RecyclerView, position: Int): Int? {
        recycler.findViewHolderForAdapterPosition(position)
            ?.itemView
            ?.width
            ?.takeIf { it > 0 }
            ?.let { return it }
        for (index in 0 until recycler.childCount) {
            val width = recycler.getChildAt(index).width
            if (width > 0) return width
        }
        return null
    }

    fun hasFocus(tab: AppTopLevelTab? = null): Boolean {
        val recycler = recyclerView ?: return false
        val focused = recycler.rootView?.findFocus() ?: return false
        if (tab == null) {
            return focused === recycler || focused.isSameOrDescendantOf(recycler)
        }
        val topBarAdapter = adapter ?: return false
        val position = topBarAdapter.positionOf(tab).takeIf { it != RecyclerView.NO_POSITION }
            ?: return false
        val holder = recycler.findViewHolderForAdapterPosition(position) ?: return false
        return focused === holder.itemView || focused.isSameOrDescendantOf(holder.itemView)
    }
}

private fun View.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}
