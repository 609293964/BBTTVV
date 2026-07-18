package com.bbttvv.app.ui.home

import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.bbttvv.app.BuildConfig
import com.bbttvv.app.R
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.focus.GridFocusDebugLog
import com.bbttvv.app.ui.focus.isSameOrDescendantOf

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
    scrollResetOnFirstComposition: Boolean = true,
    initialScrollPosition: Int = RecyclerView.NO_POSITION,
    allowChildDrawingOutsideBounds: Boolean = true,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool? = null,
    onVerticalScrollOffsetChanged: (Int) -> Unit = {},
    showHistoryProgressOnly: Boolean = false,
    showDanmakuCount: Boolean = true,
    supportingText: ((VideoItem) -> String?)? = null,
    loadMorePrefetchItems: Int = 8,
    canLoadMore: () -> Boolean = { false },
    loadMoreInProgress: Boolean? = null,
    onLoadMore: () -> Unit = {},
    onMenuRefresh: (() -> Unit)? = null,
    onVideoFocused: (VideoItem, String) -> Unit = { _, _ -> },
    onFocusedRowChanged: (Int) -> Unit = {},
    consumeTopRowDpadUp: Boolean = true,
    onTopRowDpadUp: () -> Boolean = { false },
    onBackToTopBar: (() -> Boolean)? = null,
    onLeftEdgeDpadLeft: (() -> Boolean)? = null,
    onVideoMenu: ((VideoItem) -> Unit)? = null,
    onVideoLongClick: ((VideoItem, String) -> Unit)? = null,
    onVideoClick: (VideoItem, String) -> Unit,
) {
    val keyRegistry = remember { HomeRecommendVideoKeyRegistry() }
    val items = remember(videos, scrollResetKey) {
        if (scrollResetKey != null) {
            keyRegistry.resetAndBuild(videos)
        } else {
            keyRegistry.rebuildReusingAssigned(videos)
        }
    }
    VideoCardRecyclerGridItems(
        items = items,
        modifier = modifier,
        gridColumnCount = gridColumnCount,
        contentPadding = contentPadding,
        focusState = focusState,
        focusCoordinator = focusCoordinator,
        focusTab = focusTab,
        focusRegion = focusRegion,
        scrollResetKey = scrollResetKey,
        scrollResetOnFirstComposition = scrollResetOnFirstComposition,
        initialScrollPosition = initialScrollPosition,
        allowChildDrawingOutsideBounds = allowChildDrawingOutsideBounds,
        videoCardRecycledViewPool = videoCardRecycledViewPool,
        onVerticalScrollOffsetChanged = onVerticalScrollOffsetChanged,
        showHistoryProgressOnly = showHistoryProgressOnly,
        showDanmakuCount = showDanmakuCount,
        supportingText = supportingText,
        loadMorePrefetchItems = loadMorePrefetchItems,
        canLoadMore = canLoadMore,
        loadMoreInProgress = loadMoreInProgress,
        onLoadMore = onLoadMore,
        onMenuRefresh = onMenuRefresh,
        onVideoFocused = onVideoFocused,
        onFocusedRowChanged = onFocusedRowChanged,
        consumeTopRowDpadUp = consumeTopRowDpadUp,
        onTopRowDpadUp = onTopRowDpadUp,
        onBackToTopBar = onBackToTopBar,
        onLeftEdgeDpadLeft = onLeftEdgeDpadLeft,
        onVideoMenu = onVideoMenu,
        onVideoLongClick = onVideoLongClick,
        onVideoClick = onVideoClick,
    )
}

