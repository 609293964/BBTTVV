package com.bbttvv.app.ui.home

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bbttvv.app.BuildConfig
import com.bbttvv.app.ui.focus.isSameOrDescendantOf

internal class DpadGridController(
    private val config: Config = Config(),
) {
    data class Config(
        val consumeLeftEdge: Boolean = true,
        val consumeRightEdge: Boolean = true,
        val consumeDownAtBottomEdge: Boolean = true,
        val scrollOnDownEdgeFactor: Float = 0.8f,
        val downPreloadRows: Int = 2,
        val enableCenterLongPressToLongClick: Boolean = true,
    )

    data class Callbacks(
        val isEnabled: () -> Boolean = { true },
        val onTopEdge: () -> Boolean = { false },
        val onLeftEdge: () -> Boolean = { false },
        val onRightEdge: () -> Boolean = { false },
        val onBottomEdge: (Int) -> Boolean = { false },
        val canLoadMore: () -> Boolean = { false },
        val loadMore: () -> Unit = {},
        val preloadRowsAhead: (RecyclerView, Int, Int, Int) -> Unit = { _, _, _, _ -> },
        val parkFocusForScroll: (Int) -> Boolean = { _ -> false },
        val onMenu: () -> Boolean = { false },
        val onBack: () -> Boolean = { false },
        val onFocusSettled: () -> Unit = {},
    )

    private var recyclerView: RecyclerView? = null
    private var callbacks: Callbacks = Callbacks()
    private var lastKnownFocusedPosition: Int = RecyclerView.NO_POSITION
    private var lastKnownFocusedColumn: Int = RecyclerView.NO_POSITION
    private var pendingLoadMoreFocus: PendingLoadMoreFocus? = null
    private var focusRequestToken: Int = 0
    private var centerLongPressHandled: Boolean = false
    private var focusParkedDescendantFocusability: Int? = null
    private var lastVerticalNavAtMs: Long = 0L
    private var lastVerticalNavDirection: Int = View.FOCUS_DOWN
    private var detachFocusRestoreToken: Int = 0
    private var directionalScrollParkedUntilMs: Long = 0L
    private var directionalScrollTargetPosition: Int = RecyclerView.NO_POSITION
    private var directionalScrollRequestToken: Int = 0

    private val recyclerKeyListener =
        View.OnKeyListener { _, keyCode, event ->
            handleItemKeyEvent(
                itemView = recyclerView?.rootView?.findFocus(),
                position = lastKnownFocusedPosition,
                keyCode = keyCode,
                event = event,
            )
        }

    private val childAttachListener = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) {
            maybeFocusDirectionalScrollTargetOnAttach(view)
        }

        override fun onChildViewDetachedFromWindow(view: View) {
            maybeProtectFocusOnChildDetach(view)
        }
    }

    fun attach(recyclerView: RecyclerView) {
        if (this.recyclerView === recyclerView) return
        detach()
        this.recyclerView = recyclerView
        recyclerView.setOnKeyListener(recyclerKeyListener)
        recyclerView.addOnChildAttachStateChangeListener(childAttachListener)
    }

    fun detach() {
        recyclerView?.let { currentRecycler ->
            currentRecycler.removeOnChildAttachStateChangeListener(childAttachListener)
            currentRecycler.setOnKeyListener(null)
            unparkFocusInRecyclerViewIfNeeded(currentRecycler)
        }
        recyclerView = null
        focusRequestToken++
        detachFocusRestoreToken++
        lastKnownFocusedPosition = RecyclerView.NO_POSITION
        lastKnownFocusedColumn = RecyclerView.NO_POSITION
        pendingLoadMoreFocus = null
        centerLongPressHandled = false
        clearDirectionalScrollParking()
    }

    fun updateCallbacks(callbacks: Callbacks) {
        this.callbacks = callbacks
    }

    fun onItemFocused(position: Int): Boolean {
        if (position == RecyclerView.NO_POSITION) return false
        if (shouldIgnoreDirectionalScrollFallbackFocus(position)) {
            recyclerView?.let(::parkFocusInRecyclerViewForScroll)
            return false
        }

        val shouldStopSettledDirectionalScroll =
            DpadGridDirectionalFocusPolicy.shouldStopScrollAfterTargetFocus(
                focusedPosition = position,
                directionalScrollTargetPosition = directionalScrollTargetPosition,
                isDirectionalScrollParked = isDirectionalScrollParked(),
            )
        focusRequestToken++
        if (shouldStopSettledDirectionalScroll) {
            recyclerView?.stopScroll()
        }
        clearDirectionalScrollParking()
        lastKnownFocusedPosition = position
        lastKnownFocusedColumn = spanCountOrNull()?.let { position % it } ?: RecyclerView.NO_POSITION
        clearPendingLoadMoreFocus()
        centerLongPressHandled = false
        return true
    }

    fun onItemsCommitted(): Boolean {
        return applyPendingLoadMoreFocus()
    }

    fun cancelAllPendingRequests() {
        focusRequestToken++
        clearPendingLoadMoreFocus()
    }

    fun hasPendingLoadMoreFocus(): Boolean {
        return pendingLoadMoreFocus != null
    }

    fun handleItemKeyEvent(
        itemView: View?,
        position: Int,
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (!callbacks.isEnabled()) return false
        if (handleCenterLongPress(itemView, keyCode, event)) return true
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (shouldConsumeLoadMorePendingKey(keyCode)) return true
        markVerticalNavigation(keyCode)
        val resolvedPosition = resolveAdapterPosition(itemView = itemView, fallbackPosition = position)
        return handleItemKeyDown(
            position = resolvedPosition,
            keyCode = keyCode,
        )
    }

    fun handleItemKeyDown(position: Int, keyCode: Int): Boolean {
        if (!callbacks.isEnabled()) return false
        if (shouldConsumeLoadMorePendingKey(keyCode)) return true
        val recycler = recyclerView ?: return false
        val adapter = recycler.adapter ?: return false
        val gridLayoutManager = recycler.layoutManager as? GridLayoutManager ?: return false
        val spanCount = gridLayoutManager.spanCount.takeIf { it > 0 } ?: return false
        val itemCount = adapter.itemCount
        val resolvedPosition = resolveFocusablePosition(
            candidatePosition = position,
            itemCount = itemCount,
            spanCount = spanCount,
        )
        val edge = DpadGridEdgePolicy.resolve(
            position = resolvedPosition,
            itemCount = itemCount,
            spanCount = spanCount,
        ) ?: return false

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                handleDpadUp(
                    recycler = recycler,
                    position = resolvedPosition,
                    spanCount = spanCount,
                    edge = edge,
                )
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                handleDpadLeft(
                    recycler = recycler,
                    position = resolvedPosition,
                    edge = edge,
                )
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                handleDpadRight(
                    recycler = recycler,
                    position = resolvedPosition,
                    itemCount = itemCount,
                    edge = edge,
                )
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                callbacks.preloadRowsAhead(
                    recycler,
                    resolvedPosition,
                    spanCount,
                    config.downPreloadRows,
                )
                handleDpadDown(
                    recycler = recycler,
                    position = resolvedPosition,
                    itemCount = itemCount,
                    spanCount = spanCount,
                    edge = edge,
                )
            }

            KeyEvent.KEYCODE_MENU -> callbacks.onMenu()

            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BUTTON_B -> callbacks.onBack()

            else -> false
        }
    }

    private fun shouldConsumeLoadMorePendingKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_DOWN && pendingLoadMoreFocus != null
    }

    private fun handleDpadLeft(
        recycler: RecyclerView,
        position: Int,
        edge: DpadGridEdge,
    ): Boolean {
        if (edge.isLeft) {
            return callbacks.onLeftEdge() || config.consumeLeftEdge
        }
        return focusHorizontalNeighbor(
            recycler = recycler,
            targetPosition = position - 1,
        )
    }

    private fun handleDpadRight(
        recycler: RecyclerView,
        position: Int,
        itemCount: Int,
        edge: DpadGridEdge,
    ): Boolean {
        if (edge.isRight) {
            return callbacks.onRightEdge() || config.consumeRightEdge
        }
        val targetPosition = position + 1
        if (targetPosition >= itemCount) return config.consumeRightEdge
        return focusHorizontalNeighbor(
            recycler = recycler,
            targetPosition = targetPosition,
        )
    }

    private fun focusHorizontalNeighbor(
        recycler: RecyclerView,
        targetPosition: Int,
    ): Boolean {
        val adapter = recycler.adapter ?: return false
        if (targetPosition !in 0 until adapter.itemCount) return false

        val targetItem = recycler.findViewHolderForAdapterPosition(targetPosition)?.itemView
        if (targetItem != null && targetItem.isValidFocusTarget()) {
            if (focusAttachedItemThenScrollIntoView(recycler, targetItem, targetPosition)) {
                return true
            }
        }

        val requestToken = ++focusRequestToken
        tryFocusAtPosition(
            recycler = recycler,
            position = targetPosition,
            requestToken = requestToken,
        )
        return true
    }

    private fun handleDpadUp(
        recycler: RecyclerView,
        position: Int,
        spanCount: Int,
        edge: DpadGridEdge,
    ): Boolean {
        if (edge.isTop) {
            val handled = callbacks.onTopEdge()
            return handled
        }

        val targetPosition = position - spanCount
        if (targetPosition < 0) return false

        val currentItem = recycler.findViewHolderForAdapterPosition(position)?.itemView
        val targetItem = recycler.findViewHolderForAdapterPosition(targetPosition)?.itemView
        if (targetItem != null && targetItem.isValidFocusTarget()) {
            val scrollDistance = scrollDistanceToPreviousRow(
                recycler = recycler,
                currentItem = currentItem,
                targetItem = targetItem,
            )
            val canSmoothScrollToTarget = scrollDistance < 0 && recycler.canScrollVertically(-1)
            if (
                DpadGridDirectionalFocusPolicy.shouldDeferAttachedTargetFocusForSmoothScroll(
                    isTargetFullyVisible = isItemFullyVisible(recycler, targetItem),
                    scrollDistancePx = scrollDistance,
                    canScrollInDirection = canSmoothScrollToTarget,
                )
            ) {
                val requestToken = ++focusRequestToken
                return smoothScrollThen(
                    recycler = recycler,
                    dyPx = scrollDistance,
                    requestToken = requestToken,
                    targetPosition = targetPosition,
                ) {
                    tryFocusAtPosition(
                        recycler = recycler,
                        position = targetPosition,
                        requestToken = requestToken,
                    )
                }
            }
            if (focusAttachedItemThenScrollIntoView(recycler, targetItem, targetPosition)) {
                return true
            }
            if (targetItem.requestFocus()) {
                return true
            }
        }

        val requestToken = ++focusRequestToken
        if (currentItem != null && !isPositionVisible(recycler, targetPosition)) {
            val scrollDistance = -estimatedRowScrollDistance(recycler, currentItem)
            if (recycler.canScrollVertically(-1)) {
                return smoothScrollThen(
                    recycler = recycler,
                    dyPx = scrollDistance,
                    requestToken = requestToken,
                    targetPosition = targetPosition,
                ) {
                    tryFocusAtPosition(
                        recycler = recycler,
                        position = targetPosition,
                        requestToken = requestToken,
                    )
                }
            }
        }
        tryFocusAtPosition(
            recycler = recycler,
            position = targetPosition,
            requestToken = requestToken,
        )
        return true
    }

    private fun handleDpadDown(
        recycler: RecyclerView,
        position: Int,
        itemCount: Int,
        spanCount: Int,
        edge: DpadGridEdge,
    ): Boolean {
        val currentItem = recycler.findViewHolderForAdapterPosition(position)?.itemView
        val nextPosition = position + spanCount
        logHomeFocus { "handleDpadDown: pos=$position nextPos=$nextPosition itemCount=$itemCount spanCount=$spanCount" }
        if (nextPosition in 0 until itemCount) {
            val targetItem = recycler.findViewHolderForAdapterPosition(nextPosition)?.itemView
            if (targetItem != null && targetItem.isValidFocusTarget()) {
                val scrollDistance = scrollDistanceToNextRow(
                    recycler = recycler,
                    currentItem = currentItem,
                    targetItem = targetItem,
                )
                val canSmoothScrollToTarget = scrollDistance > 0 && recycler.canScrollVertically(1)
                if (
                    DpadGridDirectionalFocusPolicy.shouldDeferAttachedTargetFocusForSmoothScroll(
                        isTargetFullyVisible = isItemFullyVisible(recycler, targetItem),
                        scrollDistancePx = scrollDistance,
                        canScrollInDirection = canSmoothScrollToTarget,
                    )
                ) {
                    val requestToken = ++focusRequestToken
                    return smoothScrollThen(
                        recycler = recycler,
                        dyPx = scrollDistance,
                        requestToken = requestToken,
                        targetPosition = nextPosition,
                    ) {
                        tryFocusAtPosition(
                            recycler = recycler,
                            position = nextPosition,
                            requestToken = requestToken,
                        )
                    }
                }
                if (focusAttachedItemThenScrollIntoView(recycler, targetItem, nextPosition)) {
                    return true
                }
                if (targetItem.requestFocus()) {
                    return true
                }
            }
            val requestToken = ++focusRequestToken
            if (currentItem != null && !isPositionVisible(recycler, nextPosition)) {
                val scrollDistance = estimatedRowScrollDistance(recycler, currentItem)
                if (scrollDistance > 0 && recycler.canScrollVertically(1)) {
                    return smoothScrollThen(
                        recycler = recycler,
                        dyPx = scrollDistance,
                        requestToken = requestToken,
                        targetPosition = nextPosition,
                    ) {
                        tryFocusAtPosition(
                            recycler = recycler,
                            position = nextPosition,
                            requestToken = requestToken,
                        )
                    }
                }
            }
            tryFocusAtPosition(
                recycler = recycler,
                position = nextPosition,
                requestToken = requestToken,
            )
            return true
        }

        if (edge.isBottom) {
            if (callbacks.onBottomEdge(position)) {
                return true
            }
            if (callbacks.canLoadMore() && pendingLoadMoreFocus == null) {
                prepareLoadMoreFocus(
                    anchorPosition = position,
                    anchorOffsetPx = currentItem?.let { focusedItem ->
                        (recycler.layoutManager as? GridLayoutManager)?.getDecoratedTop(focusedItem)
                            ?: focusedItem.top
                    },
                    oldItemCount = itemCount,
                    spanCount = spanCount,
                )
                parkFocusInRecyclerViewForLoadMore(recycler)
                callbacks.loadMore()
                return true
            }
            return config.consumeDownAtBottomEdge
        }

        if (recycler.canScrollVertically(1)) {
            val requestToken = ++focusRequestToken
            val dy = ((currentItem?.height ?: recycler.height / 2) * config.scrollOnDownEdgeFactor)
                .toInt()
                .coerceAtLeast(1)
            smoothScrollThen(
                recycler = recycler,
                dyPx = dy,
                requestToken = requestToken,
                targetPosition = RecyclerView.NO_POSITION,
            ) {
                if (this.recyclerView === recycler && focusRequestToken == requestToken) {
                    focusVisibleColumnCandidate(
                        recycler = recycler,
                        anchorPosition = position,
                        spanCount = spanCount,
                        itemCount = itemCount,
                        requestToken = requestToken,
                    )
                }
            }
            return true
        }

        return true
    }

    private fun prepareLoadMoreFocus(
        anchorPosition: Int,
        anchorOffsetPx: Int?,
        oldItemCount: Int,
        spanCount: Int,
    ): Boolean {
        pendingLoadMoreFocus = LoadMoreFocusPolicy.create(
            anchorPosition = anchorPosition,
            anchorOffsetPx = anchorOffsetPx,
            oldItemCount = oldItemCount,
            spanCount = spanCount,
        )
        return pendingLoadMoreFocus != null
    }

    private fun applyPendingLoadMoreFocus(): Boolean {
        val pending = pendingLoadMoreFocus ?: return false
        val recycler = recyclerView ?: return false
        val adapter = recycler.adapter ?: return false
        val layoutManager = recycler.layoutManager as? GridLayoutManager ?: return false
        val itemCount = adapter.itemCount
        if (itemCount <= pending.oldItemCount) {
            return false
        }

        val currentFocused = recycler.rootView?.findFocus()
        if (
            currentFocused != null &&
            currentFocused !== recycler &&
            !currentFocused.isSameOrDescendantOf(recycler)
        ) {
            clearPendingLoadMoreFocus()
            return false
        }
        if (
            currentFocused != null &&
            currentFocused !== recycler &&
            currentFocused.isValidFocusTarget() &&
            currentFocused.isSameOrDescendantOf(recycler)
        ) {
            val focusedPosition = recycler.findContainingViewHolder(currentFocused)
                ?.bindingAdapterPosition
                ?.takeIf { it != RecyclerView.NO_POSITION }
            if (focusedPosition != pending.anchorPosition) {
                clearPendingLoadMoreFocus()
                return true
            }
        }

        val requestToken = ++focusRequestToken
        val targetPosition = LoadMoreFocusPolicy.targetPosition(
            pending = pending,
            spanCount = layoutManager.spanCount,
            itemCount = itemCount,
        )
        if (targetPosition !in 0 until itemCount) {
            clearPendingLoadMoreFocus()
            return false
        }

        val targetItem = recycler.findViewHolderForAdapterPosition(targetPosition)?.itemView
        if (targetItem != null && targetItem.isValidFocusTarget()) {
            if (isItemFullyVisible(recycler, targetItem)) {
                unparkFocusInRecyclerViewIfNeeded(recycler)
                if (targetItem.requestFocus()) {
                    clearPendingLoadMoreFocus()
                    return true
                }
            }
            val anchorItem = recycler.findViewHolderForAdapterPosition(pending.anchorPosition)?.itemView
            val scrollDistance = anchorItem?.let { estimatedRowScrollDistance(recycler, it) } ?: 0
            if (scrollDistance > 0 && recycler.canScrollVertically(1)) {
                return smoothScrollThen(
                    recycler = recycler,
                    dyPx = scrollDistance,
                    requestToken = requestToken,
                    targetPosition = targetPosition,
                ) {
                    tryFocusAtPosition(
                        recycler = recycler,
                        position = targetPosition,
                        requestToken = requestToken,
                        scrollOffsetPx = pending.anchorOffsetPx,
                        onFocused = {
                            clearPendingLoadMoreFocus()
                        },
                    )
                }
            }
        } else {
            val anchorItem = recycler.findViewHolderForAdapterPosition(pending.anchorPosition)?.itemView
            val scrollDistance = anchorItem?.let { estimatedRowScrollDistance(recycler, it) } ?: 0
            if (scrollDistance > 0 && recycler.canScrollVertically(1)) {
                return smoothScrollThen(
                    recycler = recycler,
                    dyPx = scrollDistance,
                    requestToken = requestToken,
                    targetPosition = targetPosition,
                ) {
                    tryFocusAtPosition(
                        recycler = recycler,
                        position = targetPosition,
                        requestToken = requestToken,
                        scrollOffsetPx = pending.anchorOffsetPx,
                        onFocused = {
                            clearPendingLoadMoreFocus()
                        },
                    )
                }
            }
        }

        return tryFocusAtPosition(
            recycler = recycler,
            position = targetPosition,
            requestToken = requestToken,
            scrollOffsetPx = pending.anchorOffsetPx,
            onFocused = {
                clearPendingLoadMoreFocus()
            },
        )
    }

    private fun handleCenterLongPress(
        itemView: View?,
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (!config.enableCenterLongPressToLongClick) return false
        if (keyCode !in CenterKeyCodes) return false

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0 && !centerLongPressHandled) {
                    centerLongPressHandled = itemView?.performLongClick() == true
                    centerLongPressHandled
                } else {
                    false
                }
            }

            KeyEvent.ACTION_UP -> {
                val handled = centerLongPressHandled
                centerLongPressHandled = false
                handled
            }

            else -> false
        }
    }

    private fun focusVisibleColumnCandidate(
        recycler: RecyclerView,
        anchorPosition: Int,
        spanCount: Int,
        itemCount: Int,
        requestToken: Int,
    ): Boolean {
        val layoutManager = recycler.layoutManager as? GridLayoutManager ?: return false
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
            .takeIf { it != RecyclerView.NO_POSITION }
            ?: return false
        val lastVisible = layoutManager.findLastVisibleItemPosition()
            .takeIf { it != RecyclerView.NO_POSITION }
            ?: firstVisible
        val targetColumn = if (lastKnownFocusedColumn in 0 until spanCount) {
            lastKnownFocusedColumn
        } else {
            anchorPosition % spanCount
        }
        val candidate = (lastVisible downTo firstVisible)
            .firstOrNull { it in 0 until itemCount && it % spanCount == targetColumn }
            ?: lastVisible.coerceIn(0, itemCount - 1)
        return tryFocusAtPosition(
            recycler = recycler,
            position = candidate,
            requestToken = requestToken,
        )
    }

    private fun focusAttachedItemThenScrollIntoView(
        recycler: RecyclerView,
        itemView: View,
        position: Int,
    ): Boolean {
        if (!itemView.isValidFocusTarget()) return false
        unparkFocusInRecyclerViewIfNeeded(recycler)
        if (!itemView.requestFocus()) return false
        logHomeFocus { "focusAttachedItemThenScrollIntoView: pos=$position requestToken=$focusRequestToken" }
        val requestToken = focusRequestToken
        recycler.postOnAnimation {
            if (!isFocusRequestCurrent(recycler, requestToken)) {
                logHomeFocus { "focusAttachedItemThenScrollIntoView POST: STALE token=$requestToken current=$focusRequestToken" }
                return@postOnAnimation
            }
            val targetItem = recycler.findViewHolderForAdapterPosition(position)?.itemView
                ?: return@postOnAnimation
            if (!targetItem.isValidFocusTarget()) return@postOnAnimation
            val focusedView = recycler.rootView?.findFocus() ?: return@postOnAnimation
            if (!focusedView.isSameOrDescendantOf(targetItem)) {
                logHomeFocus { "focusAttachedItemThenScrollIntoView POST: focusedView not in targetItem" }
                return@postOnAnimation
            }
            logHomeFocus { "focusAttachedItemThenScrollIntoView POST: calling scrollAttachedItemIntoView pos=$position" }
            scrollAttachedItemIntoView(recycler, targetItem)
        }
        return true
    }

    private fun scrollAttachedItemIntoView(
        recycler: RecyclerView,
        itemView: View,
    ): Boolean {
        val layoutManager = recycler.layoutManager as? GridLayoutManager ?: return false
        val bounds = Rect()
        layoutManager.getDecoratedBoundsWithMargins(itemView, bounds)
        val topLimit = recycler.paddingTop
        val bottomLimit = recycler.height - recycler.paddingBottom
        val dy = when {
            bounds.top < topLimit -> bounds.top - topLimit
            bounds.bottom > bottomLimit -> bounds.bottom - bottomLimit
            else -> 0
        }
        if (dy != 0) {
            logHomeFocus { "scrollAttachedItemIntoView: dy=$dy itemTop=${bounds.top} itemBot=${bounds.bottom} topLimit=$topLimit botLimit=$bottomLimit" }
        }
        if (dy == 0 || !recycler.canScrollVertically(if (dy > 0) 1 else -1)) {
            return false
        }
        recycler.smoothScrollBy(0, dy)
        return true
    }

    private fun tryFocusAtPosition(
        recycler: RecyclerView,
        position: Int,
        requestToken: Int = ++focusRequestToken,
        scrollOffsetPx: Int? = null,
        onFocused: (() -> Unit)? = null,
        attemptsLeft: Int = FocusRetryMaxAttempts,
    ): Boolean {
        if (!isFocusRequestCurrent(recycler, requestToken)) {
            clearDirectionalScrollParkingForRequest(requestToken, recycler)
            return false
        }
        val adapter = recycler.adapter ?: return false
        if (position !in 0 until adapter.itemCount) {
            clearPendingIfTarget(position)
            clearDirectionalScrollParkingForRequest(requestToken, recycler)
            return false
        }

        recycler.findViewHolderForAdapterPosition(position)?.itemView?.let { itemView ->
            if (itemView.isValidFocusTarget()) {
                unparkFocusInRecyclerViewIfNeeded(recycler)
                if (itemView.requestFocus()) {
                    onFocused?.invoke()
                    callbacks.onFocusSettled()
                    return true
                }
            }
        }

        if (attemptsLeft == FocusRetryMaxAttempts && !isPositionVisible(recycler, position)) {
            if (scrollOffsetPx != null) {
                (recycler.layoutManager as? GridLayoutManager)
                    ?.scrollToPositionWithOffset(position, scrollOffsetPx)
                    ?: recycler.scrollToPosition(position)
            } else {
                recycler.scrollToPosition(position)
            }
        }

        if (attemptsLeft <= 0) {
            clearPendingIfTarget(position)
            clearDirectionalScrollParkingForRequest(requestToken, recycler)
            clearPendingLoadMoreFocus()
            val layoutManager = recycler.layoutManager as? GridLayoutManager
            if (layoutManager != null) {
                var closestView: View? = null
                var minDistance = Int.MAX_VALUE
                var closestPosition = RecyclerView.NO_POSITION
                for (i in 0 until layoutManager.childCount) {
                    val child = layoutManager.getChildAt(i) ?: continue
                    if (child.isValidFocusTarget()) {
                        val childPos = layoutManager.getPosition(child)
                        if (childPos != RecyclerView.NO_POSITION) {
                            val distance = Math.abs(childPos - position)
                            if (distance < minDistance) {
                                minDistance = distance
                                closestView = child
                                closestPosition = childPos
                            }
                        }
                    }
                }
                if (closestView != null && closestView.requestFocus()) {
                    onFocused?.invoke()
                    callbacks.onFocusSettled()
                    logHomeFocus { "DpadGridController Fallback SUCCESS: expected=$position, actual=$closestPosition" }
                    return true
                }
            }
            return false
        }

        recycler.postDelayed(
            {
                if (!isFocusRequestCurrent(recycler, requestToken)) return@postDelayed
                tryFocusAtPosition(
                    recycler = recycler,
                    position = position,
                    requestToken = requestToken,
                    scrollOffsetPx = scrollOffsetPx,
                    onFocused = onFocused,
                    attemptsLeft = attemptsLeft - 1,
                )
            },
            FocusRetryDelayMs,
        )

        return false
    }

    private fun smoothScrollThen(
        recycler: RecyclerView,
        dyPx: Int,
        requestToken: Int,
        targetPosition: Int,
        onSettled: () -> Unit,
    ): Boolean {
        if (dyPx == 0) {
            if (this.recyclerView === recycler && focusRequestToken == requestToken) {
                onSettled()
            }
            return true
        }

        if (recycler.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
            recycler.stopScroll()
        }

        lateinit var listener: RecyclerView.OnScrollListener
        var completed = false

        fun finish() {
            if (completed) return
            completed = true
            recycler.removeOnScrollListener(listener)
            if (this.recyclerView === recycler && focusRequestToken == requestToken) {
                onSettled()
            }
        }

        fun cancelIfStale(): Boolean {
            if (this.recyclerView === recycler && focusRequestToken == requestToken) return false
            if (!completed) {
                completed = true
                recycler.removeOnScrollListener(listener)
                clearDirectionalScrollParkingForRequest(requestToken, recycler)
            }
            return true
        }

        listener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (recyclerView !== recycler) return
                if (cancelIfStale()) return
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    finish()
                }
            }
        }

        directionalScrollTargetPosition = targetPosition
        directionalScrollRequestToken = requestToken
        callbacks.parkFocusForScroll(targetPosition)
        parkFocusInRecyclerViewForScroll(recycler)
        directionalScrollParkedUntilMs = SystemClock.uptimeMillis() + DirectionalScrollParkedWindowMs
        scheduleDirectionalScrollParkingTimeout(recycler, requestToken)
        recycler.addOnScrollListener(listener)
        recycler.smoothScrollBy(0, dyPx)
        recycler.postOnAnimation {
            if (cancelIfStale()) return@postOnAnimation
            if (recycler.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                finish()
            }
        }
        recycler.postDelayed(
            {
                if (cancelIfStale()) return@postDelayed
                if (recycler.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                    finish()
                }
            },
            SmoothFocusFallbackMs,
        )
        return true
    }

    private fun resolveFocusablePosition(
        candidatePosition: Int,
        itemCount: Int,
        spanCount: Int,
    ): Int {
        return DpadGridDirectionalFocusPolicy.resolvePosition(
            candidatePosition = candidatePosition,
            itemCount = itemCount,
            spanCount = spanCount,
            lastKnownFocusedPosition = lastKnownFocusedPosition,
            lastKnownFocusedColumn = lastKnownFocusedColumn,
            directionalScrollTargetPosition = directionalScrollTargetPosition,
            isDirectionalScrollParked = isDirectionalScrollParked(),
        )
    }

    private fun resolveAdapterPosition(itemView: View?, fallbackPosition: Int): Int {
        val recycler = recyclerView ?: return fallbackPosition
        val itemRoot = itemView?.let { view -> recycler.findContainingItemView(view) }
        val viewHolder = itemRoot?.let { root -> recycler.findContainingViewHolder(root) }
        return validAdapterPosition(viewHolder?.bindingAdapterPosition)
            ?: validAdapterPosition(viewHolder?.absoluteAdapterPosition)
            ?: validAdapterPosition(viewHolder?.layoutPosition)
            ?: validAdapterPosition(itemRoot?.let { root -> recycler.getChildAdapterPosition(root) })
            ?: validAdapterPosition(itemRoot?.let { root -> recycler.getChildLayoutPosition(root) })
            ?: fallbackPosition
    }

    private fun validAdapterPosition(position: Int?): Int? {
        return position?.takeIf { it != RecyclerView.NO_POSITION }
    }

    private fun spanCountOrNull(): Int? {
        return (recyclerView?.layoutManager as? GridLayoutManager)
            ?.spanCount
            ?.takeIf { it > 0 }
    }

    private fun markVerticalNavigation(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                lastVerticalNavAtMs = SystemClock.uptimeMillis()
                lastVerticalNavDirection = View.FOCUS_UP
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                lastVerticalNavAtMs = SystemClock.uptimeMillis()
                lastVerticalNavDirection = View.FOCUS_DOWN
            }
        }
    }

    private fun maybeFocusDirectionalScrollTargetOnAttach(attachedChild: View) {
        val recycler = recyclerView ?: return
        if (!callbacks.isEnabled()) return
        val requestToken = directionalScrollRequestToken
        val targetPosition = directionalScrollTargetPosition
        if (
            requestToken == 0 ||
            focusRequestToken != requestToken ||
            targetPosition == RecyclerView.NO_POSITION ||
            !isDirectionalScrollParked()
        ) {
            return
        }

        val attachedPosition = validAdapterPosition(recycler.getChildAdapterPosition(attachedChild))
            ?: validAdapterPosition(recycler.getChildLayoutPosition(attachedChild))
            ?: return
        if (attachedPosition != targetPosition) return

        recycler.postOnAnimation {
            if (
                this.recyclerView !== recycler ||
                focusRequestToken != requestToken ||
                !isDirectionalScrollParked()
            ) {
                return@postOnAnimation
            }
            if (recycler.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
                return@postOnAnimation
            }

            val targetItem = recycler.findViewHolderForAdapterPosition(targetPosition)?.itemView
                ?: return@postOnAnimation
            if (!targetItem.isValidFocusTarget()) return@postOnAnimation

            val focusedView = recycler.rootView?.findFocus()
            if (
                focusedView != null &&
                focusedView !== recycler &&
                !focusedView.isSameOrDescendantOf(recycler)
            ) {
                clearDirectionalScrollParkingForRequest(requestToken, recycler)
                return@postOnAnimation
            }

            unparkFocusInRecyclerViewIfNeeded(recycler)
            if (targetItem.requestFocus()) {
                scrollAttachedItemIntoView(recycler, targetItem)
            } else {
                parkFocusInRecyclerViewForScroll(recycler)
            }
        }
    }

    private fun maybeProtectFocusOnChildDetach(detachedChild: View) {
        val recycler = recyclerView ?: return
        if (!callbacks.isEnabled()) return

        val hasPendingLoadMore = pendingLoadMoreFocus != null
        val isRecentVerticalNavigation =
            SystemClock.uptimeMillis() - lastVerticalNavAtMs <= FocusProtectWindowMs
        if (!hasPendingLoadMore && !isRecentVerticalNavigation) return

        val focusedView = recycler.rootView?.findFocus()
        if (
            focusedView != null &&
            focusedView !== recycler &&
            !focusedView.isSameOrDescendantOf(recycler)
        ) {
            return
        }
        if (
            focusedView != null &&
            focusedView !== recycler &&
            focusedView.isSameOrDescendantOf(recycler) &&
            !focusedView.isSameOrDescendantOf(detachedChild)
        ) {
            return
        }

        if (parkFocusInRecyclerViewForLoadMore(recycler) && !hasPendingLoadMore && !isDirectionalScrollParked()) {
            restoreFocusAfterChildDetach(recycler)
        }
    }

    private fun restoreFocusAfterChildDetach(recycler: RecyclerView) {
        val restoreToken = ++detachFocusRestoreToken
        recycler.post {
            if (this.recyclerView !== recycler || detachFocusRestoreToken != restoreToken) return@post
            if (!callbacks.isEnabled() || pendingLoadMoreFocus != null) return@post

            val adapter = recycler.adapter ?: return@post
            val layoutManager = recycler.layoutManager as? GridLayoutManager ?: return@post
            val itemCount = adapter.itemCount
            val spanCount = layoutManager.spanCount.takeIf { it > 0 } ?: return@post
            if (itemCount <= 0) return@post

            val currentFocused = recycler.rootView?.findFocus()
            if (
                currentFocused != null &&
                currentFocused !== recycler &&
                currentFocused.isSameOrDescendantOf(recycler) &&
                currentFocused.isValidFocusTarget()
            ) {
                clearPendingLoadMoreFocus()
                return@post
            }

            val firstVisible = layoutManager.findFirstVisibleItemPosition()
                .takeIf { it != RecyclerView.NO_POSITION }
                ?: 0
            val lastVisible = layoutManager.findLastVisibleItemPosition()
                .takeIf { it != RecyclerView.NO_POSITION }
                ?: firstVisible
            val targetColumn = if (lastKnownFocusedColumn in 0 until spanCount) {
                lastKnownFocusedColumn
            } else {
                lastKnownFocusedPosition.takeIf { it in 0 until itemCount }?.rem(spanCount) ?: 0
            }
            val lastKnownVisible = lastKnownFocusedPosition.takeIf {
                it in 0 until itemCount && isPositionVisible(recycler, it)
            }
            val directionalCandidate = when (lastVerticalNavDirection) {
                View.FOCUS_UP -> lastKnownFocusedPosition - spanCount
                else -> lastKnownFocusedPosition + spanCount
            }.takeIf { it in 0 until itemCount && isPositionVisible(recycler, it) }
            val sameColumnVisible = (lastVisible downTo firstVisible)
                .firstOrNull { position -> position in 0 until itemCount && position % spanCount == targetColumn }
            val fallbackVisible = firstVisible.coerceIn(0, itemCount - 1)
            val targetPosition = lastKnownVisible ?: directionalCandidate ?: sameColumnVisible ?: fallbackVisible
            val requestToken = ++focusRequestToken
            tryFocusAtPosition(
                recycler = recycler,
                position = targetPosition,
                requestToken = requestToken,
                onFocused = {
                    clearPendingLoadMoreFocus()
                },
            )
        }
    }

    private fun shouldIgnoreDirectionalScrollFallbackFocus(position: Int): Boolean {
        val itemCount = recyclerView?.adapter?.itemCount ?: 0
        return DpadGridDirectionalFocusPolicy.shouldRejectFallbackFocus(
            position = position,
            itemCount = itemCount,
            directionalScrollTargetPosition = directionalScrollTargetPosition,
            isDirectionalScrollParked = isDirectionalScrollParked(),
        )
    }

    private fun isDirectionalScrollParked(): Boolean {
        return SystemClock.uptimeMillis() <= directionalScrollParkedUntilMs
    }

    private fun clearDirectionalScrollParking() {
        directionalScrollParkedUntilMs = 0L
        directionalScrollTargetPosition = RecyclerView.NO_POSITION
        directionalScrollRequestToken = 0
    }

    private fun clearDirectionalScrollParkingForRequest(
        requestToken: Int,
        recycler: RecyclerView? = recyclerView,
    ) {
        if (directionalScrollRequestToken != requestToken) return
        clearDirectionalScrollParking()
        unparkFocusInRecyclerViewIfNeeded(recycler)
    }

    private fun scheduleDirectionalScrollParkingTimeout(
        recycler: RecyclerView,
        requestToken: Int,
    ) {
        recycler.postDelayed(
            {
                if (SystemClock.uptimeMillis() < directionalScrollParkedUntilMs) {
                    return@postDelayed
                }
                clearDirectionalScrollParkingForRequest(requestToken, recycler)
            },
            DirectionalScrollParkedWindowMs,
        )
    }

    private fun parkFocusInRecyclerViewForScroll(recycler: RecyclerView): Boolean {
        return parkFocusInRecyclerViewForLoadMore(recycler)
    }

    private fun parkFocusInRecyclerViewForLoadMore(recycler: RecyclerView): Boolean {
        val currentFocused = recycler.rootView?.findFocus()
        if (
            currentFocused != null &&
            currentFocused !== recycler &&
            !currentFocused.isSameOrDescendantOf(recycler)
        ) {
            return false
        }
        if (focusParkedDescendantFocusability == null) {
            focusParkedDescendantFocusability = recycler.descendantFocusability
        }
        recycler.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        if (!recycler.isFocusable) {
            recycler.isFocusable = true
        }
        return recycler.requestFocus().also { parked ->
            if (!parked) {
                unparkFocusInRecyclerViewIfNeeded(recycler)
            }
        }
    }

    private fun unparkFocusInRecyclerViewIfNeeded(recycler: RecyclerView? = recyclerView) {
        val currentRecycler = recycler ?: return
        val originalDescendantFocusability = focusParkedDescendantFocusability ?: return
        if (this.recyclerView === currentRecycler) {
            currentRecycler.descendantFocusability = originalDescendantFocusability
        }
        focusParkedDescendantFocusability = null
    }

    private fun clearPendingLoadMoreFocus() {
        pendingLoadMoreFocus = null
        unparkFocusInRecyclerViewIfNeeded()
    }

    private fun clearPendingIfTarget(position: Int) {
        val pending = pendingLoadMoreFocus ?: return
        val spanCount = spanCountOrNull() ?: return
        val itemCount = recyclerView?.adapter?.itemCount ?: return
        val targetPosition = LoadMoreFocusPolicy.targetPosition(
            pending = pending,
            spanCount = spanCount,
            itemCount = itemCount,
        )
        if (targetPosition == position) {
            clearPendingLoadMoreFocus()
        }
    }

    private fun isFocusRequestCurrent(recycler: RecyclerView, requestToken: Int): Boolean {
        if (this.recyclerView !== recycler || focusRequestToken != requestToken) return false
        if (!callbacks.isEnabled()) return false
        val focusedView = recycler.rootView?.findFocus() ?: return true
        return focusedView === recycler || focusedView.isSameOrDescendantOf(recycler)
    }

    private fun isPositionVisible(recycler: RecyclerView, position: Int): Boolean {
        val layoutManager = recycler.layoutManager as? GridLayoutManager ?: return false
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        return first != RecyclerView.NO_POSITION &&
            last != RecyclerView.NO_POSITION &&
            position in first..last
    }

    private fun isItemFullyVisible(recycler: RecyclerView, itemView: View): Boolean {
        val layoutManager = recycler.layoutManager as? GridLayoutManager ?: return false
        val bounds = Rect()
        layoutManager.getDecoratedBoundsWithMargins(itemView, bounds)
        val topLimit = recycler.paddingTop
        val bottomLimit = recycler.height - recycler.paddingBottom
        return bounds.top >= topLimit && bounds.bottom <= bottomLimit
    }

    private fun scrollDistanceToNextRow(
        recycler: RecyclerView,
        currentItem: View?,
        targetItem: View,
    ): Int {
        val layoutManager = recycler.layoutManager as? GridLayoutManager
        if (layoutManager != null && currentItem != null) {
            val currentBounds = Rect()
            val targetBounds = Rect()
            layoutManager.getDecoratedBoundsWithMargins(currentItem, currentBounds)
            layoutManager.getDecoratedBoundsWithMargins(targetItem, targetBounds)
            val rowDistance = targetBounds.top - currentBounds.top
            if (rowDistance > 0) return rowDistance
        }

        val bottomLimit = recycler.height - recycler.paddingBottom
        val targetBounds = Rect()
        if (layoutManager != null) {
            layoutManager.getDecoratedBoundsWithMargins(targetItem, targetBounds)
        } else {
            targetBounds.set(targetItem.left, targetItem.top, targetItem.right, targetItem.bottom)
        }
        return (targetBounds.bottom - bottomLimit).coerceAtLeast(0)
    }

    private fun scrollDistanceToPreviousRow(
        recycler: RecyclerView,
        currentItem: View?,
        targetItem: View,
    ): Int {
        val layoutManager = recycler.layoutManager as? GridLayoutManager
        if (layoutManager != null && currentItem != null) {
            val currentBounds = Rect()
            val targetBounds = Rect()
            layoutManager.getDecoratedBoundsWithMargins(currentItem, currentBounds)
            layoutManager.getDecoratedBoundsWithMargins(targetItem, targetBounds)
            val rowDistance = targetBounds.top - currentBounds.top
            if (rowDistance < 0) return rowDistance
        }

        val topLimit = recycler.paddingTop
        val targetBounds = Rect()
        if (layoutManager != null) {
            layoutManager.getDecoratedBoundsWithMargins(targetItem, targetBounds)
        } else {
            targetBounds.set(targetItem.left, targetItem.top, targetItem.right, targetItem.bottom)
        }
        return (targetBounds.top - topLimit).coerceAtMost(0)
    }

    private fun estimatedRowScrollDistance(recycler: RecyclerView, itemView: View): Int {
        val layoutManager = recycler.layoutManager as? GridLayoutManager
        if (layoutManager != null) {
            val bounds = Rect()
            layoutManager.getDecoratedBoundsWithMargins(itemView, bounds)
            return bounds.height().coerceAtLeast(1)
        }
        return itemView.height.coerceAtLeast(1)
    }

    private companion object {
        private const val SmoothFocusFallbackMs = 360L
        private const val FocusProtectWindowMs = 500L
        private const val FocusRetryDelayMs = 16L
        private const val FocusRetryMaxAttempts = 30
        private const val DirectionalScrollParkedWindowMs = 1000L
        val CenterKeyCodes = setOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
        )
    }
}

