package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.data.model.response.Page
import com.bbttvv.app.data.model.response.RelatedVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPlaybackEndPolicyTest {
    @Test
    fun `direct playback end actions resolve without list context`() {
        assertTrue(
            resolvePlayerPlaybackEndTarget(
                action = SettingsManager.PlayerPlaybackEndAction.NONE,
                currentBvid = "BV1",
                pages = emptyList(),
                currentPageIndex = 0,
                relatedVideos = emptyList(),
            ) is PlayerPlaybackEndTarget.None
        )
        assertTrue(
            resolvePlayerPlaybackEndTarget(
                action = SettingsManager.PlayerPlaybackEndAction.LOOP_ONE,
                currentBvid = "BV1",
                pages = emptyList(),
                currentPageIndex = 0,
                relatedVideos = emptyList(),
            ) is PlayerPlaybackEndTarget.LoopOne
        )
        assertTrue(
            resolvePlayerPlaybackEndTarget(
                action = SettingsManager.PlayerPlaybackEndAction.RETURN,
                currentBvid = "BV1",
                pages = emptyList(),
                currentPageIndex = 0,
                relatedVideos = emptyList(),
            ) is PlayerPlaybackEndTarget.Return
        )
    }

    @Test
    fun `auto next prefers next page over related video`() {
        val target = resolvePlayerPlaybackEndTarget(
            action = SettingsManager.PlayerPlaybackEndAction.AUTO_NEXT,
            currentBvid = "BV_CURRENT",
            pages = listOf(
                Page(cid = 11L, page = 1, part = "P1"),
                Page(cid = 22L, page = 2, part = "P2"),
            ),
            currentPageIndex = 0,
            relatedVideos = listOf(RelatedVideo(bvid = "BV_RELATED", title = "Related")),
        )

        assertTrue(target is PlayerPlaybackEndTarget.PageTarget)
        val pageTarget = target as PlayerPlaybackEndTarget.PageTarget
        assertEquals(22L, pageTarget.page.cid)
        assertEquals(1, pageTarget.index)
    }

    @Test
    fun `auto next uses related video when there is no next page`() {
        val target = resolvePlayerPlaybackEndTarget(
            action = SettingsManager.PlayerPlaybackEndAction.AUTO_NEXT,
            currentBvid = "BV_CURRENT",
            pages = listOf(Page(cid = 11L, page = 1, part = "P1")),
            currentPageIndex = 0,
            relatedVideos = listOf(RelatedVideo(bvid = "BV_RELATED", title = "Related")),
        )

        assertTrue(target is PlayerPlaybackEndTarget.RelatedTarget)
        assertEquals(
            "BV_RELATED",
            (target as PlayerPlaybackEndTarget.RelatedTarget).video.bvid,
        )
    }

    @Test
    fun `auto next skips related video matching current bvid`() {
        val target = resolvePlayerPlaybackEndTarget(
            action = SettingsManager.PlayerPlaybackEndAction.AUTO_NEXT,
            currentBvid = "BV_CURRENT",
            pages = listOf(Page(cid = 11L, page = 1, part = "P1")),
            currentPageIndex = 0,
            relatedVideos = listOf(
                RelatedVideo(bvid = "bv_current", title = "Self"),
                RelatedVideo(bvid = "BV_NEXT", title = "Next"),
            ),
        )

        assertTrue(target is PlayerPlaybackEndTarget.RelatedTarget)
        assertEquals(
            "BV_NEXT",
            (target as PlayerPlaybackEndTarget.RelatedTarget).video.bvid,
        )
    }

    @Test
    fun `auto next falls back to return when no target exists`() {
        val target = resolvePlayerPlaybackEndTarget(
            action = SettingsManager.PlayerPlaybackEndAction.AUTO_NEXT,
            currentBvid = "BV_CURRENT",
            pages = listOf(Page(cid = 11L, page = 1, part = "P1")),
            currentPageIndex = 0,
            relatedVideos = listOf(RelatedVideo(bvid = "BV_CURRENT", title = "Self")),
        )

        assertTrue(target is PlayerPlaybackEndTarget.Return)
    }

    @Test
    fun `auto next prompt describes playable targets`() {
        val pagePrompt = buildPlayerAutoNextPrompt(
            promptId = 1L,
            target = PlayerPlaybackEndTarget.PageTarget(
                page = Page(cid = 22L, page = 2, part = "第二段"),
                index = 1,
            ),
        )
        val relatedPrompt = buildPlayerAutoNextPrompt(
            promptId = 2L,
            target = PlayerPlaybackEndTarget.RelatedTarget(
                video = RelatedVideo(bvid = "BV_NEXT", title = "推荐视频"),
            ),
        )

        assertEquals("第二段", pagePrompt?.title)
        assertEquals("即将播放下一分P", pagePrompt?.subtitle)
        assertEquals("推荐视频", relatedPrompt?.title)
        assertEquals("即将播放相关推荐", relatedPrompt?.subtitle)
    }
}
