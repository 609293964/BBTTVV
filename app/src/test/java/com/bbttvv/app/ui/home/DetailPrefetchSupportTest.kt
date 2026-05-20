package com.bbttvv.app.ui.home

import com.bbttvv.app.data.model.response.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailPrefetchSupportTest {

    @Test
    fun `buildDetailPrefetchCandidates keeps focused item first and favors nearby neighbors`() {
        val videos = listOf(
            video("BV1"),
            video("BV2"),
            video("BV3"),
            video("BV4"),
            video("BV5")
        )

        val candidates = buildDetailPrefetchCandidates(
            focusedVideo = videos[2],
            videos = videos,
            index = 2
        )

        assertEquals(listOf("BV3", "BV4", "BV5", "BV2"), candidates.map { it.bvid })
    }

    @Test
    fun `buildHomeDetailSummaryCandidates limits home focus warmup to three items`() {
        val videos = listOf(
            video("BV1"),
            video("BV2"),
            video("BV3"),
            video("BV4"),
            video("BV5")
        )

        val candidates = buildHomeDetailSummaryCandidates(
            focusedVideo = videos[2],
            videos = videos,
            index = 2
        )

        assertEquals(listOf("BV3", "BV4", "BV2"), candidates.map { it.bvid })
    }

    @Test
    fun `buildDetailPrefetchCandidates deduplicates repeated logical videos`() {
        val focused = video("BV2", aid = 2, cid = 20)
        val duplicateNeighbor = video("BV2", aid = 2, cid = 20)
        val videos = listOf(video("BV1"), focused, duplicateNeighbor, video("BV3"))

        val candidates = buildDetailPrefetchCandidates(
            focusedVideo = focused,
            videos = videos,
            index = 1
        )

        assertEquals(listOf("BV2", "BV3", "BV1"), candidates.map { it.bvid })
    }

    @Test
    fun `focus summary prefetch delay adapts to dpad cadence`() {
        assertEquals(0L, FocusSummaryPrefetchDelayPolicy.delayMillis(null, currentFocusAtMs = 1_000L))
        assertEquals(250L, FocusSummaryPrefetchDelayPolicy.delayMillis(950L, currentFocusAtMs = 1_000L))
        assertEquals(100L, FocusSummaryPrefetchDelayPolicy.delayMillis(800L, currentFocusAtMs = 1_000L))
        assertEquals(0L, FocusSummaryPrefetchDelayPolicy.delayMillis(700L, currentFocusAtMs = 1_000L))
    }

    private fun video(
        bvid: String,
        aid: Long = bvid.removePrefix("BV").toLongOrNull() ?: 0L,
        cid: Long = aid * 10
    ): VideoItem {
        return VideoItem(
            bvid = bvid,
            aid = aid,
            cid = cid,
            title = bvid
        )
    }
}
