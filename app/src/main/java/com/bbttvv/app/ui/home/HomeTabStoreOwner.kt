package com.bbttvv.app.ui.home

import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import com.bbttvv.app.ui.components.AppTopLevelTab

internal const val PLAYBACK_RETURN_TAB_STORE_IDLE_TTL_MS = 30_000L

internal sealed interface TabStorePolicy {
    data object KeepAll : TabStorePolicy
    data object KeepRecentTwo : TabStorePolicy
    data object KeepSelectedOnly : TabStorePolicy
    data class KeepSelectedOnlyAfterIdle(
        val idleTtlMs: Long = PLAYBACK_RETURN_TAB_STORE_IDLE_TTL_MS
    ) : TabStorePolicy
}

/** Owns per-tab ViewModelStores so HomeViewModel does not manage their lifecycle details. */
internal class HomeTabStoreOwner(
    private val policy: TabStorePolicy = TabStorePolicy.KeepSelectedOnly,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime
) {
    private val stores = mutableMapOf<AppTopLevelTab, ViewModelStore>()
    private val recentTabs = ArrayDeque<AppTopLevelTab>()
    private var selectedTab: AppTopLevelTab? = null
    private var protectedPreviousTab: AppTopLevelTab? = null
    private var protectedPreviousTabUntilMs: Long = 0L

    fun getOrCreate(tab: AppTopLevelTab): ViewModelStore {
        markSelected(tab)
        return stores.getOrPut(tab) { ViewModelStore() }
    }

    fun trimForSelected(selectedTab: AppTopLevelTab): Long? {
        markSelected(selectedTab)
        val nowMs = elapsedRealtimeMs()
        val retainedTabs = when (policy) {
            TabStorePolicy.KeepAll -> stores.keys
            TabStorePolicy.KeepRecentTwo -> recentTabs.takeLast(2).toSet()
            TabStorePolicy.KeepSelectedOnly -> setOf(selectedTab)
            is TabStorePolicy.KeepSelectedOnlyAfterIdle -> {
                retainedTabsForIdlePolicy(
                    selectedTab = selectedTab,
                    nowMs = nowMs,
                    idleTtlMs = policy.idleTtlMs
                )
            }
        }
        val iterator = stores.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in retainedTabs) {
                entry.value.clear()
                iterator.remove()
            }
        }
        recentTabs.removeAll { tab -> tab !in retainedTabs }
        return nextIdleTrimDelayMs(selectedTab = selectedTab, nowMs = nowMs)
    }

    fun clearAll() {
        stores.values.forEach { it.clear() }
        stores.clear()
        recentTabs.clear()
        protectedPreviousTab = null
        protectedPreviousTabUntilMs = 0L
        selectedTab = null
    }

    internal fun hasStore(tab: AppTopLevelTab): Boolean {
        return stores.containsKey(tab)
    }

    internal fun storeCount(): Int {
        return stores.size
    }

    private fun markSelected(tab: AppTopLevelTab) {
        val previousSelected = selectedTab
        if (
            policy is TabStorePolicy.KeepSelectedOnlyAfterIdle &&
            previousSelected != null &&
            previousSelected != tab
        ) {
            protectedPreviousTab = previousSelected
            protectedPreviousTabUntilMs = elapsedRealtimeMs() + policy.idleTtlMs
        }
        selectedTab = tab
        recentTabs.remove(tab)
        recentTabs.addLast(tab)
    }

    private fun retainedTabsForIdlePolicy(
        selectedTab: AppTopLevelTab,
        nowMs: Long,
        idleTtlMs: Long
    ): Set<AppTopLevelTab> {
        val previousTab = protectedPreviousTab
        if (
            previousTab == null ||
            previousTab == selectedTab ||
            nowMs >= protectedPreviousTabUntilMs ||
            idleTtlMs <= 0L
        ) {
            protectedPreviousTab = null
            protectedPreviousTabUntilMs = 0L
            return setOf(selectedTab)
        }
        return setOf(selectedTab, previousTab)
    }

    private fun nextIdleTrimDelayMs(
        selectedTab: AppTopLevelTab,
        nowMs: Long
    ): Long? {
        if (policy !is TabStorePolicy.KeepSelectedOnlyAfterIdle) return null
        val previousTab = protectedPreviousTab ?: return null
        if (previousTab == selectedTab) return null
        val remainingMs = protectedPreviousTabUntilMs - nowMs
        return remainingMs.takeIf { it > 0L }
    }
}
