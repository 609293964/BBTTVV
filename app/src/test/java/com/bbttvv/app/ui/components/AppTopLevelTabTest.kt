package com.bbttvv.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppTopLevelTabTest {

    @Test
    fun `custom layout retains user order and gates plugin tab`() {
        val layout = "PROFILE,TODAY_WATCH,SEARCH,SEARCH,obsolete,RECOMMEND"
        val expected = listOf(AppTopLevelTab.PROFILE, AppTopLevelTab.SEARCH, AppTopLevelTab.RECOMMEND)
        assertEquals(expected, AppTopLevelTab.resolveVisibleTabs(false, savedLayout = layout))
        assertEquals(AppTopLevelTab.TODAY_WATCH, AppTopLevelTab.resolveVisibleTabs(true, savedLayout = layout)[1])
    }

    @Test
    fun `invalid layout preserves home and settings access`() {
        assertEquals(
            listOf(AppTopLevelTab.RECOMMEND, AppTopLevelTab.PROFILE),
            AppTopLevelTab.resolveVisibleTabs(false, savedLayout = ""),
        )
    }
    @Test
    fun `visible tabs insert today watch between recommend and popular when enabled`() {
        val tabs = AppTopLevelTab.resolveVisibleTabs(todayWatchEnabled = true)

        assertEquals(
            listOf(
                AppTopLevelTab.SEARCH,
                AppTopLevelTab.RECOMMEND,
                AppTopLevelTab.TODAY_WATCH,
                AppTopLevelTab.POPULAR,
                AppTopLevelTab.LIVE,
                AppTopLevelTab.DYNAMIC,
                AppTopLevelTab.WATCH_LATER,
                AppTopLevelTab.PROFILE
            ),
            tabs
        )
    }

    @Test
    fun `resolve visible home tab falls back to recommend when today watch hidden`() {
        val tabs = AppTopLevelTab.resolveVisibleTabs(todayWatchEnabled = false)

        assertEquals(
            AppTopLevelTab.RECOMMEND,
            AppTopLevelTab.resolveVisibleHomeTab(
                index = AppTopLevelTab.TODAY_WATCH.index,
                visibleTabs = tabs
            )
        )
    }

    @Test
    fun `watch later can be removed without changing the remaining tab order`() {
        val tabs = AppTopLevelTab.resolveVisibleTabs(
            todayWatchEnabled = true,
            watchLaterInTopTabsEnabled = false,
        )

        assertFalse(AppTopLevelTab.WATCH_LATER in tabs)
        assertEquals(
            listOf(
                AppTopLevelTab.SEARCH,
                AppTopLevelTab.RECOMMEND,
                AppTopLevelTab.TODAY_WATCH,
                AppTopLevelTab.POPULAR,
                AppTopLevelTab.LIVE,
                AppTopLevelTab.DYNAMIC,
                AppTopLevelTab.PROFILE,
            ),
            tabs,
        )
    }

    @Test
    fun `stable ids are unique`() {
        assertEquals(
            AppTopLevelTab.all.size,
            AppTopLevelTab.all.map(AppTopLevelTab::stableId).toSet().size,
        )
    }
}
