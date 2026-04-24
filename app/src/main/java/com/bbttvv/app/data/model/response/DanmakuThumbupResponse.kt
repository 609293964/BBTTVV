package com.bbttvv.app.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DanmakuThumbupStatsResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 1,
    val data: Map<String, DanmakuThumbupStatsItem> = emptyMap()
)

@Serializable
data class DanmakuThumbupStatsItem(
    val likes: Int = 0,
    @SerialName("user_like")
    val userLike: Int = 0,
    @SerialName("id_str")
    val idStr: String = ""
)

