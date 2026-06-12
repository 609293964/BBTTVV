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
        coordinator.onContentRegionFocused(AppTopLevelTab.POPULAR, HomeFocusRegion.Grid)
        coordinator.requestSelectedContentFocus()

        assertEquals(1, tabsTarget.focusRequests)
        assertEquals(1, gridTarget.focusRequests)
    }

    @Test
    fun `top bar dpad down can enter search input`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.SEARCH)
        val searchInputTarget = FakeFocusTarget(canFocus = true)

        coordinator.registerContentTarget(
            tab = AppTopLevelTab.SEARCH,
            region = HomeFocusRegion.SearchInput,
            target = searchInputTarget,
        )

        assertTrue(coordinator.handleTopBarDpadDown())

        assertEquals(1, searchInputTarget.focusRequests)
        assertTrue(coordinator.isContentFocused)
    }

    @Test
    fun `back to top bar reset intent is consumed once`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.POPULAR)

        coordinator.requestTopBarFocus(HomeFocusScene.BackToTopBar)

        assertTrue(coordinator.consumeBackToTopBarResetIntent())
        assertEquals(HomeFocusScene.TopBarFocused, coordinator.scene)
        assertFalse(coordinator.consumeBackToTopBarResetIntent())

        coordinator.requestTopBarFocus(HomeFocusScene.BackToRecommend)

        assertFalse(coordinator.consumeBackToTopBarResetIntent())
        assertEquals(HomeFocusScene.BackToRecommend, coordinator.scene)
    }

    @Test
    fun `selected content focus restores remembered profile region`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.PROFILE)
        val sidebarTarget = FakeFocusTarget(canFocus = true)
        val contentTarget = FakeFocusTarget(canFocus = true)

        coordinator.registerContentTarget(
            tab = AppTopLevelTab.PROFILE,
            region = HomeFocusRegion.ProfileSidebar,
            target = sidebarTarget,
        )
        coordinator.registerContentTarget(
            tab = AppTopLevelTab.PROFILE,
            region = HomeFocusRegion.ProfileContent,
            target = contentTarget,
        )

        coordinator.onContentRegionFocused(AppTopLevelTab.PROFILE, HomeFocusRegion.ProfileContent)
        coordinator.requestSelectedContentFocus()

        assertEquals(0, sidebarTarget.focusRequests)
        assertEquals(1, contentTarget.focusRequests)
        assertTrue(coordinator.isContentFocused)
    }

    @Test
    fun `profile top bar dpad down keeps top bar visible while entering sidebar`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.PROFILE)
        val sidebarTarget = FakeFocusTarget(canFocus = true)

        coordinator.registerContentTarget(
            tab = AppTopLevelTab.PROFILE,
            region = HomeFocusRegion.ProfileSidebar,
            target = sidebarTarget,
        )

        assertTrue(coordinator.handleTopBarDpadDown())

        assertEquals(1, sidebarTarget.focusRequests)
        assertTrue(coordinator.isContentFocused)
        assertTrue(coordinator.isTopBarVisible)
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
    fun `restore video key does not reenter drain while focus callback is running`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.RECOMMEND)
        var restoreRequests = 0
        val gridTarget = object : HomeFocusTarget {
            override fun tryRequestFocus(): Boolean = false

            override fun requestBackReturnFocusKeyResult(key: String): HomeBackReturnRestoreResult {
                restoreRequests++
                if (restoreRequests == 1) {
                    coordinator.onContentRegionFocused(
                        AppTopLevelTab.RECOMMEND,
                        HomeFocusRegion.Grid,
                    )
                    coordinator.drainPendingFocus()
                }
                return HomeBackReturnRestoreResult.ExactFocused
            }
        }

        coordinator.registerContentTarget(
            tab = AppTopLevelTab.RECOMMEND,
            region = HomeFocusRegion.Grid,
            target = gridTarget,
        )
        coordinator.requestRestoreVideoKey(
            tab = AppTopLevelTab.RECOMMEND,
            key = "BV1:reentrant",
            onRestored = {},
        )

        assertEquals(1, restoreRequests)
    }

    @Test
    fun `back return pending restore enters content mode without showing top bar`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.RECOMMEND)
        val topBarTarget = FakeFocusTarget(canFocus = true)
        val gridTarget = FakeFocusTarget(
            canFocus = true,
            keyRequestResult = HomeFocusRequestResult.Pending,
        )
        var restoredKey: String? = null

        coordinator.registerTopBarTarget(topBarTarget)
        coordinator.registerContentTarget(
            tab = AppTopLevelTab.RECOMMEND,
            region = HomeFocusRegion.Grid,
            target = gridTarget,
        )

        coordinator.requestRestoreVideoKey(
            tab = AppTopLevelTab.RECOMMEND,
            key = "BV1:pending",
            onRestored = { restoredKey = it },
        )

        assertNull(restoredKey)
        assertTrue(coordinator.isContentFocused)
        assertFalse(coordinator.isTopBarVisible)
        assertEquals(1, topBarTarget.focusRequests)
        assertEquals(listOf("BV1:pending"), gridTarget.keyRequests)
        assertTrue(coordinator.drainPendingFocus())
        assertEquals(1, topBarTarget.focusRequests)
        assertEquals(listOf("BV1:pending", "BV1:pending"), gridTarget.keyRequests)
    }

    @Test
    fun `top bar down pending content request keeps tabs visible until focus lands`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.RECOMMEND)
        val topBarTarget = FakeFocusTarget(canFocus = true)
        val gridTarget = FakeFocusTarget(
            canFocus = true,
            focusResult = HomeFocusRequestResult.Pending,
        )

        coordinator.registerTopBarTarget(topBarTarget)
        coordinator.registerContentTarget(
            tab = AppTopLevelTab.RECOMMEND,
            region = HomeFocusRegion.Grid,
            target = gridTarget,
        )

        assertTrue(coordinator.handleTopBarDpadDown())

        assertFalse(coordinator.isContentFocused)
        assertTrue(coordinator.isTopBarVisible)
        assertTrue(coordinator.isTopBarFocusable)
        assertEquals(1, topBarTarget.focusRequests)
        assertEquals(1, gridTarget.focusRequests)
    }

    @Test
    fun `pending content request enters content mode after real content focus`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.RECOMMEND)
        val gridTarget = FakeFocusTarget(
            canFocus = true,
            focusResult = HomeFocusRequestResult.Pending,
        )

        coordinator.registerContentTarget(
            tab = AppTopLevelTab.RECOMMEND,
            region = HomeFocusRegion.Grid,
            target = gridTarget,
        )

        assertTrue(coordinator.handleTopBarDpadDown())
        assertFalse(coordinator.isContentFocused)
        assertTrue(coordinator.isTopBarVisible)

        coordinator.onContentRegionFocused(AppTopLevelTab.RECOMMEND, HomeFocusRegion.Grid)

        assertTrue(coordinator.isContentFocused)
        assertFalse(coordinator.isTopBarVisible)
    }

    @Test
    fun `first content row keeps tabs visible without making top bar focusable`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.RECOMMEND)

        coordinator.prepareForContentFocus()
        assertFalse(coordinator.isTopBarVisible)
        assertFalse(coordinator.isTopBarFocusable)

        coordinator.onContentRowFocused(0)
        assertTrue(coordinator.isTopBarVisible)
        assertFalse(coordinator.isTopBarFocusable)

        coordinator.onContentRowFocused(1)
        assertFalse(coordinator.isTopBarVisible)
        assertFalse(coordinator.isTopBarFocusable)
    }

    @Test
    fun `escape recovery keeps pending content owner instead of top bar fallback`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.RECOMMEND)
        val topBarTarget = FakeFocusTarget(canFocus = true)
        val gridTarget = FakeFocusTarget(
            canFocus = true,
            focusResult = HomeFocusRequestResult.Pending,
        )

        coordinator.registerTopBarTarget(topBarTarget)
        coordinator.registerContentTarget(
            tab = AppTopLevelTab.RECOMMEND,
            region = HomeFocusRegion.Grid,
            target = gridTarget,
        )
        coordinator.handleTopBarDpadDown()

        assertTrue(coordinator.recoverFocusAfterEscape())

        assertEquals(1, topBarTarget.focusRequests)
        assertEquals(2, gridTarget.focusRequests)
        assertFalse(coordinator.isContentFocused)
        assertTrue(coordinator.isTopBarVisible)
    }

    @Test
    fun `restore video key fallback clears pending without restored callback`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.RECOMMEND)
        val gridTarget = FakeFallbackFocusTarget()
        var restoredKey: String? = null
        var canceled = false

        coordinator.setPendingRestoreCancelCallback { canceled = true }
        coordinator.registerContentTarget(
            tab = AppTopLevelTab.RECOMMEND,
            region = HomeFocusRegion.Grid,
            target = gridTarget,
        )

        coordinator.requestRestoreVideoKey(
            tab = AppTopLevelTab.RECOMMEND,
            key = "BV1:4",
            onRestored = { restoredKey = it },
        )

        assertNull(restoredKey)
        assertTrue(canceled)
        assertEquals(listOf("BV1:4"), gridTarget.fallbackRequests)
        assertTrue(coordinator.isContentFocused)
    }

    @Test
    fun `pending restore is canceled by user grid navigation`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.RECOMMEND)
        val gridTarget = FakeFocusTarget(
            canFocus = true,
            keyRequestResult = HomeFocusRequestResult.Pending,
        )
        var restoredKey: String? = null
        var canceled = false

        coordinator.setPendingRestoreCancelCallback { canceled = true }
        coordinator.registerContentTarget(
            tab = AppTopLevelTab.RECOMMEND,
            region = HomeFocusRegion.Grid,
            target = gridTarget,
        )

        coordinator.requestRestoreVideoKey(
            tab = AppTopLevelTab.RECOMMEND,
            key = "BV1:pending",
            onRestored = { restoredKey = it },
        )

        assertTrue(coordinator.cancelPendingRestoreVideoKeyForUserNavigation(AppTopLevelTab.RECOMMEND))
        gridTarget.keyRequestResult = HomeFocusRequestResult.Focused

        assertFalse(coordinator.drainPendingFocus())
        assertNull(restoredKey)
        assertTrue(canceled)
        assertEquals(listOf("BV1:pending"), gridTarget.keyRequests)
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

    @Test
    fun `dynamic focus moves through live updates row before grid`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.DYNAMIC)
        val liveTarget = FakeFocusTarget(canFocus = true)
        val followUpdatesTarget = FakeFocusTarget(canFocus = true)
        val gridTarget = FakeFocusTarget(canFocus = true)

        coordinator.registerContentTarget(
            tab = AppTopLevelTab.DYNAMIC,
            region = HomeFocusRegion.DynamicLiveUsers,
            target = liveTarget,
        )
        coordinator.registerContentTarget(
            tab = AppTopLevelTab.DYNAMIC,
            region = HomeFocusRegion.DynamicFollowUpdates,
            target = followUpdatesTarget,
        )
        coordinator.registerContentTarget(
            tab = AppTopLevelTab.DYNAMIC,
            region = HomeFocusRegion.Grid,
            target = gridTarget,
        )

        assertTrue(coordinator.handleTopBarDpadDown())
        assertEquals(1, liveTarget.focusRequests)
        assertEquals(0, followUpdatesTarget.focusRequests)
        assertEquals(0, gridTarget.focusRequests)

        assertTrue(coordinator.handleDynamicLiveUsersDpadDown())
        assertEquals(1, followUpdatesTarget.focusRequests)

        assertTrue(coordinator.handleDynamicFollowUpdatesDpadDown())
        assertEquals(1, gridTarget.focusRequests)

        assertTrue(coordinator.handleGridTopEdge(AppTopLevelTab.DYNAMIC))
        assertEquals(2, followUpdatesTarget.focusRequests)

        assertTrue(coordinator.handleDynamicFollowUpdatesDpadUp())
        assertEquals(2, liveTarget.focusRequests)
    }

    @Test
    fun `dynamic focus enters updates row when live row is absent`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.DYNAMIC)
        val followUpdatesTarget = FakeFocusTarget(canFocus = true)
        val gridTarget = FakeFocusTarget(canFocus = true)

        coordinator.registerContentTarget(
            tab = AppTopLevelTab.DYNAMIC,
            region = HomeFocusRegion.DynamicFollowUpdates,
            target = followUpdatesTarget,
        )
        coordinator.registerContentTarget(
            tab = AppTopLevelTab.DYNAMIC,
            region = HomeFocusRegion.Grid,
            target = gridTarget,
        )

        assertTrue(coordinator.handleTopBarDpadDown())

        assertEquals(1, followUpdatesTarget.focusRequests)
        assertEquals(0, gridTarget.focusRequests)
    }

    @Test
    fun `content tabs dpad down passes source index to grid entry focus`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.POPULAR)
        val gridTarget = FakeFocusTarget(canFocus = true)

        coordinator.registerContentTarget(
            tab = AppTopLevelTab.POPULAR,
            region = HomeFocusRegion.Grid,
            target = gridTarget,
        )

        assertTrue(coordinator.handleContentTabsDpadDown(AppTopLevelTab.POPULAR, preferredIndex = 2))

        assertEquals(listOf(2), gridTarget.entryRequests)
        assertEquals(1, gridTarget.focusRequests)
        assertTrue(coordinator.isContentFocused)
    }

    @Test
    fun `dynamic live row dpad down passes source index to follow updates`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.DYNAMIC)
        val followUpdatesTarget = FakeFocusTarget(canFocus = true)
        val gridTarget = FakeFocusTarget(canFocus = true)

        coordinator.registerContentTarget(
            tab = AppTopLevelTab.DYNAMIC,
            region = HomeFocusRegion.DynamicFollowUpdates,
            target = followUpdatesTarget,
        )
        coordinator.registerContentTarget(
            tab = AppTopLevelTab.DYNAMIC,
            region = HomeFocusRegion.Grid,
            target = gridTarget,
        )

        assertTrue(coordinator.handleDynamicLiveUsersDpadDown(preferredIndex = 3))

        assertEquals(listOf(3), followUpdatesTarget.entryRequests)
        assertEquals(emptyList<Int?>(), gridTarget.entryRequests)
    }

    @Test
    fun `top bar dpad down still prefers dynamic live row after grid was remembered`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.DYNAMIC)
        val liveTarget = FakeFocusTarget(canFocus = true)
        val followUpdatesTarget = FakeFocusTarget(canFocus = true)
        val gridTarget = FakeFocusTarget(canFocus = true)

        coordinator.registerContentTarget(
            tab = AppTopLevelTab.DYNAMIC,
            region = HomeFocusRegion.DynamicLiveUsers,
            target = liveTarget,
        )
        coordinator.registerContentTarget(
            tab = AppTopLevelTab.DYNAMIC,
            region = HomeFocusRegion.DynamicFollowUpdates,
            target = followUpdatesTarget,
        )
        coordinator.registerContentTarget(
            tab = AppTopLevelTab.DYNAMIC,
            region = HomeFocusRegion.Grid,
            target = gridTarget,
        )
        coordinator.onContentRegionFocused(AppTopLevelTab.DYNAMIC, HomeFocusRegion.Grid)

        assertTrue(coordinator.handleTopBarDpadDown())

        assertEquals(1, liveTarget.focusRequests)
        assertEquals(0, followUpdatesTarget.focusRequests)
        assertEquals(0, gridTarget.focusRequests)
    }

    @Test
    fun `escape recovery restores remembered content before top bar when content was active`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.POPULAR)
        val topBarTarget = FakeFocusTarget(canFocus = true)
        val tabsTarget = FakeFocusTarget(canFocus = true)
        val gridTarget = FakeFocusTarget(canFocus = true, rememberedFocus = true)

        coordinator.registerTopBarTarget(topBarTarget)
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
        coordinator.prepareForContentFocus()

        assertTrue(coordinator.recoverFocusAfterEscape())

        assertEquals(1, gridTarget.focusRequests)
        assertEquals(0, tabsTarget.focusRequests)
        assertEquals(1, topBarTarget.focusRequests)
        assertTrue(coordinator.isContentFocused)
    }

    @Test
    fun `escape recovery restores top bar first when top bar was active`() {
        val coordinator = HomeFocusCoordinator(AppTopLevelTab.RECOMMEND)
        val topBarTarget = FakeFocusTarget(canFocus = true)
        val gridTarget = FakeFocusTarget(canFocus = true, rememberedFocus = true)

        coordinator.registerTopBarTarget(topBarTarget)
        coordinator.registerContentTarget(
            tab = AppTopLevelTab.RECOMMEND,
            region = HomeFocusRegion.Grid,
            target = gridTarget,
        )

        assertTrue(coordinator.recoverFocusAfterEscape())

        assertEquals(2, topBarTarget.focusRequests)
        assertEquals(0, gridTarget.focusRequests)
        assertFalse(coordinator.isContentFocused)
        assertTrue(coordinator.isTopBarVisible)
    }

    private class FakeFocusTarget(
        var canFocus: Boolean = true,
        var rememberedFocus: Boolean = false,
        var keyFocus: Boolean = true,
        var focusResult: HomeFocusRequestResult? = null,
        var keyRequestResult: HomeFocusRequestResult? = null,
    ) : HomeFocusTarget {
        var focusRequests: Int = 0
            private set
        val keyRequests = mutableListOf<String>()
        val entryRequests = mutableListOf<Int?>()

        override fun tryRequestFocus(): Boolean {
            focusRequests++
            return (focusResult ?: HomeFocusRequestResult.fromFocused(canFocus)).isFocused
        }

        override fun requestFocusResult(): HomeFocusRequestResult {
            focusRequests++
            return focusResult ?: HomeFocusRequestResult.fromFocused(canFocus)
        }

        override fun tryRequestFocusForEntry(entryHint: HomeFocusEntryHint): Boolean {
            entryRequests += entryHint.preferredIndex
            return tryRequestFocus()
        }

        override fun requestFocusForEntryResult(entryHint: HomeFocusEntryHint): HomeFocusRequestResult {
            entryRequests += entryHint.preferredIndex
            return requestFocusResult()
        }

        override fun tryRequestFocusKey(key: String): Boolean {
            keyRequests += key
            return (keyRequestResult ?: HomeFocusRequestResult.fromFocused(keyFocus)).isFocused
        }

        override fun requestFocusKeyOrFallbackResult(key: String): HomeFocusRequestResult {
            keyRequests += key
            return keyRequestResult ?: HomeFocusRequestResult.fromFocused(keyFocus)
        }

        override fun hasRememberedFocus(): Boolean {
            return rememberedFocus
        }
    }

    private class FakeFallbackFocusTarget : HomeFocusTarget {
        val fallbackRequests = mutableListOf<String>()

        override fun tryRequestFocus(): Boolean {
            return false
        }

        override fun tryRequestFocusKey(key: String): Boolean {
            return false
        }

        override fun tryRequestFocusKeyOrFallback(key: String): Boolean {
            fallbackRequests += key
            return true
        }

        override fun requestBackReturnFocusKeyResult(key: String): HomeBackReturnRestoreResult {
            fallbackRequests += key
            return HomeBackReturnRestoreResult.FallbackFocused
        }
    }
}
