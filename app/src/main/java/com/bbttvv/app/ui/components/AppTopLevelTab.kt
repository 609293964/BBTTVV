package com.bbttvv.app.ui.components

enum class AppTopLevelTab(
    val title: String,
    val isHomeContent: Boolean
) {
    RECOMMEND("推荐", isHomeContent = true),
    SEARCH("搜索", isHomeContent = true),
    TODAY_WATCH("推荐单", isHomeContent = true),
    POPULAR("热门", isHomeContent = true),
    LIVE("直播", isHomeContent = true),
    DYNAMIC("动态", isHomeContent = true),
    WATCH_LATER("稍后再看", isHomeContent = true),
    PROFILE("我的", isHomeContent = true);

    val index: Int
        get() = ordinal

    companion object {
        val all: List<AppTopLevelTab> = values().toList()

        private val defaultVisibleTabs = listOf(
            SEARCH,
            RECOMMEND,
            POPULAR,
            LIVE,
            DYNAMIC,
            WATCH_LATER,
            PROFILE
        )

        private val todayWatchVisibleTabs = listOf(
            SEARCH,
            RECOMMEND,
            TODAY_WATCH,
            POPULAR,
            LIVE,
            DYNAMIC,
            WATCH_LATER,
            PROFILE
        )

        fun fromIndex(index: Int): AppTopLevelTab? = all.getOrNull(index)

        fun homeContentFromIndex(index: Int): AppTopLevelTab {
            return fromIndex(index)?.takeIf { it.isHomeContent } ?: RECOMMEND
        }

        fun resolveVisibleTabs(
            todayWatchEnabled: Boolean,
            watchLaterInTopTabsEnabled: Boolean = true,
        ): List<AppTopLevelTab> {
            val tabs = if (todayWatchEnabled) todayWatchVisibleTabs else defaultVisibleTabs
            return if (watchLaterInTopTabsEnabled) {
                tabs
            } else {
                tabs.filterNot { tab -> tab == WATCH_LATER }
            }
        }

        fun resolveVisibleHomeTab(
            index: Int,
            visibleTabs: List<AppTopLevelTab>
        ): AppTopLevelTab {
            val fallback = RECOMMEND.takeIf { it in visibleTabs }
                ?: visibleTabs.firstOrNull { it.isHomeContent }
                ?: RECOMMEND
            return fromIndex(index)
                ?.takeIf { it.isHomeContent && it in visibleTabs }
                ?: fallback
        }
    }
}
