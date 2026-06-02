package com.bbttvv.app.ui.home

import com.bbttvv.app.ui.components.AppTopLevelTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFocusStrategyTest {
    @Test
    fun `initial recommend enter may reset scroll when not restoring focus`() {
        assertTrue(
            HomeFocusStrategy.shouldResetRecommendScroll(
                scene = HomeFocusScene.InitialEnter,
                selectedHomeTab = AppTopLevelTab.RECOMMEND,
                isRestoringBackReturnFocus = false,
            )
        )
    }

    @Test
    fun `tab switch back to recommend preserves previous scroll anchor`() {
        assertFalse(
            HomeFocusStrategy.shouldResetRecommendScroll(
                scene = HomeFocusScene.TabSwitch,
                selectedHomeTab = AppTopLevelTab.RECOMMEND,
                isRestoringBackReturnFocus = false,
            )
        )
    }

    @Test
    fun `back return to recommend does not reset scroll after focus key is cleared`() {
        assertFalse(
            HomeFocusStrategy.shouldResetRecommendScroll(
                scene = HomeFocusScene.BackReturn,
                selectedHomeTab = AppTopLevelTab.RECOMMEND,
                isRestoringBackReturnFocus = false,
            )
        )
    }

    @Test
    fun `active back return restore suppresses recommend scroll reset`() {
        assertFalse(
            HomeFocusStrategy.shouldResetRecommendScroll(
                scene = HomeFocusScene.InitialEnter,
                selectedHomeTab = AppTopLevelTab.RECOMMEND,
                isRestoringBackReturnFocus = true,
            )
        )
    }

    @Test
    fun `initial recommend enter does not reset after content already has focus`() {
        assertFalse(
            HomeFocusStrategy.shouldResetRecommendScroll(
                scene = HomeFocusScene.InitialEnter,
                selectedHomeTab = AppTopLevelTab.RECOMMEND,
                isRestoringBackReturnFocus = false,
                hasContentFocus = true,
            )
        )
    }

    @Test
    fun `initial recommend enter does not reset after grid remembered focus`() {
        assertFalse(
            HomeFocusStrategy.shouldResetRecommendScroll(
                scene = HomeFocusScene.InitialEnter,
                selectedHomeTab = AppTopLevelTab.RECOMMEND,
                isRestoringBackReturnFocus = false,
                hasRememberedGridFocus = true,
            )
        )
    }

    @Test
    fun `back return restores video focus when key exists even if position is missing`() {
        assertTrue(
            HomeFocusStrategy.shouldRestoreBackReturnVideoFocus(
                scene = HomeFocusScene.BackReturn,
                selectedHomeTab = AppTopLevelTab.RECOMMEND,
                restoreVideoFocusKey = "BV1:1",
                restoreVideoIndex = 3,
            )
        )

        assertFalse(
            HomeFocusStrategy.shouldRestoreBackReturnVideoFocus(
                scene = HomeFocusScene.TabSwitch,
                selectedHomeTab = AppTopLevelTab.RECOMMEND,
                restoreVideoFocusKey = "BV1:1",
                restoreVideoIndex = 3,
            )
        )

        assertFalse(
            HomeFocusStrategy.shouldRestoreBackReturnVideoFocus(
                scene = HomeFocusScene.BackReturn,
                selectedHomeTab = AppTopLevelTab.POPULAR,
                restoreVideoFocusKey = "BV1:1",
                restoreVideoIndex = 3,
            )
        )

        assertTrue(
            HomeFocusStrategy.shouldRestoreBackReturnVideoFocus(
                scene = HomeFocusScene.BackReturn,
                selectedHomeTab = AppTopLevelTab.RECOMMEND,
                restoreVideoFocusKey = "BV1:1",
                restoreVideoIndex = -1,
            )
        )
    }

    @Test
    fun `recommend card back resets grid before requesting top bar focus`() {
        val events = mutableListOf<String>()

        val handled = HomeRecommendBackReturnPolicy.handleBackToTopBar(
            resetGridToTop = { events += "reset-grid" },
            requestTopBarFocus = {
                events += "request-top-bar"
                true
            },
        )

        assertTrue(handled)
        assertEquals(listOf("reset-grid", "request-top-bar"), events)
    }

    @Test
    fun `recommend card back returns top bar focus result after reset`() {
        val events = mutableListOf<String>()

        val handled = HomeRecommendBackReturnPolicy.handleBackToTopBar(
            resetGridToTop = { events += "reset-grid" },
            requestTopBarFocus = {
                events += "request-top-bar"
                false
            },
        )

        assertFalse(handled)
        assertEquals(listOf("reset-grid", "request-top-bar"), events)
    }

    @Test
    fun `home top bar height uses fallback until measured`() {
        assertEquals(
            144,
            effectiveTopBarHeightPx(
                measuredTopBarHeightPx = 0,
                fallbackTopBarHeightPx = 144,
            ),
        )
        assertEquals(
            132,
            effectiveTopBarHeightPx(
                measuredTopBarHeightPx = 132,
                fallbackTopBarHeightPx = 144,
            ),
        )
    }

    @Test
    fun `collapsing header follows content focus regardless of top bar visibility`() {
        assertFalse(
            shouldCollapseHomeHeader(
                usesCollapsingHomeHeader = true,
                isContentFocused = false,
            )
        )
        assertTrue(
            shouldCollapseHomeHeader(
                usesCollapsingHomeHeader = true,
                isContentFocused = true,
            )
        )
        assertFalse(
            shouldCollapseHomeHeader(
                usesCollapsingHomeHeader = false,
                isContentFocused = true,
            )
        )
    }
}