private inline fun logHomeFocus(message: () -> String) {
    if (BuildConfig.DEBUG) {
        Log.d("HomeFocus", message())
    }
}

internal data class DpadGridEdge(
    val isTop: Boolean,
    val isLeft: Boolean,
    val isRight: Boolean,
    val isBottom: Boolean,
)

internal object DpadGridEdgePolicy {
    fun resolve(
        position: Int,
        itemCount: Int,
        spanCount: Int,
    ): DpadGridEdge? {
        if (position < 0 || itemCount <= 0 || spanCount <= 0 || position >= itemCount) {
            return null
        }
        return DpadGridEdge(
            isTop = position < spanCount,
            isLeft = position % spanCount == 0,
            isRight = position % spanCount == spanCount - 1 || position == itemCount - 1,
            isBottom = position / spanCount >= (itemCount - 1) / spanCount,
        )
    }
}

internal object DpadGridPreloadPolicy {
    fun positionsAhead(
        position: Int,
        itemCount: Int,
        spanCount: Int,
        rowCount: Int,
    ): IntRange? {
        if (position < 0 || itemCount <= 0 || spanCount <= 0 || rowCount <= 0 || position >= itemCount) {
            return null
        }

        val firstPreloadRow = position / spanCount + 1
        val firstPreloadPosition = firstPreloadRow * spanCount
        if (firstPreloadPosition >= itemCount) return null

        val endExclusive = ((firstPreloadRow + rowCount) * spanCount)
            .coerceAtMost(itemCount)
        return firstPreloadPosition until endExclusive
    }
}

