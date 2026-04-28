package com.bbttvv.app.data.model.response

import kotlinx.serialization.Serializable

@Serializable
data class EmoteResponse(
    val code: Int,
    val message: String? = null,
    val data: EmoteData?
)

@Serializable
data class EmoteData(
    val packages: List<EmotePackage>? = null,
    val all_packages: List<EmotePackage>? = null
)

@Serializable
data class EmotePackage(
    val id: Long,
    val text: String, // 包名，如 "小黄脸"
    val url: String = "", // 包图标
    val emote: List<EmoteItem>?
)

@Serializable
data class EmoteItem(
    val id: Long,
    val text: String, // 表情代码，如 "[doge]"
    val url: String   // 图片链接
)
