package com.bbttvv.app.feature.video.screen

import com.bbttvv.app.feature.video.viewmodel.PlayerCommentSortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerCommentsSidebarPolicyTest {
    @Test
    fun `sidebar occupies thirty percent of player width`() {
        assertEquals(0.30f, PLAYER_COMMENTS_SIDEBAR_WIDTH_FRACTION, 0.0001f)
    }

    @Test
    fun `danmaku keeps left seventy percent visible while comments are open`() {
        assertEquals(1f, resolvePlayerDanmakuVisibleWidthFraction(false), 0.0001f)
        assertEquals(0.70f, resolvePlayerDanmakuVisibleWidthFraction(true), 0.0001f)
    }

    @Test
    fun `avatar requests are staggered in bounded batches`() {
        assertEquals(140L, resolvePlayerCommentAvatarLoadDelayMs(0))
        assertEquals(190L, resolvePlayerCommentAvatarLoadDelayMs(1))
        assertEquals(540L, resolvePlayerCommentAvatarLoadDelayMs(8))
        assertEquals(140L, resolvePlayerCommentAvatarLoadDelayMs(9))
    }

    @Test
    fun `auto pagination requires real scroll and only fires once per item count`() {
        assertFalse(
            shouldAutoLoadPlayerComments(
                hasMore = true,
                isAppending = false,
                hasUserScrolled = false,
                lastVisibleIndex = 9,
                totalItems = 10,
                lastRequestedItemCount = -1,
            ),
        )
        assertTrue(
            shouldAutoLoadPlayerComments(
                hasMore = true,
                isAppending = false,
                hasUserScrolled = true,
                lastVisibleIndex = 9,
                totalItems = 10,
                lastRequestedItemCount = -1,
            ),
        )
        assertFalse(
            shouldAutoLoadPlayerComments(
                hasMore = true,
                isAppending = false,
                hasUserScrolled = true,
                lastVisibleIndex = 9,
                totalItems = 10,
                lastRequestedItemCount = 10,
            ),
        )
    }

    @Test
    fun `header keeps count and current sort labels concise`() {
        assertEquals("1873 条", playerCommentCountLabel(1873))
        assertEquals("按热度", playerCommentSortLabel(PlayerCommentSortMode.Hot))
        assertEquals("按时间", playerCommentSortLabel(PlayerCommentSortMode.Time))
    }
}
