package com.bbttvv.app.data.repository

import android.util.Log
import com.bbttvv.app.core.network.AppSignUtils
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.network.TokenRefreshHelper
import com.bbttvv.app.core.network.WbiUtils
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.data.model.response.PlayUrlData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PlayUrlFetchResult(
    val data: PlayUrlData,
    val source: PlayUrlSource
)

internal object PlayUrlResolver {
    private const val TAG = "PlayUrlResolver"

    private val api get() = NetworkModule.api

    suspend fun fetch(
        bvid: String,
        cid: Long,
        targetQn: Int,
        audioLang: String? = null,
        requestKind: PlayUrlRequestKind
    ): PlayUrlFetchResult? {
        PlaybackSessionManager.ensureBuvid3()
        TokenManager.awaitWarmup()

        val hasSessionCookie = !TokenManager.sessDataCache.isNullOrEmpty()
        val hasAccessToken = !TokenManager.accessTokenCache.isNullOrEmpty()
        val request = PlaybackStrategyRequest(
            bvid = bvid,
            cid = cid,
            targetQn = targetQn,
            audioLang = audioLang,
            requestKind = requestKind,
            isLoggedIn = resolveVideoPlaybackAuthState(
                hasSessionCookie = hasSessionCookie,
                hasAccessToken = hasAccessToken
            ),
            hasAccessToken = hasAccessToken
        )
        com.bbttvv.app.core.util.Logger.d(
            TAG,
            "fetch: bvid=$bvid, isLoggedIn=${request.isLoggedIn}, targetQn=$targetQn, audioLang=$audioLang"
        )

        val gateway = PlaybackGateway(
            fetchDash = { requestedQn, requestedAudioLang ->
                fetchPlayUrlWithWbiInternal(
                    bvid = bvid,
                    cid = cid,
                    qn = requestedQn,
                    audioLang = requestedAudioLang
                )
            },
            fetchApp = { requestedQn, requestedAudioLang ->
                fetchPlayUrlWithAccessToken(
                    bvid = bvid,
                    cid = cid,
                    qn = requestedQn,
                    audioLang = requestedAudioLang
                )
            },
            fetchLegacy = { requestedQn ->
                fetchLegacyPlayback(
                    bvid = bvid,
                    cid = cid,
                    qn = requestedQn
                )
            },
            fetchGuest = {
                fetchAsGuestFallback(
                    bvid = bvid,
                    cid = cid
                )
            }
        )

        val strategy: PlaybackStrategy = if (request.isLoggedIn) {
            LoggedInPlaybackStrategy
        } else {
            GuestPlaybackStrategy
        }
        return strategy.resolve(request = request, gateway = gateway)
    }

    suspend fun getPlayUrlData(
        bvid: String,
        cid: Long,
        qn: Int,
        audioLang: String? = null
    ): PlayUrlData? = withContext(Dispatchers.IO) {
        fetch(
            bvid = bvid,
            cid = cid,
            targetQn = qn,
            audioLang = audioLang,
            requestKind = PlayUrlRequestKind.EXPLICIT
        )?.data
    }

    suspend fun getPreviewVideoUrl(bvid: String, cid: Long): String? = withContext(Dispatchers.IO) {
        fetchAsGuestFallback(bvid = bvid, cid = cid)
            .data
            ?.durl
            ?.firstOrNull()
            ?.url
    }

    private fun hasPlayableStreams(data: PlayUrlData?): Boolean {
        if (data == null) return false
        return !data.durl.isNullOrEmpty() || !data.dash?.video.isNullOrEmpty()
    }

    private suspend fun fetchAsGuestFallback(
        bvid: String,
        cid: Long
    ): PlaybackSourceResult {
        return try {
            com.bbttvv.app.core.util.Logger.d(TAG, "fetchAsGuestFallback: bvid=$bvid, cid=$cid")
            val guestApi = NetworkModule.guestApi

            for (guestQn in buildGuestFallbackQualities()) {
                val legacyResult = guestApi.getPlayUrlLegacy(
                    bvid = bvid,
                    cid = cid,
                    qn = guestQn,
                    fnval = 1,
                    platform = "html5",
                    highQuality = if (guestQn >= 64) 1 else 0
                )

                if (legacyResult.code == 0 && legacyResult.data != null) {
                    val payload = legacyResult.data
                    if (!payload.durl.isNullOrEmpty()) {
                        com.bbttvv.app.core.util.Logger.d(TAG, "Guest fallback ${guestQn}p success: actual=${payload.quality}")
                        return PlaybackSourceResult(data = payload)
                    }
                } else {
                    com.bbttvv.app.core.util.Logger.d(TAG, "Guest fallback ${guestQn}p failed: code=${legacyResult.code}")
                }
            }

            PlaybackSourceResult(
                diagnosis = PlaybackErrorDiagnosis(
                    userMessage = "游客回退未拿到可播放地址",
                    kind = PlaybackErrorKind.EMPTY_PAYLOAD
                )
            )
        } catch (error: Exception) {
            Log.w(TAG, "Guest fallback failed: ${error.message}")
            PlaybackSourceResult(
                diagnosis = PlaybackErrorDiagnosis(
                    userMessage = error.message.orEmpty().ifBlank { "游客回退失败" },
                    kind = PlaybackErrorKind.TRANSIENT
                )
            )
        }
    }

