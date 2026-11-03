package com.bbttvv.app.data.model.response

import kotlinx.serialization.Serializable

@Serializable
data class RecommendationFeedbackMetadata(
    val goto: String = "",
    val param: String = "",
    val reasons: List<RecommendationFeedbackReason> = emptyList(),
    val supportsServerSync: Boolean = false,
    val accountMid: Long = 0L,
)

@Serializable
data class RecommendationFeedbackReason(val id: Long = 0L, val name: String = "", val type: String = "dislike")

@Serializable
data class MobileFeedThreePoint(val type: String = "", val reasons: List<RecommendationFeedbackReason> = emptyList())

internal fun RecommendationFeedbackMetadata.defaultDislikeReason(): RecommendationFeedbackReason? =
    reasons.firstOrNull { it.type == "dislike" && it.id > 0 && it.name.trim() == "不感兴趣" }
