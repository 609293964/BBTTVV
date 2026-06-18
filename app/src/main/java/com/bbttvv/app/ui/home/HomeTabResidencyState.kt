package com.bbttvv.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bbttvv.app.ui.components.AppTopLevelTab

/**
 * Bounds composed home pages while retaining each tab's ViewModel and saveable UI state.
 */
internal class HomeTabResidencyState(
    initialSelectedTab: AppTopLevelTab,
    private val maxResidentTabs: Int = DEFAULT_MAX_RESIDENT_TABS,
    private val persistentResidentTabs: Set<AppTopLevelTab> = emptySet(),
) {
    init {
        require(maxResidentTabs > 0)
    }

    private val residentTabs = mutableStateListOf(initialSelectedTab)
    private val recency = ArrayDeque<AppTopLevelTab>().apply {
        addLast(initialSelectedTab)
    }

    var selectedTab: AppTopLevelTab by mutableStateOf(initialSelectedTab)
        private set

    var previousSelectedTab: AppTopLevelTab? by mutableStateOf(null)
        private set

    var pinnedTab: AppTopLevelTab? by mutableStateOf(null)
        private set

    var movementDirection: Int by mutableIntStateOf(FORWARD)
        private set

    var generation: Int by mutableIntStateOf(0)
        private set

    private var visibleTabs: List<AppTopLevelTab> = listOf(initialSelectedTab)

    fun updateVisibleTabs(tabs: List<AppTopLevelTab>) {
        val nextVisibleTabs = tabs.distinct()
        if (visibleTabs == nextVisibleTabs) return
        visibleTabs = nextVisibleTabs
        residentTabs.removeAll { it !in nextVisibleTabs }
        recency.removeAll { it !in nextVisibleTabs }
        previousSelectedTab = previousSelectedTab?.takeIf { it in nextVisibleTabs }
        pinnedTab = pinnedTab?.takeIf { it in nextVisibleTabs }
        if (selectedTab !in nextVisibleTabs) {
            nextVisibleTabs.firstOrNull()?.let(::select)
        } else {
            ensureResident(selectedTab)
            ensurePersistentResidents()
            trimToLimit()
            advanceGeneration()
        }
    }

    fun select(tab: AppTopLevelTab) {
        if (tab !in visibleTabs && visibleTabs.isNotEmpty()) return
        val selectionChanged = tab != selectedTab
        if (selectionChanged) {
            val oldIndex = visibleTabs.indexOf(selectedTab)
            val newIndex = visibleTabs.indexOf(tab)
            if (oldIndex >= 0 && newIndex >= 0 && oldIndex != newIndex) {
                movementDirection = if (newIndex > oldIndex) FORWARD else BACKWARD
            }
            previousSelectedTab = selectedTab.takeIf { it in visibleTabs }
            selectedTab = tab
        }
        ensureResident(previousSelectedTab)
        ensureResident(tab)
        ensureResident(pinnedTab)
        ensurePersistentResidents()
        if (selectionChanged) {
            val retainedTabs = buildSet {
                add(selectedTab)
                previousSelectedTab?.let(::add)
                pinnedTab?.let(::add)
                persistentResidentTabs.filterTo(this) { it in visibleTabs }
            }
            residentTabs.removeAll { it !in retainedTabs }
            recency.removeAll { it !in retainedTabs }
        }
        touch(tab)
        trimToLimit()
        advanceGeneration()
    }

    fun updatePinnedTab(tab: AppTopLevelTab?) {
        val legalTab = tab?.takeIf { it in visibleTabs }
        if (pinnedTab == legalTab) return
        val previouslyPinnedTab = pinnedTab
        pinnedTab = legalTab
        ensureResident(legalTab)
        if (
            legalTab == null &&
            previouslyPinnedTab != null &&
            previouslyPinnedTab != selectedTab &&
            previouslyPinnedTab != previousSelectedTab
        ) {
            residentTabs.remove(previouslyPinnedTab)
            recency.remove(previouslyPinnedTab)
        }
        trimToLimit()
        advanceGeneration()
    }

    fun residentTabsInDisplayOrder(): List<AppTopLevelTab> {
        return visibleTabs.filter { it in residentTabs || it == selectedTab }
    }

    fun prewarmNextAdjacent(): AppTopLevelTab? {
        if (residentTabs.size >= maxResidentTabs || visibleTabs.isEmpty()) return null
        val selectedIndex = visibleTabs.indexOf(selectedTab)
        if (selectedIndex < 0) return null

        val candidates = buildList {
            add(selectedIndex + movementDirection)
            add(selectedIndex - movementDirection)
        }
        val candidate = candidates
            .asSequence()
            .mapNotNull(visibleTabs::getOrNull)
            .firstOrNull { it !in residentTabs }
            ?: return null

        ensureResident(candidate)
        trimToLimit()
        advanceGeneration()
        return candidate
    }

    fun trimToSelected() {
        val changed = residentTabs.size != 1 || residentTabs.firstOrNull() != selectedTab
        residentTabs.clear()
        residentTabs += selectedTab
        recency.clear()
        recency += selectedTab
        previousSelectedTab = null
        pinnedTab = null
        if (changed) {
            advanceGeneration()
        }
    }

    private fun ensureResident(tab: AppTopLevelTab?) {
        if (tab == null || tab !in visibleTabs || tab in residentTabs) return
        residentTabs += tab
        touch(tab)
    }

    private fun ensurePersistentResidents() {
        persistentResidentTabs.forEach(::ensureResident)
    }

    private fun touch(tab: AppTopLevelTab) {
        recency.remove(tab)
        recency.addLast(tab)
    }

    private fun trimToLimit() {
        val protectedTabs = buildSet {
            add(selectedTab)
            previousSelectedTab?.let(::add)
            pinnedTab?.let(::add)
            persistentResidentTabs.filterTo(this) { it in visibleTabs }
        }
        while (residentTabs.size > maxResidentTabs) {
            val eviction = recency.firstOrNull { it !in protectedTabs }
                ?: recency.firstOrNull { it != selectedTab && it != pinnedTab }
                ?: break
            recency.remove(eviction)
            residentTabs.remove(eviction)
        }
    }

    private fun advanceGeneration() {
        generation += 1
    }

    private companion object {
        const val FORWARD = 1
        const val BACKWARD = -1
        const val DEFAULT_MAX_RESIDENT_TABS = 3
    }
}