    private suspend fun fetchLegacyPlayback(
        bvid: String,
        cid: Long,
        qn: Int
    ): PlaybackSourceResult {
        return try {
            val response = api.getPlayUrlLegacy(bvid = bvid, cid = cid, qn = qn)
            if (response.code == 0 && response.data != null) {
                val payload = response.data
                if (hasPlayableStreams(payload)) {
                    PlaybackSourceResult(data = payload)
                } else {
                    PlaybackSourceResult(
                        diagnosis = PlaybackErrorClassifier.emptyPayload("Legacy playurl")
                    )
                }
            } else {
                PlaybackSourceResult(
                    diagnosis = PlaybackErrorDiagnosis(
                        userMessage = response.message.ifBlank { "Legacy playurl 失败(${response.code})" },
                        code = response.code
                    )
                )
            }
        } catch (error: Exception) {
            PlaybackSourceResult(
                diagnosis = PlaybackErrorDiagnosis(
                    userMessage = error.message.orEmpty().ifBlank { "Legacy playurl 请求失败" },
                    kind = PlaybackErrorKind.TRANSIENT
                )
            )
        }
    }

    private suspend fun fetchPlayUrlWithWbiInternal(
        bvid: String,
        cid: Long,
        qn: Int,
        audioLang: String?
    ): PlaybackSourceResult {
        return try {
            com.bbttvv.app.core.util.Logger.d(
                TAG,
                "fetchPlayUrlWithWbiInternal: bvid=$bvid, cid=$cid, qn=$qn, audioLang=$audioLang"
            )

            val (imgKey, subKey) = PlaybackSessionManager.getWbiKeys()
            val isLoggedIn = resolveVideoPlaybackAuthState(
                hasSessionCookie = !TokenManager.sessDataCache.isNullOrEmpty(),
                hasAccessToken = !TokenManager.accessTokenCache.isNullOrEmpty()
            )
            val auto1080pEnabled = NetworkModule.appContext
                ?.let(SettingsManager::getAutoHighestQualitySync)
                ?: true

            val params = buildPlayUrlWbiBaseParams(
                bvid = bvid,
                cid = cid,
                qn = qn,
                audioLang = audioLang,
                tryLook = shouldRequestPlayUrlTryLook(
                    isLoggedIn = isLoggedIn,
                    auto1080pEnabled = auto1080pEnabled
                )
            )

            val signedParams = WbiUtils.sign(params, imgKey, subKey)
            val response = api.getPlayUrl(signedParams)
            val dashIds = response.data?.dash?.video?.map { it.id }?.distinct()?.sortedDescending()

            com.bbttvv.app.core.util.Logger.d(
                TAG,
                "PlayUrl response: code=${response.code}, requestedQn=$qn, returnedQuality=${response.data?.quality}"
            )
            com.bbttvv.app.core.util.Logger.d(
                TAG,
                "accept_quality=${response.data?.accept_quality}, accept_description=${response.data?.accept_description}"
            )
            com.bbttvv.app.core.util.Logger.d(TAG, "DASH video IDs: $dashIds")

            if (response.code == 0) {
                val payload = response.data
                return if (hasPlayableStreams(payload)) {
                    PlaybackSourceResult(data = payload)
                } else {
                    com.bbttvv.app.core.util.Logger.w(
                        TAG,
                        "PlayUrl success but empty payload: requestedQn=$qn, returnedQuality=${payload?.quality}, dashIds=$dashIds"
                    )
                    PlaybackSourceResult(
                        diagnosis = PlaybackErrorClassifier.emptyPayload("WBI playurl")
                    )
                }
            }

            val diagnosis = PlaybackErrorClassifier.classifyWbiApi(
                code = response.code,
                message = response.message
            )
            Log.e(
                TAG,
                "PlayUrl API error: code=${response.code}, message=${response.message}, classified=${diagnosis.userMessage}"
            )
            PlaybackSourceResult(diagnosis = diagnosis)
        } catch (error: Exception) {
            val diagnosis = PlaybackErrorClassifier.classifyWbiThrowable(error)
            Log.w(TAG, "WBI playurl failed: ${diagnosis.userMessage}")
            PlaybackSourceResult(diagnosis = diagnosis)
        }
    }

