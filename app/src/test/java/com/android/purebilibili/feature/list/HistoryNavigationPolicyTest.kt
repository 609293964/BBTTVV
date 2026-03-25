package com.android.purebilibili.feature.list

import com.android.purebilibili.data.model.response.HistoryBusiness
import com.android.purebilibili.data.model.response.HistoryItem
import com.android.purebilibili.data.model.response.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryNavigationPolicyTest {

    @Test
    fun `history lookup key prefers bvid for archive items`() {
        val key = resolveHistoryLookupKey(
            HistoryItem(
                videoItem = VideoItem(
                    bvid = "BV1archive",
                    id = 123L
                ),
                business = HistoryBusiness.ARCHIVE
            )
        )

        assertEquals("BV1archive", key)
    }

    @Test
    fun `history lookup key falls back to live render key when bvid is blank`() {
        val key = resolveHistoryLookupKey(
            HistoryItem(
                videoItem = VideoItem(
                    id = 9988L,
                    title = "直播回放"
                ),
                business = HistoryBusiness.LIVE,
                roomId = 445566L
            )
        )

        assertEquals("live_445566", key)
    }

    @Test
    fun `history navigation kind resolves article entries explicitly`() {
        val kind = resolveHistoryNavigationKind(
            HistoryItem(
                videoItem = VideoItem(
                    id = 334455L,
                    title = "专栏"
                ),
                business = HistoryBusiness.ARTICLE
            )
        )

        assertEquals(HistoryNavigationKind.ARTICLE, kind)
    }
}
