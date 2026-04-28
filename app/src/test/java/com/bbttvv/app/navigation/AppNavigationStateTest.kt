package com.bbttvv.app.navigation

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.bbttvv.app.ui.components.AppTopLevelTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeNavStateTest {
    @Test
    fun `normalize home tab falls back to recommend when today watch tab is hidden`() {
        val state = createHomeNavState(homeTabIndex = AppTopLevelTab.TODAY_WATCH.index)

        state.normalizeHomeTabIfNeeded(
            visibleTabs = AppTopLevelTab.resolveVisibleTabs(todayWatchEnabled = false)
        )

        assertEquals(AppTopLevelTab.RECOMMEND.index, state.homeTabIndex)
    }

    @Test
    fun `route check only treats home route as home`() {
        val state = createHomeNavState()

        assertEquals(true, state.isOnHome(ScreenRoutes.Home.route))
        assertEquals(false, state.isOnHome(ScreenRoutes.Settings.route))
    }
}

class DetailReturnStateTest {
    @Test
    fun `recommend detail open keeps video focus key until restored`() {
        val state = createDetailReturnState()

        state.prepareForRecommendDetailOpen("BV1xx:3")

        assertEquals("BV1xx:3", state.homeVideoFocusRestoreKey)
        assertNull(state.restoreVideoFocusKey(isOnHome = true))

        state.onHostActivityPaused()
        state.onHostActivityResumed()

        assertEquals("BV1xx:3", state.restoreVideoFocusKey(isOnHome = true))
        assertEquals(AppTopLevelTab.RECOMMEND, state.restoreVideoFocusTab(isOnHome = true))

        state.markHomeVideoFocusRestored("other")
        assertEquals("BV1xx:3", state.homeVideoFocusRestoreKey)

        state.markHomeVideoFocusRestored("BV1xx:3")
        assertNull(state.homeVideoFocusRestoreKey)
    }

    @Test
    fun `comment focus restore is scoped by detail bvid`() {
        val state = createDetailReturnState()

        state.setDetailCommentFocusRestore(bvid = "BV1xx", rpid = 42L)

        assertEquals(42L, state.restoreCommentFocusRpidFor("BV1xx"))
        assertNull(state.restoreCommentFocusRpidFor("BV-other"))

        state.markCommentFocusRestored(bvid = "BV-other", restoredRpid = 42L)
        assertEquals(42L, state.restoreCommentFocusRpidFor("BV1xx"))

        state.markCommentFocusRestored(bvid = "BV1xx", restoredRpid = 42L)
        assertNull(state.restoreCommentFocusRpidFor("BV1xx"))
    }
}

class BackPressExitStateTest {
    @Test
    fun `second back press inside exit window exits`() {
        val state = BackPressExitState(mutableLongStateOf(0L))

        assertEquals(HomeBackPressResult.ShowExitHint, state.handleBackPressedOnDefaultTab(now = 3_000L))
        assertEquals(HomeBackPressResult.Exit, state.handleBackPressedOnDefaultTab(now = 4_000L))
    }

    @Test
    fun `leaving home clears exit back press window`() {
        val state = BackPressExitState(mutableLongStateOf(0L))

        assertEquals(HomeBackPressResult.ShowExitHint, state.handleBackPressedOnDefaultTab(now = 3_000L))
        state.onRouteChanged(isOnHome = false)

        assertEquals(HomeBackPressResult.ShowExitHint, state.handleBackPressedOnDefaultTab(now = 4_000L))
    }
}

class AppNavigationStateTest {
    @Test
    fun `back press on hidden today watch tab normalizes to recommend then shows exit hint`() {
        val state = createNavigationState(
            homeTabIndex = AppTopLevelTab.TODAY_WATCH.index
        )

        val result = state.handleHomeBackPressed(
            now = 3_000L,
            visibleTabs = AppTopLevelTab.resolveVisibleTabs(todayWatchEnabled = false)
        )

        assertEquals(HomeBackPressResult.ShowExitHint, result)
        assertEquals(AppTopLevelTab.RECOMMEND.index, state.homeTabIndex)
    }
}

private fun createHomeNavState(
    homeTabIndex: Int = AppTopLevelTab.RECOMMEND.index
): HomeNavState {
    return HomeNavState(
        homeTabIndexState = mutableIntStateOf(homeTabIndex)
    )
}

private fun createDetailReturnState(): DetailReturnState {
    return DetailReturnState(
        homeVideoFocusRestoreKeyState = mutableStateOf(null),
        homeVideoFocusRestoreTabIndexState = mutableStateOf(null),
        homeVideoFocusRestoreReadyState = mutableStateOf(false),
        homeVideoFocusRestoreSawPauseState = mutableStateOf(false),
        detailCommentFocusRestoreRpidState = mutableStateOf(null),
        detailCommentFocusRestoreBvidState = mutableStateOf(null)
    )
}

private fun createNavigationState(
    homeTabIndex: Int = AppTopLevelTab.RECOMMEND.index
): AppNavigationState {
    return AppNavigationState(
        homeNavState = createHomeNavState(homeTabIndex),
        detailReturnState = createDetailReturnState(),
        backPressExitState = BackPressExitState(mutableLongStateOf(0L))
    )
}
