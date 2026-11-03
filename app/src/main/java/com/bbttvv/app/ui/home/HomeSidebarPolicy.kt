package com.bbttvv.app.ui.home

import com.bbttvv.app.ui.components.AppTopLevelTab

/** Presentation grouping only; tab identities, data ownership and restore keys remain unchanged. */
internal object HomeSidebarPolicy {
    private val videoTabs = setOf(AppTopLevelTab.RECOMMEND, AppTopLevelTab.POPULAR)

    fun isVideo(tab: AppTopLevelTab): Boolean = tab in videoTabs

    fun hasTopHeader(sidebarTheme: Boolean, tab: AppTopLevelTab): Boolean =
        !sidebarTheme || isVideo(tab)

    fun selectedModule(tab: AppTopLevelTab): AppTopLevelTab =
        if (isVideo(tab)) AppTopLevelTab.RECOMMEND else tab

    fun sidebarTabs(visibleTabs: List<AppTopLevelTab>): List<AppTopLevelTab> =
        visibleTabs.map(::selectedModule).distinct()

    fun columns(visibleTabs: List<AppTopLevelTab>, selected: AppTopLevelTab): List<AppTopLevelTab> =
        if (isVideo(selected)) visibleTabs.filter(::isVideo) else listOf(selected)

    fun videoEntry(visibleTabs: List<AppTopLevelTab>, rememberedTabName: String): AppTopLevelTab =
        visibleTabs.firstOrNull { it.name == rememberedTabName && isVideo(it) }
            ?: AppTopLevelTab.RECOMMEND
}
