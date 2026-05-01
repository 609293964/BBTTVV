package com.bbttvv.app.feature.plugin

import com.bbttvv.app.core.store.CreatorSignalSnapshot
import com.bbttvv.app.core.store.TodayWatchDislikedVideoSnapshot
import com.bbttvv.app.core.store.TodayWatchFeedbackSnapshot
import com.bbttvv.app.ui.home.TodayWatchMode

data class TodayWatchTasteSignalUiModel(
    val label: String,
    val value: String
)

data class TodayWatchRecentDislikedVideoUiModel(
    val title: String,
    val subtitle: String
)

data class TodayWatchTasteInsightState(
    val modeTitle: String,
    val modeSummary: String,
    val preferredCreators: List<TodayWatchTasteSignalUiModel>,
    val negativeSignals: List<TodayWatchTasteSignalUiModel>,
    val recentDislikedVideos: List<TodayWatchRecentDislikedVideoUiModel>
)

fun buildTodayWatchTasteInsightState(
    mode: TodayWatchMode,
    feedbackSnapshot: TodayWatchFeedbackSnapshot,
    creatorSignals: List<CreatorSignalSnapshot>
): TodayWatchTasteInsightState {
    val preferredCreators = creatorSignals
        .sortedByDescending { it.score }
        .take(5)
        .map { signal ->
            TodayWatchTasteSignalUiModel(
                label = signal.name.ifBlank { "UP主${signal.mid}" },
                value = "${signal.watchCount} 次"
            )
        }
    val negativeKeywords = feedbackSnapshot.dislikedKeywords
        .take(8)
        .map { keyword -> TodayWatchTasteSignalUiModel(label = keyword, value = "已降权") }
    val negativeCreators = feedbackSnapshot.dislikedCreatorMids
        .take(4)
        .map { mid -> TodayWatchTasteSignalUiModel(label = "UP主$mid", value = "已降权") }

    return TodayWatchTasteInsightState(
        modeTitle = when (mode) {
            TodayWatchMode.RELAX -> "今晚轻松看"
            TodayWatchMode.LEARN -> "深度学习看"
        },
        modeSummary = when (mode) {
            TodayWatchMode.RELAX -> "优先短时长、低刺激、近期更新和轻松主题；降低硬核学习与高刺激内容。"
            TodayWatchMode.LEARN -> "优先教程、科普、技术、复盘和中长时长内容；降低短平快娱乐内容。"
        },
        preferredCreators = preferredCreators,
        negativeSignals = negativeKeywords + negativeCreators,
        recentDislikedVideos = feedbackSnapshot.recentDislikedVideos
            .sortedByDescending { it.dislikedAtMillis }
            .take(6)
            .map { it.toUiModel() }
    )
}

private fun TodayWatchDislikedVideoSnapshot.toUiModel(): TodayWatchRecentDislikedVideoUiModel {
    return TodayWatchRecentDislikedVideoUiModel(
        title = title.ifBlank { bvid },
        subtitle = creatorName.ifBlank {
            if (creatorMid > 0L) "UP主$creatorMid" else "未知 UP"
        }
    )
}
