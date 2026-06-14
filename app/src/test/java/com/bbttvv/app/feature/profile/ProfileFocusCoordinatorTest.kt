package com.bbttvv.app.feature.profile

import androidx.compose.foundation.lazy.LazyListState
import com.bbttvv.app.ui.home.HomeFocusRequestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFocusCoordinatorTest {
    @Test
    fun `stale content generation cannot consume new menu focus`() {
        val coordinator = coordinator()
        coordinator.updateSelection(
            menu = ProfileMenu.HISTORY,
            menus = listOf(ProfileMenu.HISTORY, ProfileMenu.FAVORITE),
        )
        val oldScope = ProfileContentFocusScope(
            coordinator = coordinator,
            menu = ProfileMenu.HISTORY,
            generation = coordinator.contentGeneration,
        )

        coordinator.prepareContentMenu(ProfileMenu.FAVORITE)
        val currentScope = ProfileContentFocusScope(
            coordinator = coordinator,
            menu = ProfileMenu.FAVORITE,
            generation = coordinator.contentGeneration,
        )

        assertFalse(oldScope.isCurrent())
        assertTrue(currentScope.isCurrent())
    }

    @Test
    fun `pending sidebar request drains when target registers`() {
        val coordinator = coordinator()
        coordinator.updateSelection(
            menu = ProfileMenu.HISTORY,
            menus = listOf(ProfileMenu.HISTORY),
        )

        assertEquals(HomeFocusRequestResult.Pending, coordinator.requestSidebarFocusResult())
        var focusRequests = 0
        coordinator.registerMenuTarget(ProfileMenu.HISTORY) {
            focusRequests += 1
            true
        }

        assertEquals(1, focusRequests)
        assertEquals(HomeFocusRequestResult.Unavailable, coordinator.drainPendingSidebarFocus())
    }

    @Test
    fun `removed selected menu resolves to nearest legal menu`() {
        val previous = listOf(
            ProfileMenu.HISTORY,
            ProfileMenu.FAVORITE,
            ProfileMenu.BANGUMI,
            ProfileMenu.WATCH_LATER,
            ProfileMenu.SETTINGS,
        )

        assertEquals(
            ProfileMenu.BANGUMI,
            ProfileMenuFocusPolicy.resolve(
                selectedMenu = ProfileMenu.WATCH_LATER,
                previousMenus = previous,
                currentMenus = previous.filterNot { it == ProfileMenu.WATCH_LATER },
            ),
        )
    }

    private fun coordinator(): ProfileFocusCoordinator {
        return ProfileFocusCoordinator(
            menuListState = LazyListState(),
            menuFocusRequesters = emptyMap(),
        )
    }
}
