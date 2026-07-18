package com.bbttvv.app.ui.components

import com.bbttvv.app.data.model.response.Stat
import com.bbttvv.app.data.model.response.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeVideoCardUiModelTest {

    @Test
    fun `live viewer count uses the shared Chinese compact format`() {
        val video = VideoItem(
            aid = 0L,
            cid = 0L,
            duration = 0,
            stat = Stat(view = 6_039_000),
        )

        assertEquals("在线 603.9万", video.toHomeVideoCardUiModel().leadingMetaText)
    }

    @Test
    fun `video stats use the shared Chinese compact format`() {
        val video = VideoItem(
            aid = 1L,
            cid = 2L,
            duration = 60,
            stat = Stat(view = 8_148_000, danmaku = 118_000),
        )

        assertEquals(
            "播放 814.8万  弹幕 11.8万",
            video.toHomeVideoCardUiModel().leadingMetaText,
        )
    }
}
