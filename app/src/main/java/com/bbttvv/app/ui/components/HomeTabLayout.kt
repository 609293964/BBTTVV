package com.bbttvv.app.ui.components

internal data class HomeTabLayout(
    val order: List<AppTopLevelTab>,
    val visible: Set<AppTopLevelTab>,
) {
    // Positive IDs are visible, negative IDs are hidden. Both retain their position.
    fun encode(): String = order.joinToString(",", prefix = PREFIX) { tab ->
        (if (tab in visible) tab.stableId else -tab.stableId).toString()
    }

    companion object {
        private const val PREFIX = "v2:"
        // The original name-only format omitted hidden tabs. Freeze its catalog for migration.
        private val legacyIds = mapOf(
            "RECOMMEND" to 1L, "SEARCH" to 2L, "TODAY_WATCH" to 3L, "POPULAR" to 4L,
            "LIVE" to 5L, "DYNAMIC" to 6L, "WATCH_LATER" to 7L, "PROFILE" to 8L,
        )

        fun restore(
            savedLayout: String?,
            watchLaterInTopTabsEnabled: Boolean,
            catalog: List<AppTopLevelTab> = AppTopLevelTab.all,
        ): HomeTabLayout {
            val byId = catalog.associateBy { it.stableId }
            val entries = linkedMapOf<AppTopLevelTab, Boolean>()
            val legacy = savedLayout != null && !savedLayout.startsWith(PREFIX)
            if (savedLayout != null) {
                for (token in savedLayout.removePrefix(PREFIX).split(',')) {
                    val id = if (legacy) legacyIds[token.trim()] else token.trim().toLongOrNull()
                    if (id == null) continue
                    val tab = byId[kotlin.math.abs(id)] ?: continue
                    entries.putIfAbsent(tab, tab.alwaysVisible || id > 0)
                }
            }
            for (tab in catalog) {
                val showByDefault = tab != AppTopLevelTab.WATCH_LATER || watchLaterInTopTabsEnabled
                val hiddenByLegacyLayout = legacy && tab.stableId in legacyIds.values
                entries.putIfAbsent(tab, tab.alwaysVisible || (showByDefault && !hiddenByLegacyLayout))
            }
            return HomeTabLayout(entries.keys.toList(), entries.filterValues { it }.keys)
        }
    }
}
