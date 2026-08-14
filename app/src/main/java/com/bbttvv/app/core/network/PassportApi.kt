package com.bbttvv.app.core.network

import com.bbttvv.app.data.model.response.TvPollResponse
import com.bbttvv.app.data.model.response.TvQrCodeResponse
interface PassportApi {
    // TV 端申请二维码
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code")
    suspend fun generateTvQrCode(
        @retrofit2.http.FieldMap params: Map<String, String>
    ): TvQrCodeResponse
    
    // TV 端轮询登录状态
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://passport.bilibili.com/x/passport-tv-login/qrcode/poll")
    suspend fun pollTvQrCode(
        @retrofit2.http.FieldMap params: Map<String, String>
    ): TvPollResponse

    // TV 端刷新 Token
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://passport.bilibili.com/x/passport-tv-login/h5/refresh")
    suspend fun refreshToken(
        @retrofit2.http.FieldMap params: Map<String, String>
    ): com.bbttvv.app.data.model.response.TvTokenRefreshResponse
}

// ==================== 音频 API ====================
