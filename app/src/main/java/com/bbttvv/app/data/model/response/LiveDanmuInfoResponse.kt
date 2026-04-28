package com.bbttvv.app.data.model.response

import kotlinx.serialization.Serializable

/**
 * 直播弹幕服务器信息响应
 */
@Serializable
data class LiveDanmuInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LiveDanmuInfoData? = null
)

@Serializable
data class LiveDanmuInfoData(
    val group: String = "",
    val business_id: Int = 0,
    val refresh_row_factor: Double = 0.0,
    val refresh_rate: Int = 0,
    val max_delay: Int = 0,
    val token: String = "",
    val host_list: List<LiveDanmuHost> = emptyList()
)

@Serializable
data class LiveDanmuHost(
    val host: String = "",
    val port: Int = 0,
    val wss_port: Int = 0,
    val ws_port: Int = 0
)

