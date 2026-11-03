package com.bbttvv.app.ui.home

import com.bbttvv.app.core.store.SettingsManager.ThemeMode
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.AppTopLevelTab.*
import org.junit.Assert.*
import org.junit.Test

class HomeSidebarPolicyTest {
    @Test fun `sidebar reserves the top header only for video while original themes retain navigation`() {
        listOf(RECOMMEND, POPULAR).forEach {
            assertTrue(HomeSidebarPolicy.hasTopHeader(true, it))
        }
        listOf(SEARCH, TODAY_WATCH, LIVE, DYNAMIC, WATCH_LATER, PROFILE).forEach {
            assertFalse(HomeSidebarPolicy.hasTopHeader(true, it))
            assertTrue(HomeSidebarPolicy.hasTopHeader(false, it))
        }
    }
    @Test fun `grouping preserves module order and original content identities`() {
        val visible = AppTopLevelTab.resolveVisibleTabs(true, true)
        assertEquals(listOf(SEARCH, RECOMMEND, TODAY_WATCH, LIVE, DYNAMIC, WATCH_LATER, PROFILE), HomeSidebarPolicy.sidebarTabs(visible))
        assertEquals(listOf(RECOMMEND, POPULAR), HomeSidebarPolicy.columns(visible, POPULAR))
        assertEquals(TODAY_WATCH, HomeSidebarPolicy.selectedModule(TODAY_WATCH))
        assertEquals(listOf(LIVE), HomeSidebarPolicy.columns(visible, LIVE))
    }

    @Test fun `custom navigation flags independently control sidebar entries without duplicates`() {
        listOf(false, true).forEach { todayWatchEnabled ->
            listOf(false, true).forEach { watchLaterEnabled ->
                val visible = AppTopLevelTab.resolveVisibleTabs(todayWatchEnabled, watchLaterEnabled)
                val sidebar = HomeSidebarPolicy.sidebarTabs(visible)
                assertEquals(todayWatchEnabled, TODAY_WATCH in sidebar)
                assertEquals(watchLaterEnabled, WATCH_LATER in sidebar)
                assertEquals(sidebar.size, sidebar.map { it.stableId }.toSet().size)
                assertEquals(listOf(RECOMMEND, POPULAR), HomeSidebarPolicy.columns(visible, RECOMMEND))
                sidebar.forEach { assertEquals(it, HomeSidebarPolicy.selectedModule(it)) }
                assertEquals(visible.filterNot { it == POPULAR }, sidebar)
            }
        }
    }

    @Test fun `video entry cannot restore an independent or disabled module from older theme state`() {
        val visible = AppTopLevelTab.resolveVisibleTabs(true, true)
        assertEquals(POPULAR, HomeSidebarPolicy.videoEntry(visible, POPULAR.name))
        listOf(TODAY_WATCH.name, WATCH_LATER.name, "unknown").forEach {
            assertEquals(RECOMMEND, HomeSidebarPolicy.videoEntry(visible, it))
        }
        val disabled = AppTopLevelTab.resolveVisibleTabs(false, false)
        assertEquals(RECOMMEND, HomeSidebarPolicy.videoEntry(disabled, TODAY_WATCH.name))
    }

    @Test fun `hidden modules are not reintroduced by sidebar grouping`() {
        val visible = AppTopLevelTab.resolveVisibleTabs(false, false)
        assertFalse(WATCH_LATER in HomeSidebarPolicy.sidebarTabs(visible))
        assertEquals(listOf(RECOMMEND, POPULAR), HomeSidebarPolicy.columns(visible, RECOMMEND))
    }

    @Test fun `theme storage remains backward compatible and selection cycles through all modes`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromValue(null))
        assertEquals(ThemeMode.DARK, ThemeMode.fromValue("unknown"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromValue("light"))
        assertEquals(ThemeMode.LIGHT_SIDEBAR, ThemeMode.fromValue("light_sidebar"))
        assertEquals(ThemeMode.LIGHT_SIDEBAR, ThemeMode.DARK.next().next())
        assertEquals(ThemeMode.DARK, ThemeMode.LIGHT_SIDEBAR.next())
    }
}
