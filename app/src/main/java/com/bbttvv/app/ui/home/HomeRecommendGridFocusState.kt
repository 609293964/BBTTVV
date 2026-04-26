package com.bbttvv.app.ui.home

import android.os.SystemClock
import android.view.KeyEvent
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

internal class HomeRecommendGridFocusState {
    private var recyclerView: RecyclerView? = null
    private var lastFocusedKey: String? = null
    private var lastFocusedPosition: Int = RecyclerView.NO_POSITION
    private var pendingScrollToTop: Boolean = false
    private var pendingDataSetFocus: PendingGridFocus? = null
    private var pendingDirectionalScrollFocusPosition: Int = RecyclerView.NO_POSITION
    private var pendingDirectionalScrollFocusUntilUptimeMs: Long = 0L
    private var focusRequestToken: Int = 0
    private var lastUserNavigationUptimeMs: Long = 0L
    private var suppressRecyclerFocusRestoreUntilUptimeMs: Long = 0L
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
        applyPendingAfterItemsAvailable()
    }

    fun setOnFocusTargetAvailabilityChanged(callback: (() -> Unit)?) {
        onFocusTargetAvailabilityChanged = callback
    }

    fun onRecyclerFocusChanged(hasFocus: Boolean) {
        if (hasFocus && !isRecyclerFocusRestoreSuppressed()) {
            restoreChildFocusIfRecyclerOwnsFocus()
        }
    }

    fun parkFocusForDirectionalScroll(targetPosition: Int): Boolean {
        val recycler = currentRecyclerView() ?: return false
        val now = SystemClock.uptimeMillis()
        val suppressedUntil = now + DirectionalScrollFocusParkingWindowMs
        suppressRecyclerFocusRestoreUntilUptimeMs = maxOf(
            suppressRecyclerFocusRestoreUntilUptimeMs,
            suppressedUntil,
        )
        if (targetPosition != RecyclerView.NO_POSITION) {
            pendingDirectionalScrollFocusPosition = targetPosition
            pendingDirectionalScrollFocusUntilUptimeMs = now + DirectionalScrollFocusTargetWindowMs
        } else {
            clearPendingDirectionalScrollFocus()
        }
        return recycler.requestFocusParking()
    }

    fun noteUserNavigation(keyCode: Int, event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_DOWN && keyCode in DirectionalKeyCodes) {
            lastUserNavigationUptimeMs = SystemClock.uptimeMillis()
        }
    }

    fun prepareForDataSetChange(nextItems: List<HomeRecommendVideoCardItem>): Boolean {
        val recycler = currentRecyclerView() ?: return false
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return false
        val currentFocused = recycler.rootView?.findFocus()
        val focusInsideRecycler = currentFocused === recycler ||
            currentFocused?.isSameOrDescendantOf(recycler) == true
        if (!focusInsideRecycler) return false

        val focusedPosition = findFocusedAdapterPosition(
            recycler = recycler,
            focusedView = currentFocused,
            itemCount = adapter.itemCount,
        )
        val focusedKey = focusedPosition?.let(adapter::keyAt) ?: lastFocusedKey
        val nextPositionForKey = focusedKey?.let { key ->
            nextItems.indexOfFirst { it.key == key }.takeIf { it >= 0 }
        }

        if (
            HomeGridDataSetFocusPolicy.shouldKeepFocusedChild(
                focusIsRecyclerContainer = currentFocused === recycler,
                focusedKey = focusedKey,
                nextPositionForKey = nextPositionForKey,
            )
        ) {
            lastFocusedKey = focusedKey
            lastFocusedPosition = nextPositionForKey ?: RecyclerView.NO_POSITION
            pendingDataSetFocus = null
            return false
        }

        val pendingDirectionalPosition = pendingDirectionalScrollFocusPositionForItemCount(nextItems.size)
        pendingDataSetFocus = if (pendingDirectionalPosition != null) {
            PendingGridFocus(
                key = nextItems.getOrNull(pendingDirectionalPosition)?.key,
                position = pendingDirectionalPosition,
            )
        } else {
            PendingGridFocus(
                key = focusedKey?.takeIf { nextPositionForKey != null },
                position = HomeGridDataSetFocusPolicy.pendingPosition(
                    nextItemCount = nextItems.size,
                    nextPositionForKey = nextPositionForKey,
                    focusedPosition = focusedPosition,
                    rememberedPosition = lastFocusedPosition,
                    firstVisiblePosition = firstVisiblePosition(recycler),
                ),
            )
        }

        if (currentFocused === recycler) {
            if (!isRecyclerFocusRestoreSuppressed()) {
                schedulePendingFocusAfterLayout()
            }
            return true
        }

        val parked = recycler.parkFocusForDataSetReset()
        if (parked) {
            schedulePendingFocusAfterLayout()
        }
        return parked
    }

    fun onItemFocused(key: String, position: Int) {
        suppressRecyclerFocusRestoreUntilUptimeMs = 0L
        clearPendingDirectionalScrollFocus()
        lastFocusedKey = key
        lastFocusedPosition = position

        val pending = pendingDataSetFocus ?: return
        when {
            pending.matches(key = key, position = position) -> pendingDataSetFocus = null
            isRecentUserNavigation() -> pendingDataSetFocus = null
            else -> schedulePendingFocusAfterLayout()
        }
    }

    fun hasRememberedFocus(): Boolean {
        return lastFocusedKey != null
    }

    fun hasFocusInside(): Boolean {
        val recycler = currentRecyclerView() ?: return false
        val currentFocused = recycler.rootView?.findFocus() ?: return false
        return currentFocused === recycler || currentFocused.isSameOrDescendantOf(recycler)
    }

    fun requestScrollToTop() {
        pendingScrollToTop = true
        pendingDataSetFocus = null
        clearPendingDirectionalScrollFocus()
        val recycler = currentRecyclerView()
        val currentFocused = recycler?.rootView?.findFocus()
        if (
            recycler != null &&
            currentFocused != null &&
            (currentFocused === recycler || currentFocused.isSameOrDescendantOf(recycler))
        ) {
            pendingDataSetFocus = PendingGridFocus(key = null, position = 0)
            recycler.parkFocusForDataSetReset()
        }
        applyPendingScrollToTop()
    }

    fun resetRememberedFocusToTop() {
        lastFocusedKey = null
        lastFocusedPosition = RecyclerView.NO_POSITION
        requestScrollToTop()
    }

    fun tryFocusVisibleItem(): Boolean {
        val recycler = currentRecyclerView() ?: return false
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

        val pendingDirectionalPosition = pendingDirectionalScrollFocusPosition(adapter)
        val targetPosition =
            pendingDirectionalPosition
                ?: lastFocusedKey
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

    fun clearVisibleFocusVisualState(): Boolean {
        val recycler = currentRecyclerView() ?: return false
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return false
        adapter.clearVisibleFocusVisualState(recycler)
        return true
    }

    fun tryFocusKey(key: String): Boolean {
        pendingDataSetFocus = null
        clearPendingDirectionalScrollFocus()
        val recycler = currentRecyclerView() ?: return false
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
        val recycler = currentRecyclerView() ?: return false
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return false
        val layoutManager = recycler.layoutManager as? GridLayoutManager ?: return false
        val spanCount = layoutManager.spanCount.takeIf { it > 0 } ?: return false
        val currentFocused = recycler.rootView?.findFocus() ?: return false
        val currentPosition = findFocusedAdapterPosition(
            recycler = recycler,
            focusedView = currentFocused,
            itemCount = adapter.itemCount,
        ) ?: return false
        if (currentPosition < spanCount) return false
        val targetPosition = currentPosition - spanCount
        return tryFocusPosition(
            recycler = recycler,
            position = targetPosition,
            expectedKey = adapter.keyAt(targetPosition),
        )
    }

    private fun applyPendingAfterItemsAvailable() {
        applyPendingScrollToTop()
        if (isRecyclerFocusRestoreSuppressed()) return
        if (!applyPendingDataSetFocus()) {
            restoreChildFocusIfRecyclerOwnsFocus()
        }
    }

    private fun applyPendingScrollToTop() {
        if (!pendingScrollToTop) return
        val recycler = currentRecyclerView() ?: return
        if ((recycler.adapter?.itemCount ?: 0) <= 0) return
        pendingScrollToTop = false
        recycler.scrollToPosition(0)
        schedulePendingFocusAfterLayout()
    }

    private fun restoreChildFocusIfRecyclerOwnsFocus(): Boolean {
        val recycler = currentRecyclerView() ?: return false
        if (recycler.rootView?.findFocus() !== recycler) return false
        if (pendingDataSetFocus == null) {
            val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return false
            if (adapter.itemCount <= 0) return false
            val pendingDirectionalPosition = pendingDirectionalScrollFocusPosition(adapter)
            pendingDataSetFocus = PendingGridFocus(
                key = pendingDirectionalPosition
                    ?.let(adapter::keyAt)
                    ?: lastFocusedKey?.takeIf { adapter.positionOfKey(it) in 0 until adapter.itemCount },
                position = pendingDirectionalPosition
                    ?: lastFocusedKey
                    ?.let(adapter::positionOfKey)
                    ?.takeIf { it in 0 until adapter.itemCount }
                    ?: firstVisiblePosition(recycler).takeIf { it in 0 until adapter.itemCount }
                    ?: 0,
            )
        }
        return applyPendingDataSetFocus()
    }

    private fun applyPendingDataSetFocus(): Boolean {
        val pending = pendingDataSetFocus ?: return false
        val recycler = currentRecyclerView() ?: return false
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return false
        val itemCount = adapter.itemCount
        if (itemCount <= 0) return false

        val targetPosition = HomeGridDataSetFocusPolicy.resolvePendingTarget(
            itemCount = itemCount,
            keyPosition = pending.key?.let(adapter::positionOfKey),
            fallbackPosition = pending.position,
        )
        if (targetPosition == RecyclerView.NO_POSITION) return false
        return tryFocusPosition(
            recycler = recycler,
            position = targetPosition,
            expectedKey = adapter.keyAt(targetPosition),
            onFocused = {
                pendingDataSetFocus = null
            },
            retryCount = PendingFocusRetryCount,
        )
    }

    private fun schedulePendingFocusAfterLayout() {
        val recycler = currentRecyclerView() ?: return
        if (pendingDataSetFocus == null) return
        val requestToken = nextFocusRequestToken()
        recycler.postOnAnimation {
            if (recyclerView === recycler && isFocusRequestTokenCurrent(requestToken)) {
                applyPendingDataSetFocus()
            }
        }
        onFocusTargetAvailabilityChanged?.invoke()
    }

    private fun tryFocusPosition(
        recycler: RecyclerView,
        position: Int,
        expectedKey: String?,
        onFocused: (() -> Unit)? = null,
        requestToken: Int = nextFocusRequestToken(),
        retryCount: Int = DefaultFocusRetryCount,
    ): Boolean {
        if (!isFocusRequestTokenCurrent(requestToken)) return false
        if (!recycler.isValidFocusTarget()) return false
        val adapter = recycler.adapter ?: return false
        if (position !in 0 until adapter.itemCount) return false

        val holder = recycler.findViewHolderForAdapterPosition(position)
        val itemView = holder?.itemView
        if (itemView != null && itemView.isValidFocusTarget() && itemView.requestFocus()) {
            if (expectedKey != null) onItemFocused(expectedKey, position)
            onFocused?.invoke()
            return true
        }

        if (!isPositionVisible(recycler, position)) {
            recycler.scrollToPosition(position)
        }

        if (retryCount > 0) {
            recycler.postOnAnimation {
                if (recyclerView === recycler && isFocusRequestTokenCurrent(requestToken)) {
                    tryFocusPosition(
                        recycler = recycler,
                        position = position,
                        expectedKey = expectedKey,
                        onFocused = onFocused,
                        requestToken = requestToken,
                        retryCount = retryCount - 1,
                    )
                }
            }
        } else {
            onFocusTargetAvailabilityChanged?.invoke()
        }

        return false
    }

    private fun currentRecyclerView(): RecyclerView? {
        return attachedRecyclerView(recyclerView)
    }

    private fun nextFocusRequestToken(): Int {
        focusRequestToken++
        return focusRequestToken
    }

    private fun isFocusRequestTokenCurrent(requestToken: Int): Boolean {
        return focusRequestToken == requestToken
    }

    private fun isRecentUserNavigation(): Boolean {
        return SystemClock.uptimeMillis() - lastUserNavigationUptimeMs <= UserNavigationFocusWindowMs
    }

    private fun isRecyclerFocusRestoreSuppressed(): Boolean {
        return SystemClock.uptimeMillis() <= suppressRecyclerFocusRestoreUntilUptimeMs
    }

    private fun pendingDirectionalScrollFocusPosition(adapter: HomeVideoCardAdapter): Int? {
        return pendingDirectionalScrollFocusPositionForItemCount(adapter.itemCount)
    }

    private fun pendingDirectionalScrollFocusPositionForItemCount(itemCount: Int): Int? {
        if (pendingDirectionalScrollFocusPosition == RecyclerView.NO_POSITION) return null
        if (SystemClock.uptimeMillis() > pendingDirectionalScrollFocusUntilUptimeMs) {
            clearPendingDirectionalScrollFocus()
            return null
        }
        return pendingDirectionalScrollFocusPosition.takeIf { it in 0 until itemCount }
    }

    private fun clearPendingDirectionalScrollFocus() {
        pendingDirectionalScrollFocusPosition = RecyclerView.NO_POSITION
        pendingDirectionalScrollFocusUntilUptimeMs = 0L
    }

    private companion object {
        private const val UserNavigationFocusWindowMs = 250L
        private const val DirectionalScrollFocusParkingWindowMs = 700L
        private const val DirectionalScrollFocusTargetWindowMs = 1_500L
        private const val DefaultFocusRetryCount = 2
        private const val PendingFocusRetryCount = 4
        private val DirectionalKeyCodes = setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        )
    }
}

private data class PendingGridFocus(
    val key: String?,
    val position: Int,
) {
    fun matches(key: String, position: Int): Boolean {
        return this.key == key || (this.key == null && this.position == position)
    }
}
