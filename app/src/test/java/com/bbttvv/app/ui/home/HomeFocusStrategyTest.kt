package com.bbttvv.app.ui.home

import com.bbttvv.app.ui.components.AppTopLevelTab
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
    fun `back return restores video focus only when key and position are valid`() {
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

        assertFalse(
            HomeFocusStrategy.shouldRestoreBackReturnVideoFocus(
                scene = HomeFocusScene.BackReturn,
                selectedHomeTab = AppTopLevelTab.RECOMMEND,
                restoreVideoFocusKey = "BV1:1",
                restoreVideoIndex = -1,
            )
        )
    }
}
