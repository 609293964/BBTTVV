package com.bbttvv.app.ui.home

import androidx.lifecycle.ViewModelStore
import com.bbttvv.app.ui.components.AppTopLevelTab

internal enum class TabStorePolicy {
    KeepAll,
    KeepRecentTwo,
    KeepSelectedOnly
}

/** Owns per-tab ViewModelStores so HomeViewModel does not manage their lifecycle details. */
internal class HomeTabStoreOwner(
    private val policy: TabStorePolicy = TabStorePolicy.KeepSelectedOnly
) {
    private val stores = mutableMapOf<AppTopLevelTab, ViewModelStore>()
    private val recentTabs = ArrayDeque<AppTopLevelTab>()

    fun getOrCreate(tab: AppTopLevelTab): ViewModelStore {
        markSelected(tab)
        return stores.getOrPut(tab) { ViewModelStore() }
    }

    fun trimForSelected(selectedTab: AppTopLevelTab) {
        markSelected(selectedTab)
        val retainedTabs = when (policy) {
            TabStorePolicy.KeepAll -> stores.keys
            TabStorePolicy.KeepRecentTwo -> recentTabs.takeLast(2).toSet()
            TabStorePolicy.KeepSelectedOnly -> setOf(selectedTab)
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
    }

    fun clearAll() {
        stores.values.forEach { it.clear() }
        stores.clear()
        recentTabs.clear()
    }

    private fun markSelected(tab: AppTopLevelTab) {
        recentTabs.remove(tab)
        recentTabs.addLast(tab)
    }
}
