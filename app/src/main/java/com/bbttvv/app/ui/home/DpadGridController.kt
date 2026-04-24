package com.bbttvv.app.ui.home

import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

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
        val onMenu: () -> Boolean = { false },
        val onBack: () -> Boolean = { false },
    )

    private var recyclerView: RecyclerView? = null
    private var focusProtectionHandle: RecyclerViewFocusProtectionHandle? = null
    private var callbacks: Callbacks = Callbacks()
    private var lastKnownFocusedPosition: Int = RecyclerView.NO_POSITION
    private var lastKnownFocusedColumn: Int = RecyclerView.NO_POSITION
    private var pendingLoadMoreFocus: PendingLoadMoreFocus? = null
    private var focusRequestToken: Int = 0
    private var centerLongPressHandled: Boolean = false

    private val recyclerKeyListener =
        View.OnKeyListener { _, keyCode, event ->
            handleItemKeyEvent(
                itemView = recyclerView?.rootView?.findFocus(),
                position = lastKnownFocusedPosition,
                keyCode = keyCode,
                event = event,
            )
        }

    fun attach(recyclerView: RecyclerView) {
        if (this.recyclerView === recyclerView) return
        detach()
        this.recyclerView = recyclerView
        recyclerView.setOnKeyListener(recyclerKeyListener)
        focusProtectionHandle = recyclerView.installChildFocusParkingOnDetach()
    }

    fun detach() {
        focusProtectionHandle?.dispose()
        focusProtectionHandle = null
        recyclerView?.setOnKeyListener(null)
        recyclerView = null
        lastKnownFocusedPosition = RecyclerView.NO_POSITION
        lastKnownFocusedColumn = RecyclerView.NO_POSITION
        pendingLoadMoreFocus = null
    }

    fun updateCallbacks(callbacks: Callbacks) {
        this.callbacks = callbacks
    }

    fun onItemFocused(position: Int) {
        if (position != RecyclerView.NO_POSITION) {
            focusRequestToken++
            lastKnownFocusedPosition = position
            lastKnownFocusedColumn = spanCountOrNull()?.let { position % it } ?: RecyclerView.NO_POSITION
            pendingLoadMoreFocus = null
            centerLongPressHandled = false
        }
    }

    fun onItemsCommitted(): Boolean {
        return applyPendingLoadMoreFocus()
    }

    fun hasPendingLoadMoreFocus(): Boolean {
        return pendingLoadMoreFocus != null
    }

    fun prepareForDataSetChange(): Boolean {
        if (pendingLoadMoreFocus != null) return false
        val recycler = recyclerView ?: return false
        val currentFocused = recycler.rootView?.findFocus()
        val focusInsideRecycler = currentFocused === recycler ||
            currentFocused?.isSameOrDescendantOf(recycler) == true
        return if (focusInsideRecycler) {
            recycler.parkFocusForDataSetReset()
        } else {
            false
        }
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
        return handleItemKeyDown(position = position, keyCode = keyCode)
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
                if (edge.isLeft) callbacks.onLeftEdge() || config.consumeLeftEdge else false
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (edge.isRight) callbacks.onRightEdge() || config.consumeRightEdge else false
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

    private fun handleDpadUp(
        recycler: RecyclerView,
        position: Int,
        spanCount: Int,
        edge: DpadGridEdge,
    ): Boolean {
        if (edge.isTop) {
            return callbacks.onTopEdge()
        }

        val targetPosition = position - spanCount
        if (targetPosition < 0) return false

        recycler.findViewHolderForAdapterPosition(targetPosition)?.itemView?.let { target ->
            if (target.requestFocus()) {
                return true
            }
        }

        recycler.scrollToPosition(targetPosition)
        val requestToken = ++focusRequestToken
        recycler.post {
            if (focusRequestToken != requestToken) return@post
            recycler.findViewHolderForAdapterPosition(targetPosition)?.itemView?.requestFocus()
        }
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
        if (currentItem != null) {
            val next = FocusFinder.getInstance().findNextFocus(
                recycler,
                currentItem,
                View.FOCUS_DOWN,
            )
            if (next != null && next.isSameOrDescendantOf(recycler)) {
                next.requestFocus()
                return true
            }
        }

        val nextPosition = position + spanCount
        if (nextPosition in 0 until itemCount) {
            val requestToken = ++focusRequestToken
            if (currentItem != null && recycler.canScrollVertically(1)) {
                val dy = (currentItem.height * config.scrollOnDownEdgeFactor)
                    .toInt()
                    .coerceAtLeast(1)
                recycler.scrollBy(0, dy)
            }
            tryFocusAtPosition(
                recycler = recycler,
                position = nextPosition,
                requestToken = requestToken,
            )
            return true
        }

        if (recycler.canScrollVertically(1)) {
            val requestToken = ++focusRequestToken
            val dy = ((currentItem?.height ?: recycler.height / 2) * config.scrollOnDownEdgeFactor)
                .toInt()
                .coerceAtLeast(1)
            recycler.scrollBy(0, dy)
            recycler.post {
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

        if (!edge.isBottom) return true
        if (callbacks.onBottomEdge(position)) return true
        if (callbacks.canLoadMore()) {
            prepareLoadMoreFocus(
                recycler = recycler,
                anchorPosition = position,
                oldItemCount = itemCount,
                spanCount = spanCount,
            )
            callbacks.loadMore()
            return true
        }
        return config.consumeDownAtBottomEdge
    }

    private fun prepareLoadMoreFocus(
        recycler: RecyclerView,
        anchorPosition: Int,
        oldItemCount: Int,
        spanCount: Int,
    ): Boolean {
        pendingLoadMoreFocus = LoadMoreFocusPolicy.create(
            anchorPosition = anchorPosition,
            oldItemCount = oldItemCount,
            spanCount = spanCount,
        )
        recycler.parkFocusForDataSetReset()
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
            currentFocused.isValidFocusTarget() &&
            currentFocused.isSameOrDescendantOf(recycler)
        ) {
            pendingLoadMoreFocus = null
            return true
        }

        val requestToken = ++focusRequestToken
        val targetPosition = LoadMoreFocusPolicy.targetPosition(
            pending = pending,
            spanCount = layoutManager.spanCount,
            itemCount = itemCount,
        )
        if (targetPosition !in 0 until itemCount) return false

        return tryFocusAtPosition(
            recycler = recycler,
            position = targetPosition,
            requestToken = requestToken,
            onFocused = {
                pendingLoadMoreFocus = null
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

    private fun tryFocusAtPosition(
        recycler: RecyclerView,
        position: Int,
        requestToken: Int = ++focusRequestToken,
        onFocused: (() -> Unit)? = null,
    ): Boolean {
        if (focusRequestToken != requestToken) return false
        val adapter = recycler.adapter ?: return false
        if (position !in 0 until adapter.itemCount) return false

        recycler.findViewHolderForAdapterPosition(position)?.itemView?.let { itemView ->
            if (itemView.isValidFocusTarget() && itemView.requestFocus()) {
                onFocused?.invoke()
                return true
            }
        }

        if (!isPositionVisible(recycler, position)) {
            recycler.scrollToPosition(position)
        }

        recycler.post {
            if (this.recyclerView === recycler && focusRequestToken == requestToken) {
                tryFocusAtPosition(
                    recycler = recycler,
                    position = position,
                    requestToken = requestToken,
                    onFocused = onFocused,
                )
            }
        }

        return false
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

    private fun spanCountOrNull(): Int? {
        return (recyclerView?.layoutManager as? GridLayoutManager)
            ?.spanCount
            ?.takeIf { it > 0 }
    }

    private fun isPositionVisible(recycler: RecyclerView, position: Int): Boolean {
        val layoutManager = recycler.layoutManager as? GridLayoutManager ?: return false
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        return first != RecyclerView.NO_POSITION &&
            last != RecyclerView.NO_POSITION &&
            position in first..last
    }

    private companion object {
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

internal data class PendingLoadMoreFocus(
    val anchorPosition: Int,
    val anchorColumn: Int,
    val oldItemCount: Int,
)

internal object LoadMoreFocusPolicy {
    fun create(
        anchorPosition: Int,
        oldItemCount: Int,
        spanCount: Int,
    ): PendingLoadMoreFocus? {
        if (oldItemCount <= 0 || spanCount <= 0 || anchorPosition !in 0 until oldItemCount) {
            return null
        }
        return PendingLoadMoreFocus(
            anchorPosition = anchorPosition,
            anchorColumn = anchorPosition % spanCount,
            oldItemCount = oldItemCount,
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
