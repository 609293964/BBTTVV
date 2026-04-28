package com.bbttvv.app.ui.home

import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bbttvv.app.ui.focus.isSameOrDescendantOf

internal class DpadGridController(
    private val config: Config = Config(),
) {
    data class Config(
        val consumeLeftEdge: Boolean = true,
        val consumeRightEdge: Boolean = true,
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

    // Removed globalFocusChangeListener to prevent aggressive focus retry cancellation during valid focus transitions.

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
        override fun onChildViewAttachedToWindow(view: View) = Unit

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

    fun onItemFocused(position: Int) {
        if (position != RecyclerView.NO_POSITION) {
            val directionalTarget = activeDirectionalScrollTarget()
            if (directionalTarget != null && position != directionalTarget) {
                return
            }
            focusRequestToken++
            clearDirectionalScrollParking()
            lastKnownFocusedPosition = position
            lastKnownFocusedColumn = spanCountOrNull()?.let { position % it } ?: RecyclerView.NO_POSITION
            clearPendingLoadMoreFocus()
            centerLongPressHandled = false
        }
    }

    fun onItemsCommitted(): Boolean {
        return applyPendingLoadMoreFocus()
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
        val resolvedPosition = resolveAdapterPosition(itemView = itemView, fallbackPosition = position)
        markVerticalNavigation(keyCode)
        return handleItemKeyDown(
            position = resolvedPosition,
            keyCode = keyCode,
        )
    }

    fun handleItemKeyDown(position: Int, keyCode: Int): Boolean {
        if (!callbacks.isEnabled()) return false
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
                if (edge.isLeft) {
                    if (callbacks.onLeftEdge()) {
                        focusRequestToken++
                        true
                    } else config.consumeLeftEdge
                } else false
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (edge.isRight) {
                    if (callbacks.onRightEdge()) {
                        focusRequestToken++
                        true
                    } else config.consumeRightEdge
                } else false
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

            KeyEvent.KEYCODE_MENU -> {
                val handled = callbacks.onMenu()
                if (handled) focusRequestToken++
                handled
            }

            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BUTTON_B -> {
                val handled = callbacks.onBack()
                if (handled) focusRequestToken++
                handled
            }

            else -> false
        }
    }

    private fun handleDpadUp(
        recycler: RecyclerView,
        position: Int,
        spanCount: Int,
        edge: DpadGridEdge,
    ): Boolean {
        if (edge.isTop) {
            val handled = callbacks.onTopEdge()
            if (handled) focusRequestToken++
            return handled
        }

        val targetPosition = position - spanCount
        if (targetPosition < 0) return false

        val currentItem = recycler.findViewHolderForAdapterPosition(position)?.itemView
        val targetItem = recycler.findViewHolderForAdapterPosition(targetPosition)?.itemView
        if (targetItem != null && targetItem.isValidFocusTarget()) {
            if (isItemFullyVisible(recycler, targetItem) && targetItem.requestFocus()) {
                return true
            }
            val requestToken = ++focusRequestToken
            val scrollDistance = scrollDistanceToPreviousRow(
                recycler = recycler,
                currentItem = currentItem,
                targetItem = targetItem,
            )
            if (scrollDistance < 0 && recycler.canScrollVertically(-1)) {
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
        val targetPosition = DpadGridDirectionalFocusPolicy.downTargetPosition(
            position = position,
            itemCount = itemCount,
            spanCount = spanCount,
        )
        if (targetPosition != RecyclerView.NO_POSITION) {
            scrollAndFocusAdapterPosition(
                recycler = recycler,
                position = targetPosition,
                smooth = false,
                scrollOffsetPx = currentItem?.let { focusedItem ->
                    (recycler.layoutManager as? GridLayoutManager)?.getDecoratedTop(focusedItem)
                        ?: focusedItem.top
                },
            )
            return true
        }

        if (edge.isBottom && callbacks.onBottomEdge(position)) {
            focusRequestToken++
            return true
        }
        if (callbacks.canLoadMore()) {
            prepareLoadMoreFocus(
                anchorPosition = position,
                anchorOffsetPx = currentItem?.let { focusedItem ->
                    (recycler.layoutManager as? GridLayoutManager)?.getDecoratedTop(focusedItem)
                        ?: focusedItem.top
                },
                oldItemCount = itemCount,
                spanCount = spanCount,
            )
            val parked = parkFocusInRecyclerViewForLoadMore(recycler)
            callbacks.loadMore()
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
        if (itemCount <= pending.oldItemCount) return false

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

    private fun tryFocusAtPosition(
        recycler: RecyclerView,
        position: Int,
        requestToken: Int = ++focusRequestToken,
        scrollOffsetPx: Int? = null,
        onFocused: (() -> Unit)? = null,
        attemptsLeft: Int = FocusRetryMaxAttempts,
    ): Boolean {
        if (!isFocusRequestCurrent(recycler, requestToken)) return false
        val adapter = recycler.adapter ?: return false
        if (position !in 0 until adapter.itemCount) {
            clearPendingIfTarget(position)
            return false
        }

        recycler.findViewHolderForAdapterPosition(position)?.itemView?.let { itemView ->
            if (itemView.isValidFocusTarget()) {
                unparkFocusInRecyclerViewIfNeeded(recycler)
                val focused = itemView.requestFocus()
                if (focused) {
                    onFocused?.invoke()
                    return true
                }
            }
        }

        if (!isPositionVisible(recycler, position)) {
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

    private fun scrollAndFocusAdapterPosition(
        recycler: RecyclerView,
        position: Int,
        smooth: Boolean,
        scrollOffsetPx: Int? = null,
    ): Boolean {
        val adapter = recycler.adapter ?: return false
        if (position !in 0 until adapter.itemCount) return false

        val targetItem = recycler.findViewHolderForAdapterPosition(position)?.itemView
        if (targetItem != null && targetItem.isValidFocusTarget() && isItemFullyVisible(recycler, targetItem)) {
            unparkFocusInRecyclerViewIfNeeded(recycler)
            val focused = targetItem.requestFocus()
            if (focused) {
                return true
            }
        }

        val requestToken = ++focusRequestToken
        val targetOffsetPx = scrollOffsetPx ?: recycler.paddingTop
        if (smooth) {
            val currentItem = lastKnownFocusedPosition
                .takeIf { it != RecyclerView.NO_POSITION }
                ?.let { recycler.findViewHolderForAdapterPosition(it)?.itemView }
            val dy = currentItem
                ?.let { estimatedRowScrollDistance(recycler, it) }
                ?: (recycler.height / 2).coerceAtLeast(1)
            return smoothScrollThen(
                recycler = recycler,
                dyPx = dy,
                requestToken = requestToken,
                targetPosition = position,
            ) {
                tryFocusAtPosition(
                    recycler = recycler,
                    position = position,
                    requestToken = requestToken,
                    scrollOffsetPx = targetOffsetPx,
                )
            }
        }

        parkFocusForDirectionalScroll(position)
        (recycler.layoutManager as? GridLayoutManager)
            ?.scrollToPositionWithOffset(position, targetOffsetPx)
            ?: recycler.scrollToPosition(position)
        recycler.postOnAnimation {
            tryFocusAtPosition(
                recycler = recycler,
                position = position,
                requestToken = requestToken,
                scrollOffsetPx = targetOffsetPx,
            )
        }
        return true
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

        parkFocusForDirectionalScroll(targetPosition)
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
        if (candidatePosition in 0 until itemCount) return candidatePosition
        if (lastKnownFocusedPosition in 0 until itemCount) return lastKnownFocusedPosition
        if (itemCount <= 0 || spanCount <= 0) return RecyclerView.NO_POSITION

        val column = lastKnownFocusedColumn
        if (column in 0 until spanCount) {
            for (position in itemCount - 1 downTo 0) {
                if (position % spanCount == column) return position
            }
        }
        return itemCount - 1
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

    private fun parkFocusForDirectionalScroll(targetPosition: Int) {
        val recycler = recyclerView ?: return
        val parked = callbacks.parkFocusForScroll(targetPosition)
        directionalScrollTargetPosition = targetPosition
        directionalScrollParkedUntilMs = SystemClock.uptimeMillis() + DirectionalScrollParkedWindowMs
        val recyclerParked = parkFocusInRecyclerViewForLoadMore(recycler)
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

        val isDirectionalScrollParked = SystemClock.uptimeMillis() <= directionalScrollParkedUntilMs
        if (parkFocusInRecyclerViewForLoadMore(recycler) && !hasPendingLoadMore && !isDirectionalScrollParked) {
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
                !currentFocused.isSameOrDescendantOf(recycler)
            ) {
                clearPendingLoadMoreFocus()
                return@post
            }
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
        if (directionalScrollTargetPosition == position) {
            clearDirectionalScrollParking()
        }
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

    private fun activeDirectionalScrollTarget(): Int? {
        val targetPosition = directionalScrollTargetPosition
            .takeIf { it != RecyclerView.NO_POSITION }
            ?: return null
        if (SystemClock.uptimeMillis() > directionalScrollParkedUntilMs) {
            clearDirectionalScrollParking()
            return null
        }
        return targetPosition
    }

    private fun clearDirectionalScrollParking() {
        directionalScrollParkedUntilMs = 0L
        directionalScrollTargetPosition = RecyclerView.NO_POSITION
        unparkFocusInRecyclerViewIfNeeded()
    }

    private fun isFocusRequestCurrent(recycler: RecyclerView, requestToken: Int): Boolean {
        if (this.recyclerView !== recycler || focusRequestToken != requestToken) return false
        if (!callbacks.isEnabled()) return false
        val focusedView = recycler.rootView?.findFocus()
        if (focusedView == null) return true // Allow null focus to continue retrying during detached transitions
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
    fun downTargetPosition(
        position: Int,
        itemCount: Int,
        spanCount: Int,
    ): Int {
        if (position < 0 || itemCount <= 0 || spanCount <= 0 || position >= itemCount) {
            return RecyclerView.NO_POSITION
        }
        val targetPosition = position + spanCount
        return targetPosition.takeIf { it in 0 until itemCount } ?: RecyclerView.NO_POSITION
    }
}

internal fun RecyclerView.focusSearchAdapterPositionDown(focused: View?): View? {
    val currentHolder = focused?.let(::findContainingViewHolder) ?: return null
    val currentPosition = currentHolder.bindingAdapterPosition
        .takeIf { it != RecyclerView.NO_POSITION }
        ?: return null
    val gridLayoutManager = layoutManager as? GridLayoutManager ?: return null
    val spanCount = gridLayoutManager.spanCount.takeIf { it > 0 } ?: return null
    val itemCount = adapter?.itemCount ?: return null
    val targetPosition = DpadGridDirectionalFocusPolicy.downTargetPosition(
        position = currentPosition,
        itemCount = itemCount,
        spanCount = spanCount,
    )
    if (targetPosition == RecyclerView.NO_POSITION) return focused

    findViewHolderForAdapterPosition(targetPosition)?.itemView?.let { targetItem ->
        if (targetItem.isValidFocusTarget() && isAdapterItemFullyVisible(targetItem)) return targetItem
    }

    requestFocusAdapterPositionReliable(
        position = targetPosition,
        attempts = AdapterPositionFocusRetryMaxAttempts,
        scrollOffsetPx = gridLayoutManager.getDecoratedTop(currentHolder.itemView),
        expectedFocusedPosition = currentPosition,
    )
    return focused
}

internal fun RecyclerView.requestFocusAdapterPositionReliable(
    position: Int,
    attempts: Int = AdapterPositionFocusRetryMaxAttempts,
    scrollOffsetPx: Int? = null,
    expectedFocusedPosition: Int? = null,
): Boolean {
    val recycler = this
    if (position == RecyclerView.NO_POSITION) return false
    val itemCount = adapter?.itemCount ?: return false
    if (position !in 0 until itemCount) return false

    if (scrollOffsetPx != null) {
        (layoutManager as? GridLayoutManager)
            ?.scrollToPositionWithOffset(position, scrollOffsetPx)
            ?: scrollToPosition(position)
    } else {
        scrollToPosition(position)
    }

    fun attempt(remaining: Int) {
        if (expectedFocusedPosition != null) {
            val focusedView = rootView?.findFocus()
            val focusedPosition = focusedView
                ?.let(::findContainingViewHolder)
                ?.bindingAdapterPosition
                ?.takeIf { it != RecyclerView.NO_POSITION }
            if (focusedView !== recycler && focusedPosition != expectedFocusedPosition) return
        }

        val itemView = findViewHolderForAdapterPosition(position)?.itemView
        if (itemView != null && itemView.isValidFocusTarget() && itemView.requestFocus()) return
        if (remaining > 0) {
            post { attempt(remaining - 1) }
        }
    }

    post { attempt(attempts.coerceAtLeast(0)) }
    return true
}

private fun RecyclerView.isAdapterItemFullyVisible(itemView: View): Boolean {
    val gridLayoutManager = layoutManager as? GridLayoutManager ?: return false
    val bounds = Rect()
    gridLayoutManager.getDecoratedBoundsWithMargins(itemView, bounds)
    return bounds.top >= paddingTop && bounds.bottom <= height - paddingBottom
}

private const val AdapterPositionFocusRetryMaxAttempts = 8

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
