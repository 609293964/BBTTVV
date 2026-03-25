package com.android.purebilibili.feature.list

import com.android.purebilibili.data.model.response.HistoryBusiness
import com.android.purebilibili.data.model.response.HistoryItem
import com.android.purebilibili.data.model.response.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryPaginationPolicyTest {

    @Test
    fun `filterAppendableHistoryItems keeps article entries with blank bvid when render key is unique`() {
        val currentKeys = setOf("BV1archive", "article_1001")
        val incoming = listOf(
            HistoryItem(
                videoItem = VideoItem(id = 1001L, title = "旧专栏"),
                business = HistoryBusiness.ARTICLE
            ),
            HistoryItem(
                videoItem = VideoItem(id = 2002L, title = "新专栏"),
                business = HistoryBusiness.ARTICLE
            ),
            HistoryItem(
                videoItem = VideoItem(id = 3003L, bvid = "BV1new"),
                business = HistoryBusiness.ARCHIVE
            )
        )

        val result = filterAppendableHistoryItems(
            currentRenderKeys = currentKeys,
            incomingItems = incoming
        )

        assertEquals(
            listOf(
                incoming[1],
                incoming[2]
            ),
            result
        )
    }
}
