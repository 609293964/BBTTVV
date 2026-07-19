package com.bbttvv.app.feature.search

import android.animation.AnimatorInflater
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import coil.transform.CircleCropTransformation
import com.bbttvv.app.R
import com.bbttvv.app.data.model.response.SearchUpItem
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.focus.isSameOrDescendantOf
import com.bbttvv.app.ui.home.DpadGridController
import com.bbttvv.app.ui.home.HomeFocusCoordinator
import com.bbttvv.app.ui.home.HomeFocusRequestResult
import com.bbttvv.app.ui.home.HomeFocusRegion
import com.bbttvv.app.ui.home.HomeFocusTarget
import com.bbttvv.app.ui.home.GridLoadMoreFocusRecoveryPolicy
import com.bbttvv.app.ui.home.LocalHomeTabActive
import com.bbttvv.app.ui.home.applyHomeTabActiveState
import com.bbttvv.app.ui.home.installChildFocusParkingOnDetach
import com.bbttvv.app.ui.home.parkFocusForDataSetReset
import com.bbttvv.app.ui.home.requestFocusParking
import com.bbttvv.app.ui.theme.LocalIsLightTheme
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

@Composable
internal fun UpSearchRecyclerGrid(
    items: List<SearchUpItem>,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    onVerticalScrollOffsetChanged: (Int) -> Unit = {},
    canLoadMore: () -> Boolean = { false },
    loadMoreInProgress: Boolean? = null,
    onLoadMore: () -> Unit = {},
    onTopRowDpadUp: () -> Boolean = { false },
    onOpenUp: (Long) -> Unit,
) {
    val paddingPx = rememberUpRecyclerPadding(contentPadding)
    val isHomeTabActive = LocalHomeTabActive.current
    val focusState = remember { UpSearchGridFocusState() }
    val dpadGridController = remember { DpadGridController() }
    val recyclerViewRef = remember { UpSearchRecyclerViewRef() }
    val latestCanLoadMore by rememberUpdatedState(canLoadMore)
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    val latestOnTopRowDpadUp by rememberUpdatedState(onTopRowDpadUp)
    val latestOnVerticalScrollOffsetChanged by rememberUpdatedState(onVerticalScrollOffsetChanged)
    val latestFocusCoordinator by rememberUpdatedState(focusCoordinator)
    val latestFocusTab by rememberUpdatedState(focusTab)
    val latestOnOpenUp by rememberUpdatedState(onOpenUp)
    val latestIsHomeTabActive by rememberUpdatedState(isHomeTabActive)
    val isLightTheme = LocalIsLightTheme.current
    val colors = remember(isLightTheme) {
        if (isLightTheme) UpSearchLightColors else UpSearchDarkColors
    }

    SideEffect {
        dpadGridController.updateCallbacks(
            DpadGridController.Callbacks(
                onTopEdge = { latestIsHomeTabActive && latestOnTopRowDpadUp() },
                canLoadMore = { latestIsHomeTabActive && latestCanLoadMore() },
                loadMore = {
                    if (latestIsHomeTabActive) {
                        latestOnLoadMore()
                    }
                },
                parkFocusForScroll = { targetPosition ->
                    focusState.parkFocusForDirectionalScroll(targetPosition)
                },
                clearFocusParking = {
                    focusState.clearDirectionalScrollFocus()
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

    DisposableEffect(focusCoordinator, focusTab, focusState, isHomeTabActive) {
        focusState.setOnFocusTargetAvailabilityChanged {
            if (isHomeTabActive) {
                focusCoordinator?.drainPendingFocus()
            }
        }
        val registration = if (isHomeTabActive && focusCoordinator != null && focusTab != null) {
            focusCoordinator.registerContentTarget(
                tab = focusTab,
                region = HomeFocusRegion.Grid,
                target = object : HomeFocusTarget {
                    override fun tryRequestFocus(): Boolean {
                        return focusState.tryFocusVisibleItem()
                    }

                    override fun requestFocusResult(): HomeFocusRequestResult {
                        return focusState.requestFocusVisibleItem()
                    }

                    override fun tryRequestFocusKey(key: String): Boolean {
                        return focusState.tryFocusKey(key)
                    }

                    override fun requestFocusKeyResult(key: String): HomeFocusRequestResult {
                        return focusState.requestFocusKey(key)
                    }

                    override fun tryRequestFocusKeyOrFallback(key: String): Boolean {
                        return focusState.tryFocusKeyOrFallback(key)
                    }

                    override fun requestFocusKeyOrFallbackResult(key: String): HomeFocusRequestResult {
                        return focusState.requestFocusKeyOrFallback(key)
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
                },
            )
        } else {
            null
        }
        onDispose {
            registration?.unregister()
            focusState.setOnFocusTargetAvailabilityChanged(null)
        }
    }

    AndroidView(
        modifier = modifier.focusGroup(),
        factory = { context ->
            object : RecyclerView(context) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (!latestIsHomeTabActive) return false
                    if (event.action == KeyEvent.ACTION_DOWN && event.keyCode in UpGridNavigationKeyCodes) {
                        val focusedView = rootView?.findFocus()
                        val currentPosition = focusedView
                            ?.let(::findContainingViewHolder)
                            ?.bindingAdapterPosition
                            ?.takeIf { it != NO_POSITION }
                        if (currentPosition != null && event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
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
                    if (next === this) return focused
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
                configureUpSearchRecycler()
                applyHomeTabActiveState(isHomeTabActive)
                setBackgroundColor(colors.recyclerBackground)
                setPadding(paddingPx.start, paddingPx.top, paddingPx.end, paddingPx.bottom)
                layoutManager = GridLayoutManager(context, columns)
                setOnFocusChangeListener { _, hasFocus ->
                    focusState.onRecyclerFocusChanged(hasFocus)
                }
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        latestOnVerticalScrollOffsetChanged(recyclerView.computeVerticalScrollOffset())
                        if (dy <= 0) return
                        val manager = recyclerView.layoutManager as? GridLayoutManager ?: return
                        val totalItemCount = manager.itemCount
                        if (totalItemCount <= 0) return
                        val lastVisibleItemPosition = manager.findLastVisibleItemPosition()
                        if (lastVisibleItemPosition >= totalItemCount - UpSearchLoadMorePrefetchItems &&
                            latestIsHomeTabActive &&
                            latestCanLoadMore()
                        ) {
                            latestOnLoadMore()
                        }
                    }
                })
                adapter = UpSearchAdapter(
                    colors = colors,
                    onItemClick = { item ->
                        if (latestIsHomeTabActive) {
                            latestOnOpenUp(item.mid)
                        }
                    },
                    onItemFocused = { item, position ->
                        if (dpadGridController.onItemFocused(position = position)) {
                            focusState.onItemFocused(item.searchUpKey(), position)
                            if (latestIsHomeTabActive) latestFocusTab?.let { tab ->
                                latestFocusCoordinator?.onContentRegionFocused(tab, HomeFocusRegion.Grid)
                            }
                            latestFocusCoordinator?.drainPendingFocus()
                        }
                    },
                    onItemKeyEvent = { itemView, _, position, keyCode, event ->
                        latestIsHomeTabActive && dpadGridController.handleItemKeyEvent(
                            itemView = itemView,
                            position = position,
                            keyCode = keyCode,
                            event = event,
                        )
                    },
                ).apply {
                    submitList(items) {
                        dpadGridController.onItemsCommitted()
                        focusState.onItemsCommitted()
                        latestOnVerticalScrollOffsetChanged(computeVerticalScrollOffset())
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
            recyclerView.setBackgroundColor(colors.recyclerBackground)
            recyclerView.setPadding(paddingPx.start, paddingPx.top, paddingPx.end, paddingPx.bottom)
            val manager = recyclerView.layoutManager as? GridLayoutManager
            if (manager == null) {
                recyclerView.layoutManager = GridLayoutManager(recyclerView.context, columns)
            } else if (manager.spanCount != columns) {
                manager.spanCount = columns
            }
            (recyclerView.adapter as? UpSearchAdapter)?.let { adapter ->
                adapter.updateColors(recyclerView, colors)
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
                    return@let
                }
                if (!dpadGridController.hasPendingLoadMoreFocus()) {
                    focusState.prepareForDataSetChange()
                }
                adapter.submitList(items) {
                    dpadGridController.onItemsCommitted()
                    focusState.onItemsCommitted()
                    latestOnVerticalScrollOffsetChanged(recyclerView.computeVerticalScrollOffset())
                    if (latestIsHomeTabActive) {
                        latestFocusCoordinator?.drainPendingFocus()
                    }
                }
            }
        },
    )
}

private class UpSearchAdapter(
    private var colors: UpSearchColors,
    private val onItemClick: (SearchUpItem) -> Unit,
    private val onItemFocused: (SearchUpItem, Int) -> Unit,
    private val onItemKeyEvent: (View, SearchUpItem, Int, Int, KeyEvent) -> Boolean,
) : ListAdapter<SearchUpItem, UpSearchAdapter.UpViewHolder>(UpSearchDiffCallback) {
    private var visualFocusKey: String? = null

    init {
        setHasStableIds(true)
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UpViewHolder {
        return UpViewHolder(createUpSearchCard(parent.context, colors))
    }

    override fun onBindViewHolder(holder: UpViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, isLogicallyFocused = item.searchUpKey() == visualFocusKey)
    }

    override fun getItemId(position: Int): Long {
        return stableLongHash(getItem(position).searchUpKey())
    }

    fun positionOfKey(key: String): Int {
        return currentList.indexOfFirst { it.searchUpKey() == key }
    }

    fun keyAt(position: Int): String? {
        return currentList.getOrNull(position)?.searchUpKey()
    }

    fun clearVisibleFocusVisualState(recyclerView: RecyclerView) {
        visualFocusKey = null
        for (index in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(index)
            (recyclerView.getChildViewHolder(child) as? UpViewHolder)?.setLogicalFocused(false)
        }
    }

    fun setVisualFocusKey(recyclerView: RecyclerView, key: String?) {
        if (visualFocusKey == key) return
        visualFocusKey = key
        for (index in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(index)
            val holder = recyclerView.getChildViewHolder(child) as? UpViewHolder ?: continue
            holder.setLogicalFocused(holder.boundKey == key)
        }
    }

    fun updateColors(recyclerView: RecyclerView, colors: UpSearchColors) {
        if (this.colors == colors) return
        this.colors = colors
        for (index in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(index)
            val holder = recyclerView.getChildViewHolder(child) as? UpViewHolder ?: continue
            holder.setLogicalFocused(holder.boundKey == visualFocusKey)
        }
    }

    inner class UpViewHolder(private val card: UpSearchCardView) : RecyclerView.ViewHolder(card.root) {
        private var logicalFocused = false
        val boundKey: String?
            get() = currentItem()?.searchUpKey()

        init {
            card.root.isFocusable = true
            card.root.isFocusableInTouchMode = false
            card.root.isClickable = true
            card.root.defaultFocusHighlightEnabled = false
            card.root.stateListAnimator = runCatching {
                AnimatorInflater.loadStateListAnimator(card.root.context, R.animator.blbl_focus_scale)
            }.getOrNull()
            card.root.setOnClickListener {
                currentItem()?.let(onItemClick)
            }
            card.root.setOnFocusChangeListener { _, hasFocus ->
                applyFocused(hasFocus || logicalFocused)
                if (hasFocus) {
                    val position = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                        ?: return@setOnFocusChangeListener
                    currentItem()?.let { item -> onItemFocused(item, position) }
                }
            }
            card.root.setOnKeyListener { itemView, keyCode, event ->
                val item = currentItem() ?: return@setOnKeyListener false
                val position = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                    ?: return@setOnKeyListener false
                onItemKeyEvent(itemView, item, position, keyCode, event)
            }
        }

        fun bind(item: SearchUpItem, isLogicallyFocused: Boolean) {
            logicalFocused = isLogicallyFocused
            card.name.text = item.uname
            card.subtitle.text = "粉丝: ${formatFans(item.fans)}"
            card.avatar.load(item.upic.normalizedHttpUrl()) {
                crossfade(false)
                diskCachePolicy(CachePolicy.ENABLED)
                transformations(CircleCropTransformation())
            }
            applyFocused(card.root.hasFocus() || logicalFocused)
        }

        fun setLogicalFocused(focused: Boolean) {
            logicalFocused = focused
            applyFocused(card.root.hasFocus() || logicalFocused)
        }

        private fun applyFocused(focused: Boolean) {
            card.root.isSelected = focused
            card.root.setCardBackgroundColor(if (focused) colors.focusedBackground else colors.normalBackground)
            card.root.strokeColor = if (focused) colors.focusedStroke else colors.normalStroke
            card.name.setTextColor(if (focused) colors.focusedText else colors.normalText)
            card.subtitle.setTextColor(if (focused) colors.focusedSubtitle else colors.normalSubtitle)
        }

        private fun currentItem(): SearchUpItem? {
            val position = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return null
            return currentList.getOrNull(position)
        }
    }
}

private class UpSearchGridFocusState {
    private var recyclerViewRef: WeakReference<RecyclerView>? = null
    private var lastFocusedKey: String? = null
    private var hasRecyclerFocus = false
    private var onFocusTargetAvailabilityChanged: (() -> Unit)? = null

    fun attach(recyclerView: RecyclerView) {
        recyclerViewRef = WeakReference(recyclerView)
    }

    fun detach(recyclerView: RecyclerView) {
        if (recyclerViewRef?.get() === recyclerView) {
            clearVisualFocusAnchor()
            recyclerViewRef = null
            hasRecyclerFocus = false
        }
    }

    fun onRecyclerFocusChanged(hasFocus: Boolean) {
        hasRecyclerFocus = hasFocus
    }

    fun onItemFocused(key: String, position: Int) {
        lastFocusedKey = key
        clearVisualFocusAnchor()
        hasRecyclerFocus = false
    }

    fun setOnFocusTargetAvailabilityChanged(callback: (() -> Unit)?) {
        onFocusTargetAvailabilityChanged = callback
    }

    fun parkFocusForDirectionalScroll(targetPosition: Int): Boolean {
        setVisualFocusAnchor(targetPosition)
        return currentRecyclerView()?.requestFocusParking() == true
    }

    fun clearDirectionalScrollFocus() {
        clearVisualFocusAnchor()
    }

    fun prepareForDataSetChange(): Boolean {
        val recycler = currentRecyclerView() ?: return false
        val currentFocused = recycler.rootView?.findFocus()
        val focusInsideRecycler = currentFocused === recycler ||
            currentFocused?.isSameOrDescendantOf(recycler) == true
        if (!focusInsideRecycler) return false
        return recycler.parkFocusForDataSetReset()
    }

    fun onItemsCommitted() {
        onFocusTargetAvailabilityChanged?.invoke()
    }

    fun tryFocusVisibleItem(): Boolean {
        return requestFocusVisibleItem().isAccepted
    }

    fun requestFocusVisibleItem(): HomeFocusRequestResult {
        val recycler = currentRecyclerView() ?: return HomeFocusRequestResult.Unavailable
        val adapter = recycler.adapter as? UpSearchAdapter ?: return HomeFocusRequestResult.Unavailable
        if (adapter.itemCount <= 0) return HomeFocusRequestResult.Unavailable
        val currentFocused = recycler.rootView?.findFocus()
        if (
            currentFocused != null &&
            currentFocused !== recycler &&
            currentFocused.isSameOrDescendantOf(recycler)
        ) {
            return HomeFocusRequestResult.Focused
        }
        val position = lastFocusedKey
            ?.let(adapter::positionOfKey)
            ?.takeIf { it in 0 until adapter.itemCount }
            ?: firstVisiblePosition(recycler)
            ?: 0
        return requestFocusPosition(recycler, position)
    }

    fun tryFocusKey(key: String): Boolean {
        return requestFocusKey(key).isAccepted
    }

    fun requestFocusKey(key: String): HomeFocusRequestResult {
        val recycler = currentRecyclerView() ?: return HomeFocusRequestResult.Unavailable
        val adapter = recycler.adapter as? UpSearchAdapter ?: return HomeFocusRequestResult.Unavailable
        val position = adapter.positionOfKey(key)
        if (position !in 0 until adapter.itemCount) return HomeFocusRequestResult.Unavailable
        return requestFocusPosition(recycler, position)
    }

    fun tryFocusKeyOrFallback(key: String): Boolean {
        return requestFocusKeyOrFallback(key).isAccepted
    }

    fun requestFocusKeyOrFallback(key: String): HomeFocusRequestResult {
        val recycler = currentRecyclerView() ?: return HomeFocusRequestResult.Unavailable
        val adapter = recycler.adapter as? UpSearchAdapter ?: return HomeFocusRequestResult.Unavailable
        if (adapter.itemCount <= 0) return HomeFocusRequestResult.Unavailable
        val position = adapter.positionOfKey(key).takeIf { it in 0 until adapter.itemCount }
            ?: firstVisiblePosition(recycler)
            ?: 0
        return requestFocusPosition(recycler, position)
    }

    fun hasFocusInside(): Boolean {
        val recycler = currentRecyclerView() ?: return false
        val focused = recycler.rootView?.findFocus()
        return hasRecyclerFocus || focused?.isSameOrDescendantOf(recycler) == true
    }

    fun hasRememberedFocus(): Boolean {
        return lastFocusedKey != null
    }

    fun clearVisibleFocusVisualState(): Boolean {
        val recycler = currentRecyclerView() ?: return false
        val adapter = recycler.adapter as? UpSearchAdapter ?: return false
        adapter.clearVisibleFocusVisualState(recycler)
        return true
    }

    private fun currentRecyclerView(): RecyclerView? {
        return recyclerViewRef?.get()
    }

    private fun requestFocusPosition(recycler: RecyclerView, position: Int): HomeFocusRequestResult {
        val adapter = recycler.adapter as? UpSearchAdapter ?: return HomeFocusRequestResult.Unavailable
        if (position !in 0 until adapter.itemCount) return HomeFocusRequestResult.Unavailable
        val expectedKey = adapter.keyAt(position)
        recycler.findViewHolderForAdapterPosition(position)?.itemView?.let { view ->
            if (view.requestFocus()) {
                expectedKey?.let { lastFocusedKey = it }
                clearVisualFocusAnchor()
                return HomeFocusRequestResult.Focused
            }
        }
        setVisualFocusAnchor(position)
        recycler.scrollToPosition(position)
        recycler.post {
            val holder = recycler.findViewHolderForAdapterPosition(position)
            if (holder?.itemView?.requestFocus() == true) {
                expectedKey?.let { lastFocusedKey = it }
                clearVisualFocusAnchor()
            }
        }
        return HomeFocusRequestResult.Pending
    }

    private fun setVisualFocusAnchor(position: Int) {
        val recycler = currentRecyclerView() ?: return
        val adapter = recycler.adapter as? UpSearchAdapter ?: return
        val key = adapter.keyAt(position) ?: return
        adapter.setVisualFocusKey(recycler, key)
    }

    private fun clearVisualFocusAnchor() {
        val recycler = currentRecyclerView() ?: return
        val adapter = recycler.adapter as? UpSearchAdapter ?: return
        adapter.setVisualFocusKey(recycler, null)
    }

    private fun firstVisiblePosition(recycler: RecyclerView): Int? {
        return (recycler.layoutManager as? GridLayoutManager)
            ?.findFirstVisibleItemPosition()
            ?.takeIf { it != RecyclerView.NO_POSITION }
    }
}

private fun createUpSearchCard(context: Context, colors: UpSearchColors): UpSearchCardView {
    val margin = context.dp(8)
    val padding = context.dp(16)
    val avatarSize = context.dp(64)
    val card = UpSearchCardRoot(context).apply {
        layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            setMargins(margin, margin, margin, margin)
        }
        radius = context.dp(12).toFloat()
        strokeWidth = context.dp(2)
        strokeColor = colors.normalStroke
        setCardBackgroundColor(colors.normalBackground)
    }
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(padding, padding, padding, padding)
        minimumHeight = context.dp(104)
    }
    val avatar = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colors.avatarBackground)
        }
        layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
    }
    val textColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply {
            marginStart = context.dp(16)
        }
    }
    val name = TextView(context).apply {
        setTextColor(colors.normalText)
        textSize = 16f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    val subtitle = TextView(context).apply {
        setTextColor(colors.normalSubtitle)
        textSize = 13f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = context.dp(6)
        }
    }
    textColumn.addView(name)
    textColumn.addView(subtitle)
    row.addView(avatar)
    row.addView(textColumn)
    card.addView(row)
    return UpSearchCardView(root = card, avatar = avatar, name = name, subtitle = subtitle)
}

