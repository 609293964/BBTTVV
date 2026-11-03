package com.bbttvv.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppTopBarEntriesTest {
    private val categories = listOf(
        AppTopBarCategory("热门", 0),
        AppTopBarCategory("动画", 1005),
        AppTopBarCategory("音乐", 1003),
    )

    @Test fun `unified header places categories after recommend without repeating popular`() {
        val entries = homeHeaderEntries(listOf(AppTopLevelTab.RECOMMEND, AppTopLevelTab.POPULAR), categories)
        assertEquals(listOf("推荐", "热门", "动画", "音乐"), entries.map { it.label })
        assertEquals(listOf(null, 0, 1, 2), entries.map { it.categoryIndex })
        assertEquals(entries.size, entries.map { it.stableId }.toSet().size)
        assertEquals(AppTopLevelTab.POPULAR.stableId, entries[1].stableId)
    }

    @Test fun `category identity survives reordering while selection index follows presentation`() {
        val original = homeHeaderEntries(listOf(AppTopLevelTab.POPULAR), categories)
        val reordered = homeHeaderEntries(listOf(AppTopLevelTab.POPULAR), categories.reversed())
        original.forEach { entry ->
            assertEquals(entry.stableId, reordered.first { it.label == entry.label }.stableId)
        }
        assertEquals(0, reordered.first { it.label == "音乐" }.categoryIndex)
    }

    @Test fun `original themes retain their top level tabs and no category selection`() {
        val tabs = AppTopLevelTab.resolveVisibleTabs(true, true)
        val entries = homeHeaderEntries(tabs, emptyList())
        assertEquals(tabs.map { it.stableId }, entries.map { it.stableId })
        entries.forEach { assertNull(it.categoryIndex) }
    }
}
