package com.bbttvv.app.feature.video.screen

import com.bbttvv.app.feature.video.viewmodel.PlayerCommentSortMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerCommentsSidebarPolicyTest {
    @Test
    fun `sidebar occupies exactly one quarter of player width`() {
        assertEquals(0.25f, PLAYER_COMMENTS_SIDEBAR_WIDTH_FRACTION, 0.0001f)
    }

    @Test
    fun `header keeps count and current sort labels concise`() {
        assertEquals("1873 条", playerCommentCountLabel(1873))
        assertEquals("按热度", playerCommentSortLabel(PlayerCommentSortMode.Hot))
        assertEquals("按时间", playerCommentSortLabel(PlayerCommentSortMode.Time))
    }
}
