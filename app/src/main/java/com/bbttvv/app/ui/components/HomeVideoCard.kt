package com.bbttvv.app.ui.components

import androidx.compose.runtime.Immutable
import com.bbttvv.app.core.util.FormatUtils
import com.bbttvv.app.core.util.formatShortVideoPubDate
import com.bbttvv.app.data.model.response.VideoItem

@Immutable
data class HomeVideoCardUiModel(
    val coverUrl: String,
    val title: String,
    val ownerName: String,
    val ownerFaceUrl: String,
    val pubDateText: String,
    val leadingMetaText: String,
    val durationMetaText: String,
    val compactMeta: Boolean
)

fun VideoItem.toHomeVideoCardUiModel(
    showHistoryProgressOnly: Boolean = false
): HomeVideoCardUiModel {
    val isBangumiCard = bvid.startsWith("ss") || bvid.startsWith("ep")
    val isLiveCard = !isBangumiCard && aid == 0L && cid == 0L && duration <= 0
    val showHistoryFallbackMeta = !isBangumiCard && (showHistoryProgressOnly || (!isLiveCard &&
        view_at > 0L &&
        stat.view <= 0 &&
        stat.danmaku <= 0))
    val durationMetaText = when {
        isBangumiCard -> collectionSubtitle.ifBlank { "\u756a\u5267" }
        isLiveCard -> "\u76f4\u64ad\u4e2d"
        else -> formatDuration(duration)
    }
    val leadingMetaText = when {
        isBangumiCard -> ""
        showHistoryFallbackMeta -> {
            if (progress > 0 && duration > 0) {
                "\u5df2\u770b ${formatDuration(progress.coerceIn(0, duration))}"
            } else {
                "\u89c2\u770b\u8bb0\u5f55"
            }
        }
        isLiveCard -> "\u5728\u7ebf ${FormatUtils.formatStat(stat.view.toLong())}"
        else -> {
            "\u64ad\u653e ${FormatUtils.formatStat(stat.view.toLong())}  " +
                "\u5f39\u5e55 ${FormatUtils.formatStat(stat.danmaku.toLong())}"
        }
    }
    return HomeVideoCardUiModel(
        coverUrl = pic,
        title = title,
        ownerName = owner.name,
        ownerFaceUrl = owner.face,
        pubDateText = formatShortVideoPubDate(pubdate),
        leadingMetaText = leadingMetaText,
        durationMetaText = durationMetaText,
        compactMeta = showHistoryProgressOnly
    )
}

fun List<VideoItem>.toHomeVideoCardUiModels(
    showHistoryProgressOnly: Boolean = false
): List<HomeVideoCardUiModel> {
    if (isEmpty()) return emptyList()
    return map { video -> video.toHomeVideoCardUiModel(showHistoryProgressOnly) }
}

private fun formatDuration(durationSeconds: Int): String {
    if (durationSeconds <= 0) return "--:--"
    val m = durationSeconds / 60
    val s = durationSeconds % 60
    val sStr = if (s < 10) "0$s" else s.toString()
    return if (m >= 60) {
        val h = m / 60
        val remainM = m % 60
        val mStr = if (remainM < 10) "0$remainM" else remainM.toString()
        "$h:$mStr:$sStr"
    } else {
        val mStr = if (m < 10) "0$m" else m.toString()
        "$mStr:$sStr"
    }
}
