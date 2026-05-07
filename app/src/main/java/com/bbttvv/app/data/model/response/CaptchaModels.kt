package com.bbttvv.app.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ========== 极验验证相关 ==========

@Serializable
data class CaptchaResponse(
    val code: Int = 0,
    val message: String = "",
    val data: CaptchaData? = null
)

@Serializable
data class CaptchaData(
    val token: String = "",
    val geetest: GeetestData? = null,
    val tencent: TencentData? = null,
    val type: String = ""  // geetest 或 tencent
)

@Serializable
data class GeetestData(
    val gt: String = "",
    val challenge: String = ""
)

@Serializable
data class TencentData(
    val appid: String = ""
)

// ========== 短信验证码相关 ==========

@Serializable
data class SmsCodeResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SmsCodeData? = null
)

@Serializable
data class SmsCodeData(
    @SerialName("captcha_key")
    val captchaKey: String = ""  // 验证码登录时需要的 key
)

// ========== RSA 密钥相关 ==========

@Serializable
data class WebKeyResponse(
    val code: Int = 0,
    val message: String = "",
    val data: WebKeyData? = null
)

@Serializable
data class WebKeyData(
    val hash: String = "",  // 用于密码加密的 salt
    val key: String = ""    // RSA 公钥
)

// ========== 登录响应 ==========

@Serializable
data class LoginResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LoginData? = null
)

@Serializable
data class LoginData(
    val status: Int = 0,       // 0=成功, 其他=需要额外验证
    val message: String = "",
    val url: String = "",      // 可能需要跳转的验证 URL
    @SerialName("refresh_token")
    val refreshToken: String = "",
    @SerialName("timestamp")
    val timestamp: Long = 0,
    @SerialName("cookie_info")
    val cookieInfo: CookieInfo? = null
)

@Serializable
data class CookieInfo(
    val cookies: List<CookieItem>? = null,
    val domains: List<String>? = null
)

@Serializable
data class CookieItem(
    val name: String = "",
    val value: String = "",
    @SerialName("http_only")
    val httpOnly: Int = 0,
    val expires: Long = 0
)

