package com.bbttvv.app.ui.home

import com.bbttvv.app.data.model.response.Stat
import com.bbttvv.app.data.model.response.VideoItem
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HomeVideoCardPayloadTest {
    @Test
    fun `stats-only update uses metadata payload`() {
        val oldItem = card(VideoItem(bvid = "BV1", title = "title", stat = Stat(view = 1)))
        val newItem = card(oldItem.video.copy(stat = Stat(view = 2)))

        assertNotNull(HomeVideoCardAdapter.VideoDiffCallback.getChangePayload(oldItem, newItem))
    }

    @Test
    fun `title update uses partial payload`() {
        val oldItem = card(VideoItem(bvid = "BV1", title = "old"))
        val newItem = card(oldItem.video.copy(title = "new"))

        assertNotNull(HomeVideoCardAdapter.VideoDiffCallback.getChangePayload(oldItem, newItem))
    }

    @Test
    fun `cover update uses partial payload`() {
        val oldItem = card(VideoItem(bvid = "BV1", pic = "old"))
        val newItem = card(oldItem.video.copy(pic = "new"))

        assertNotNull(HomeVideoCardAdapter.VideoDiffCallback.getChangePayload(oldItem, newItem))
    }

    @Test
    fun `structural update requires full bind`() {
        val oldItem = card(VideoItem(bvid = "BV1", rights = null))
        val newItem = card(oldItem.video.copy(isVertical = true))

        assertNull(HomeVideoCardAdapter.VideoDiffCallback.getChangePayload(oldItem, newItem))
    }

    private fun card(video: VideoItem): HomeRecommendVideoCardItem {
        return HomeRecommendVideoCardItem(
            key = "bvid:${video.bvid}",
            stableId = 1L,
            video = video,
        )
    }
}
