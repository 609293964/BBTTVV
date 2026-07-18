package com.bbttvv.app.ui.home

import com.bbttvv.app.ui.components.AppTopLevelTab
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTabCompositionPolicyTest {
    @Test
    fun `first home composition includes only selected tab`() {
        val result = HomeTabCompositionPolicy.resolve(
            visibleTabs = visibleTabs,
            selectedTab = AppTopLevelTab.RECOMMEND,
            residentTabs = emptySet(),
        )

        assertEquals(listOf(AppTopLevelTab.RECOMMEND), result)
    }

    @Test
    fun `resident tabs stay composed while home remains active`() {
        val result = HomeTabCompositionPolicy.resolve(
            visibleTabs = visibleTabs,
            selectedTab = AppTopLevelTab.LIVE,
            residentTabs = setOf(AppTopLevelTab.RECOMMEND, AppTopLevelTab.POPULAR),
        )

        assertEquals(
            listOf(
                AppTopLevelTab.RECOMMEND,
                AppTopLevelTab.POPULAR,
                AppTopLevelTab.LIVE,
            ),
            result,
        )
    }

    @Test
    fun `cpu bound residency keeps only the selected and previous page`() {
        val state = HomeTabResidencyState(
            initialSelectedTab = AppTopLevelTab.RECOMMEND,
            maxResidentTabs = 2,
        )
        state.updateVisibleTabs(visibleTabs)

        state.select(AppTopLevelTab.POPULAR)
        state.select(AppTopLevelTab.LIVE)

        assertEquals(
            listOf(AppTopLevelTab.POPULAR, AppTopLevelTab.LIVE),
            state.residentTabsInDisplayOrder(),
        )
    }

    @Test
    fun `hidden activated tabs are not composed`() {
        val result = HomeTabCompositionPolicy.resolve(
            visibleTabs = listOf(AppTopLevelTab.RECOMMEND, AppTopLevelTab.LIVE),
            selectedTab = AppTopLevelTab.RECOMMEND,
            residentTabs = setOf(AppTopLevelTab.WATCH_LATER),
        )

        assertEquals(listOf(AppTopLevelTab.RECOMMEND), result)
    }

    @Test
    fun `residency keeps current previous and movement adjacent within three pages`() {
        val state = HomeTabResidencyState(
            initialSelectedTab = AppTopLevelTab.RECOMMEND,
            maxResidentTabs = 3,
        )
        state.updateVisibleTabs(visibleTabs)

        state.select(AppTopLevelTab.POPULAR)
        assertEquals(AppTopLevelTab.LIVE, state.prewarmNextAdjacent())

        assertEquals(
            listOf(
                AppTopLevelTab.RECOMMEND,
                AppTopLevelTab.POPULAR,
                AppTopLevelTab.LIVE,
            ),
            state.residentTabsInDisplayOrder(),
        )
    }

    @Test
    fun `pending restore tab remains resident until unpinned`() {
        val state = HomeTabResidencyState(
            initialSelectedTab = AppTopLevelTab.RECOMMEND,
            maxResidentTabs = 3,
        )
        state.updateVisibleTabs(visibleTabs)
        state.updatePinnedTab(AppTopLevelTab.DYNAMIC)
        state.select(AppTopLevelTab.POPULAR)
        state.prewarmNextAdjacent()

        assertEquals(true, AppTopLevelTab.DYNAMIC in state.residentTabsInDisplayOrder())
        assertEquals(3, state.residentTabsInDisplayOrder().size)

        state.updatePinnedTab(null)
        assertEquals(false, AppTopLevelTab.DYNAMIC in state.residentTabsInDisplayOrder())
    }

    @Test
    fun `recommend stays resident across dynamic navigation with bounded adjacent prewarm`() {
        val state = HomeTabResidencyState(
            initialSelectedTab = AppTopLevelTab.RECOMMEND,
            maxResidentTabs = 4,
            persistentResidentTabs = setOf(AppTopLevelTab.RECOMMEND),
        )
        state.updateVisibleTabs(visibleTabs)

        state.select(AppTopLevelTab.POPULAR)
        assertEquals(AppTopLevelTab.LIVE, state.prewarmNextAdjacent())
        state.select(AppTopLevelTab.LIVE)
        assertEquals(AppTopLevelTab.DYNAMIC, state.prewarmNextAdjacent())
        state.select(AppTopLevelTab.DYNAMIC)
        assertEquals(AppTopLevelTab.PROFILE, state.prewarmNextAdjacent())

        assertEquals(true, AppTopLevelTab.RECOMMEND in state.residentTabsInDisplayOrder())
        assertEquals(4, state.residentTabsInDisplayOrder().size)

        state.select(AppTopLevelTab.LIVE)
        assertEquals(AppTopLevelTab.POPULAR, state.prewarmNextAdjacent())
        assertEquals(
            listOf(
                AppTopLevelTab.RECOMMEND,
                AppTopLevelTab.POPULAR,
                AppTopLevelTab.LIVE,
                AppTopLevelTab.DYNAMIC,
            ),
            state.residentTabsInDisplayOrder(),
        )
    }

    @Test
    fun `memory trim keeps only selected tab`() {
        val state = HomeTabResidencyState(
            initialSelectedTab = AppTopLevelTab.RECOMMEND,
            maxResidentTabs = 3,
        )
        state.updateVisibleTabs(visibleTabs)
        state.select(AppTopLevelTab.POPULAR)
        state.prewarmNextAdjacent()

        state.trimToSelected()

        assertEquals(listOf(AppTopLevelTab.POPULAR), state.residentTabsInDisplayOrder())
    }

    private companion object {
        val visibleTabs = listOf(
            AppTopLevelTab.SEARCH,
            AppTopLevelTab.RECOMMEND,
            AppTopLevelTab.POPULAR,
            AppTopLevelTab.LIVE,
            AppTopLevelTab.DYNAMIC,
            AppTopLevelTab.PROFILE,
        )
    }
}
