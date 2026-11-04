package com.bbttvv.app.ui.components

import com.bbttvv.app.ui.components.AppTopLevelTab.*
import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicHomeTabLayoutTest {
    @Test
    fun `editing while plugin is disabled keeps its position and hidden flag through restart`() {
        val original = HomeTabLayout(
            listOf(DYNAMIC, TODAY_WATCH, PROFILE, POPULAR, SEARCH, LIVE, RECOMMEND, WATCH_LATER),
            setOf(DYNAMIC, TODAY_WATCH, PROFILE, POPULAR, SEARCH, RECOMMEND),
        )
        val disabled = AppTopLevelTab.resolveVisibleTabs(false, savedLayout = original.encode())
        assertEquals(original.order.filter { it in original.visible && it != TODAY_WATCH }, disabled)
        // The editor receives the complete catalog, including temporarily unavailable tabs.
        val draft = HomeTabLayout.restore(original.encode(), false)
        val edited = draft.copy(order = draft.order.reversed(), visible = draft.visible - SEARCH)
        val restarted = HomeTabLayout.restore(edited.encode(), false)
        assertEquals(edited, restarted)
        assertEquals(restarted.order.filter { it in restarted.visible },
            AppTopLevelTab.resolveVisibleTabs(true, savedLayout = restarted.encode()))

        val hidden = restarted.copy(visible = restarted.visible - TODAY_WATCH)
        for (pluginEnabled in listOf(false, true, false, true)) {
            assertEquals(false, TODAY_WATCH in
                AppTopLevelTab.resolveVisibleTabs(pluginEnabled, savedLayout = hidden.encode()))
        }
        val shown = hidden.copy(visible = hidden.visible + TODAY_WATCH)
        assertEquals(shown.order.indexOf(TODAY_WATCH),
            HomeTabLayout.restore(shown.encode(), false).order.indexOf(TODAY_WATCH))
        assertEquals(shown.order.filter { it in shown.visible },
            AppTopLevelTab.resolveVisibleTabs(true, savedLayout = shown.encode()))
    }
}
