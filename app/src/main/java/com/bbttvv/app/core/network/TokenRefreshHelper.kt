package com.bbttvv.app.core.network

import android.content.Context
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.util.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Token 刷新助手
 * 用于刷新 TV 端登录的 access_token
 */
object TokenRefreshHelper {
    private const val TAG = "TokenRefreshHelper"
    private val mutex = Mutex()
    
    /**
     * 尝试刷新 Token
     * 如果刷新成功，会自动更新 TokenManager 中的缓存
     * 
     * @return true if refresh success, false otherwise
     */
    suspend fun refresh(context: Context): Boolean = mutex.withLock {
        try {
            val refreshToken = TokenManager.refreshTokenCache
            if (refreshToken.isNullOrEmpty()) {
                Logger.d(TAG, "Refresh failed: No refresh_token available")
                return false
            }
            
            Logger.d(TAG, "Starting token refresh...")
            
            val params = mapOf(
                "access_key" to (TokenManager.accessTokenCache ?: ""),
                "refresh_token" to refreshToken,
                "appkey" to AppSignUtils.TV_APP_KEY,
                "ts" to AppSignUtils.getTimestamp().toString()
            )
            
            val signedParams = AppSignUtils.signForTvLogin(params)
            
            val response = NetworkModule.passportApi.refreshToken(signedParams)
            
            if (response.code == 0 && response.data != null) {
                val data = response.data
                Logger.d(TAG, "Token refresh success!")
                Logger.d(TAG, "New access_token: ${data.accessToken.take(10)}...")
                
                // 更新 Token
                TokenManager.saveAccessToken(
                    context, 
                    data.accessToken, 
                    data.refreshToken
                )
                
                // 更新 Cookies (如果有)
                data.cookieInfo?.cookies?.forEach { cookie ->
                     if (cookie.name == "SESSDATA") {
                         TokenManager.saveCookies(context, cookie.value)
                     } else if (cookie.name == "bili_jct") {
                         TokenManager.saveCsrf(context, cookie.value)
                     }
                }
                
                return true
            } else {
                Logger.e(TAG, "Token refresh failed: code=${response.code}, msg=${response.message}")
                return false
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Token refresh exception", e)
            return false
        }
    }
}

