package com.bbttvv.app.ui.home

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bbttvv.app.BuildConfig
import com.bbttvv.app.ui.focus.GridFocusDebugLog
import com.bbttvv.app.ui.focus.isSameOrDescendantOf

internal class HomeRecommendGridFocusState {
    private var recyclerView: RecyclerView? = null
    private var lastFocusedKey: String? = null
    private var lastFocusedPosition: Int = RecyclerView.NO_POSITION
    private var lastFocusedRow: Int = RecyclerView.NO_POSITION
    private var lastFocusedColumn: Int = RecyclerView.NO_POSITION
    private var pendingScrollToTop: Boolean = false
    private var restoreFocusAfterPendingScrollToTop: Boolean = true
    private var pendingDataSetFocus: PendingGridFocus? = null
    private var forceFirstItemFocusOnNextDataSetChange: Boolean = false
    private var pendingDirectionalScrollFocusPosition: Int = RecyclerView.NO_POSITION
    private var pendingDirectionalScrollFocusUntilUptimeMs: Long = 0L
    private var pendingDownSearchPosition: Int = RecyclerView.NO_POSITION
    private var pendingDownSearchUntilUptimeMs: Long = 0L
    private var pendingBackReturnRestore: PendingBackReturnRestore? = null
    private var focusRequestToken: Int = 0
    private var lastUserNavigationUptimeMs: Long = 0L
    private var suppressRecyclerFocusRestoreUntilUptimeMs: Long = 0L
    private var onFocusTargetAvailabilityChanged: (() -> Unit)? = null
    private var canPerformPhysicalScroll: () -> Boolean = { true }
    private var lastFocusAttemptPending: Boolean = false
    private var dataSetChangePendingCommit: Boolean = false
    private var dataSetParkedDescendantFocusability: Int? = null

