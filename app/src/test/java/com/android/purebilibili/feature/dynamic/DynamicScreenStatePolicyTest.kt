package com.android.purebilibili.feature.dynamic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicScreenStatePolicyTest {

    @Test
    fun `horizontal dynamic header should use tighter list top padding`() {
        assertEquals(168, resolveDynamicListTopPaddingExtraDp(isHorizontalMode = true))
        assertEquals(100, resolveDynamicListTopPaddingExtraDp(isHorizontalMode = false))
    }

    @Test
    fun `horizontal user list should use compact vertical padding`() {
        assertEquals(4, resolveHorizontalUserListVerticalPaddingDp())
    }

    @Test
    fun `error overlay should show when active list is empty and error exists`() {
        assertTrue(
            shouldShowDynamicErrorOverlay(
                error = "加载失败",
                activeItemsCount = 0
            )
        )
    }

    @Test
    fun `error overlay should hide when active list has data`() {
        assertFalse(
            shouldShowDynamicErrorOverlay(
                error = "加载失败",
                activeItemsCount = 3
            )
        )
    }

    @Test
    fun `loading footer should follow active list size`() {
        assertTrue(shouldShowDynamicLoadingFooter(isLoading = true, activeItemsCount = 1))
        assertFalse(shouldShowDynamicLoadingFooter(isLoading = true, activeItemsCount = 0))
        assertFalse(shouldShowDynamicLoadingFooter(isLoading = false, activeItemsCount = 2))
    }

    @Test
    fun `no more footer should follow active hasMore and list size`() {
        assertTrue(shouldShowDynamicNoMoreFooter(hasMore = false, activeItemsCount = 1))
        assertFalse(shouldShowDynamicNoMoreFooter(hasMore = true, activeItemsCount = 1))
        assertFalse(shouldShowDynamicNoMoreFooter(hasMore = false, activeItemsCount = 0))
    }

    @Test
    fun `followed user list reset should trigger only for fresh prepended refresh while viewing all`() {
        assertTrue(
            shouldResetFollowedUserListToTopOnRefresh(
                boundaryKey = "dyn:123",
                prependedCount = 3,
                selectedUserId = null,
                handledBoundaryKey = null
            )
        )
        assertFalse(
            shouldResetFollowedUserListToTopOnRefresh(
                boundaryKey = "dyn:123",
                prependedCount = 0,
                selectedUserId = null,
                handledBoundaryKey = null
            )
        )
        assertFalse(
            shouldResetFollowedUserListToTopOnRefresh(
                boundaryKey = "dyn:123",
                prependedCount = 2,
                selectedUserId = 10001L,
                handledBoundaryKey = null
            )
        )
        assertFalse(
            shouldResetFollowedUserListToTopOnRefresh(
                boundaryKey = "dyn:123",
                prependedCount = 2,
                selectedUserId = null,
                handledBoundaryKey = "dyn:123"
            )
        )
    }

    @Test
    fun `clicking the selected user again should return to all followed dynamics`() {
        assertNull(
            resolveDynamicSelectedUserIdAfterClick(
                selectedUserId = 10001L,
                clickedUserId = 10001L
            )
        )
    }

    @Test
    fun `clicking a different user should switch the dynamic filter`() {
        assertEquals(
            10002L,
            resolveDynamicSelectedUserIdAfterClick(
                selectedUserId = 10001L,
                clickedUserId = 10002L
            )
        )
        assertEquals(
            10003L,
            resolveDynamicSelectedUserIdAfterClick(
                selectedUserId = null,
                clickedUserId = 10003L
            )
        )
    }
}
