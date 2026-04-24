package com.bbttvv.app.navigation

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.bbttvv.app.ui.components.AppTopLevelTab
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationStateTest {

    @Test
    fun `normalize home tab falls back to recommend when today watch tab is hidden`() {
        val state = createNavigationState(
            homeTabIndex = AppTopLevelTab.TODAY_WATCH.index
        )

        state.normalizeHomeTabIfNeeded(
            visibleTabs = AppTopLevelTab.resolveVisibleTabs(todayWatchEnabled = false)
        )

        assertEquals(AppTopLevelTab.RECOMMEND.index, state.homeTabIndex)
    }

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

    @Test
    fun `recommend detail open keeps video focus key until restored`() {
        val state = createNavigationState()

        state.prepareForRecommendDetailOpen("BV1xx:3")

        assertEquals("BV1xx:3", state.homeVideoFocusRestoreKey)
        assertEquals(null, state.restoreVideoFocusKey(ScreenRoutes.Home.route))

        state.onHostActivityPaused()
        state.onHostActivityResumed()

        assertEquals("BV1xx:3", state.restoreVideoFocusKey(ScreenRoutes.Home.route))

        state.markHomeVideoFocusRestored("other")
        assertEquals("BV1xx:3", state.homeVideoFocusRestoreKey)

        state.markHomeVideoFocusRestored("BV1xx:3")
        assertEquals(null, state.homeVideoFocusRestoreKey)
    }

    private fun createNavigationState(
        homeTabIndex: Int = AppTopLevelTab.RECOMMEND.index
    ): AppNavigationState {
        return AppNavigationState(
            homeTabIndexState = mutableIntStateOf(homeTabIndex),
            homeVideoFocusRestoreKeyState = mutableStateOf(null),
            homeVideoFocusRestoreTabIndexState = mutableStateOf(null),
            homeVideoFocusRestoreReadyState = mutableStateOf(false),
            homeVideoFocusRestoreSawPauseState = mutableStateOf(false),
            detailCommentFocusRestoreRpidState = mutableStateOf(null),
            detailCommentFocusRestoreBvidState = mutableStateOf(null),
            lastBackPressedAtState = mutableLongStateOf(0L),
            previousRouteState = mutableStateOf(null)
        )
    }
}
