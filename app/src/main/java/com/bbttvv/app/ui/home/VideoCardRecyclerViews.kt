package com.bbttvv.app.ui.home

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.bbttvv.app.R
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopLevelTab

@Composable
internal fun VideoCardRecyclerGrid(
    videos: List<VideoItem>,
    modifier: Modifier = Modifier,
    gridColumnCount: Int = 4,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    focusState: HomeRecommendGridFocusState = remember { HomeRecommendGridFocusState() },
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    focusRegion: HomeFocusRegion = HomeFocusRegion.Grid,
    scrollResetKey: Any? = null,
    showHistoryProgressOnly: Boolean = false,
    showDanmakuCount: Boolean = true,
    supportingText: ((VideoItem) -> String?)? = null,
    loadMorePrefetchItems: Int = 8,
    canLoadMore: () -> Boolean = { false },
    onLoadMore: () -> Unit = {},
    onMenuRefresh: (() -> Unit)? = null,
    onVideoFocused: (VideoItem, String) -> Unit = { _, _ -> },
    onFocusedRowChanged: (Int) -> Unit = {},
    consumeTopRowDpadUp: Boolean = true,
    onTopRowDpadUp: () -> Boolean = { false },
    onBackToTopBar: (() -> Boolean)? = null,
    onLeftEdgeDpadLeft: (() -> Boolean)? = null,
    onVideoMenu: ((VideoItem) -> Unit)? = null,
    onVideoLongClick: ((VideoItem) -> Unit)? = null,
    onVideoClick: (VideoItem, String) -> Unit,
) {
    val items = remember(videos) { videos.toHomeRecommendVideoCardItems() }
    val paddingPx = rememberRecyclerPadding(contentPadding)
    val latestCanLoadMore by rememberUpdatedState(canLoadMore)
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    val latestOnMenuRefresh by rememberUpdatedState(onMenuRefresh)
    val latestOnVideoFocused by rememberUpdatedState(onVideoFocused)
    val latestOnFocusedRowChanged by rememberUpdatedState(onFocusedRowChanged)
    val latestConsumeTopRowDpadUp by rememberUpdatedState(consumeTopRowDpadUp)
    val latestOnTopRowDpadUp by rememberUpdatedState(onTopRowDpadUp)
    val latestOnBackToTopBar by rememberUpdatedState(onBackToTopBar)
    val latestOnLeftEdgeDpadLeft by rememberUpdatedState(onLeftEdgeDpadLeft)
    val latestOnVideoMenu by rememberUpdatedState(onVideoMenu)
    val latestOnVideoLongClick by rememberUpdatedState(onVideoLongClick)
    val latestSupportingText by rememberUpdatedState(supportingText)
    val latestOnVideoClick by rememberUpdatedState(onVideoClick)
    val latestFocusCoordinator by rememberUpdatedState(focusCoordinator)
    val latestFocusTab by rememberUpdatedState(focusTab)
    val latestFocusRegion by rememberUpdatedState(focusRegion)
    val dpadGridController = remember { DpadGridController() }
    val recyclerViewRef = remember { RecyclerViewRef() }

    SideEffect {
        dpadGridController.updateCallbacks(
            DpadGridController.Callbacks(
                onTopEdge = {
                    if (latestConsumeTopRowDpadUp) {
                        latestOnTopRowDpadUp()
                    } else {
                        false
                    }
                },
                onLeftEdge = {
                    latestOnLeftEdgeDpadLeft?.invoke() == true
                },
                canLoadMore = { latestCanLoadMore() },
                loadMore = {
                    latestOnLoadMore()
                },
                preloadRowsAhead = { recyclerView, position, spanCount, rowCount ->
                    (recyclerView.adapter as? HomeVideoCardAdapter)?.preloadRowsAhead(
                        recyclerView = recyclerView,
                        position = position,
                        spanCount = spanCount,
                        rowCount = rowCount,
                    )
                },
                parkFocusForScroll = { targetPosition ->
                    focusState.parkFocusForDirectionalScroll(targetPosition)
                },
                onMenu = {
                    latestOnMenuRefresh?.invoke()
                    latestOnMenuRefresh != null
                },
                onBack = {
                    latestOnBackToTopBar?.invoke() == true
                },
            )
        )
    }

    DisposableEffect(dpadGridController, focusState) {
        onDispose {
            recyclerViewRef.value?.let(focusState::detach)
            recyclerViewRef.value = null
            dpadGridController.detach()
        }
    }

    DisposableEffect(focusCoordinator, focusTab, focusRegion, focusState) {
        focusState.setOnFocusTargetAvailabilityChanged {
            focusCoordinator?.drainPendingFocus()
        }
        val registration = if (focusCoordinator != null && focusTab != null) {
            focusCoordinator.registerContentTarget(
                tab = focusTab,
                region = focusRegion,
                target = object : HomeFocusTarget {
                    override fun tryRequestFocus(): Boolean {
                        return focusState.tryFocusVisibleItem()
                    }

                    override fun tryRequestFocusKey(key: String): Boolean {
                        return focusState.tryFocusKey(key)
                    }

                    override fun hasFocus(): Boolean {
                        return focusState.hasFocusInside()
                    }

                    override fun hasRememberedFocus(): Boolean {
                        return focusState.hasRememberedFocus()
                    }

                    override fun clearFocusVisualState(): Boolean {
                        return focusState.clearVisibleFocusVisualState()
                    }
                }
            )
        } else {
            null
        }
        onDispose {
            registration?.unregister()
            focusState.setOnFocusTargetAvailabilityChanged(null)
        }
    }

    LaunchedEffect(scrollResetKey) {
        if (scrollResetKey != null) {
            focusState.requestScrollToTop()
        }
    }

    AndroidView(
        modifier = modifier.focusGroup(),
        factory = { rawContext ->
            val context = ContextThemeWrapper(rawContext, R.style.Theme_BBTTVV)
            object : RecyclerView(context) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (event.action == KeyEvent.ACTION_DOWN && event.keyCode in GridNavigationKeyCodes) {
                        val focusedView = rootView?.findFocus()
                        val currentPosition = focusedView
                            ?.let(::findContainingViewHolder)
                            ?.bindingAdapterPosition
                            ?.takeIf { it != NO_POSITION }
                        if (currentPosition != null && event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            focusState.noteUserNavigation(event.keyCode, event)
                            if (dpadGridController.handleItemKeyEvent(
                                    itemView = focusedView,
                                    position = currentPosition,
                                    keyCode = event.keyCode,
                                    event = event,
                                )
                            ) {
                                return true
                            }
                        }
                        if (
                            currentPosition == null &&
                            focusedView != null &&
                            focusedView.isSameOrDescendantOf(this)
                        ) {
                            focusState.noteUserNavigation(event.keyCode, event)
                            if (dpadGridController.handleItemKeyEvent(
                                    itemView = focusedView,
                                    position = NO_POSITION,
                                    keyCode = event.keyCode,
                                    event = event,
                                )
                            ) {
                                return true
                            }
                            return true
                        }
                    }
                    return super.dispatchKeyEvent(event)
                }

                override fun focusSearch(focused: View?, direction: Int): View? {
                    val next = super.focusSearch(focused, direction)
                    if (next === this) {
                        return focused
                    }
                    if (direction == View.FOCUS_DOWN) {
                        val currentPosition = focused
                            ?.let(::findContainingViewHolder)
                            ?.bindingAdapterPosition
                            ?.takeIf { it != NO_POSITION }
                        val nextPosition = next
                            ?.let(::findContainingViewHolder)
                            ?.bindingAdapterPosition
                            ?.takeIf { it != NO_POSITION }
                        val itemCount = adapter?.itemCount ?: 0
                        val spanCount = (layoutManager as? GridLayoutManager)?.spanCount?.takeIf { it > 0 }
                        if (
                            currentPosition != null &&
                            ((spanCount != null && currentPosition + spanCount >= itemCount) ||
                                (nextPosition != null && nextPosition <= currentPosition))
                        ) {
                            return focused
                        }
                    }
                    if (direction == View.FOCUS_UP) {
                        val currentPosition = focused
                            ?.let(::findContainingViewHolder)
                            ?.bindingAdapterPosition
                            ?.takeIf { it != NO_POSITION }
                        val spanCount = (layoutManager as? GridLayoutManager)?.spanCount?.takeIf { it > 0 }
                        if (currentPosition != null && spanCount != null && currentPosition >= spanCount) {
                            val targetPosition = currentPosition - spanCount
                            findViewHolderForAdapterPosition(targetPosition)?.itemView?.let { return it }
                            scrollToPosition(targetPosition)
                            return focused
                        }
                    }
                    if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT || direction == View.FOCUS_DOWN) {
                        if (next == null) return focused
                        var parent = next.parent
                        var isChild = false
                        while (parent != null) {
                            if (parent === this) {
                                isChild = true
                                break
                            }
                            parent = parent.parent
                        }
                        if (!isChild) return focused
                    }
                    return next
                }
            }.apply {
                recyclerViewRef.value = this
                focusState.attach(this)
                dpadGridController.attach(this)
                configureVideoCardRecycler()
                clipToPadding = false
                clipChildren = false
                setPadding(paddingPx.start, paddingPx.top, paddingPx.end, paddingPx.bottom)
                layoutManager = GridLayoutManager(context, gridColumnCount)
                setOnFocusChangeListener { _, hasFocus ->
                    focusState.onRecyclerFocusChanged(hasFocus)
                }

                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        if (dy <= 0) return
                        val manager = recyclerView.layoutManager as? GridLayoutManager ?: return
                        val totalItemCount = manager.itemCount
                        if (totalItemCount <= 0) return
                        val lastVisibleItemPosition = manager.findLastVisibleItemPosition()
                        if (
                            lastVisibleItemPosition >= totalItemCount - loadMorePrefetchItems &&
                            latestCanLoadMore()
                        ) {
                            latestOnLoadMore()
                        }
                    }
                })

                adapter = HomeVideoCardAdapter(
                    showHistoryProgressOnly = showHistoryProgressOnly,
                    showDanmakuCount = showDanmakuCount,
                    onItemClick = { item ->
                        latestOnVideoClick(item.video, item.key)
                    },
                    onItemFocused = { item, position ->
                        dpadGridController.onItemFocused(position = position)
                        focusState.onItemFocused(item.key, position)
                        latestFocusTab?.let { tab ->
                            latestFocusCoordinator?.onContentRegionFocused(tab, latestFocusRegion)
                        }
                        latestOnFocusedRowChanged(position / gridColumnCount.coerceAtLeast(1))
                        latestOnVideoFocused(item.video, item.key)
                    },
                    onItemMenu = onVideoMenu?.let {
                        { item -> latestOnVideoMenu?.invoke(item.video) }
                    },
                    onItemLongClick = onVideoLongClick?.let {
                        { item -> latestOnVideoLongClick?.invoke(item.video) }
                    },
                    onItemKeyEvent = { itemView, _, position, keyCode, event ->
                        focusState.noteUserNavigation(keyCode, event)
                        dpadGridController.handleItemKeyEvent(
                            itemView = itemView,
                            position = position,
                            keyCode = keyCode,
                            event = event,
                        )
                    },
                    onBackKeyUp = latestOnBackToTopBar?.let {
                        { latestOnBackToTopBar?.invoke() == true }
                    },
                    supportingTextProvider = { item ->
                        latestSupportingText?.invoke(item.video)
                },
                ).apply {
                    submitList(items) {
                        dpadGridController.onItemsCommitted()
                        focusState.onItemsCommitted()
                        latestFocusCoordinator?.drainPendingFocus()
                    }
                }
            }
        },
        update = { recyclerView ->
            recyclerViewRef.value = recyclerView
            focusState.attach(recyclerView)
            dpadGridController.attach(recyclerView)
            recyclerView.clipToPadding = false
            recyclerView.clipChildren = false
            recyclerView.setPadding(paddingPx.start, paddingPx.top, paddingPx.end, paddingPx.bottom)
            val manager = recyclerView.layoutManager as? GridLayoutManager
            if (manager == null) {
                recyclerView.layoutManager = GridLayoutManager(recyclerView.context, gridColumnCount)
            } else if (manager.spanCount != gridColumnCount) {
                manager.spanCount = gridColumnCount
            }
            (recyclerView.adapter as? HomeVideoCardAdapter)?.let { adapter ->
                adapter.updateLayoutOptions(
                    showHistoryProgressOnly = showHistoryProgressOnly,
                    showDanmakuCount = showDanmakuCount,
                    fixedItemWidthPx = null,
                )
                if (adapter.currentList != items && !dpadGridController.hasPendingLoadMoreFocus()) {
                    focusState.prepareForDataSetChange(items)
                }
                adapter.submitList(items) {
                    dpadGridController.onItemsCommitted()
                    focusState.onItemsCommitted()
                    latestFocusCoordinator?.drainPendingFocus()
                }
            }
        }
    )
}