internal object DpadGridDirectionalFocusPolicy {
    fun resolvePosition(
        candidatePosition: Int,
        itemCount: Int,
        spanCount: Int,
        lastKnownFocusedPosition: Int,
        lastKnownFocusedColumn: Int,
        directionalScrollTargetPosition: Int,
        isDirectionalScrollParked: Boolean,
    ): Int {
        val candidate = validPosition(candidatePosition, itemCount)
        if (isDirectionalScrollParked) {
            val directionalTarget = validPosition(directionalScrollTargetPosition, itemCount)
            if (directionalTarget != null && candidate != directionalTarget) {
                return directionalTarget
            }
        }
        candidate?.let { return it }
        validPosition(lastKnownFocusedPosition, itemCount)?.let { return it }
        if (itemCount <= 0 || spanCount <= 0) return RecyclerView.NO_POSITION

        if (lastKnownFocusedColumn in 0 until spanCount) {
            for (position in itemCount - 1 downTo 0) {
                if (position % spanCount == lastKnownFocusedColumn) return position
            }
        }
        return itemCount - 1
    }

    fun shouldRejectFallbackFocus(
        position: Int,
        itemCount: Int,
        directionalScrollTargetPosition: Int,
        isDirectionalScrollParked: Boolean,
    ): Boolean {
        if (!isDirectionalScrollParked) return false
        if (validPosition(directionalScrollTargetPosition, itemCount) == null) return false
        return position != directionalScrollTargetPosition
    }

