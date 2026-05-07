package com.bbttvv.app.ui.home

import com.bbttvv.app.ui.components.AppTopLevelTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTabGridFocusStatesTest {
    @Test
    fun `same tab reuses the same focus state`() {
        val states = HomeTabGridFocusStates()

        val first = states.stateFor(AppTopLevelTab.POPULAR)
        val second = states.stateFor(AppTopLevelTab.POPULAR)

        assertSame(first, second)
    }

    @Test
    fun `different tabs get isolated focus states`() {
        val states = HomeTabGridFocusStates()

        val popular = states.stateFor(AppTopLevelTab.POPULAR)
        val live = states.stateFor(AppTopLevelTab.LIVE)

        assertNotSame(popular, live)
    }

    @Test
    fun `hidden tabs are pruned while visible tabs are retained`() {
        val states = HomeTabGridFocusStates()
        val popular = states.stateFor(AppTopLevelTab.POPULAR)
        states.stateFor(AppTopLevelTab.DYNAMIC)

        states.retainVisibleTabs(setOf(AppTopLevelTab.POPULAR, AppTopLevelTab.RECOMMEND))

        assertTrue(states.contains(AppTopLevelTab.POPULAR))
        assertFalse(states.contains(AppTopLevelTab.DYNAMIC))
        assertSame(popular, states.stateFor(AppTopLevelTab.POPULAR))
    }
}
