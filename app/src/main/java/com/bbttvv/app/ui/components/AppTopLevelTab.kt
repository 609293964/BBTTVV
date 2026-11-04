package com.bbttvv.app.ui.components

enum class AppTopLevelTab(
    val stableId: Long,
    val title: String,
    val isHomeContent: Boolean
) {
    // Declaration order is the default display order. Persisted IDs must never be reused.
    SEARCH(2L, "搜索", isHomeContent = true),
    RECOMMEND(1L, "推荐", isHomeContent = true),
    TODAY_WATCH(3L, "推荐单", isHomeContent = true),
    POPULAR(4L, "热门", isHomeContent = true),
    LIVE(5L, "直播", isHomeContent = true),
    DYNAMIC(6L, "动态", isHomeContent = true),
    WATCH_LATER(7L, "稍后再看", isHomeContent = true),
    PROFILE(8L, "我的", isHomeContent = true);

    val alwaysVisible: Boolean
        get() = this == RECOMMEND || this == PROFILE

    val index: Int
        get() = stableId.toInt() - 1 // Compatible with previously saved navigation indices.

    companion object {
        val all: List<AppTopLevelTab> = values().toList()

        fun fromIndex(index: Int): AppTopLevelTab? = all.firstOrNull { it.index == index }

        fun homeContentFromIndex(index: Int): AppTopLevelTab {
            return fromIndex(index)?.takeIf { it.isHomeContent } ?: RECOMMEND
        }

        fun resolveVisibleTabs(
            todayWatchEnabled: Boolean,
            watchLaterInTopTabsEnabled: Boolean = true,
            savedLayout: String? = null,
        ): List<AppTopLevelTab> {
            val layout = HomeTabLayout.restore(savedLayout, watchLaterInTopTabsEnabled)
            return layout.order.filter { tab ->
                tab in layout.visible && (tab != TODAY_WATCH || todayWatchEnabled)
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
