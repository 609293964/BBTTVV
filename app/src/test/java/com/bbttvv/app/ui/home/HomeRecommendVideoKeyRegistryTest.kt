package com.bbttvv.app.ui.home

import com.bbttvv.app.data.model.response.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRecommendVideoKeyRegistryTest {
    @Test
    fun `append only assigns keys for the new page`() {
        val registry = HomeRecommendVideoKeyRegistry()
        val firstPage = registry.resetAndBuild(
            listOf(
                video("BV1"),
                video("BV2"),
            )
        )

        val appended = registry.append(
            videos = listOf(video("BV3")),
            startIndex = firstPage.size,
        )

        assertEquals(listOf("bvid:BV1", "bvid:BV2"), firstPage.map { it.key })
        assertEquals(listOf("bvid:BV3"), appended.map { it.key })
    }

    @Test
    fun `dismiss keeps remaining item keys stable`() {
        val registry = HomeRecommendVideoKeyRegistry()
        val initial = registry.resetAndBuild(
            listOf(
                video("BV1"),
                video("BV2"),
                video("BV3"),
            )
        )

        val afterDismiss = initial.filterNot { it.video.bvid == "BV2" }
        val appended = registry.append(
            videos = listOf(video("BV4")),
            startIndex = afterDismiss.size,
        )

        assertEquals(
            listOf("bvid:BV1", "bvid:BV3", "bvid:BV4"),
            (afterDismiss + appended).map { it.key },
        )
    }

    @Test
    fun `plugin reapply reuses assigned keys and keys newly visible items`() {
        val registry = HomeRecommendVideoKeyRegistry()
        val initial = registry.resetAndBuild(
            listOf(
                video("BV1"),
                video("BV2"),
            )
        )

        val reapplied = registry.rebuildReusingAssigned(
            listOf(
                video("BV2", title = "second updated"),
                video("BV3"),
            )
        )

        assertEquals("bvid:BV2", reapplied[0].key)
        assertEquals(initial[1].stableId, reapplied[0].stableId)
        assertEquals("bvid:BV3", reapplied[1].key)
    }

    @Test
    fun `refresh resets duplicate numbering`() {
        val registry = HomeRecommendVideoKeyRegistry()
        registry.resetAndBuild(
            listOf(
                video("BV1", title = "first"),
                video("BV1", title = "duplicate"),
            )
        )
        registry.append(
            videos = listOf(video("BV1", title = "third")),
            startIndex = 2,
        )

        val refreshed = registry.resetAndBuild(listOf(video("BV1", title = "refreshed")))

        assertEquals(listOf("bvid:BV1"), refreshed.map { it.key })
    }

    @Test
    fun `key priority matches recommendation identity order`() {
        val registry = HomeRecommendVideoKeyRegistry()

        val items = registry.resetAndBuild(
            listOf(
                VideoItem(dynamicId = "D1", bvid = "BV1", title = "dynamic wins"),
                VideoItem(id = 2L, title = "id wins"),
                VideoItem(collectionId = 3L, title = "collection wins"),
                VideoItem(aid = 4L, cid = 5L, title = "aid cid wins"),
                VideoItem(title = "title", pic = "https://example.com/cover.jpg"),
                VideoItem(),
            )
        )

        assertEquals(
            listOf(
                "dynamic:D1",
                "id:2",
                "collection:3",
                "aid:4:cid:5",
                "content:title:https://example.com/cover.jpg",
                "position:5",
            ),
            items.map { it.key },
        )
    }

    private fun video(
        bvid: String,
        title: String = bvid,
    ): VideoItem {
        return VideoItem(
            bvid = bvid,
            title = title,
            pic = "https://example.com/$bvid.jpg",
        )
    }
}
