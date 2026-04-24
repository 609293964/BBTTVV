package com.bbttvv.app.ui.home

import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

internal class HomeRecommendGridFocusState {
    private var recyclerView: RecyclerView? = null
    private var lastFocusedKey: String? = null
    private var pendingScrollToTop: Boolean = false
    private var pendingDataSetFocus: PendingDataSetFocus? = null
    private var suppressRecyclerFocusRecovery: Boolean = false
    private var focusRequestToken: Int = 0
    private var onFocusTargetAvailabilityChanged: (() -> Unit)? = null

    fun attach(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        applyPendingAfterItemsAvailable()
    }

    fun detach(recyclerView: RecyclerView) {
        if (this.recyclerView === recyclerView) {
            this.recyclerView = null
        }
    }

    fun onItemsCommitted() {
        suppressRecyclerFocusRecovery = false
        applyPendingAfterItemsAvailable()
    }

    fun setOnFocusTargetAvailabilityChanged(callback: (() -> Unit)?) {
        onFocusTargetAvailabilityChanged = callback
    }

    private fun applyPendingAfterItemsAvailable() {
        applyPendingScrollToTop()
        applyPendingDataSetFocus()
        enqueueRecyclerChildFocusRecoveryIfNeeded()
    }

    fun prepareForDataSetChange(): Boolean {
        val recycler = attachedRecyclerView() ?: return false
        val currentFocused = recycler.rootView?.findFocus()
        val focusInsideRecycler = currentFocused === recycler ||
            currentFocused?.isSameOrDescendantOf(recycler) == true
        return if (focusInsideRecycler) {
            pendingDataSetFocus = captureCurrentFocus(recycler, currentFocused)
            suppressRecyclerFocusRecovery = true
            pendingDataSetFocus != null
        } else {
            false
        }
    }

    fun onItemFocused(key: String) {
        focusRequestToken++
        lastFocusedKey = key
        pendingDataSetFocus = null
    }

    fun hasRememberedFocus(): Boolean {
        return lastFocusedKey != null
    }

    fun hasFocusInside(): Boolean {
        val recycler = attachedRecyclerView() ?: return false
        val currentFocused = recycler.rootView?.findFocus() ?: return false
        return currentFocused === recycler || currentFocused.isSameOrDescendantOf(recycler)
    }

    fun requestScrollToTop() {
        pendingScrollToTop = true
        pendingDataSetFocus = null
        prepareForDataSetChange()
        applyPendingScrollToTop()
    }

    fun resetRememberedFocusToTop() {
        lastFocusedKey = null
        requestScrollToTop()
    }

    fun tryFocusVisibleItem(): Boolean {
        val recycler = attachedRecyclerView() ?: return false
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return false
        if (adapter.itemCount <= 0) return false

        val currentFocused = recycler.rootView?.findFocus()
        if (
            currentFocused != null &&
            currentFocused !== recycler &&
            currentFocused.isValidFocusTarget() &&
            currentFocused.isSameOrDescendantOf(recycler)
        ) {
            return true
        }

        val targetPosition =
            lastFocusedKey
                ?.let(adapter::positionOfKey)
                ?.takeIf { it in 0 until adapter.itemCount }
                ?: firstVisiblePosition(recycler).takeIf { it in 0 until adapter.itemCount }
                ?: 0

        return tryFocusPosition(
            recycler = recycler,
            position = targetPosition,
            expectedKey = adapter.keyAt(targetPosition),
        )
    }

    fun enqueueRecyclerChildFocusRecoveryIfNeeded() {
        if (suppressRecyclerFocusRecovery) return
        val recycler = attachedRecyclerView() ?: return
        if (recycler.rootView?.findFocus() !== recycler) return
        val requestToken = ++focusRequestToken
        recycler.post {
            if (
                !suppressRecyclerFocusRecovery &&
                focusRequestToken == requestToken &&
                this.recyclerView === recycler &&
                recycler.isValidFocusTarget() &&
                recycler.rootView?.findFocus() === recycler
            ) {
                tryFocusInVisibleWindow(recycler, requestToken)
            }
        }
    }

    fun clearVisibleFocusVisualState(): Boolean {
        val recycler = attachedRecyclerView() ?: return false
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return false
        adapter.clearVisibleFocusVisualState(recycler)
        return true
    }

    private fun applyPendingScrollToTop() {
        if (!pendingScrollToTop) return
        val recycler = attachedRecyclerView() ?: return
        if ((recycler.adapter?.itemCount ?: 0) <= 0) return
        pendingScrollToTop = false
        recycler.scrollToPosition(0)
    }

    fun tryFocusKey(key: String): Boolean {
        pendingDataSetFocus = null
        val recycler = attachedRecyclerView() ?: return false
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return false
        val position = adapter.positionOfKey(key)
        if (position !in 0 until adapter.itemCount) return false
        return tryFocusPosition(
            recycler = recycler,
            position = position,
            expectedKey = key,
        )
    }

    fun tryFocusPreviousRow(): Boolean {
        val recycler = attachedRecyclerView() ?: return false
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return false
        val layoutManager = recycler.layoutManager as? GridLayoutManager ?: return false
        val spanCount = layoutManager.spanCount.takeIf { it > 0 } ?: return false
        val currentFocused = recycler.rootView?.findFocus() ?: return false
        val currentPosition = currentFocused
            .takeUnless { it === recycler }
            ?.let(recycler::findContainingViewHolder)
            ?.bindingAdapterPosition
            ?.takeIf { it in 0 until adapter.itemCount }
            ?: return false
        if (currentPosition < spanCount) return false
        val targetPosition = currentPosition - spanCount
        return tryFocusPosition(
            recycler = recycler,
            position = targetPosition,
            expectedKey = adapter.keyAt(targetPosition),
        )
    }