    private suspend fun fetchPlayUrlWithAccessToken(
        bvid: String,
        cid: Long,
        qn: Int,
        allowRetry: Boolean = true,
        audioLang: String? = null
    ): PlaybackSourceResult {
        TokenManager.awaitWarmup()
        val accessToken = TokenManager.accessTokenCache
        if (accessToken.isNullOrEmpty()) {
            com.bbttvv.app.core.util.Logger.d(TAG, "No access_token available, fallback to Web API")
            return PlaybackSourceResult(
                diagnosis = PlaybackErrorDiagnosis(
                    userMessage = "缺少 access_token",
                    kind = PlaybackErrorKind.ACCESS_TOKEN_EXPIRED
                )
            )
        }

        com.bbttvv.app.core.util.Logger.d(
            TAG,
            "fetchPlayUrlWithAccessToken: bvid=$bvid, qn=$qn, retry=$allowRetry"
        )

        val params = mutableMapOf(
            "bvid" to bvid,
            "cid" to cid.toString(),
            "qn" to qn.toString(),
            "fnval" to "4048",
            "fnver" to "0",
            "fourk" to "1",
            "access_key" to accessToken,
            "appkey" to AppSignUtils.TV_APP_KEY,
            "ts" to AppSignUtils.getTimestamp().toString(),
            "platform" to "android",
            "mobi_app" to "android_tv_yst",
            "device" to "android"
        )

        if (!audioLang.isNullOrEmpty()) {
            params["cur_language"] = audioLang
            params["lang"] = audioLang
        }

        val signedParams = AppSignUtils.signForTvLogin(params)

        return try {
            val response = api.getPlayUrlApp(signedParams)
            if (response.code == -101 && allowRetry) {
                val appContext = PlaybackSessionManager.applicationContext
                if (appContext != null) {
                    com.bbttvv.app.core.util.Logger.w(TAG, "Access token invalid (-101), trying to refresh")
                    val success = TokenRefreshHelper.refresh(appContext)
                    if (success) {
                        com.bbttvv.app.core.util.Logger.i(TAG, "Token refreshed successfully, retrying request")
                        return fetchPlayUrlWithAccessToken(
                            bvid = bvid,
                            cid = cid,
                            qn = qn,
                            allowRetry = false,
                            audioLang = audioLang
                        )
                    }
                    com.bbttvv.app.core.util.Logger.e(TAG, "Token refresh failed, aborting retry")
                }
            }

            val dashIds = response.data?.dash?.video?.map { it.id }?.distinct()?.sortedDescending()
            com.bbttvv.app.core.util.Logger.d(
                TAG,
                "APP PlayUrl response: code=${response.code}, qn=$qn, dashIds=$dashIds"
            )

            if (response.code == 0 && response.data != null) {
                val payload = response.data
                return if (hasPlayableStreams(payload)) {
                    PlaybackSessionManager.recordAppApiSuccess()
                    com.bbttvv.app.core.util.Logger.d(
                        TAG,
                        "APP API success: returned quality=${payload.quality}, available=$dashIds"
                    )
                    PlaybackSourceResult(data = payload)
                } else {
                    com.bbttvv.app.core.util.Logger.w(
                        TAG,
                        "APP API success but empty payload: qn=$qn, quality=${payload.quality}"
                    )
                    PlaybackSourceResult(
                        diagnosis = PlaybackErrorClassifier.emptyPayload("APP playurl")
                    )
                }
            }

            val diagnosis = PlaybackErrorClassifier.classifyAppApi(
                code = response.code,
                message = response.message
            )
            if (diagnosis.shouldApplyAppCooldown) {
                PlaybackSessionManager.recordAppApiRiskHit()
                com.bbttvv.app.core.util.Logger.w(TAG, "APP API hit anti-risk, cooldown applied")
            }
            com.bbttvv.app.core.util.Logger.d(
                TAG,
                "APP API error: code=${response.code}, msg=${response.message}"
            )
            PlaybackSourceResult(diagnosis = diagnosis)
        } catch (error: Exception) {
            com.bbttvv.app.core.util.Logger.d(TAG, "APP API exception: ${error.message}")
            PlaybackSourceResult(
                diagnosis = PlaybackErrorDiagnosis(
                    userMessage = error.message.orEmpty().ifBlank { "APP playurl 请求失败" },
                    kind = PlaybackErrorKind.TRANSIENT
                )
            )
        }
    }
}
