package com.bbttvv.app.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 用户相关 API 响应模型
 */

@Serializable
data class UserCardResponse(
    val code: Int = 0,
    val message: String = "",
    val data: UserCardData? = null
)

@Serializable
data class UserCardData(
    val card: UserCardBase? = null,
    val space: UserSpaceBase? = null,
    val following: Boolean = false,
    val archive_count: Int = 0,
    val article_count: Int = 0,
    val follower: Int = 0,
    val like_num: Int = 0,
    @SerialName("ip_location")
    val ipLocation: String? = null
)

@Serializable
data class UserCardBase(
    val mid: String = "",
    val name: String = "",
    val face: String = "",
    val sex: String = "",
    val rank: String = "",
    val sign: String = "",
    val level_info: LevelInfo? = null,
    val Official:  UserOfficial? = null,
    val vip: VipInfo? = null,
    @SerialName("ip_location")
    val ipLocation: String? = null
)

@Serializable
data class UserSpaceBase(
    val s_img: String = "",
    val l_img: String = ""
)

@Serializable
data class UserOfficial(
    val role: Int = 0,
    val title: String = "",
    val desc: String = "",
    val type: Int = -1
)