private fun RecyclerView.configureUpSearchRecycler() {
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

@Composable
private fun rememberUpRecyclerPadding(contentPadding: PaddingValues): UpRecyclerPaddingPx {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    return UpRecyclerPaddingPx(
        start = with(density) { contentPadding.calculateStartPadding(layoutDirection).roundToPx() },
        top = with(density) { contentPadding.calculateTopPadding().roundToPx() },
        end = with(density) { contentPadding.calculateEndPadding(layoutDirection).roundToPx() },
        bottom = with(density) { contentPadding.calculateBottomPadding().roundToPx() },
    )
}

private fun SearchUpItem.searchUpKey(): String {
    return mid.takeIf { it > 0L }?.toString() ?: "$uname|$upic"
}

private fun String.normalizedHttpUrl(): String {
    return if (startsWith("//")) "https:$this" else this
}

private fun stableLongHash(value: String): Long {
    var hash = 1125899906842597L
    for (char in value) {
        hash = 31 * hash + char.code
    }
    return hash and Long.MAX_VALUE
}

private fun formatFans(fans: Int): String {
    return if (fans >= 10_000) {
        val whole = fans / 10_000
        val decimal = (fans % 10_000) / 1_000
        if (decimal == 0) "${whole}万" else "$whole.${decimal}万"
    } else {
        fans.coerceAtLeast(0).toString()
    }
}

private fun Context.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).roundToInt()
}

