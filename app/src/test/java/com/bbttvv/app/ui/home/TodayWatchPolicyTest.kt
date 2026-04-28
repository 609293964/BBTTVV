package com.bbttvv.app.ui.home

import com.bbttvv.app.data.model.response.Owner
import com.bbttvv.app.data.model.response.Stat
import com.bbttvv.app.data.model.response.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayWatchPolicyTest {

    @Test
    fun `plan prefers creators user watched more`() {
        val history = listOf(
            historyVideo(bvid = "h1", mid = 1, name = "UP-A", duration = 600, progress = 500, viewAt = 1_700_000_000),
            historyVideo(bvid = "h2", mid = 1, name = "UP-A", duration = 700, progress = 620, viewAt = 1_700_000_800),
            historyVideo(bvid = "h3", mid = 2, name = "UP-B", duration = 800, progress = 120, viewAt = 1_700_001_200)
        )
        val candidates = listOf(
            candidateVideo(bvid = "c1", mid = 1, name = "UP-A", title = "A 视频"),
            candidateVideo(bvid = "c2", mid = 2, name = "UP-B", title = "B 视频")
        )

        val plan = buildTodayWatchPlan(
            historyVideos = history,
            candidateVideos = candidates,
            mode = TodayWatchMode.RELAX,
            eyeCareNightActive = false,
            nowEpochSec = 1_700_010_000
        )

        assertEquals(1L, plan.upRanks.first().mid)
        assertEquals("c1", plan.videoQueue.first().bvid)
    }

    @Test
    fun `night signal pushes short and calm videos`() {
        val history = listOf(
            historyVideo(bvid = "h1", mid = 1, name = "UP-A", duration = 600, progress = 500, viewAt = 1_700_000_000)
        )
        val candidates = listOf(
            candidateVideo(
                bvid = "long_hot",
                mid = 1,
                name = "UP-A",
                duration = 3600,
                stat = Stat(view = 10_000, danmaku = 2_500),
                title = "高刺激长视频"
            ),
            candidateVideo(
                bvid = "short_calm",
                mid = 1,
                name = "UP-A",
                duration = 420,
                stat = Stat(view = 10_000, danmaku = 30),
                title = "轻松短视频"
            )
        )

        val plan = buildTodayWatchPlan(
            historyVideos = history,
            candidateVideos = candidates,
            mode = TodayWatchMode.RELAX,
            eyeCareNightActive = true,
            nowEpochSec = 1_700_010_000
        )

        assertEquals("short_calm", plan.videoQueue.first().bvid)
    }

    @Test
    fun `learn mode promotes knowledge titles`() {
        val history = listOf(
            historyVideo(bvid = "h1", mid = 8, name = "UP-K", duration = 900, progress = 720, viewAt = 1_700_000_000)
        )
        val candidates = listOf(
            candidateVideo(
                bvid = "learn",
                mid = 8,
                name = "UP-K",
                duration = 1400,
                stat = Stat(view = 8_000, danmaku = 80),
                title = "Kotlin 协程原理与实战教程"
            ),
            candidateVideo(
                bvid = "fun",
                mid = 8,
                name = "UP-K",
                duration = 240,
                stat = Stat(view = 8_000, danmaku = 80),
                title = "今日搞笑合集"
            )
        )

        val plan = buildTodayWatchPlan(
            historyVideos = history,
            candidateVideos = candidates,
            mode = TodayWatchMode.LEARN,
            eyeCareNightActive = false,
            nowEpochSec = 1_700_010_000
        )

        assertEquals("learn", plan.videoQueue.first().bvid)
    }

    @Test
    fun `plan diversifies creators in top queue`() {
        val history = listOf(
            historyVideo(bvid = "h1", mid = 1, name = "UP-A", duration = 600, progress = 550, viewAt = 1_700_000_000),
            historyVideo(bvid = "h2", mid = 1, name = "UP-A", duration = 620, progress = 560, viewAt = 1_700_000_500)
        )
        val candidates = listOf(
            candidateVideo(bvid = "a1", mid = 1, name = "UP-A", title = "A1"),
            candidateVideo(bvid = "a2", mid = 1, name = "UP-A", title = "A2"),
            candidateVideo(bvid = "b1", mid = 2, name = "UP-B", title = "B1"),
            candidateVideo(bvid = "c1", mid = 3, name = "UP-C", title = "C1")
        )

        val plan = buildTodayWatchPlan(
            historyVideos = history,
            candidateVideos = candidates,
            mode = TodayWatchMode.RELAX,
            eyeCareNightActive = false,
            nowEpochSec = 1_700_010_000,
            queueLimit = 4
        )

        val topThreeMids = plan.videoQueue.take(3).map { it.owner.mid }.toSet()
        assertTrue(topThreeMids.size >= 2)
    }

    @Test
    fun `negative feedback penalizes disliked creator and title`() {
        val history = listOf(
            historyVideo(bvid = "h1", mid = 1, name = "UP-A", duration = 600, progress = 550, viewAt = 1_700_000_000)
        )
        val candidates = listOf(
            candidateVideo(
                bvid = "disliked",
                mid = 8,
                name = "UP-X",
                duration = 600,
                stat = Stat(view = 20_000, danmaku = 90),
                title = "震惊吵闹整活合集"
            ),
            candidateVideo(
                bvid = "normal",
                mid = 2,
                name = "UP-B",
                duration = 620,
                stat = Stat(view = 16_000, danmaku = 80),
                title = "通勤学习总结"
            )
        )

        val plan = buildTodayWatchPlan(
            historyVideos = history,
            candidateVideos = candidates,
            mode = TodayWatchMode.RELAX,
            eyeCareNightActive = false,
            nowEpochSec = 1_700_010_000,
            penaltySignals = TodayWatchPenaltySignals(
                dislikedBvids = setOf("disliked"),
                dislikedCreatorMids = setOf(8L),
                dislikedKeywords = setOf("吵闹", "整活")
            )
        )

        assertEquals("normal", plan.videoQueue.first().bvid)
        assertFalse(plan.videoQueue.take(1).any { it.bvid == "disliked" })
    }

    @Test
    fun `creator signals boost preferred up even when history owner mid is missing`() {
        val history = listOf(
            VideoItem(
                bvid = "h_missing_mid",
                owner = Owner(mid = 0, name = "Unknown"),
                duration = 600,
                progress = 500,
                view_at = 1_700_000_000
            )
        )
        val candidates = listOf(
            candidateVideo(bvid = "prefer_a", mid = 11, name = "UP-A", title = "普通内容A"),
            candidateVideo(bvid = "prefer_b", mid = 22, name = "UP-B", title = "普通内容B")
        )

        val plan = buildTodayWatchPlan(
            historyVideos = history,
            candidateVideos = candidates,
            mode = TodayWatchMode.RELAX,
            eyeCareNightActive = false,
            nowEpochSec = 1_700_010_000,
            creatorSignals = listOf(
                TodayWatchCreatorSignal(
                    mid = 22,
                    name = "UP-B",
                    score = 6.5,
                    watchCount = 4
                )
            )
        )

        assertEquals("prefer_b", plan.videoQueue.first().bvid)
        assertEquals(22L, plan.upRanks.first().mid)
    }

    @Test
    fun `plan filters out consumed queue items`() {
        val candidates = listOf(
            candidateVideo(bvid = "keep", mid = 1, name = "UP-A", title = "保留视频"),
            candidateVideo(bvid = "consumed", mid = 2, name = "UP-B", title = "已看视频")
        )

        val plan = buildTodayWatchPlan(
            historyVideos = emptyList(),
            candidateVideos = candidates,
            mode = TodayWatchMode.RELAX,
            eyeCareNightActive = false,
            nowEpochSec = 1_700_010_000,
            penaltySignals = TodayWatchPenaltySignals(consumedBvids = setOf("consumed"))
        )

        assertTrue(plan.videoQueue.any { it.bvid == "keep" })
        assertFalse(plan.videoQueue.any { it.bvid == "consumed" })
    }

    private fun historyVideo(
        bvid: String,
        mid: Long,
        name: String,
        duration: Int,
        progress: Int,
        viewAt: Long
    ): VideoItem {
        return VideoItem(
            bvid = bvid,
            owner = Owner(mid = mid, name = name),
            duration = duration,
            progress = progress,
            view_at = viewAt
        )
    }

    private fun candidateVideo(
        bvid: String,
        mid: Long,
        name: String,
        title: String,
        duration: Int = 480,
        stat: Stat = Stat(view = 1_000, danmaku = 10)
    ): VideoItem {
        return VideoItem(
            bvid = bvid,
            title = title,
            owner = Owner(mid = mid, name = name),
            duration = duration,
            stat = stat
        )
    }
}
