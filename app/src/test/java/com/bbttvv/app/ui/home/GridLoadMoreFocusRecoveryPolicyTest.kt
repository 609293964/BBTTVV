package com.bbttvv.app.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridLoadMoreFocusRecoveryPolicyTest {

    @Test
    fun `restores parked focus when load finishes without an appended list`() {
        assertTrue(
            GridLoadMoreFocusRecoveryPolicy.shouldRestoreFocus(
                hasPendingLoadMoreFocus = true,
                listChanged = false,
                loadMoreInProgress = false,
            )
        )
    }

    @Test
    fun `keeps parked focus while loading or when caller does not report loading`() {
        assertFalse(
            GridLoadMoreFocusRecoveryPolicy.shouldRestoreFocus(
                hasPendingLoadMoreFocus = true,
                listChanged = false,
                loadMoreInProgress = true,
            )
        )
        assertFalse(
            GridLoadMoreFocusRecoveryPolicy.shouldRestoreFocus(
                hasPendingLoadMoreFocus = true,
                listChanged = false,
                loadMoreInProgress = null,
            )
        )
    }

    @Test
    fun `does not restore when append commit will resolve pending focus`() {
        assertFalse(
            GridLoadMoreFocusRecoveryPolicy.shouldRestoreFocus(
                hasPendingLoadMoreFocus = true,
                listChanged = true,
                loadMoreInProgress = false,
            )
        )
    }
}
