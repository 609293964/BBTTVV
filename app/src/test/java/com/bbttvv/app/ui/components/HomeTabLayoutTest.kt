package com.bbttvv.app.ui.components

import com.bbttvv.app.ui.components.AppTopLevelTab.*
import com.bbttvv.app.ui.home.HomeSidebarPolicy
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTabLayoutTest {
    @Test
    fun `all optional visibility combinations survive shuffled orders and plugin changes`() {
        val random = Random(20261103)
        val optional = AppTopLevelTab.all.filterNot { it.alwaysVisible }
        for (mask in 0 until (1 shl optional.size)) {
            val visible = optional.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet() +
                setOf(RECOMMEND, PROFILE)
            repeat(16) {
                val order = AppTopLevelTab.all.shuffled(random)
                val layout = HomeTabLayout(order, visible)
                val saved = layout.encode()
                assertEquals(layout, HomeTabLayout.restore(saved, watchLaterInTopTabsEnabled = false))
                for (pluginEnabled in listOf(false, true)) {
                    val expected = order.filter { it in visible && (it != TODAY_WATCH || pluginEnabled) }
                    val actual = AppTopLevelTab.resolveVisibleTabs(pluginEnabled, savedLayout = saved)
                    assertEquals(expected, actual)
                    val sidebar = expected.map { if (it == POPULAR) RECOMMEND else it }.distinct()
                    assertEquals(sidebar, HomeSidebarPolicy.sidebarTabs(actual))
                    assertEquals(expected.filter { it == RECOMMEND || it == POPULAR },
                        HomeSidebarPolicy.columns(actual, RECOMMEND))
                    for (tab in order) {
                        assertEquals(if (tab in expected) tab else RECOMMEND,
                            AppTopLevelTab.resolveVisibleHomeTab(tab.index, actual))
                    }
                }
            }
        }
    }

    @Test
    fun `catalog upgrade drops retired entries and appends new tabs without moving hidden ones`() {
        val old = HomeTabLayout(listOf(PROFILE, LIVE, POPULAR, SEARCH, RECOMMEND),
            setOf(POPULAR, PROFILE, RECOMMEND))
        val catalog = listOf(WATCH_LATER, LIVE, DYNAMIC, RECOMMEND, PROFILE, SEARCH)
        val migrated = HomeTabLayout.restore(old.encode(), false, catalog)
        assertEquals(listOf(PROFILE, LIVE, SEARCH, RECOMMEND, WATCH_LATER, DYNAMIC), migrated.order)
        assertEquals(setOf(PROFILE, RECOMMEND, DYNAMIC), migrated.visible)
        assertEquals(migrated, HomeTabLayout.restore(migrated.encode(), false, catalog))
    }

    @Test
    fun `duplicates removed ids malformed tokens and hidden mandatory tabs normalize on save`() {
        val layout = HomeTabLayout.restore("v2:-8,6,-6,900,-1,junk,9999999999999999999999,-2", false)
        assertEquals(listOf(PROFILE, DYNAMIC, RECOMMEND, SEARCH), layout.order.take(4))
        assertEquals(AppTopLevelTab.all.size, layout.order.size)
        assertEquals(false, SEARCH in layout.visible)
        assertEquals(true, PROFILE in layout.visible && RECOMMEND in layout.visible)
        assertEquals(layout, HomeTabLayout.restore(layout.encode(), false))
    }

    @Test
    fun `saved navigation indexes still address the original tab after catalog reordering`() {
        val old = listOf(RECOMMEND, SEARCH, TODAY_WATCH, POPULAR, LIVE, DYNAMIC, WATCH_LATER, PROFILE)
        old.forEachIndexed { index, tab -> assertEquals(tab, AppTopLevelTab.fromIndex(index)) }
        assertEquals(null, AppTopLevelTab.fromIndex(999))
    }
}