    fun shouldStopScrollAfterTargetFocus(
        focusedPosition: Int,
        directionalScrollTargetPosition: Int,
        isDirectionalScrollParked: Boolean,
    ): Boolean {
        if (!isDirectionalScrollParked) return false
        return directionalScrollTargetPosition != RecyclerView.NO_POSITION &&
            focusedPosition == directionalScrollTargetPosition
    }

    fun shouldDeferAttachedTargetFocusForSmoothScroll(
        isTargetFullyVisible: Boolean,
        scrollDistancePx: Int,
        canScrollInDirection: Boolean,
    ): Boolean {
        return !isTargetFullyVisible &&
            scrollDistancePx != 0 &&
            canScrollInDirection
    }

    private fun validPosition(position: Int, itemCount: Int): Int? {
        return position.takeIf { it in 0 until itemCount }
    }
}

internal data class PendingLoadMoreFocus(
    val anchorPosition: Int,
    val anchorColumn: Int,
    val oldItemCount: Int,
    val anchorOffsetPx: Int?,
)

internal object LoadMoreFocusPolicy {
    fun create(
        anchorPosition: Int,
        oldItemCount: Int,
        spanCount: Int,
        anchorOffsetPx: Int? = null,
    ): PendingLoadMoreFocus? {
        if (oldItemCount <= 0 || spanCount <= 0 || anchorPosition !in 0 until oldItemCount) {
            return null
        }
        return PendingLoadMoreFocus(
            anchorPosition = anchorPosition,
            anchorColumn = anchorPosition % spanCount,
            oldItemCount = oldItemCount,
            anchorOffsetPx = anchorOffsetPx,
        )
    }

    fun targetPosition(
        pending: PendingLoadMoreFocus,
        spanCount: Int,
        itemCount: Int,
    ): Int {
        if (spanCount <= 0 || itemCount <= pending.oldItemCount) {
            return RecyclerView.NO_POSITION
        }

        val sameColumnNextRow = pending.anchorPosition + spanCount
        if (sameColumnNextRow in pending.oldItemCount until itemCount) {
            return sameColumnNextRow
        }

        return (pending.oldItemCount until itemCount)
            .firstOrNull { position -> position % spanCount == pending.anchorColumn }
            ?: pending.oldItemCount.takeIf { it < itemCount }
            ?: RecyclerView.NO_POSITION
    }
}
