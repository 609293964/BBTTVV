package com.bbttvv.app.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * AI 视频总结响应
 * API: https://api.bilibili.com/x/web-interface/view/conclusion/get
 */
@Serializable
data class AiSummaryResponse(
    val code: Int = 0,
    val message: String = "",
    val data: AiSummaryData? = null
)

@Serializable
data class AiSummaryData(
    val code: Int = 0, // -1: 不支持, 0: 有摘要, 1: 无摘要
    @SerialName("model_result")
    val modelResult: AiModelResult? = null,
    val stid: String = "",
    val status: Int = 0,
    @SerialName("like_num")
    val likeNum: Int = 0,
    @SerialName("dislike_num")
    val dislikeNum: Int = 0
)

@Serializable
data class AiModelResult(
    @SerialName("result_type")
    val resultType: Int = 0, // 0: 无, 1: 仅摘要, 2: 摘要+大纲
    val summary: String = "",
    val outline: List<AiOutline> = emptyList(),
    val subtitle: List<AiSubtitle> = emptyList()
)

@Serializable
data class AiOutline(
    val title: String = "",
    @SerialName("part_outline")
    val partOutline: List<AiPartOutline> = emptyList(),
    val timestamp: Long = 0
)

@Serializable
data class AiPartOutline(
    val timestamp: Long = 0,
    val content: String = ""
)

@Serializable
data class AiSubtitle(
    val title: String = "",
    @SerialName("part_subtitle")
    val partSubtitle: List<AiPartSubtitle> = emptyList(),
    val timestamp: Long = 0
)

@Serializable
data class AiPartSubtitle(
    val content: String = "",
    @SerialName("start_timestamp")
    val startTimestamp: Long = 0,
    @SerialName("end_timestamp")
    val endTimestamp: Long = 0
)

