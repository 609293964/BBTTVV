package com.android.purebilibili.feature.home.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeInteractionMotionBudgetPolicyTest {

    @Test
    fun activePagerOrFeedMotion_reducesHomeBudget() {
        assertEquals(
            HomeInteractionMotionBudget.REDUCED,
            resolveHomeInteractionMotionBudget(
                isPagerScrolling = true,
                isProgrammaticPageSwitchInProgress = false,
                isFeedScrolling = false
            )
        )
        assertEquals(
            HomeInteractionMotionBudget.REDUCED,
            resolveHomeInteractionMotionBudget(
                isPagerScrolling = false,
                isProgrammaticPageSwitchInProgress = true,
                isFeedScrolling = false
            )
        )
        assertEquals(
            HomeInteractionMotionBudget.REDUCED,
            resolveHomeInteractionMotionBudget(
                isPagerScrolling = false,
                isProgrammaticPageSwitchInProgress = false,
                isFeedScrolling = true
            )
        )
    }

    @Test
    fun idleHomeState_keepsFullBudget() {
        assertEquals(
            HomeInteractionMotionBudget.FULL,
            resolveHomeInteractionMotionBudget(
                isPagerScrolling = false,
                isProgrammaticPageSwitchInProgress = false,
                isFeedScrolling = false
            )
        )
    }

    @Test
    fun reducedBudget_onlyAutoScrollsTabsWhenTargetIsOutOfViewport() {
        assertFalse(
            shouldAnimateTopTabAutoScroll(
                selectedIndex = 2,
                firstVisibleIndex = 0,
                lastVisibleIndex = 4,
                budget = HomeInteractionMotionBudget.REDUCED
            )
        )
        assertTrue(
            shouldAnimateTopTabAutoScroll(
                selectedIndex = 5,
                firstVisibleIndex = 0,
                lastVisibleIndex = 4,
                budget = HomeInteractionMotionBudget.REDUCED
            )
        )
    }

    @Test
    fun fullBudget_keepsLeadingTabsVisibleWhenSelectionAlreadyInViewport() {
        assertFalse(
            shouldAnimateTopTabAutoScroll(
                selectedIndex = 1,
                firstVisibleIndex = 0,
                lastVisibleIndex = 3,
                budget = HomeInteractionMotionBudget.FULL
            )
        )
    }

    @Test
    fun topTabTapPolicy_usesImmediatePageSwitchWhenTargetChanges() {
        assertTrue(shouldSnapHomeTopTabSelection(currentPage = 0, targetPage = 1))
        assertFalse(shouldSnapHomeTopTabSelection(currentPage = 2, targetPage = 2))
    }

    @Test
    fun activePagerSwipe_prefersPagerTargetForTopTabViewportAnchor() {
        assertEquals(
            4,
            resolveTopTabViewportAnchorIndex(
                selectedIndex = 2,
                pagerCurrentPage = 2,
                pagerTargetPage = 4,
                pagerIsScrolling = true
            )
        )
    }

    @Test
    fun idlePagerViewportAnchor_prefersSettledPagerPage() {
        assertEquals(
            3,
            resolveTopTabViewportAnchorIndex(
                selectedIndex = 1,
                pagerCurrentPage = 3,
                pagerTargetPage = 4,
                pagerIsScrolling = false
            )
        )
    }

    @Test
    fun pagerSwipePosition_tracksTargetPageContinuously() {
        assertEquals(
            0.35f,
            resolveTopTabPagerPosition(
                selectedIndex = 0,
                pagerCurrentPage = 0,
                pagerTargetPage = 1,
                pagerCurrentPageOffsetFraction = -0.35f,
                pagerIsScrolling = true
            )
        )
        assertEquals(
            0.65f,
            resolveTopTabPagerPosition(
                selectedIndex = 1,
                pagerCurrentPage = 1,
                pagerTargetPage = 0,
                pagerCurrentPageOffsetFraction = 0.35f,
                pagerIsScrolling = true
            )
        )
    }

    @Test
    fun idlePagerPosition_prefersSettledPagerPage() {
        assertEquals(
            1f,
            resolveTopTabPagerPosition(
                selectedIndex = 2,
                pagerCurrentPage = 1,
                pagerTargetPage = 3,
                pagerCurrentPageOffsetFraction = 0.4f,
                pagerIsScrolling = false
            )
        )
    }
}
