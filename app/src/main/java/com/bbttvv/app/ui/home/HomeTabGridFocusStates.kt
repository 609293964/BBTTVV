package com.bbttvv.app.ui.home

import com.bbttvv.app.ui.components.AppTopLevelTab

internal class HomeTabGridFocusStates {
    private val states = LinkedHashMap<AppTopLevelTab, HomeRecommendGridFocusState>()

    fun stateFor(tab: AppTopLevelTab): HomeRecommendGridFocusState {
        return states.getOrPut(tab) { HomeRecommendGridFocusState() }
    }

    fun retainVisibleTabs(visibleTabs: Set<AppTopLevelTab>) {
        val iterator = states.keys.iterator()
        while (iterator.hasNext()) {
            if (iterator.next() !in visibleTabs) {
                iterator.remove()
            }
        }
    }

    fun contains(tab: AppTopLevelTab): Boolean {
        return tab in states
    }
}