@Composable
internal fun VideoCardRecyclerGridItems(
    items: List<HomeRecommendVideoCardItem>,
    modifier: Modifier = Modifier,
    gridColumnCount: Int = 4,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    focusState: HomeRecommendGridFocusState = remember { HomeRecommendGridFocusState() },
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    focusRegion: HomeFocusRegion = HomeFocusRegion.Grid,
    scrollResetKey: Any? = null,
    scrollResetOnFirstComposition: Boolean = true,
    initialScrollPosition: Int = RecyclerView.NO_POSITION,
    allowChildDrawingOutsideBounds: Boolean = true,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool? = null,
    onVerticalScrollOffsetChanged: (Int) -> Unit = {},
    showHistoryProgressOnly: Boolean = false,
    showDanmakuCount: Boolean = true,
    supportingText: ((VideoItem) -> String?)? = null,
    loadMorePrefetchItems: Int = 8,
    canLoadMore: () -> Boolean = { false },
    loadMoreInProgress: Boolean? = null,
    onLoadMore: () -> Unit = {},
    onMenuRefresh: (() -> Unit)? = null,
    onVideoFocused: (VideoItem, String) -> Unit = { _, _ -> },
    onFocusedRowChanged: (Int) -> Unit = {},
    consumeTopRowDpadUp: Boolean = true,
    onTopRowDpadUp: () -> Boolean = { false },
    onBackToTopBar: (() -> Boolean)? = null,
    onLeftEdgeDpadLeft: (() -> Boolean)? = null,
    onVideoMenu: ((VideoItem) -> Unit)? = null,
    onVideoLongClick: ((VideoItem, String) -> Unit)? = null,
    onVideoClick: (VideoItem, String) -> Unit,
) {
    val paddingPx = rememberRecyclerPadding(contentPadding)
    val isHomeTabActive = LocalHomeTabActive.current
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
    val latestOnVerticalScrollOffsetChanged by rememberUpdatedState(onVerticalScrollOffsetChanged)
    val verticalOffsetDispatcher = remember { RecyclerVerticalOffsetDispatcher() }
    val latestOnVideoClick by rememberUpdatedState(onVideoClick)
    val latestFocusCoordinator by rememberUpdatedState(focusCoordinator)
    val latestFocusTab by rememberUpdatedState(focusTab)
    val latestFocusRegion by rememberUpdatedState(focusRegion)
    val latestGridColumnCount by rememberUpdatedState(gridColumnCount)
    val latestIsHomeTabActive by rememberUpdatedState(isHomeTabActive)
    val dpadGridController = remember { DpadGridController() }
    val preloadThrottler = remember { RecyclerGridPreloadThrottler() }
    val focusDispatchState = remember { VideoGridFocusDispatchState() }
    val recyclerViewRef = remember { RecyclerViewRef() }
    var hasObservedScrollResetKey by remember { mutableStateOf(false) }

    SideEffect {
        dpadGridController.updateCallbacks(
            DpadGridController.Callbacks(
                onTopEdge = {
                    if (latestIsHomeTabActive && latestConsumeTopRowDpadUp) {
                        latestOnTopRowDpadUp()
                    } else {
                        false
                    }
                },
                onLeftEdge = {
                    latestIsHomeTabActive && latestOnLeftEdgeDpadLeft?.invoke() == true
                },
                canLoadMore = { latestIsHomeTabActive && latestCanLoadMore() },
                loadMore = {
                    if (latestIsHomeTabActive) {
                        latestOnLoadMore()
                    }
                },
                preloadRowsAhead = { recyclerView, position, spanCount, rowCount ->
                    preloadThrottler.preloadRowsAhead(
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
                    val refresh = latestOnMenuRefresh
                    if (latestIsHomeTabActive && refresh != null) {
                        focusState.requestMenuRefreshFocusToFirstItem()
                        refresh()
                        true
                    } else {
                        false
                    }
                },
                onBack = {
                    latestIsHomeTabActive && latestOnBackToTopBar?.invoke() == true
                },
                onFocusSettled = {
                    GridFocusDebugLog.d {
                        "VideoCardRecyclerGrid.onFocusSettled cancelAllPendingRequests=false " +
                            "${focusState.debugSnapshot()} ${dpadGridController.debugSnapshot()} " +
                            GridFocusDebugLog.recycler(recyclerViewRef.value)
                    }
                },
            )
        )
    }

    DisposableEffect(dpadGridController, focusState, videoCardRecycledViewPool) {
        focusState.setPhysicalScrollAllowedProvider {
            !dpadGridController.hasPendingLoadMoreFocus()
        }
        onDispose {
            focusState.setPhysicalScrollAllowedProvider(null)
            preloadThrottler.cancel()
            dpadGridController.detach()
            recyclerViewRef.value?.let { recyclerView ->
                focusState.detach(recyclerView)
                if (videoCardRecycledViewPool != null) {
                    recyclerView.swapAdapter(null, true)
                }
            }
            recyclerViewRef.value = null
        }
    }

    DisposableEffect(focusCoordinator, focusTab, focusRegion, focusState, isHomeTabActive) {
        focusState.setOnFocusTargetAvailabilityChanged {
            if (isHomeTabActive) {
                focusCoordinator?.drainPendingFocus()
            }
        }
        val registration = if (isHomeTabActive && focusCoordinator != null && focusTab != null) {
            focusCoordinator.registerContentTarget(
                tab = focusTab,
                region = focusRegion,
                target = object : HomeFocusTarget {
                    override fun tryRequestFocus(): Boolean {
                        dpadGridController.cancelAllPendingRequests()
                        return focusState.tryFocusVisibleItem()
                    }

                    override fun requestFocusResult(): HomeFocusRequestResult {
                        dpadGridController.cancelAllPendingRequests()
                        return focusState.requestFocusVisibleItem()
                    }

                    override fun tryRequestFocusForEntry(entryHint: HomeFocusEntryHint): Boolean {
                        dpadGridController.cancelAllPendingRequests()
                        return focusState.tryFocusEntryItem(entryHint.preferredIndex)
                    }

                    override fun requestFocusForEntryResult(entryHint: HomeFocusEntryHint): HomeFocusRequestResult {
                        dpadGridController.cancelAllPendingRequests()
                        return focusState.requestFocusEntryItem(entryHint.preferredIndex)
                    }

                    override fun tryRequestFocusKey(key: String): Boolean {
                        dpadGridController.cancelAllPendingRequests()
                        return focusState.tryFocusKey(key)
                    }

                    override fun requestFocusKeyResult(key: String): HomeFocusRequestResult {
                        dpadGridController.cancelAllPendingRequests()
                        return focusState.requestFocusKey(key)
                    }

                    override fun tryRequestFocusKeyOrFallback(key: String): Boolean {
                        dpadGridController.cancelAllPendingRequests()
                        return focusState.tryFocusKeyOrFallback(key)
                    }

                    override fun requestFocusKeyOrFallbackResult(key: String): HomeFocusRequestResult {
                        dpadGridController.cancelAllPendingRequests()
                        return focusState.requestFocusKeyOrFallback(key)
                    }

                    override fun requestBackReturnFocusKeyResult(key: String): HomeBackReturnRestoreResult {
                        dpadGridController.cancelAllPendingRequests()
                        return focusState.requestBackReturnFocusKey(key)
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

    fun cancelPendingRestoreForUserGridNavigation(keyCode: Int, event: KeyEvent) {
        if (!latestIsHomeTabActive) return
        if (event.action != KeyEvent.ACTION_DOWN || keyCode !in GridNavigationKeyCodes) return
        val tab = latestFocusTab ?: return
        latestFocusCoordinator?.cancelPendingRestoreVideoKeyForUserNavigation(tab)
    }

    LaunchedEffect(scrollResetKey, scrollResetOnFirstComposition) {
        verticalOffsetDispatcher.reset()
        if (scrollResetKey == null) {
            hasObservedScrollResetKey = false
            return@LaunchedEffect
        }
        val shouldReset = scrollResetOnFirstComposition || hasObservedScrollResetKey
        hasObservedScrollResetKey = true
        if (shouldReset) {
            focusState.requestScrollToTop()
        }
    }

    AndroidView(
        modifier = modifier.focusGroup(),
        factory = { rawContext ->
            val context = ContextThemeWrapper(rawContext, R.style.Theme_BBTTVV)
            object : RecyclerView(context) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    GridFocusDebugLog.d {
                        "VideoCardRecyclerGrid.RecyclerView.dispatchKeyEvent before " +
                            "${GridFocusDebugLog.event(event)} ${focusState.debugSnapshot()} " +
                            "${dpadGridController.debugSnapshot()} ${GridFocusDebugLog.recycler(this)}"
                    }
                    if (event.action == KeyEvent.ACTION_DOWN && event.keyCode in GridNavigationKeyCodes) {
                        val focusedView = rootView?.findFocus()
                        val currentPosition = focusedView
                            ?.let(::findContainingViewHolder)
                            ?.bindingAdapterPosition
                            ?.takeIf { it != NO_POSITION }
                        if (currentPosition != null && (event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) {
                            cancelPendingRestoreForUserGridNavigation(event.keyCode, event)
                            focusState.noteUserNavigation(event.keyCode, event)
                            if (dpadGridController.handleItemKeyEvent(
                                    itemView = focusedView,
                                    position = currentPosition,
                                    keyCode = event.keyCode,
                                    event = event,
                                )
                            ) {
                                GridFocusDebugLog.d {
                                    "VideoCardRecyclerGrid.RecyclerView.dispatchKeyEvent handledByController=true " +
                                        "${GridFocusDebugLog.event(event)} adapterPosition=$currentPosition " +
                                        "${focusState.debugSnapshot()} ${dpadGridController.debugSnapshot()}"
                                }
                                return true
                            }
                        }
                        if (
                            currentPosition == null &&
                            focusedView != null &&
                            focusedView.isSameOrDescendantOf(this)
                        ) {
                            cancelPendingRestoreForUserGridNavigation(event.keyCode, event)
                            focusState.noteUserNavigation(event.keyCode, event)
                            if (dpadGridController.handleItemKeyEvent(
                                    itemView = focusedView,
                                    position = NO_POSITION,
                                    keyCode = event.keyCode,
                                    event = event,
                                )
                            ) {
                                GridFocusDebugLog.d {
                                    "VideoCardRecyclerGrid.RecyclerView.dispatchKeyEvent noPositionRecoveryHandled=true " +
                                        "${GridFocusDebugLog.event(event)} adapterPosition=$NO_POSITION " +
                                        "${focusState.debugSnapshot()} ${dpadGridController.debugSnapshot()}"
                                }
                                return true
                            }
                            GridFocusDebugLog.d {
                                "VideoCardRecyclerGrid.RecyclerView.dispatchKeyEvent noPositionConsumed=false " +
                                    "controllerHandled=false ${GridFocusDebugLog.event(event)} " +
                                    "${focusState.debugSnapshot()} ${dpadGridController.debugSnapshot()} " +
                                    GridFocusDebugLog.recycler(this)
                            }
                        }
                    }
                    val handled = super.dispatchKeyEvent(event)
                    GridFocusDebugLog.d {
                        "VideoCardRecyclerGrid.RecyclerView.dispatchKeyEvent superResult=$handled " +
                            "${GridFocusDebugLog.event(event)} ${focusState.debugSnapshot()} " +
                            "${dpadGridController.debugSnapshot()} ${GridFocusDebugLog.recycler(this)}"
                    }
                    return handled
                }

                override fun focusSearch(focused: View?, direction: Int): View? {
                    val next = super.focusSearch(focused, direction)
                    GridFocusDebugLog.d {
                        val currentPosition = focused
                            ?.let(::findContainingViewHolder)
                            ?.bindingAdapterPosition
                            ?: NO_POSITION
                        val nextPosition = next
                            ?.let(::findContainingViewHolder)
                            ?.bindingAdapterPosition
                            ?: NO_POSITION
                        "VideoCardRecyclerGrid.RecyclerView.focusSearch direction=$direction " +
                            "adapterPosition=$currentPosition nextPosition=$nextPosition " +
                            "nextClass=${next?.javaClass?.simpleName} ${focusState.debugSnapshot()} " +
                            "${dpadGridController.debugSnapshot()} ${GridFocusDebugLog.recycler(this)}"
                    }
                    if (next === this) {
                        GridFocusDebugLog.d {
                            "VideoCardRecyclerGrid.RecyclerView.focusSearch returnFocused reason=nextIsRecycler " +
                                "direction=$direction"
                        }
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
                        if (currentPosition != null && spanCount != null) {
                            val targetPosition = currentPosition + spanCount
                            if (targetPosition < itemCount) {
                                val hasValidNextFocus = next != null && next !== focused && next !== this &&
                                        nextPosition != null && nextPosition > currentPosition
                                if (!hasValidNextFocus) {
                                    focusState.schedulePendingDownSearch(targetPosition)
                                    GridFocusDebugLog.d {
                                        "VideoCardRecyclerGrid.RecyclerView.focusSearch returnFocused " +
                                            "reason=pendingDownSearch direction=$direction targetPosition=$targetPosition"
                                    }
                                    return focused
                                }
                            }
                        }
                        if (
                            currentPosition != null &&
                            ((spanCount != null && currentPosition + spanCount >= itemCount) ||
                                (nextPosition != null && nextPosition <= currentPosition) ||
                                (nextPosition == null && canScrollVertically(1)))
                        ) {
                            GridFocusDebugLog.d {
                                "VideoCardRecyclerGrid.RecyclerView.focusSearch returnFocused " +
                                    "reason=downBoundary direction=$direction adapterPosition=$currentPosition"
                            }
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
                            focusState.parkFocusForDirectionalScroll(targetPosition)
                            scrollToPosition(targetPosition)
                            requestFocusAfterScrollToPosition(
                                position = targetPosition,
                                expectedFocusedView = focused,
                            )
                            GridFocusDebugLog.d {
                                "VideoCardRecyclerGrid.RecyclerView.focusSearch returnFocused " +
                                    "reason=upScroll targetPosition=$targetPosition"
                            }
                            return focused
                        }
                    }
                    if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT || direction == View.FOCUS_UP || direction == View.FOCUS_DOWN) {
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
                        if (!isChild) {
                            if (direction == View.FOCUS_UP && latestConsumeTopRowDpadUp) {
                                return next
                            }
                            return focused
                        }
                    }
                    GridFocusDebugLog.d {
                        "VideoCardRecyclerGrid.RecyclerView.focusSearch returnNext direction=$direction " +
                            "nextClass=${next?.javaClass?.simpleName}"
                    }
                    return next
                }
            }.apply {
                recyclerViewRef.value = this
                focusState.attach(this)
                dpadGridController.attach(this)
                configureVideoCardRecycler()
                applyHomeTabActiveState(isHomeTabActive)
                videoCardRecycledViewPool?.let(::setRecycledViewPool)
                applyVideoCardRecyclerOverflowPolicy(allowChildDrawingOutsideBounds)
                setPadding(paddingPx.start, paddingPx.top, paddingPx.end, paddingPx.bottom)
                layoutManager = GridLayoutManager(context, gridColumnCount)
                setOnFocusChangeListener { _, hasFocus ->
                    focusState.onRecyclerFocusChanged(hasFocus)
                }

                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        val verticalScrollOffset = recyclerView.computeVerticalScrollOffset()
                        if (BuildConfig.DEBUG && dy != 0) {
                            Log.d("HomeFocus", "RecyclerView.onScrolled: dx=$dx dy=$dy scrollOffset=$verticalScrollOffset padTop=${recyclerView.paddingTop}")
                        }
                        verticalOffsetDispatcher.dispatch(
                            verticalScrollOffset,
                            latestOnVerticalScrollOffsetChanged,
                        )
                        if (dy <= 0) return
                        val manager = recyclerView.layoutManager as? GridLayoutManager ?: return
                        val totalItemCount = manager.itemCount
                        if (totalItemCount <= 0) return
                        val lastVisibleItemPosition = manager.findLastVisibleItemPosition()
                        if (
                            lastVisibleItemPosition >= totalItemCount - loadMorePrefetchItems &&
                            latestIsHomeTabActive &&
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
                        if (latestIsHomeTabActive) {
                            latestOnVideoClick(item.video, item.key)
                        }
                    },
                    onItemFocused = { item, position ->
                        GridFocusDebugLog.d {
                            "VideoCardRecyclerGrid.onItemFocused before adapterPosition=$position " +
                                "itemKey=${item.key} ${focusState.debugSnapshot()} " +
                                "${dpadGridController.debugSnapshot()} " +
                                GridFocusDebugLog.recycler(recyclerViewRef.value)
                        }
                        if (dpadGridController.onItemFocused(position = position)) {
                            focusState.onItemFocused(item.key, position)
                            GridFocusDebugLog.d {
                                "VideoCardRecyclerGrid.onItemFocused accepted=true " +
                                    "cancelAllPendingRequests=false adapterPosition=$position itemKey=${item.key} " +
                                    "${focusState.debugSnapshot()} ${dpadGridController.debugSnapshot()}"
                            }
                            if (latestIsHomeTabActive) {
                                recyclerViewRef.value?.let { recyclerView ->
                                    preloadThrottler.preloadRowsAhead(
                                        recyclerView = recyclerView,
                                        position = position,
                                        spanCount = latestGridColumnCount.coerceAtLeast(1),
                                        rowCount = FocusSettledPreloadRows,
                                    )
                                }
                            }
                            if (latestIsHomeTabActive && focusDispatchState.shouldDispatch(item.key)) {
                                latestFocusTab?.let { tab ->
                                    latestFocusCoordinator?.onContentRegionFocused(tab, latestFocusRegion)
                                }
                                latestFocusCoordinator?.drainPendingFocus()
                                latestOnFocusedRowChanged(position / latestGridColumnCount.coerceAtLeast(1))
                                latestOnVideoFocused(item.video, item.key)
                            }
                        } else {
                            GridFocusDebugLog.d {
                                "VideoCardRecyclerGrid.onItemFocused accepted=false adapterPosition=$position " +
                                    "itemKey=${item.key} ${focusState.debugSnapshot()} " +
                                    dpadGridController.debugSnapshot()
                            }
                        }
                    },
                    onItemMenu = onVideoMenu?.let {
                        { item ->
                            if (latestIsHomeTabActive) {
                                latestOnVideoMenu?.invoke(item.video)
                            }
                        }
                    },
                    onItemLongClick = onVideoLongClick?.let {
                        { item ->
                            if (latestIsHomeTabActive) {
                                latestOnVideoLongClick?.invoke(item.video, item.key)
                            }
                        }
                    },
                    onItemKeyEvent = { itemView, _, position, keyCode, event ->
                        if (!latestIsHomeTabActive) {
                            false
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            false
                        } else {
                            cancelPendingRestoreForUserGridNavigation(keyCode, event)
                            focusState.noteUserNavigation(keyCode, event)
                            dpadGridController.handleItemKeyEvent(
                                itemView = itemView,
                                position = position,
                                keyCode = keyCode,
                                event = event,
                            )
                        }
                    },
                    onBackKeyUp = latestOnBackToTopBar?.let {
                        { latestIsHomeTabActive && latestOnBackToTopBar?.invoke() == true }
                    },
                    supportingTextProvider = { item ->
                        latestSupportingText?.invoke(item.video)
                },
                ).apply {
                    GridFocusDebugLog.d {
                        "VideoCardRecyclerGrid.submitList initial before listChanged=true isAppend=false " +
                            "calledSubmitList=true oldItemCount=$itemCount newItemCount=${items.size} " +
                            "${focusState.debugSnapshot()} ${dpadGridController.debugSnapshot()} " +
                            GridFocusDebugLog.recycler(recyclerViewRef.value)
                    }
                    submitList(items) {
                        GridFocusDebugLog.d {
                            "VideoCardRecyclerGrid.submitList initial committed calledSubmitList=true " +
                                "itemCount=$itemCount ${focusState.debugSnapshot()} " +
                                "${dpadGridController.debugSnapshot()} " +
                                GridFocusDebugLog.recycler(recyclerViewRef.value)
                        }
                        applyInitialScrollPosition(initialScrollPosition)
                        dpadGridController.onItemsCommitted()
                        focusState.onItemsCommitted()
                        verticalOffsetDispatcher.dispatch(
                            computeVerticalScrollOffset(),
                            latestOnVerticalScrollOffsetChanged,
                        )
                        if (latestIsHomeTabActive) {
                            latestFocusCoordinator?.drainPendingFocus()
                        }
                    }
                }
            }
        },
        update = { recyclerView ->
            recyclerViewRef.value = recyclerView
            focusState.attach(recyclerView)
            dpadGridController.attach(recyclerView)
            recyclerView.applyHomeTabActiveState(isHomeTabActive)
            recyclerView.applyVideoCardRecyclerOverflowPolicy(allowChildDrawingOutsideBounds)
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
                val listChanged = adapter.currentList != items
                if (!listChanged) {
                    if (
                        GridLoadMoreFocusRecoveryPolicy.shouldRestoreFocus(
                            hasPendingLoadMoreFocus = dpadGridController.hasPendingLoadMoreFocus(),
                            listChanged = false,
                            loadMoreInProgress = loadMoreInProgress,
                        )
                    ) {
                        dpadGridController.cancelAllPendingRequests()
                        focusState.tryFocusVisibleItem()
                    }
                    GridFocusDebugLog.d {
                        "VideoCardRecyclerGrid.submitList update skipped listChanged=false " +
                            "isAppend=false calledSubmitList=false itemCount=${adapter.itemCount} " +
                            "${focusState.debugSnapshot()} ${dpadGridController.debugSnapshot()} " +
                            GridFocusDebugLog.recycler(recyclerView)
                    }
                    verticalOffsetDispatcher.dispatch(
                        recyclerView.computeVerticalScrollOffset(),
                        latestOnVerticalScrollOffsetChanged,
                    )
                    return@let
                }
                val isAppend = adapter.currentList.isNotEmpty() &&
                        items.size > adapter.currentList.size &&
                        items.take(adapter.currentList.size) == adapter.currentList
                val currentFocused = recyclerView.rootView?.findFocus()
                val focusInsideRecycler = currentFocused === recyclerView ||
                    currentFocused?.isSameOrDescendantOf(recyclerView) == true
                GridFocusDebugLog.d {
                    "VideoCardRecyclerGrid.submitList update before listChanged=$listChanged " +
                        "isAppend=$isAppend calledSubmitList=true oldItemCount=${adapter.currentList.size} " +
                        "newItemCount=${items.size} focusInsideRecycler=$focusInsideRecycler " +
                        "${focusState.debugSnapshot()} ${dpadGridController.debugSnapshot()} " +
                        GridFocusDebugLog.recycler(recyclerView)
                }

                if (isAppend) {
                    if (BuildConfig.DEBUG) {
                        Log.d("HomeFocus", "listChanged is pure APPEND. Keep Dpad pending target and skip focusState.prepareForDataSetChange.")
                    }
                } else {
                    if (!dpadGridController.hasPendingLoadMoreFocus()) {
                        dpadGridController.cancelAllPendingRequests()
                        focusState.prepareForDataSetChange(items)
                    } else {
                        focusState.cancelAllPendingRequests()
                    }
                }
                adapter.submitList(items) {
                    GridFocusDebugLog.d {
                        "VideoCardRecyclerGrid.submitList update committed listChanged=$listChanged " +
                            "isAppend=$isAppend calledSubmitList=true itemCount=${adapter.itemCount} " +
                            "${focusState.debugSnapshot()} ${dpadGridController.debugSnapshot()} " +
                            GridFocusDebugLog.recycler(recyclerView)
                    }
                    recyclerView.applyInitialScrollPosition(initialScrollPosition)
                    dpadGridController.onItemsCommitted()
                    focusState.onItemsCommitted()
                    verticalOffsetDispatcher.dispatch(
                        recyclerView.computeVerticalScrollOffset(),
                        latestOnVerticalScrollOffsetChanged,
                    )
                    if (latestIsHomeTabActive) {
                        latestFocusCoordinator?.drainPendingFocus()
                    }
                }
            }
        }
    )
}

internal object GridLoadMoreFocusRecoveryPolicy {
    fun shouldRestoreFocus(
        hasPendingLoadMoreFocus: Boolean,
        listChanged: Boolean,
        loadMoreInProgress: Boolean?,
    ): Boolean {
        return hasPendingLoadMoreFocus && !listChanged && loadMoreInProgress == false
    }
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
    val keyRegistry = remember { HomeRecommendVideoKeyRegistry() }
    val items = remember(videos) { keyRegistry.rebuildReusingAssigned(videos) }
    val paddingPx = rememberRecyclerPadding(contentPadding)
    val isHomeTabActive = LocalHomeTabActive.current
    val density = LocalDensity.current
    val fixedItemWidthPx = with(density) { cardWidth.roundToPx() }
    val latestOnRailFocusChanged by rememberUpdatedState(onRailFocusChanged)
    val latestOnHorizontalRailFocusChanged by rememberUpdatedState(onHorizontalRailFocusChanged)
    val latestOnVideoFocused by rememberUpdatedState(onVideoFocused)
    val latestOnVideoClick by rememberUpdatedState(onVideoClick)
    val latestItemCount by rememberUpdatedState(items.size)
    val latestHorizontalContainerWidth by rememberUpdatedState(horizontalContainerWidth)
    val latestIsHomeTabActive by rememberUpdatedState(isHomeTabActive)

    AndroidView(
        modifier = modifier.focusGroup(),
        factory = { rawContext ->
            val context = ContextThemeWrapper(rawContext, R.style.Theme_BBTTVV)
            object : RecyclerView(context) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (!latestIsHomeTabActive) return false
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
                applyHomeTabActiveState(isHomeTabActive)
                setPadding(paddingPx.start, paddingPx.top, paddingPx.end, paddingPx.bottom)
                layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                setOnFocusChangeListener { _, hasFocus ->
                    latestOnRailFocusChanged(hasFocus && latestIsHomeTabActive)
                    if (!hasFocus || !latestIsHomeTabActive) {
                        latestOnHorizontalRailFocusChanged(null)
                    }
                }

                adapter = HomeVideoCardAdapter(
                    showHistoryProgressOnly = showHistoryProgressOnly,
                    fixedItemWidthPx = fixedItemWidthPx,
                    onItemClick = { item ->
                        if (latestIsHomeTabActive) {
                            latestOnVideoClick(item.video, item.key)
                        }
                    },
                    onItemFocused = { item, _ ->
                        if (latestIsHomeTabActive) {
                            latestOnRailFocusChanged(true)
                            latestOnVideoFocused(item.video, item.key)
                        }
                    },
                    onItemKeyEvent = { _, _, position, keyCode, event ->
                        if (!latestIsHomeTabActive || event.action != KeyEvent.ACTION_DOWN) {
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
            recyclerView.applyHomeTabActiveState(isHomeTabActive)
            recyclerView.setPadding(paddingPx.start, paddingPx.top, paddingPx.end, paddingPx.bottom)
            (recyclerView.adapter as? HomeVideoCardAdapter)?.let { adapter ->
                adapter.updateLayoutOptions(
                    showHistoryProgressOnly = showHistoryProgressOnly,
                    fixedItemWidthPx = fixedItemWidthPx,
                )
                val listChanged = adapter.currentList != items
                val currentFocused = recyclerView.rootView?.findFocus()
                if (
                    listChanged &&
                    (currentFocused === recyclerView || currentFocused?.isSameOrDescendantOf(recyclerView) == true)
                ) {
                    recyclerView.parkFocusForDataSetReset()
                }
                if (!listChanged) return@let
                adapter.submitList(items)
            }
        }
    )
}

private class RecyclerVerticalOffsetDispatcher {
    private var lastOffset: Int? = null

    fun dispatch(offset: Int, onChanged: (Int) -> Unit) {
        if (lastOffset == offset) return
        lastOffset = offset
        onChanged(offset)
    }

    fun reset() {
        lastOffset = null
    }
}

private class RecyclerGridPreloadThrottler {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingPreload: Runnable? = null

    fun preloadRowsAhead(
        recyclerView: RecyclerView,
        position: Int,
        spanCount: Int,
        rowCount: Int,
    ) {
        val adapter = recyclerView.adapter as? HomeVideoCardAdapter ?: return
        DpadGridPreloadPolicy.positionsAhead(
            position = position,
            itemCount = adapter.itemCount,
            spanCount = spanCount,
            rowCount = rowCount,
        ) ?: return

        pendingPreload?.let(handler::removeCallbacks)
        val request = Runnable {
            pendingPreload = null
            if (!recyclerView.isAttachedToWindow || recyclerView.adapter !== adapter) return@Runnable
            adapter.preloadRowsAhead(
                recyclerView = recyclerView,
                position = position,
                spanCount = spanCount,
                rowCount = rowCount,
            )
        }
        pendingPreload = request
        handler.postDelayed(request, RecyclerGridPreloadDebounceMs)
    }

    fun cancel() {
        pendingPreload?.let(handler::removeCallbacks)
        pendingPreload = null
    }
}

private class VideoGridFocusDispatchState {
    private var lastDispatchedKey: String? = null
    private var lastDispatchedAtMs: Long = 0L

    fun shouldDispatch(key: String): Boolean {
        val now = SystemClock.uptimeMillis()
        val isRapidDuplicate = key == lastDispatchedKey &&
            now - lastDispatchedAtMs <= DuplicateFocusDispatchWindowMs
        lastDispatchedKey = key
        lastDispatchedAtMs = now
        return !isRapidDuplicate
    }
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

private fun RecyclerView.applyVideoCardRecyclerOverflowPolicy(
    allowChildDrawingOutsideBounds: Boolean,
) {
    clipToPadding = false
    clipChildren = !allowChildDrawingOutsideBounds
}

private fun RecyclerView.applyInitialScrollPosition(position: Int): Boolean {
    val targetPosition = InitialGridRestoreScrollPolicy.targetPosition(
        position = position,
        itemCount = adapter?.itemCount ?: 0,
    )
    if (targetPosition == RecyclerView.NO_POSITION) return false
    val manager = layoutManager as? GridLayoutManager
    if (manager != null) {
        manager.scrollToPositionWithOffset(targetPosition, paddingTop)
    } else {
        scrollToPosition(targetPosition)
    }
    return true
}

private fun RecyclerView.requestFocusAfterScrollToPosition(
    position: Int,
    expectedFocusedView: View?,
    attemptsLeft: Int = FocusSearchScrollFocusMaxAttempts,
) {
    if (position == RecyclerView.NO_POSITION) return
    postOnAnimation {
        val itemCount = adapter?.itemCount ?: return@postOnAnimation
        if (position !in 0 until itemCount) return@postOnAnimation

        val currentFocused = rootView?.findFocus()
        val focusStillAtScrollOrigin = currentFocused === this ||
            (
                expectedFocusedView != null &&
                    currentFocused?.isSameOrDescendantOf(expectedFocusedView) == true
                )
        if (!focusStillAtScrollOrigin) return@postOnAnimation

        val targetItem = findViewHolderForAdapterPosition(position)?.itemView
        if (targetItem != null && targetItem.isValidFocusTarget()) {
            val focused = targetItem.requestFocus()
            GridFocusDebugLog.d {
                "VideoCardRecyclerGrid.requestFocusAfterScrollToPosition calledRequestFocus=true " +
                    "requestFocusSuccess=$focused adapterPosition=$position attemptsLeft=$attemptsLeft " +
                    GridFocusDebugLog.recycler(this)
            }
            return@postOnAnimation
        }

        if (attemptsLeft > 0) {
            requestFocusAfterScrollToPosition(
                position = position,
                expectedFocusedView = expectedFocusedView,
                attemptsLeft = attemptsLeft - 1,
            )
        }
    }
}

internal object InitialGridRestoreScrollPolicy {
    fun targetPosition(position: Int, itemCount: Int): Int {
        if (position == RecyclerView.NO_POSITION || itemCount <= 0) {
            return RecyclerView.NO_POSITION
        }
        return position.coerceIn(0, itemCount - 1)
    }
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

private const val FocusSettledPreloadRows = 2
private const val RecyclerGridPreloadDebounceMs = 120L
private const val DuplicateFocusDispatchWindowMs = 500L
private const val FocusSearchScrollFocusMaxAttempts = 12

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