@Composable
internal fun VideoCardRecyclerRow(
    videos: List<VideoItem>,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalContainerWidth: Dp? = null,
    showHistoryProgressOnly: Boolean = false,
    onRailFocusChanged: (Boolean) -> Unit = {},
    onHorizontalRailFocusChanged: (Dp?) -> Unit = {},
    onVideoFocused: (VideoItem, String) -> Unit = { _, _ -> },
    onVideoClick: (VideoItem, String) -> Unit,
) {
    val items = remember(videos) { videos.toHomeRecommendVideoCardItems() }
    val paddingPx = rememberRecyclerPadding(contentPadding)
    val density = LocalDensity.current
    val fixedItemWidthPx = with(density) { cardWidth.roundToPx() }
    val latestOnRailFocusChanged by rememberUpdatedState(onRailFocusChanged)
    val latestOnHorizontalRailFocusChanged by rememberUpdatedState(onHorizontalRailFocusChanged)
    val latestOnVideoFocused by rememberUpdatedState(onVideoFocused)
    val latestOnVideoClick by rememberUpdatedState(onVideoClick)
    val latestItemCount by rememberUpdatedState(items.size)
    val latestHorizontalContainerWidth by rememberUpdatedState(horizontalContainerWidth)

    AndroidView(
        modifier = modifier.focusGroup(),
        factory = { rawContext ->
            val context = ContextThemeWrapper(rawContext, R.style.Theme_BBTTVV)
            object : RecyclerView(context) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (
                        event.action == KeyEvent.ACTION_DOWN &&
                        (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                            event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                    ) {
                        latestOnHorizontalRailFocusChanged(latestHorizontalContainerWidth)
                        val position = rootView
                            ?.findFocus()
                            ?.let(::findContainingViewHolder)
                            ?.bindingAdapterPosition
                            ?.takeIf { it != NO_POSITION }
                            ?: return super.dispatchKeyEvent(event)
                        val targetPosition = when (event.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> position - 1
                            KeyEvent.KEYCODE_DPAD_RIGHT -> position + 1
                            else -> position
                        }
                        if (targetPosition !in 0 until latestItemCount) return true
                        findViewHolderForAdapterPosition(targetPosition)?.itemView?.let { target ->
                            if (target.requestFocus()) return true
                        }
                        scrollToPosition(targetPosition)
                        post {
                            findViewHolderForAdapterPosition(targetPosition)?.itemView?.requestFocus()
                        }
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }

                override fun focusSearch(focused: View?, direction: Int): View? {
                    val position = focused?.let(::findContainingViewHolder)
                        ?.bindingAdapterPosition
                        ?.takeIf { it != NO_POSITION }
                    val itemCount = adapter?.itemCount ?: 0
                    if (
                        position != null &&
                        itemCount > 0 &&
                        isHorizontalRecyclerFocusBoundary(
                            position = position,
                            itemCount = itemCount,
                            direction = direction
                        )
                    ) {
                        return focused
                    }

                    val next = super.focusSearch(focused, direction)
                    if (next === this) {
                        return focused
                    }
                    if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT || direction == View.FOCUS_DOWN) {
                        if (next == null) return focused
                        var parent = next.parent
                        var isChild = false
                        while (parent != null) {
                            if (parent === this) {
                                isChild = true
                                break
                            }
                            parent = parent.parent
                        }
                        if (!isChild) return focused
                    }
                    return next
                }
            }.apply {
                configureVideoCardRecycler()
                setPadding(paddingPx.start, paddingPx.top, paddingPx.end, paddingPx.bottom)
                layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                setOnFocusChangeListener { _, hasFocus ->
                    latestOnRailFocusChanged(hasFocus)
                    if (!hasFocus) {
                        latestOnHorizontalRailFocusChanged(null)
                    }
                }

                adapter = HomeVideoCardAdapter(
                    showHistoryProgressOnly = showHistoryProgressOnly,
                    fixedItemWidthPx = fixedItemWidthPx,
                    onItemClick = { item ->
                        latestOnVideoClick(item.video, item.key)
                    },
                    onItemFocused = { item, _ ->
                        latestOnRailFocusChanged(true)
                        latestOnVideoFocused(item.video, item.key)
                    },
                    onItemKeyEvent = { _, _, position, keyCode, event ->
                        if (event.action != KeyEvent.ACTION_DOWN) {
                            false
                        } else when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> position <= 0
                            KeyEvent.KEYCODE_DPAD_RIGHT -> position >= latestItemCount - 1

                            KeyEvent.KEYCODE_DPAD_UP,
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                latestOnHorizontalRailFocusChanged(null)
                                false
                            }

                            else -> false
                        }
                    },
                ).apply {
                    submitList(items)
                }
            }
        },
        update = { recyclerView ->
            recyclerView.setPadding(paddingPx.start, paddingPx.top, paddingPx.end, paddingPx.bottom)
            (recyclerView.adapter as? HomeVideoCardAdapter)?.let { adapter ->
                adapter.updateLayoutOptions(
                    showHistoryProgressOnly = showHistoryProgressOnly,
                    fixedItemWidthPx = fixedItemWidthPx,
                )
                val currentFocused = recyclerView.rootView?.findFocus()
                if (
                    adapter.currentList != items &&
                    (currentFocused === recyclerView || currentFocused?.isSameOrDescendantOf(recyclerView) == true)
                ) {
                    recyclerView.parkFocusForDataSetReset()
                }
                adapter.submitList(items)
            }
        }
    )
}

