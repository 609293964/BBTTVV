package com.bbttvv.app.feature.login

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bbttvv.app.core.coroutines.RequestGeneration
import com.bbttvv.app.core.network.AppSignUtils
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.store.AccountSessionStore
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.data.model.response.TvPollData
import com.bbttvv.app.data.repository.SubtitleAndAuxRepository
import com.bbttvv.app.data.repository.VideoRepository
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val loginGeneration = RequestGeneration()
    private var qrLoadJob: Job? = null
    private var pollingJob: Job? = null

    fun loadQrCode() {
        qrLoadJob?.cancel()
        pollingJob?.cancel()
        pollingJob = null
        val generation = loginGeneration.next()

        qrLoadJob = viewModelScope.launch {
            try {
                publishIfCurrent(generation) { TvLoginUiState() }
                val params = mapOf(
                    "appkey" to AppSignUtils.TV_APP_KEY,
                    "local_id" to "0",
                    "ts" to AppSignUtils.getTimestamp().toString()
                )
                val signedParams = AppSignUtils.signForTvLogin(params)
                val response = NetworkModule.passportApi.generateTvQrCode(signedParams)
                currentCoroutineContext().ensureActive()
                if (!loginGeneration.isCurrent(generation)) return@launch

                if (response.code != 0 || response.data == null) {
                    publishIfCurrent(generation) {
                        it.copy(
                            isLoading = false,
                            errorMessage = response.message.ifBlank { "获取二维码失败" },
                            statusText = "二维码获取失败"
                        )
                    }
                    return@launch
                }

                val requestAuthCode = response.data.authCode.orEmpty()
                val qrUrl = response.data.url.orEmpty()
                if (requestAuthCode.isBlank() || qrUrl.isBlank()) {
                    publishIfCurrent(generation) {
                        it.copy(
                            isLoading = false,
                            errorMessage = "二维码数据为空",
                            statusText = "二维码获取失败"
                        )
                    }
                    return@launch
                }

                val qrBitmap = withContext(Dispatchers.Default) {
                    generateQrBitmap(qrUrl)
                }
                currentCoroutineContext().ensureActive()
                if (!loginGeneration.isCurrent(generation)) return@launch

                publishIfCurrent(generation) {
                    it.copy(
                        isLoading = false,
                        qrBitmap = qrBitmap,
                        statusText = "请使用哔哩哔哩 App 扫码登录",
                        errorMessage = null,
                        isScanned = false,
                        isSuccess = false
                    )
                }
                startPolling(generation = generation, authCode = requestAuthCode)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                publishIfCurrent(generation) {
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
        loginGeneration.invalidate()
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun startPolling(generation: Long, authCode: String) {
        if (!loginGeneration.isCurrent(generation)) return
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive && loginGeneration.isCurrent(generation)) {
                delay(2000L)
                if (!loginGeneration.isCurrent(generation)) break
                try {
                    val params = mapOf(
                        "appkey" to AppSignUtils.TV_APP_KEY,
                        "auth_code" to authCode,
                        "local_id" to "0",
                        "ts" to AppSignUtils.getTimestamp().toString()
                    )
                    val signedParams = AppSignUtils.signForTvLogin(params)
                    val response = NetworkModule.passportApi.pollTvQrCode(signedParams)
                    currentCoroutineContext().ensureActive()
                    if (!loginGeneration.isCurrent(generation)) break

                    when (response.code) {
                        0 -> {
                            val data = response.data
                            if (data != null) {
                                handleLoginSuccess(data)
                                currentCoroutineContext().ensureActive()
                                if (!loginGeneration.isCurrent(generation)) break
                                publishIfCurrent(generation) {
                                    it.copy(
                                        isSuccess = true,
                                        isScanned = true,
                                        statusText = "登录成功"
                                    )
                                }
                                break
                            }
                        }

                        86090 -> publishIfCurrent(generation) {
                            it.copy(
                                isScanned = true,
                                statusText = "已扫码，请在手机上确认"
                            )
                        }

                        86039 -> publishIfCurrent(generation) {
                            it.copy(
                                isScanned = false,
                                statusText = "等待扫码确认"
                            )
                        }

                        86038 -> {
                            publishIfCurrent(generation) {
                                it.copy(
                                    errorMessage = "二维码已过期，请刷新",
                                    statusText = "二维码已过期"
                                )
                            }
                            break
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // 短暂轮询失败保持二维码有效，下一轮继续尝试。
                }
            }
        }
    }

    private inline fun publishIfCurrent(
        generation: Long,
        transform: (TvLoginUiState) -> TvLoginUiState
    ) {
        if (!loginGeneration.isCurrent(generation)) return
        _uiState.update { current ->
            if (loginGeneration.isCurrent(generation)) transform(current) else current
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
        loginGeneration.invalidate()
        qrLoadJob?.cancel()
        qrLoadJob = null
        pollingJob?.cancel()
        pollingJob = null
        super.onCleared()
    }
}
