package com.bbttvv.app.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SplashResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 0,
    val data: SplashData? = null
)

@Serializable
data class SplashData(
    val list: List<SplashItem> = emptyList()
)

@Serializable
data class SplashItem(
    val id: Long = 0,
    val type: Int = 0,
    val title: String = "",
    @SerialName("thumb") val thumb: String = "", // 缩略图/图片地址
    @SerialName("image") val image: String = "", // 有些 API 可能返回这个
    @SerialName("logo_url") val logoUrl: String = "",
    @SerialName("video_url") val videoUrl: String = "",
    @SerialName("begin_time") val beginTime: Long = 0,
    @SerialName("end_time") val endTime: Long = 0,
    @SerialName("duration") val duration: Int = 0,
    @SerialName("is_ad") val isAd: Boolean = false,
    @SerialName("schema") val schema: String = "",
    @SerialName("schema_title") val schemaTitle: String = ""
)

// [新增] 品牌开屏壁纸响应 (无广告)
@Serializable
data class SplashBrandResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 0,
    val data: SplashBrandData? = null
)

@Serializable
data class SplashBrandData(
    val list: List<SplashBrandItem> = emptyList(),
    val show: List<SplashShowItem> = emptyList(),
    @SerialName("pull_interval") val pullInterval: Int = 0,
    val forcibly: Boolean = false,
    val rule: String = ""
)

@Serializable
data class SplashBrandItem(
    val id: Long = 0,
    val thumb: String = "",        // 开屏图片 URL
    @SerialName("logo_url") val logoUrl: String = ""
)

@Serializable
data class SplashShowItem(
    val id: Long = 0,
    @SerialName("begin_time") val beginTime: Long = 0,
    @SerialName("end_time") val endTime: Long = 0,
    val probability: Int = 0,
    val duration: Int = 0
)