@Composable
private fun rememberRecyclerPadding(contentPadding: PaddingValues): RecyclerPaddingPx {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    return RecyclerPaddingPx(
        start = with(density) { contentPadding.calculateStartPadding(layoutDirection).roundToPx() },
        top = with(density) { contentPadding.calculateTopPadding().roundToPx() },
        end = with(density) { contentPadding.calculateEndPadding(layoutDirection).roundToPx() },
        bottom = with(density) { contentPadding.calculateBottomPadding().roundToPx() },
    )
}

private fun RecyclerView.configureVideoCardRecycler() {
    clipToPadding = false
    clipChildren = false
    overScrollMode = View.OVER_SCROLL_NEVER
    setHasFixedSize(true)
    setItemViewCacheSize(12)
    descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    isFocusable = true
    isFocusableInTouchMode = false
    defaultFocusHighlightEnabled = false
    itemAnimator = null
    installChildFocusParkingOnDetach()
}

private val GridNavigationKeyCodes = setOf(
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
)

private data class RecyclerPaddingPx(
    val start: Int,
    val top: Int,
    val end: Int,
    val bottom: Int,
)

private class RecyclerViewRef {
    var value: RecyclerView? = null
}

private fun isHorizontalRecyclerFocusBoundary(
    position: Int,
    itemCount: Int,
    direction: Int
): Boolean {
    return when (direction) {
        View.FOCUS_LEFT -> position <= 0
        View.FOCUS_RIGHT -> position >= itemCount - 1
        else -> false
    }
}
