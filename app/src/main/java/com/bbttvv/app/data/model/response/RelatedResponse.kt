package com.bbttvv.app.data.model.response

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
data class RelatedResponse(
    val data: List<RelatedVideo>? = null
)

@Serializable
@Immutable
data class RelatedVideo(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val owner: Owner = Owner(),
    val stat: Stat = Stat(),
    val duration: Int = 0 // 视频时长(秒)
)