    private fun captureCurrentFocus(
        recycler: RecyclerView,
        currentFocused: View?,
    ): PendingDataSetFocus? {
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return null
        val directPosition = currentFocused
            ?.takeUnless { it === recycler }
            ?.let(recycler::findContainingViewHolder)
            ?.bindingAdapterPosition
            ?.takeIf { it in 0 until adapter.itemCount }
        val keyedPosition = lastFocusedKey
            ?.let(adapter::positionOfKey)
            ?.takeIf { it in 0 until adapter.itemCount }
        val position = directPosition ?: keyedPosition ?: return null
        val key = adapter.keyAt(position) ?: return null
        return PendingDataSetFocus(key = key, position = position)
    }

    private fun tryFocusInVisibleWindow(
        recycler: RecyclerView,
        requestToken: Int = ++focusRequestToken,
    ): Boolean {
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return false
        val itemCount = adapter.itemCount
        if (itemCount <= 0) return false
        val layoutManager = recycler.layoutManager as? GridLayoutManager ?: return false
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
            .takeIf { it != RecyclerView.NO_POSITION }
            ?: return false
        val lastVisible = layoutManager.findLastVisibleItemPosition()
            .takeIf { it != RecyclerView.NO_POSITION }
            ?: firstVisible
        val lastFocusedPosition = lastFocusedKey
            ?.let(adapter::positionOfKey)
            ?.takeIf { it in 0 until itemCount }
        val spanCount = layoutManager.spanCount.takeIf { it > 0 } ?: 1
        val targetPosition = when {
            lastFocusedPosition != null && lastFocusedPosition in firstVisible..lastVisible -> {
                lastFocusedPosition
            }
            lastFocusedPosition != null -> {
                val targetColumn = lastFocusedPosition % spanCount
                val visiblePositions = firstVisible..lastVisible
                if (lastFocusedPosition < firstVisible) {
                    visiblePositions.reversed()
                } else {
                    visiblePositions
                }.firstOrNull { it in 0 until itemCount && it % spanCount == targetColumn }
            }
            else -> null
        } ?: firstVisible.coerceIn(0, itemCount - 1)

        return tryFocusPosition(
            recycler = recycler,
            position = targetPosition,
            expectedKey = adapter.keyAt(targetPosition),
            requestToken = requestToken,
        )
    }

    private fun tryFocusPosition(
        recycler: RecyclerView,
        position: Int,
        expectedKey: String?,
        onSuccess: (() -> Unit)? = null,
        requestToken: Int = ++focusRequestToken,
    ): Boolean {
        if (focusRequestToken != requestToken) return false
        if (!recycler.isValidFocusTarget()) return false
        val adapter = recycler.adapter ?: return false
        if (position !in 0 until adapter.itemCount) return false

        if (!isPositionVisible(recycler, position)) {
            recycler.scrollToPosition(position)
            notifyFocusTargetAvailabilityAfterLayout(recycler)
            return false
        }

        val holder = recycler.findViewHolderForAdapterPosition(position)
        val itemView = holder?.itemView
        if (itemView != null && itemView.isValidFocusTarget() && itemView.requestFocus()) {
            if (expectedKey != null) onItemFocused(expectedKey)
            onSuccess?.invoke()
            return true
        }

        notifyFocusTargetAvailabilityAfterLayout(recycler)
        return false
    }

    private fun applyPendingDataSetFocus(): Boolean {
        val pending = pendingDataSetFocus ?: return false
        val recycler = attachedRecyclerView() ?: return false
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return false
        val itemCount = adapter.itemCount
        if (itemCount <= 0) {
            pendingDataSetFocus = null
            return false
        }

        val currentFocused = recycler.rootView?.findFocus()

        if (
            currentFocused != null &&
            currentFocused !== recycler &&
            currentFocused.isValidFocusTarget() &&
            currentFocused.isSameOrDescendantOf(recycler)
        ) {
            pendingDataSetFocus = null
            return true
        }

        val targetPosition = adapter.positionOfKey(pending.key)
            .takeIf { it in 0 until itemCount }
            ?: pending.position.takeIf { it in 0 until itemCount }
            ?: pending.position.coerceIn(0, itemCount - 1)

        return tryFocusPosition(
            recycler = recycler,
            position = targetPosition,
            expectedKey = adapter.keyAt(targetPosition),
            onSuccess = {
                pendingDataSetFocus = null
            }
        )
    }

    private fun attachedRecyclerView(): RecyclerView? {
        return recyclerView?.takeIf { it.isValidFocusTarget() }
    }

    private fun firstVisiblePosition(recycler: RecyclerView): Int {
        return (recycler.layoutManager as? GridLayoutManager)
            ?.findFirstVisibleItemPosition()
            ?.takeIf { it != RecyclerView.NO_POSITION }
            ?: RecyclerView.NO_POSITION
    }

    private fun isPositionVisible(recycler: RecyclerView, position: Int): Boolean {
        val layoutManager = recycler.layoutManager as? GridLayoutManager ?: return false
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        return first != RecyclerView.NO_POSITION &&
            last != RecyclerView.NO_POSITION &&
            position in first..last
    }

    private fun notifyFocusTargetAvailabilityAfterLayout(recycler: RecyclerView) {
        recycler.post {
            if (this.recyclerView === recycler && recycler.isValidFocusTarget()) {
                onFocusTargetAvailabilityChanged?.invoke()
            }
        }
    }
}

private data class PendingDataSetFocus(
    val key: String,
    val position: Int,
)
