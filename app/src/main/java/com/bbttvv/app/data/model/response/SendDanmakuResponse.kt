// 文件路径: data/model/response/SendDanmakuResponse.kt
package com.bbttvv.app.data.model.response

import kotlinx.serialization.Serializable

/**
 * 发送弹幕响应
 */
@Serializable
data class SendDanmakuResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SendDanmakuData? = null
)

@Serializable
data class SendDanmakuData(
    val dmid: Long = 0,        // 弹幕 ID
    val dmid_str: String = "", // 弹幕 ID (字符串)
    val visible: Boolean = true
)

/**
 * 弹幕操作响应 (撤回/点赞/举报)
 */
@Serializable
data class DanmakuActionResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 1
)

