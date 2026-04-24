package com.bbttvv.app.ui.home

import com.bbttvv.app.ui.components.AppTopLevelTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFocusCoordinatorTest {
    @Test
    fun `pending top bar focus is completed only after target can really focus`() {
        val coordinator = HomeFocusCoordinator()
        val topBarTarget = FakeFocusTarget(canFocus = false)

        assertFalse(coordinator.drainPendingFocus())
        coordinator.registerTopBarTarget(topBarTarget)
        assertEquals(1, topBarTarget.focusRequests)

        topBarTarget.canFocus = true

        assertTrue(coordinator.drainPendingFocus())
        assertEquals(2, topBarTarget.focusRequests)
        assertFalse(coordinator.isContentFocused)
        assertTrue(coordinator.isTopBarVisible)
    }

    @Test
    fun `selected content focus prefers tabs until grid has remembered focus`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.POPULAR)
        val tabsTarget = FakeFocusTarget(canFocus = true)
        val gridTarget = FakeFocusTarget(canFocus = true, rememberedFocus = false)

        coordinator.registerContentTarget(
            tab = AppTopLevelTab.POPULAR,
            region = HomeFocusRegion.ContentTabs,
            target = tabsTarget,
        )
        coordinator.registerContentTarget(
            tab = AppTopLevelTab.POPULAR,
            region = HomeFocusRegion.Grid,
            target = gridTarget,
        )

        coordinator.requestSelectedContentFocus()

        assertEquals(1, tabsTarget.focusRequests)
        assertEquals(0, gridTarget.focusRequests)

        gridTarget.rememberedFocus = true
        coordinator.requestSelectedContentFocus()

        assertEquals(1, tabsTarget.focusRequests)
        assertEquals(1, gridTarget.focusRequests)
    }

    @Test
    fun `restore video key callback runs only after real key focus succeeds`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.RECOMMEND)
        val gridTarget = FakeFocusTarget(canFocus = true, keyFocus = false)
        var restoredKey: String? = null

        coordinator.registerContentTarget(
            tab = AppTopLevelTab.RECOMMEND,
            region = HomeFocusRegion.Grid,
            target = gridTarget,
        )

        coordinator.requestRestoreVideoKey(
            tab = AppTopLevelTab.RECOMMEND,
            key = "BV1:3",
            onRestored = { restoredKey = it },
        )

        assertNull(restoredKey)
        assertEquals(listOf("BV1:3"), gridTarget.keyRequests)

        gridTarget.keyFocus = true

        assertTrue(coordinator.drainPendingFocus())
        assertEquals("BV1:3", restoredKey)
        assertEquals(listOf("BV1:3", "BV1:3"), gridTarget.keyRequests)
    }

    @Test
    fun `grid top edge is decided by coordinator before falling back to top bar`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.POPULAR)
        val topBarTarget = FakeFocusTarget(canFocus = true)
        val tabsTarget = FakeFocusTarget(canFocus = false)

        coordinator.registerTopBarTarget(topBarTarget)
        coordinator.registerContentTarget(
            tab = AppTopLevelTab.POPULAR,
            region = HomeFocusRegion.ContentTabs,
            target = tabsTarget,
        )

        coordinator.handleGridTopEdge(AppTopLevelTab.POPULAR)

        assertEquals(1, tabsTarget.focusRequests)
        assertEquals(2, topBarTarget.focusRequests)
        assertFalse(coordinator.isContentFocused)
        assertTrue(coordinator.isTopBarVisible)
    }

    private class FakeFocusTarget(
        var canFocus: Boolean = true,
        var rememberedFocus: Boolean = false,
        var keyFocus: Boolean = true,
    ) : HomeFocusTarget {
        var focusRequests: Int = 0
            private set
        val keyRequests = mutableListOf<String>()

        override fun tryRequestFocus(): Boolean {
            focusRequests++
            return canFocus
        }

        override fun tryRequestFocusKey(key: String): Boolean {
            keyRequests += key
            return keyFocus
        }

        override fun hasRememberedFocus(): Boolean {
            return rememberedFocus
        }
    }
}
