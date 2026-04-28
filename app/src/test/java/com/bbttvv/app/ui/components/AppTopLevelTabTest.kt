package com.bbttvv.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AppTopLevelTabTest {

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
}