private data class UpSearchCardView(
    val root: UpSearchCardRoot,
    val avatar: ImageView,
    val name: TextView,
    val subtitle: TextView,
)

private class UpSearchCardRoot(context: Context) : FrameLayout(context) {
    var radius: Float = 0f
        set(value) {
            field = value
            refreshBackground()
        }

    var strokeWidth: Int = 0
        set(value) {
            field = value
            refreshBackground()
        }

    var strokeColor: Int = UpSearchDarkColors.normalStroke
        set(value) {
            field = value
            refreshBackground()
        }

    private var cardBackgroundColor: Int = UpSearchDarkColors.normalBackground

    fun setCardBackgroundColor(color: Int) {
        cardBackgroundColor = color
        refreshBackground()
    }

    private fun refreshBackground() {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(cardBackgroundColor)
            setStroke(strokeWidth, strokeColor)
        }
    }
}

private data class UpRecyclerPaddingPx(
    val start: Int,
    val top: Int,
    val end: Int,
    val bottom: Int,
)

private class UpSearchRecyclerViewRef {
    var value: RecyclerView? = null
}

private object UpSearchDiffCallback : DiffUtil.ItemCallback<SearchUpItem>() {
    override fun areItemsTheSame(oldItem: SearchUpItem, newItem: SearchUpItem): Boolean {
        return oldItem.searchUpKey() == newItem.searchUpKey()
    }

