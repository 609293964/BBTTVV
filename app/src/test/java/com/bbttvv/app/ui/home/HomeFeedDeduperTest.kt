package com.bbttvv.app.ui.home

import com.bbttvv.app.data.model.response.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFeedDeduperTest {
    @Test
    fun `dedupe new videos removes duplicates inside incoming page`() {
        val videos = listOf(
            video("BV1", "first"),
            video("BV2", "second"),
            video("BV1", "first duplicate"),
        )

        val deduped = HomeFeedDeduper.dedupeNewVideos(
            existingVideos = emptyList(),
            incomingVideos = videos,
        )

        assertEquals(listOf("BV1", "BV2"), deduped.map { it.bvid })
    }

    @Test
    fun `dedupe new videos removes items already loaded in previous pages`() {
        val existing = listOf(video("BV1", "first"))
        val incoming = listOf(
            video("BV1", "first duplicate"),
            video("BV2", "second"),
        )

        val deduped = HomeFeedDeduper.dedupeNewVideos(
            existingVideos = existing,
            incomingVideos = incoming,
        )

        assertEquals(listOf("BV2"), deduped.map { it.bvid })
    }

    private fun video(bvid: String, title: String): VideoItem {
        return VideoItem(bvid = bvid, title = title, pic = "https://example.com/$bvid.jpg")
    }
}
