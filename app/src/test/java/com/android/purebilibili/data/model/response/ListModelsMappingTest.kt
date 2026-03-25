package com.android.purebilibili.data.model.response

import kotlin.test.Test
import kotlin.test.assertEquals

class ListModelsMappingTest {

    @Test
    fun `recommend item toVideoItem keeps pubdate`() {
        val item = RecommendItem(
            id = 123L,
            bvid = "BV1xx411c7mD",
            cid = 456L,
            title = "test",
            pic = "cover",
            duration = 120,
            pubdate = 1_730_000_000L
        )

        val videoItem = item.toVideoItem()

        assertEquals(1_730_000_000L, videoItem.pubdate)
    }

    @Test
    fun `popular item toVideoItem keeps pubdate`() {
        val item = PopularItem(
            aid = 123L,
            bvid = "BV1xx411c7mD",
            cid = 456L,
            title = "test",
            pic = "cover",
            duration = 120,
            pubdate = 1_731_111_111L
        )

        val videoItem = item.toVideoItem()

        assertEquals(1_731_111_111L, videoItem.pubdate)
    }
}
