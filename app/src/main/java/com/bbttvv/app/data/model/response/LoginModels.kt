package com.bbttvv.app.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- 1. 二维码申请响应 ---
@Serializable
data class QrCodeResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 1,
    val data: QrData? = null
)

@Serializable
data class QrData(
    val url: String? = null,
    val qrcode_key: String? = null
)

// --- 2. 轮询状态响应 ---
@Serializable
data class PollResponse(
    val code: Int = 0,    // 接口请求状态 (0为成功)
    val message: String = "",
    val ttl: Int = 1,
    val data: PollData? = null
)

@Serializable
data class PollData(
    val url: String? = null,

    @SerialName("refresh_token")
    val refreshToken: String? = null,

    val timestamp: Long = 0,

    //  核心字段：
    // 0: 成功 (此时才有 refresh_token 和 cookie)
    // 86101: 未扫码
    // 86090: 已扫码未确认
    // 86038: 二维码过期
    val code: Int = 0,

    val message: String = ""
)

// ==========  TV 端登录响应模型 ==========

// TV 端二维码申请响应
@Serializable
data class TvQrCodeResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 1,
    val data: TvQrData? = null
)

@Serializable
data class TvQrData(
    val url: String? = null,
    @SerialName("auth_code")
    val authCode: String? = null
)

// TV 端登录轮询响应
@Serializable
data class TvPollResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 1,
    val data: TvPollData? = null
)

@Serializable
data class TvPollData(
    val mid: Long = 0,
    @SerialName("access_token")
    val accessToken: String = "",
    @SerialName("refresh_token")
    val refreshToken: String = "",
    @SerialName("expires_in")
    val expiresIn: Long = 0,
    @SerialName("cookie_info")
    val cookieInfo: TvCookieInfo? = null
)

@Serializable
data class TvCookieInfo(
    val cookies: List<TvCookie> = emptyList()
)

@Serializable
data class TvCookie(
    val name: String = "",
    val value: String = "",
    @SerialName("http_only")
    val httpOnly: Int = 0,
    val expires: Long = 0
)

// TV 端 Token 刷新响应
@Serializable
data class TvTokenRefreshResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 1,
    val data: TvTokenRefreshData? = null
)

//  [新增] Token 刷新数据
@Serializable
data class TvTokenRefreshData(
    val mid: Long = 0,
    @SerialName("access_token")
    val accessToken: String = "",
    @SerialName("refresh_token")
    val refreshToken: String = "",
    @SerialName("expires_in")
    val expiresIn: Long = 0,
    @SerialName("cookie_info")
    val cookieInfo: TvCookieInfo? = null
)