    fun cancelAllPendingRequests() {
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.cancelAllPendingRequests before cancelAllPendingRequests=true " +
                debugSnapshot()
        }
        nextFocusRequestToken()
        pendingDataSetFocus = null
        pendingBackReturnRestore = null
        dataSetChangePendingCommit = false
        unparkFocusAfterDataSetCommit()
        clearPendingDirectionalScrollFocus()
        clearPendingDownSearch()
        clearVisualFocusAnchor()
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.cancelAllPendingRequests after cancelAllPendingRequests=true " +
                debugSnapshot()
        }
    }

    fun attach(recyclerView: RecyclerView) {
        if (this.recyclerView === recyclerView) return
        this.recyclerView = recyclerView
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.attach ${debugSnapshot()} ${GridFocusDebugLog.recycler(recyclerView)}"
        }
        applyPendingAfterItemsAvailable()
    }

    fun detach(recyclerView: RecyclerView) {
        if (this.recyclerView === recyclerView) {
            unparkFocusAfterDataSetCommit(recyclerView)
            this.recyclerView = null
        }
    }

    fun onItemsCommitted() {
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.onItemsCommitted before ${debugSnapshot()} " +
                GridFocusDebugLog.recycler(recyclerView)
        }
        dataSetChangePendingCommit = false
        if (pendingDataSetFocus != null) {
            applyPendingScrollToTop()
            scheduleDataSetFocusAfterCommit()
        } else {
            unparkFocusAfterDataSetCommit()
            applyPendingAfterItemsAvailable()
        }
        applyPendingDownSearch()
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.onItemsCommitted after ${debugSnapshot()} " +
                GridFocusDebugLog.recycler(recyclerView)
        }
    }

    fun setOnFocusTargetAvailabilityChanged(callback: (() -> Unit)?) {
        onFocusTargetAvailabilityChanged = callback
    }

    fun setPhysicalScrollAllowedProvider(provider: (() -> Boolean)?) {
        canPerformPhysicalScroll = provider ?: { true }
    }

    fun onRecyclerFocusChanged(hasFocus: Boolean) {
        if (
            hasFocus &&
            pendingBackReturnRestore == null &&
            !dataSetChangePendingCommit &&
            !isRecyclerFocusRestoreSuppressed()
        ) {
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
            setVisualFocusAnchor(targetPosition)
        } else {
            clearPendingDirectionalScrollFocus()
        }
        val parked = recycler.requestFocusParking()
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.parkFocusForDirectionalScroll targetPosition=$targetPosition " +
                "calledRequestFocus=true requestFocusSuccess=$parked ${debugSnapshot()} " +
                GridFocusDebugLog.recycler(recycler)
        }
        return parked
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
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.prepareForDataSetChange begin nextItemCount=${nextItems.size} " +
                "focusInsideRecycler=$focusInsideRecycler ${debugSnapshot()} " +
                GridFocusDebugLog.recycler(recycler)
        }
        if (!focusInsideRecycler) {
            GridFocusDebugLog.d {
                "HomeRecommendGridFocusState.prepareForDataSetChange skipped reason=focusOutside " +
                    "calledRequestFocus=false ${debugSnapshot()}"
            }
            return false
        }

        val focusedPosition = findFocusedAdapterPosition(
            recycler = recycler,
            focusedView = currentFocused,
            itemCount = adapter.itemCount,
        )
        val focusedKey = focusedPosition?.let(adapter::keyAt) ?: lastFocusedKey
        val nextPositionForKey = focusedKey?.let { key ->
            nextItems.indexOfFirst { it.key == key }.takeIf { it >= 0 }
        }

        if (forceFirstItemFocusOnNextDataSetChange) {
            forceFirstItemFocusOnNextDataSetChange = false
            clearRememberedFocus()
            clearPendingDirectionalScrollFocus()
            val targetPosition = HomeGridDataSetFocusPolicy.menuRefreshFocusPosition(nextItems.size)
            pendingDataSetFocus = PendingGridFocus(
                key = null,
                position = if (targetPosition != RecyclerView.NO_POSITION) targetPosition else 0,
                preferPosition = true,
            )
            val parked = parkFocusForPendingDataSetFocus(recycler, currentFocused)
            GridFocusDebugLog.d {
                "HomeRecommendGridFocusState.prepareForDataSetChange forceFirst parked=$parked " +
                    "${debugSnapshot()} ${GridFocusDebugLog.recycler(recycler)}"
            }
            return parked
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
                    rememberedPosition = rememberedRowColumnPosition(recycler, nextItems.size)
                        ?: lastFocusedPosition,
                    firstVisiblePosition = firstVisiblePosition(recycler),
                ),
            )
        }
        dataSetChangePendingCommit = true

        if (currentFocused === recycler) {
            val parked = parkFocusUntilDataSetCommit(recycler)
            GridFocusDebugLog.d {
                "HomeRecommendGridFocusState.prepareForDataSetChange recyclerAlreadyParked=$parked " +
                    "${debugSnapshot()} ${GridFocusDebugLog.recycler(recycler)}"
            }
            return parked
        }

        val parked = parkFocusUntilDataSetCommit(recycler)
        if (!parked) {
            dataSetChangePendingCommit = false
        }
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.prepareForDataSetChange parked=$parked " +
                "calledRequestFocus=true requestFocusSuccess=$parked focusedKey=$focusedKey " +
                "focusedPosition=$focusedPosition nextPositionForKey=$nextPositionForKey " +
                "${debugSnapshot()} ${GridFocusDebugLog.recycler(recycler)}"
        }
        return parked
    }

    fun onItemFocused(key: String, position: Int) {
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.onItemFocused before adapterPosition=$position itemKey=$key " +
                debugSnapshot()
        }
        suppressRecyclerFocusRestoreUntilUptimeMs = 0L
        clearVisualFocusAnchor()
        clearPendingDirectionalScrollFocus()
        clearPendingDownSearch()
        if (pendingBackReturnRestore?.key == key) {
            pendingBackReturnRestore = null
        }
        rememberFocusedPosition(key, position)

        val pending = pendingDataSetFocus
        if (pending == null) {
            GridFocusDebugLog.d {
                "HomeRecommendGridFocusState.onItemFocused after adapterPosition=$position itemKey=$key " +
                    debugSnapshot()
            }
            return
        }
        if (dataSetChangePendingCommit) {
            GridFocusDebugLog.d {
                "HomeRecommendGridFocusState.onItemFocused keepPendingUntilCommit=true " +
                    "adapterPosition=$position itemKey=$key ${debugSnapshot()}"
            }
            return
        }
        when {
            pending.matches(key = key, position = position) -> {
                if (!forceFirstItemFocusOnNextDataSetChange) {
                    pendingDataSetFocus = null
                }
            }
            isRecentUserNavigation() -> {
                pendingDataSetFocus = null
            }
            else -> {
                schedulePendingFocusAfterLayout()
            }
        }
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.onItemFocused after adapterPosition=$position itemKey=$key " +
                debugSnapshot()
        }
    }

    fun hasRememberedFocus(): Boolean {
        return lastFocusedKey != null || lastFocusedPosition != RecyclerView.NO_POSITION
    }

    fun hasFocusInside(): Boolean {
        val recycler = currentRecyclerView() ?: return false
        val currentFocused = recycler.rootView?.findFocus() ?: return false
        return currentFocused === recycler || currentFocused.isSameOrDescendantOf(recycler)
    }

    fun requestScrollToTop() {
        if (hasFocusInside() && isRecentUserNavigation()) {
            logHomeFocus { "requestScrollToTop IGNORED to protect active focus inside Grid." }
            return
        }
        pendingScrollToTop = true
        restoreFocusAfterPendingScrollToTop = true
        pendingDataSetFocus = null
        pendingBackReturnRestore = null
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

    fun requestMenuRefreshFocusToFirstItem() {
        forceFirstItemFocusOnNextDataSetChange = true
        clearRememberedFocus()
        clearPendingDirectionalScrollFocus()
        pendingScrollToTop = true
        restoreFocusAfterPendingScrollToTop = true
        pendingDataSetFocus = PendingGridFocus(
            key = null,
            position = 0,
            preferPosition = true,
        )
        applyPendingScrollToTop()
        applyPendingDataSetFocus()
    }

    fun resetRememberedFocusToTop() {
        clearRememberedFocus()
        requestScrollToTop()
    }

    // Back-to-TopBar keeps the tab focused, so do not restore a grid child after scrolling.
    fun resetRememberedFocusToTopForTopBarReturn() {
        clearRememberedFocus()
        pendingScrollToTop = true
        restoreFocusAfterPendingScrollToTop = false
        pendingDataSetFocus = null
        clearPendingDirectionalScrollFocus()
        applyPendingScrollToTop()
        scheduleScrollToTopAfterLayout()
    }

    fun tryFocusVisibleItem(): Boolean {
        return requestFocusVisibleItem().isFocused
    }

    fun requestFocusVisibleItem(): HomeFocusRequestResult {
        beginFocusAttempt()
        pendingBackReturnRestore = null
        val recycler = currentRecyclerView() ?: return HomeFocusRequestResult.Unavailable
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return HomeFocusRequestResult.Unavailable
        if (adapter.itemCount <= 0) return HomeFocusRequestResult.Unavailable

        val currentFocused = recycler.rootView?.findFocus()
        if (
            currentFocused != null &&
            currentFocused !== recycler &&
            currentFocused.isValidFocusTarget() &&
            currentFocused.isSameOrDescendantOf(recycler)
        ) {
            return HomeFocusRequestResult.Focused
        }

        val pendingDirectionalPosition = pendingDirectionalScrollFocusPosition(adapter)
        val rememberedKeyPosition = lastFocusedKey
            ?.let(adapter::positionOfKey)
            ?.takeIf { it in 0 until adapter.itemCount }
        val targetPosition =
            pendingDirectionalPosition
                ?: rememberedKeyPosition
                ?: rememberedVisibleFallbackPosition(recycler, adapter)

        return tryFocusPosition(
            recycler = recycler,
            position = targetPosition,
            expectedKey = adapter.keyAt(targetPosition),
        ).toRequestResult()
    }

    fun tryFocusEntryItem(preferredIndex: Int?): Boolean {
        return requestFocusEntryItem(preferredIndex).isFocused
    }

    fun requestFocusEntryItem(preferredIndex: Int?): HomeFocusRequestResult {
        if (preferredIndex == null) return requestFocusVisibleItem()
        beginFocusAttempt()
        pendingBackReturnRestore = null
        pendingDataSetFocus = null
        clearPendingDirectionalScrollFocus()
        val recycler = currentRecyclerView() ?: return HomeFocusRequestResult.Unavailable
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return HomeFocusRequestResult.Unavailable
        val spanCount = recycler.gridSpanCount() ?: return HomeFocusRequestResult.Unavailable
        val targetPosition = HomeGridEntryFocusPolicy.targetPosition(
            itemCount = adapter.itemCount,
            spanCount = spanCount,
            firstVisiblePosition = firstVisiblePosition(recycler),
            preferredIndex = preferredIndex,
        )
        if (targetPosition == RecyclerView.NO_POSITION) return HomeFocusRequestResult.Unavailable
        return tryFocusPosition(
            recycler = recycler,
            position = targetPosition,
            expectedKey = adapter.keyAt(targetPosition),
            retryCount = PendingFocusRetryCount,
        ).toRequestResult()
    }

    fun clearVisibleFocusVisualState(): Boolean {
        val recycler = currentRecyclerView() ?: return false
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return false
        adapter.clearVisibleFocusVisualState(recycler)
        return true
    }

    fun tryFocusKeyOrFallback(key: String): Boolean {
        return requestFocusKeyOrFallback(key).isFocused
    }

    fun requestFocusKeyOrFallback(key: String): HomeFocusRequestResult {
        beginFocusAttempt()
        pendingBackReturnRestore = null
        pendingDataSetFocus = null
        clearPendingDirectionalScrollFocus()
        val recycler = currentRecyclerView() ?: return HomeFocusRequestResult.Unavailable
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return HomeFocusRequestResult.Unavailable
        if (adapter.itemCount <= 0) return HomeFocusRequestResult.Unavailable
        val position = adapter.positionOfKey(key).takeIf { it in 0 until adapter.itemCount }
            ?: missingKeyFallbackPosition(recycler, adapter)
        return tryFocusPosition(
            recycler = recycler,
            position = position,
            expectedKey = adapter.keyAt(position),
        ).toRequestResult()
    }

    fun requestBackReturnFocusKey(key: String): HomeBackReturnRestoreResult {
        beginFocusAttempt()
        pendingDataSetFocus = null
        clearPendingDirectionalScrollFocus()
        val recycler = currentRecyclerView() ?: return HomeBackReturnRestoreResult.Unavailable
        val adapter = recycler.adapter as? HomeVideoCardAdapter
            ?: return HomeBackReturnRestoreResult.Unavailable
        if (adapter.itemCount <= 0) return HomeBackReturnRestoreResult.Unavailable

        backReturnFocusedResult(
            recycler = recycler,
            adapter = adapter,
            key = key,
        )?.let { return it }

        val exactPosition = adapter.positionOfKey(key).takeIf { it in 0 until adapter.itemCount }
        if (exactPosition == null) {
            return requestBackReturnFallbackFocus(
                key = key,
                recycler = recycler,
                adapter = adapter,
                exactPosition = RecyclerView.NO_POSITION,
                fallbackPosition = missingKeyFallbackPosition(recycler, adapter),
                requestToken = nextFocusRequestToken(),
                retryCount = BackReturnFallbackRetryCount,
            )
        }

        return requestBackReturnExactFocus(
            key = key,
            recycler = recycler,
            exactPosition = exactPosition,
        )
    }

    fun tryFocusKey(key: String): Boolean {
        return requestFocusKey(key).isFocused
    }

    fun requestFocusKey(key: String): HomeFocusRequestResult {
        beginFocusAttempt()
        pendingBackReturnRestore = null
        pendingDataSetFocus = null
        clearPendingDirectionalScrollFocus()
        val recycler = currentRecyclerView() ?: return HomeFocusRequestResult.Unavailable
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return HomeFocusRequestResult.Unavailable
        val position = adapter.positionOfKey(key)
        if (position !in 0 until adapter.itemCount) return HomeFocusRequestResult.Unavailable
        return tryFocusPosition(
            recycler = recycler,
            position = position,
            expectedKey = key,
        ).toRequestResult()
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
        logHomeFocus { "applyPendingScrollToTop: scrollToPositionWithOffset(0, paddingTop=${recycler.paddingTop})" }
        val shouldRestoreFocus = restoreFocusAfterPendingScrollToTop
        restoreFocusAfterPendingScrollToTop = true
        recycler.scrollToTopRespectingPadding()
        if (shouldRestoreFocus) {
            schedulePendingFocusAfterLayout()
        }
    }

    private fun scheduleScrollToTopAfterLayout() {
        val recycler = currentRecyclerView() ?: return
        recycler.postOnAnimation {
            if (recyclerView !== recycler) return@postOnAnimation
            recycler.scrollToTopRespectingPadding()
            recycler.postOnAnimation {
                if (recyclerView === recycler) {
                    recycler.scrollToTopRespectingPadding()
                }
            }
        }
    }

    private fun restoreChildFocusIfRecyclerOwnsFocus(): Boolean {
        val recycler = currentRecyclerView() ?: return false
        if (recycler.rootView?.findFocus() !== recycler) return false
        if (pendingBackReturnRestore != null) return false
        if (pendingDataSetFocus == null) {
            val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return false
            if (adapter.itemCount <= 0) return false
            val pendingDirectionalPosition = pendingDirectionalScrollFocusPosition(adapter)
            val rememberedKeyPosition = lastFocusedKey
                ?.let(adapter::positionOfKey)
                ?.takeIf { it in 0 until adapter.itemCount }
            pendingDataSetFocus = PendingGridFocus(
                key = pendingDirectionalPosition
                    ?.let(adapter::keyAt)
                    ?: lastFocusedKey?.takeIf { rememberedKeyPosition != null },
                position = pendingDirectionalPosition
                    ?: rememberedKeyPosition
                    ?: rememberedVisibleFallbackPosition(recycler, adapter),
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
            preferFallbackPosition = pending.preferPosition,
        )
        if (targetPosition == RecyclerView.NO_POSITION) return false
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.applyPendingDataSetFocus targetPosition=$targetPosition " +
                "targetKey=${adapter.keyAt(targetPosition)} ${debugSnapshot()} " +
                GridFocusDebugLog.recycler(recycler)
        }
        val focused = tryFocusPosition(
            recycler = recycler,
            position = targetPosition,
            expectedKey = adapter.keyAt(targetPosition),
            onFocused = {
                if (!forceFirstItemFocusOnNextDataSetChange) {
                    pendingDataSetFocus = null
                }
            },
            retryCount = PendingFocusRetryCount,
            requireRecyclerFocusOwnership = true,
        )
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.applyPendingDataSetFocus calledRequestFocus=true " +
                "requestFocusSuccess=$focused targetPosition=$targetPosition ${debugSnapshot()}"
        }
        return focused
    }

    private fun parkFocusForPendingDataSetFocus(
        recycler: RecyclerView,
        currentFocused: View?,
    ): Boolean {
        if (pendingDataSetFocus == null) return false
        dataSetChangePendingCommit = true
        if (currentFocused === recycler) {
            return parkFocusUntilDataSetCommit(recycler)
        }

        val parked = parkFocusUntilDataSetCommit(recycler)
        if (!parked) {
            dataSetChangePendingCommit = false
        }
        return parked
    }

    private fun parkFocusUntilDataSetCommit(recycler: RecyclerView): Boolean {
        installDataSetFocusProtection(recycler)
        if (dataSetParkedDescendantFocusability == null) {
            dataSetParkedDescendantFocusability = recycler.descendantFocusability
        }
        recycler.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        if (!recycler.isFocusable) {
            recycler.isFocusable = true
        }
        val parked = recycler.requestFocus()
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.parkFocusUntilDataSetCommit calledRequestFocus=true " +
                "requestFocusSuccess=$parked ${debugSnapshot()} ${GridFocusDebugLog.recycler(recycler)}"
        }
        if (!parked) {
            unparkFocusAfterDataSetCommit(recycler)
        }
        return parked
    }

    private fun installDataSetFocusProtection(recycler: RecyclerView) {
        recycler.installChildFocusParkingOnDetach().protectFocusDuringNextLayout()
    }

    private fun unparkFocusAfterDataSetCommit(recycler: RecyclerView? = recyclerView) {
        val currentRecycler = recycler ?: return
        val originalDescendantFocusability = dataSetParkedDescendantFocusability ?: return
        currentRecycler.descendantFocusability = originalDescendantFocusability
        dataSetParkedDescendantFocusability = null
    }

    private fun scheduleDataSetFocusAfterCommit() {
        val recycler = currentRecyclerView() ?: return
        if (pendingDataSetFocus == null) {
            unparkFocusAfterDataSetCommit(recycler)
            return
        }
        val requestToken = nextFocusRequestToken()
        lastFocusAttemptPending = true
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.scheduleDataSetFocusAfterCommit requestToken=$requestToken " +
                debugSnapshot()
        }
        recycler.postOnAnimation {
            if (recyclerView !== recycler || !isFocusRequestTokenCurrent(requestToken)) {
                return@postOnAnimation
            }
            unparkFocusAfterDataSetCommit(recycler)
            applyPendingDataSetFocus()
        }
    }

    private fun schedulePendingFocusAfterLayout() {
        val recycler = currentRecyclerView() ?: return
        if (pendingDataSetFocus == null) return
        val requestToken = nextFocusRequestToken()
        lastFocusAttemptPending = true
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.schedulePendingFocusAfterLayout requestToken=$requestToken " +
                debugSnapshot()
        }
        recycler.postOnAnimation {
            if (recyclerView === recycler && isFocusRequestTokenCurrent(requestToken)) {
                applyPendingDataSetFocus()
            }
        }
        onFocusTargetAvailabilityChanged?.invoke()
    }

    private fun backReturnFocusedResult(
        recycler: RecyclerView,
        adapter: HomeVideoCardAdapter,
        key: String,
    ): HomeBackReturnRestoreResult? {
        val currentFocused = recycler.rootView?.findFocus()
        val focusedPosition = findFocusedAdapterPosition(
            recycler = recycler,
            focusedView = currentFocused,
            itemCount = adapter.itemCount,
        )
        if (focusedPosition != null && adapter.keyAt(focusedPosition) == key) {
            pendingBackReturnRestore = null
            clearVisualFocusAnchor()
            return HomeBackReturnRestoreResult.ExactFocused
        }

        val pending = pendingBackReturnRestore ?: return null
        if (pending.key != key) return null
        val focusedFallback = focusedPosition != null &&
            focusedPosition == pending.fallbackPosition
        val parkedFallback = currentFocused === recycler &&
            pending.acceptRecyclerParkingAsFallback
        if (focusedFallback || parkedFallback) {
            pendingBackReturnRestore = null
            clearVisualFocusAnchor()
            return HomeBackReturnRestoreResult.FallbackFocused
        }
        return null
    }

    private fun requestBackReturnExactFocus(
        key: String,
        recycler: RecyclerView,
        exactPosition: Int,
    ): HomeBackReturnRestoreResult {
        val requestToken = nextFocusRequestToken()
        if (focusAdapterPosition(recycler, exactPosition, key)) {
            pendingBackReturnRestore = null
            return HomeBackReturnRestoreResult.ExactFocused
        }

        pendingBackReturnRestore = PendingBackReturnRestore(
            key = key,
            exactPosition = exactPosition,
            fallbackPosition = RecyclerView.NO_POSITION,
            requestToken = requestToken,
            acceptRecyclerParkingAsFallback = false,
        )
        setVisualFocusAnchor(exactPosition)
        if (!isPositionVisible(recycler, exactPosition) && canPerformPhysicalScroll()) {
            logHomeFocus { "requestBackReturnExactFocus: scrollToPosition pos=$exactPosition" }
            recycler.scrollToPosition(exactPosition)
        }
        parkBackReturnFocusIfOutsideRecycler(recycler)
        lastFocusAttemptPending = true
        scheduleBackReturnExactRetry(
            key = key,
            requestToken = requestToken,
            attemptsLeft = BackReturnExactRetryCount,
        )
        return HomeBackReturnRestoreResult.PendingShort
    }

    private fun scheduleBackReturnExactRetry(
        key: String,
        requestToken: Int,
        attemptsLeft: Int,
    ) {
        val recycler = currentRecyclerView() ?: return
        recycler.postOnAnimation {
            if (recyclerView !== recycler || !isFocusRequestTokenCurrent(requestToken)) {
                return@postOnAnimation
            }
            val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return@postOnAnimation
            val exactPosition = adapter.positionOfKey(key)
                .takeIf { it in 0 until adapter.itemCount }
            if (exactPosition != null && focusAdapterPosition(recycler, exactPosition, key)) {
                pendingBackReturnRestore = null
                return@postOnAnimation
            }
            if (attemptsLeft > 0) {
                scheduleBackReturnExactRetry(
                    key = key,
                    requestToken = requestToken,
                    attemptsLeft = attemptsLeft - 1,
                )
                return@postOnAnimation
            }

            val fallbackPosition = backReturnFallbackPosition(
                recycler = recycler,
                adapter = adapter,
                targetPosition = exactPosition,
            )
            requestBackReturnFallbackFocus(
                key = key,
                recycler = recycler,
                adapter = adapter,
                exactPosition = exactPosition ?: RecyclerView.NO_POSITION,
                fallbackPosition = fallbackPosition,
                requestToken = requestToken,
                retryCount = BackReturnFallbackRetryCount,
            )
        }
    }

    private fun requestBackReturnFallbackFocus(
        key: String,
        recycler: RecyclerView,
        adapter: HomeVideoCardAdapter,
        exactPosition: Int,
        fallbackPosition: Int,
        requestToken: Int,
        retryCount: Int,
    ): HomeBackReturnRestoreResult {
        if (fallbackPosition !in 0 until adapter.itemCount) {
            return parkBackReturnFallback(
                key = key,
                recycler = recycler,
                exactPosition = exactPosition,
                requestToken = requestToken,
            )
        }

        pendingBackReturnRestore = PendingBackReturnRestore(
            key = key,
            exactPosition = exactPosition,
            fallbackPosition = fallbackPosition,
            requestToken = requestToken,
            acceptRecyclerParkingAsFallback = false,
        )
        if (focusAdapterPosition(recycler, fallbackPosition, adapter.keyAt(fallbackPosition))) {
            return HomeBackReturnRestoreResult.FallbackFocused
        }

        setVisualFocusAnchor(fallbackPosition)
        if (!isPositionVisible(recycler, fallbackPosition) && canPerformPhysicalScroll()) {
            recycler.scrollToPosition(fallbackPosition)
        }
        parkBackReturnFocusIfOutsideRecycler(recycler)
        if (retryCount > 0) {
            lastFocusAttemptPending = true
            scheduleBackReturnFallbackRetry(
                key = key,
                requestToken = requestToken,
                attemptsLeft = retryCount - 1,
            )
            return HomeBackReturnRestoreResult.PendingShort
        }

        return parkBackReturnFallback(
            key = key,
            recycler = recycler,
            exactPosition = exactPosition,
            requestToken = requestToken,
        )
    }

    private fun scheduleBackReturnFallbackRetry(
        key: String,
        requestToken: Int,
        attemptsLeft: Int,
    ) {
        val recycler = currentRecyclerView() ?: return
        recycler.postOnAnimation {
            if (recyclerView !== recycler || !isFocusRequestTokenCurrent(requestToken)) {
                return@postOnAnimation
            }
            val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return@postOnAnimation
            val pending = pendingBackReturnRestore
                ?.takeIf { it.key == key && it.requestToken == requestToken }
                ?: return@postOnAnimation
            val exactPosition = adapter.positionOfKey(key)
                .takeIf { it in 0 until adapter.itemCount }
            if (exactPosition != null && focusAdapterPosition(recycler, exactPosition, key)) {
                pendingBackReturnRestore = null
                return@postOnAnimation
            }
            if (
                pending.fallbackPosition in 0 until adapter.itemCount &&
                focusAdapterPosition(
                    recycler = recycler,
                    position = pending.fallbackPosition,
                    expectedKey = adapter.keyAt(pending.fallbackPosition),
                )
            ) {
                return@postOnAnimation
            }
            if (attemptsLeft > 0) {
                scheduleBackReturnFallbackRetry(
                    key = key,
                    requestToken = requestToken,
                    attemptsLeft = attemptsLeft - 1,
                )
            } else {
                parkBackReturnFallback(
                    key = key,
                    recycler = recycler,
                    exactPosition = pending.exactPosition,
                    requestToken = requestToken,
                )
            }
        }
    }

    private fun parkBackReturnFallback(
        key: String,
        recycler: RecyclerView,
        exactPosition: Int,
        requestToken: Int,
    ): HomeBackReturnRestoreResult {
        pendingBackReturnRestore = PendingBackReturnRestore(
            key = key,
            exactPosition = exactPosition,
            fallbackPosition = RecyclerView.NO_POSITION,
            requestToken = requestToken,
            acceptRecyclerParkingAsFallback = true,
        )
        return if (recycler.requestFocusParking()) {
            clearVisualFocusAnchor()
            onFocusTargetAvailabilityChanged?.invoke()
            HomeBackReturnRestoreResult.FallbackFocused
        } else {
            pendingBackReturnRestore = null
            HomeBackReturnRestoreResult.Unavailable
        }
    }

    private fun focusAdapterPosition(
        recycler: RecyclerView,
        position: Int,
        expectedKey: String?,
    ): Boolean {
        val adapter = recycler.adapter ?: return false
        if (position !in 0 until adapter.itemCount) return false
        val itemView = recycler.findViewHolderForAdapterPosition(position)?.itemView ?: return false
        if (!itemView.isValidFocusTarget() || !itemView.requestFocus()) return false
        clearVisualFocusAnchor()
        if (expectedKey != null) onItemFocused(expectedKey, position)
        return true
    }

    private fun parkBackReturnFocusIfOutsideRecycler(recycler: RecyclerView) {
        val currentFocused = recycler.rootView?.findFocus()
        if (currentFocused == null || !currentFocused.isSameOrDescendantOf(recycler)) {
            recycler.requestFocusParking()
        }
    }

    private fun backReturnFallbackPosition(
        recycler: RecyclerView,
        adapter: HomeVideoCardAdapter,
        targetPosition: Int?,
    ): Int {
        return closestVisibleFocusablePosition(
            recycler = recycler,
            targetPosition = targetPosition ?: lastFocusedPosition,
        )
            ?: rememberedVisibleFallbackPosition(recycler, adapter)
    }

    private fun closestVisibleFocusablePosition(
        recycler: RecyclerView,
        targetPosition: Int,
    ): Int? {
        val layoutManager = recycler.layoutManager as? GridLayoutManager ?: return null
        var closestPosition = RecyclerView.NO_POSITION
        var minDistance = Int.MAX_VALUE
        for (i in 0 until layoutManager.childCount) {
            val child = layoutManager.getChildAt(i) ?: continue
            if (!child.isValidFocusTarget()) continue
            val childPosition = layoutManager.getPosition(child)
            if (childPosition == RecyclerView.NO_POSITION) continue
            val distance = Math.abs(childPosition - targetPosition)
            if (distance < minDistance) {
                minDistance = distance
                closestPosition = childPosition
            }
        }
        return closestPosition.takeIf { it != RecyclerView.NO_POSITION }
    }

    private fun tryFocusPosition(
        recycler: RecyclerView,
        position: Int,
        expectedKey: String?,
        onFocused: (() -> Unit)? = null,
        requestToken: Int = nextFocusRequestToken(),
        retryCount: Int = DefaultFocusRetryCount,
        requireRecyclerFocusOwnership: Boolean = false,
    ): Boolean {
        if (!isFocusRequestTokenCurrent(requestToken)) {
            return false
        }
        if (!recycler.isValidFocusTarget()) {
            return false
        }
        if (requireRecyclerFocusOwnership && !recyclerOwnsPhysicalFocus(recycler)) {
            pendingDataSetFocus = null
            return false
        }
        val adapter = recycler.adapter ?: return false
        if (position !in 0 until adapter.itemCount) {
            return false
        }

        val holder = recycler.findViewHolderForAdapterPosition(position)
        val itemView = holder?.itemView
        if (itemView != null && itemView.isValidFocusTarget()) {
            val focused = itemView.requestFocus()
            GridFocusDebugLog.d {
                "HomeRecommendGridFocusState.tryFocusPosition attached calledRequestFocus=true " +
                    "requestFocusSuccess=$focused requestToken=$requestToken adapterPosition=$position " +
                    "itemKey=$expectedKey attemptsLeft=$retryCount requireRecyclerFocusOwnership=" +
                    "$requireRecyclerFocusOwnership ${debugSnapshot()} ${GridFocusDebugLog.recycler(recycler)}"
            }
            if (focused) {
                clearVisualFocusAnchor()
                if (expectedKey != null) onItemFocused(expectedKey, position)
                onFocused?.invoke()
                return true
            }
        } else {
            GridFocusDebugLog.d {
                "HomeRecommendGridFocusState.tryFocusPosition unattached calledRequestFocus=false " +
                    "requestToken=$requestToken adapterPosition=$position itemKey=$expectedKey " +
                    "attemptsLeft=$retryCount ${debugSnapshot()} ${GridFocusDebugLog.recycler(recycler)}"
            }
        }

        setVisualFocusAnchor(position)
        if (!isPositionVisible(recycler, position) && canPerformPhysicalScroll()) {
            logHomeFocus { "tryFocusPosition: scrollToPosition pos=$position (not visible)" }
            recycler.scrollToPosition(position)
            lastFocusAttemptPending = true
        }

        if (retryCount > 0) {
            lastFocusAttemptPending = true
            recycler.postOnAnimation {
                if (
                    recyclerView === recycler &&
                    isFocusRequestTokenCurrent(requestToken) &&
                    (!requireRecyclerFocusOwnership || recyclerOwnsPhysicalFocus(recycler))
                ) {
                    tryFocusPosition(
                        recycler = recycler,
                        position = position,
                        expectedKey = expectedKey,
                        onFocused = onFocused,
                        requestToken = requestToken,
                        retryCount = retryCount - 1,
                        requireRecyclerFocusOwnership = requireRecyclerFocusOwnership,
                    )
                } else if (requireRecyclerFocusOwnership) {
                    pendingDataSetFocus = null
                }
            }
        } else {
            if (!isPositionVisible(recycler, position) && !canPerformPhysicalScroll()) {
                return false
            }
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
                if (closestView != null) {
                    val isPendingDown = pendingDownSearchPosition != RecyclerView.NO_POSITION
                    val isUpwardFallback = closestPosition < position && (lastFocusedPosition == RecyclerView.NO_POSITION || closestPosition <= lastFocusedPosition)
                    if (isPendingDown || isUpwardFallback) {
                        logHomeFocus { "tryFocusPosition Fallback INHIBITED to prevent upward jump: expected=$position, closest=$closestPosition, lastFocused=$lastFocusedPosition, isPendingDown=$isPendingDown" }
                    } else if (closestView.requestFocus()) {
                        if (expectedKey != null) {
                            val fallbackKey = (recycler.adapter as? HomeVideoCardAdapter)?.keyAt(closestPosition)
                            onItemFocused(fallbackKey ?: expectedKey, closestPosition)
                        }
                        onFocused?.invoke()
                        logHomeFocus { "tryFocusPosition Fallback SUCCESS: expected=$position, actual=$closestPosition" }
                        return true
                    }
                }
            }
            lastFocusAttemptPending = true
            onFocusTargetAvailabilityChanged?.invoke()
        }

        return false
    }

    private fun beginFocusAttempt() {
        lastFocusAttemptPending = false
    }

    private fun Boolean.toRequestResult(): HomeFocusRequestResult {
        return when {
            this -> HomeFocusRequestResult.Focused
            lastFocusAttemptPending -> HomeFocusRequestResult.Pending
            else -> HomeFocusRequestResult.Unavailable
        }
    }

    private fun currentRecyclerView(): RecyclerView? {
        return attachedRecyclerView(recyclerView)
    }

    private fun recyclerOwnsPhysicalFocus(recycler: RecyclerView): Boolean {
        val currentFocused = recycler.rootView?.findFocus() ?: return true
        return currentFocused === recycler || currentFocused.isSameOrDescendantOf(recycler)
    }

    private fun rememberFocusedPosition(
        key: String?,
        position: Int,
        recycler: RecyclerView? = currentRecyclerView(),
    ) {
        lastFocusedKey = key
        lastFocusedPosition = position
        val spanCount = recycler?.gridSpanCount()
        if (position == RecyclerView.NO_POSITION || spanCount == null) {
            lastFocusedRow = RecyclerView.NO_POSITION
            lastFocusedColumn = RecyclerView.NO_POSITION
            return
        }
        lastFocusedRow = position / spanCount
        lastFocusedColumn = position % spanCount
    }

    private fun clearRememberedFocus() {
        lastFocusedKey = null
        lastFocusedPosition = RecyclerView.NO_POSITION
        lastFocusedRow = RecyclerView.NO_POSITION
        lastFocusedColumn = RecyclerView.NO_POSITION
    }

    private fun setVisualFocusAnchor(position: Int) {
        val recycler = currentRecyclerView() ?: return
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return
        val key = adapter.keyAt(position) ?: return
        adapter.setVisualFocusKey(recycler, key)
    }

    private fun clearVisualFocusAnchor() {
        val recycler = currentRecyclerView() ?: return
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return
        adapter.setVisualFocusKey(recycler, null)
    }

    private fun missingKeyFallbackPosition(
        recycler: RecyclerView,
        adapter: HomeVideoCardAdapter,
    ): Int {
        return rememberedRowColumnPosition(recycler, adapter.itemCount)
            ?: nearestVisibleRememberedPosition(recycler, adapter.itemCount)
            ?: 0
    }

    private fun rememberedVisibleFallbackPosition(
        recycler: RecyclerView,
        adapter: HomeVideoCardAdapter,
    ): Int {
        return rememberedRowColumnPosition(recycler, adapter.itemCount)
            ?: nearestVisibleRememberedPosition(recycler, adapter.itemCount)
            ?: firstVisiblePosition(recycler).takeIf { it in 0 until adapter.itemCount }
            ?: 0
    }

    private fun rememberedRowColumnPosition(recycler: RecyclerView, itemCount: Int): Int? {
        val spanCount = recycler.gridSpanCount() ?: return null
        val row = lastFocusedRow.takeIf { it != RecyclerView.NO_POSITION } ?: return null
        val column = lastFocusedColumn.takeIf { it != RecyclerView.NO_POSITION } ?: return null
        val position = row * spanCount + column.coerceAtMost(spanCount - 1)
        return position.takeIf { it in 0 until itemCount }
    }

    private fun nearestVisibleRememberedPosition(recycler: RecyclerView, itemCount: Int): Int? {
        val anchor = lastFocusedPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return null
        val layoutManager = recycler.layoutManager as? GridLayoutManager ?: return null
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return null
        val boundedFirst = first.coerceAtLeast(0)
        val boundedLast = last.coerceAtMost(itemCount - 1)
        if (boundedFirst > boundedLast) return null
        return anchor.coerceIn(boundedFirst, boundedLast)
    }

    private fun RecyclerView.gridSpanCount(): Int? {
        return (layoutManager as? GridLayoutManager)?.spanCount?.takeIf { it > 0 }
    }

    private fun RecyclerView.scrollToTopRespectingPadding() {
        val manager = layoutManager as? GridLayoutManager
        if (manager != null) {
            manager.scrollToPositionWithOffset(0, paddingTop)
        } else {
            scrollToPosition(0)
        }
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
        if (pendingDownSearchPosition == RecyclerView.NO_POSITION) {
            clearVisualFocusAnchor()
        }
    }

    fun schedulePendingDownSearch(targetPosition: Int) {
        val now = SystemClock.uptimeMillis()
        pendingDownSearchPosition = targetPosition
        pendingDownSearchUntilUptimeMs = now + 350L
        setVisualFocusAnchor(targetPosition)
        
        val recycler = currentRecyclerView() ?: return
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.schedulePendingDownSearch targetPosition=$targetPosition " +
                debugSnapshot() + " " + GridFocusDebugLog.recycler(recycler)
        }
        if (targetPosition != RecyclerView.NO_POSITION && targetPosition < (recycler.adapter?.itemCount ?: 0)) {
            logHomeFocus { "schedulePendingDownSearch: pos=$targetPosition, posting scroll." }
            recycler.scrollToPosition(targetPosition)
            schedulePendingDownSearchRetry()
        }
    }

    private fun schedulePendingDownSearchRetry() {
        val recycler = currentRecyclerView() ?: return
        val requestToken = focusRequestToken
        recycler.postOnAnimation {
            if (recyclerView === recycler && focusRequestToken == requestToken) {
                applyPendingDownSearch()
            }
        }
    }

    private fun applyPendingDownSearch(): Boolean {
        val targetPos = pendingDownSearchPosition
        if (targetPos == RecyclerView.NO_POSITION) return false
        if (SystemClock.uptimeMillis() > pendingDownSearchUntilUptimeMs) {
            clearPendingDownSearch()
            return false
        }
        val recycler = currentRecyclerView() ?: return false
        val adapter = recycler.adapter as? HomeVideoCardAdapter ?: return false
        if (targetPos >= adapter.itemCount) return false

        val holder = recycler.findViewHolderForAdapterPosition(targetPos)
        val itemView = holder?.itemView
        if (itemView != null && itemView.isValidFocusTarget()) {
            val focused = itemView.requestFocus()
            GridFocusDebugLog.d {
                "HomeRecommendGridFocusState.applyPendingDownSearch calledRequestFocus=true " +
                    "requestFocusSuccess=$focused adapterPosition=$targetPos " +
                    "itemKey=${adapter.keyAt(targetPos)} ${debugSnapshot()} " +
                    GridFocusDebugLog.recycler(recycler)
            }
            if (focused) {
                logHomeFocus { "applyPendingDownSearch SUCCESS: focused pos=$targetPos" }
                onItemFocused(adapter.keyAt(targetPos) ?: "", targetPos)
                clearPendingDownSearch()
                return true
            }
        }

        if (SystemClock.uptimeMillis() <= pendingDownSearchUntilUptimeMs) {
            schedulePendingDownSearchRetry()
        }
        return false
    }

    fun clearPendingDownSearch() {
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.clearPendingDownSearch before ${debugSnapshot()}"
        }
        pendingDownSearchPosition = RecyclerView.NO_POSITION
        pendingDownSearchUntilUptimeMs = 0L
        if (pendingDirectionalScrollFocusPosition == RecyclerView.NO_POSITION) {
            clearVisualFocusAnchor()
        }
        GridFocusDebugLog.d {
            "HomeRecommendGridFocusState.clearPendingDownSearch after ${debugSnapshot()}"
        }
    }

    internal fun debugSnapshot(): String {
        val pendingDataSet = pendingDataSetFocus
        val pendingBackReturn = pendingBackReturnRestore
        return "lastKnownFocusedPosition=$lastFocusedPosition lastFocusedKey=$lastFocusedKey " +
            "pendingFocusKey=${pendingDataSet?.key ?: pendingBackReturn?.key} " +
            "pendingFocusPosition=${pendingDataSet?.position ?: pendingBackReturn?.exactPosition ?: RecyclerView.NO_POSITION} " +
            "acceptRecyclerParkingAsFallback=${pendingBackReturn?.acceptRecyclerParkingAsFallback ?: false} " +
            "pendingDirectionalPosition=$pendingDirectionalScrollFocusPosition " +
            "pendingDownPosition=$pendingDownSearchPosition requestToken=$focusRequestToken " +
            "dataSetChangePendingCommit=$dataSetChangePendingCommit"
    }

    private companion object {
        private const val UserNavigationFocusWindowMs = 250L
        private const val DirectionalScrollFocusParkingWindowMs = 700L
        private const val DirectionalScrollFocusTargetWindowMs = 1_500L
        private const val DefaultFocusRetryCount = 5
        private const val PendingFocusRetryCount = 20
        private const val BackReturnExactRetryCount = 2
        private const val BackReturnFallbackRetryCount = 1
        private val DirectionalKeyCodes = setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        )
    }
}

private inline fun logHomeFocus(message: () -> String) {
    if (BuildConfig.DEBUG) {
        Log.d("HomeFocus", message())
    }
}

private data class PendingGridFocus(
    val key: String?,
    val position: Int,
    val preferPosition: Boolean = false,
) {
    fun matches(key: String, position: Int): Boolean {
        return this.key == key || (this.key == null && this.position == position)
    }
}

private data class PendingBackReturnRestore(
    val key: String,
    val exactPosition: Int,
    val fallbackPosition: Int,
    val requestToken: Int,
    val acceptRecyclerParkingAsFallback: Boolean,
)
