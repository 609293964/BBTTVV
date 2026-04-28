package com.bbttvv.app.data.model.response

import kotlinx.serialization.Serializable

/**
 *  在线观看人数响应
 * 
 * API: GET https://api.bilibili.com/x/player/online/total
 */
@Serializable
data class OnlineResponse(
    val code: Int = 0,
    val message: String = "",
    val data: OnlineData? = null
)

@Serializable
data class OnlineData(
    val total: String = "",    // 所有平台（web+app）估计总在线人数，如 "9.4万+"
    val count: String = ""     // Web 端精确在线人数
)

