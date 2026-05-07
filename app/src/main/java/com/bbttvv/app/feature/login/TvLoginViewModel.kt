package com.bbttvv.app.feature.login

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bbttvv.app.core.network.AppSignUtils
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.store.AccountSessionStore
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.data.model.response.TvPollData
import com.bbttvv.app.data.repository.SubtitleAndAuxRepository
import com.bbttvv.app.data.repository.VideoRepository
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TvLoginUiState(
    val isLoading: Boolean = true,
    val qrBitmap: Bitmap? = null,
    val statusText: String = "正在获取二维码...",
    val errorMessage: String? = null,
    val isScanned: Boolean = false,
    val isSuccess: Boolean = false
)

class TvLoginViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(TvLoginUiState())
    val uiState: StateFlow<TvLoginUiState> = _uiState.asStateFlow()

    private var authCode: String = ""
    private var pollingJob: Job? = null

    fun loadQrCode() {
        pollingJob?.cancel()
        viewModelScope.launch {
            try {
                _uiState.update { TvLoginUiState() }
                val params = mapOf(
                    "appkey" to AppSignUtils.TV_APP_KEY,
                    "local_id" to "0",
                    "ts" to AppSignUtils.getTimestamp().toString()
                )
                val signedParams = AppSignUtils.signForTvLogin(params)
                val response = NetworkModule.passportApi.generateTvQrCode(signedParams)
                if (response.code != 0 || response.data == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = response.message.ifBlank { "获取二维码失败" },
                            statusText = "二维码获取失败"
                        )
                    }
                    return@launch
                }

                authCode = response.data.authCode.orEmpty()
                val qrUrl = response.data.url.orEmpty()
                if (authCode.isBlank() || qrUrl.isBlank()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "二维码数据为空",
                            statusText = "二维码获取失败"
                        )
                    }
                    return@launch
                }

                val qrBitmap = generateQrBitmap(qrUrl)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        qrBitmap = qrBitmap,
                        statusText = "请使用哔哩哔哩 App 扫码登录",
                        errorMessage = null,
                        isScanned = false,
                        isSuccess = false
                    )
                }

                startPolling()
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "网络错误",
                        statusText = "二维码获取失败"
                    )
                }
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun startPolling() {
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(2000L)
                try {
                    val params = mapOf(
                        "appkey" to AppSignUtils.TV_APP_KEY,
                        "auth_code" to authCode,
                        "local_id" to "0",
                        "ts" to AppSignUtils.getTimestamp().toString()
                    )
                    val signedParams = AppSignUtils.signForTvLogin(params)
                    val response = NetworkModule.passportApi.pollTvQrCode(signedParams)
                    when (response.code) {
                        0 -> {
                            val data = response.data
                            if (data != null) {
                                handleLoginSuccess(data)
                                _uiState.update {
                                    it.copy(
                                        isSuccess = true,
                                        isScanned = true,
                                        statusText = "登录成功"
                                    )
                                }
                                break
                            }
                        }

                        86090 -> {
                            _uiState.update {
                                it.copy(
                                    isScanned = true,
                                    statusText = "已扫码，请在手机上确认"
                                )
                            }
                        }

                        86039 -> {
                            _uiState.update {
                                it.copy(
                                    isScanned = false,
                                    statusText = "等待扫码确认"
                                )
                            }
                        }

                        86038 -> {
                            _uiState.update {
                                it.copy(
                                    errorMessage = "二维码已过期，请刷新",
                                    statusText = "二维码已过期"
                                )
                            }
                            break
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun handleLoginSuccess(data: TvPollData) {
        val context = getApplication<Application>()
        TokenManager.saveAccessToken(context, data.accessToken, data.refreshToken)
        if (data.mid > 0L) {
            TokenManager.saveMid(context, data.mid)
        }

        data.cookieInfo?.cookies?.forEach { cookie ->
            when (cookie.name) {
                "SESSDATA" -> TokenManager.saveCookies(context, cookie.value)
                "bili_jct" -> TokenManager.saveCsrf(context, cookie.value)
            }
        }

        VideoRepository.invalidateAccountScopedCaches()
        val navData = SubtitleAndAuxRepository.getNavInfo().getOrNull()
        if (navData != null && navData.isLogin) {
            TokenManager.saveMid(context, navData.mid)
            TokenManager.saveVipStatus(navData.vip.status == 1)
            AccountSessionStore.upsertCurrentAccount(context, navData)
        } else {
            AccountSessionStore.upsertCurrentAccount(context)
        }
    }

    private fun generateQrBitmap(content: String): Bitmap {
        val size = 720
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }
}
