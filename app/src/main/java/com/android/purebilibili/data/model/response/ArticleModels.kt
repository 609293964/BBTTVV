package com.android.purebilibili.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Serializable
data class ArticleDetailResponse(
    val code: Int = 0,
    val message: String = "",
    val data: ArticleViewData? = null
)

@Serializable
data class ArticleViewData(
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    val title: String = "",
    val summary: String = "",
    @SerialName("banner_url")
    val bannerUrl: String = "",
    val author: ArticleAuthor? = null,
    @SerialName("publish_time")
    @Serializable(with = FlexibleLongSerializer::class)
    val publishTime: Long = 0,
    @SerialName("image_urls")
    val imageUrls: List<String> = emptyList(),
    @SerialName("origin_image_urls")
    val originImageUrls: List<String> = emptyList(),
    @SerialName("dyn_id_str")
    val dynamicId: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val type: Int = 0,
    val content: String = "",
    val opus: ArticleOpus? = null
)

@Serializable
data class ArticleAuthor(
    @Serializable(with = FlexibleLongSerializer::class)
    val mid: Long = 0,
    val name: String = "",
    val face: String = ""
)

@Serializable
data class ArticleOpus(
    val title: String = "",
    val content: JsonObject? = null
) {
    fun paragraphs(): List<JsonObject> {
        val paragraphs = content?.get("paragraphs")?.jsonArray ?: return emptyList()
        return paragraphs.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
    }
}