    override fun areContentsTheSame(oldItem: SearchUpItem, newItem: SearchUpItem): Boolean {
        return oldItem == newItem
    }
}

private val UpGridNavigationKeyCodes = setOf(
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
)

private const val UpSearchLoadMorePrefetchItems = 8

private data class UpSearchColors(
    val recyclerBackground: Int,
    val normalBackground: Int,
    val focusedBackground: Int,
    val normalStroke: Int,
    val focusedStroke: Int,
    val normalText: Int,
    val focusedText: Int,
    val normalSubtitle: Int,
    val focusedSubtitle: Int,
    val avatarBackground: Int,
)

private val UpSearchLightColors = UpSearchColors(
    recyclerBackground = Color.TRANSPARENT,
    normalBackground = 0xFFF1F2F3.toInt(),
    focusedBackground = 0xFFFB7299.toInt(),
    normalStroke = 0x0D000000,
    focusedStroke = 0xFFFB7299.toInt(),
    normalText = 0xFF18191C.toInt(),
    focusedText = 0xFFFFFFFF.toInt(),
    normalSubtitle = 0xFF61666D.toInt(),
    focusedSubtitle = 0xFFFFFFFF.toInt(),
    avatarBackground = 0xFFE3E5E7.toInt(),
)

private val UpSearchDarkColors = UpSearchColors(
    recyclerBackground = Color.TRANSPARENT,
    normalBackground = 0xFF20252D.toInt(),
    focusedBackground = 0xFFFFFFFF.toInt(),
    normalStroke = 0x1AFFFFFF,
    focusedStroke = 0xFFFFFFFF.toInt(),
    normalText = 0xFFFFFFFF.toInt(),
    focusedText = 0xFF000000.toInt(),
    normalSubtitle = 0xFF9AA2AD.toInt(),
    focusedSubtitle = 0xFF444444.toInt(),
    avatarBackground = Color.DKGRAY,
)
