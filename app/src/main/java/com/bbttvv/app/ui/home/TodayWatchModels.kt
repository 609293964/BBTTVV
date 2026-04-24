package com.bbttvv.app.ui.home

import androidx.compose.runtime.Immutable
import com.bbttvv.app.data.model.response.VideoItem
import kotlinx.serialization.Serializable

@Serializable
enum class TodayWatchMode(val label: String) {
    RELAX("今晚轻松看"),
    LEARN("深度学习看")
}

@Immutable
data class TodayUpRank(
    val mid: Long,
    val name: String,
    val score: Double,
    val watchCount: Int
)

@Immutable
data class TodayWatchPlan(
    val mode: TodayWatchMode = TodayWatchMode.RELAX,
    val upRanks: List<TodayUpRank> = emptyList(),
    val videoQueue: List<VideoItem> = emptyList(),
    val explanationByBvid: Map<String, String> = emptyMap(),
    val historySampleCount: Int = 0,
    val nightSignalUsed: Boolean = false,
    val generatedAt: Long = 0L
)

internal data class TodayWatchCreatorSignal(
    val mid: Long,
    val name: String = "",
    val score: Double,
    val watchCount: Int = 1
)

internal data class TodayWatchPenaltySignals(
    val consumedBvids: Set<String> = emptySet(),
    val dislikedBvids: Set<String> = emptySet(),
    val dislikedCreatorMids: Set<Long> = emptySet(),
    val dislikedKeywords: Set<String> = emptySet()
)
