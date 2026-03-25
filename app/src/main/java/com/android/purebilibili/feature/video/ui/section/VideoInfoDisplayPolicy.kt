package com.android.purebilibili.feature.video.ui.section

import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.AiSummaryData
import java.util.Locale
import java.util.TimeZone

private val CURRENT_AFFAIRS_PARTITION_KEYWORDS = listOf(
    "资讯",
    "热点",
    "环球",
    "社会",
    "新闻",
    "时政"
)

private val CURRENT_AFFAIRS_TITLE_KEYWORDS = listOf(
    "时政",
    "新闻",
    "快讯",
    "发布会",
    "记者会",
    "回应",
    "通报",
    "局势",
    "突发",
    "声明",
    "公告",
    "联合国",
    "白宫",
    "国务院",
    "外交部",
    "国防部",
    "两会",
    "俄乌",
    "巴以",
    "选举"
)

internal fun shouldShowAiSummaryEntry(
    aiSummary: AiSummaryData?,
    isAiSummaryEntryEnabled: Boolean
): Boolean {
    return isAiSummaryEntryEnabled && hasAiSummaryContent(aiSummary)
}

internal fun hasAiSummaryContent(aiSummary: AiSummaryData?): Boolean {
    val modelResult = aiSummary?.modelResult ?: return false
    if (aiSummary.code != 0) return false
    return modelResult.summary.isNotBlank() || modelResult.outline.isNotEmpty()
}

internal fun shouldShowInlineOwnerIdentity(showOwnerAvatar: Boolean): Boolean {
    return !showOwnerAvatar
}

internal fun shouldEmphasizePrecisePublishTime(
    partitionName: String,
    title: String
): Boolean {
    return CURRENT_AFFAIRS_PARTITION_KEYWORDS.any { keyword ->
        partitionName.contains(keyword, ignoreCase = true)
    } || CURRENT_AFFAIRS_TITLE_KEYWORDS.any { keyword ->
        title.contains(keyword, ignoreCase = true)
    }
}

internal fun resolvePublishTimeRowText(
    pubdate: Long,
    partitionName: String,
    title: String,
    nowMs: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    if (pubdate <= 0L) return ""

    val relativeText = FormatUtils.formatPublishTime(
        timestampSeconds = pubdate,
        nowMs = nowMs
    )
    if (relativeText.isBlank()) return ""

    return if (shouldEmphasizePrecisePublishTime(partitionName = partitionName, title = title)) {
        val preciseText = FormatUtils.formatPrecisePublishTime(
            timestampSeconds = pubdate,
            locale = locale,
            timeZone = timeZone
        )
        "发布时间 $relativeText  ·  $preciseText"
    } else {
        "发布于 $relativeText"
    }
}

internal fun resolveDynamicPublishTimeRowText(
    publishTs: Long,
    title: String,
    nowMs: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    if (publishTs <= 0L) return ""

    val relativeText = FormatUtils.formatPublishTime(
        timestampSeconds = publishTs,
        nowMs = nowMs
    )
    if (relativeText.isBlank()) return ""

    return if (shouldEmphasizePrecisePublishTime(partitionName = "", title = title)) {
        val preciseText = FormatUtils.formatPrecisePublishTime(
            timestampSeconds = publishTs,
            locale = locale,
            timeZone = timeZone
        )
        "动态发布 $relativeText  ·  $preciseText"
    } else {
        "动态发布 $relativeText"
    }
}
