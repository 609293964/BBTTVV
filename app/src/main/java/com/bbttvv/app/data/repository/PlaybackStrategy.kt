package com.bbttvv.app.data.repository

import com.bbttvv.app.data.model.response.PlayUrlData
import kotlinx.coroutines.delay

internal data class PlaybackStrategyRequest(
    val bvid: String,
    val cid: Long,
    val targetQn: Int,
    val audioLang: String?,
    val requestKind: PlayUrlRequestKind,
    val isLoggedIn: Boolean,
    val hasAccessToken: Boolean
)

internal data class PlaybackSourceResult(
    val data: PlayUrlData? = null,
    val diagnosis: PlaybackErrorDiagnosis? = null
)

internal class PlaybackGateway(
    val fetchDash: suspend (requestedQn: Int, audioLang: String?) -> PlaybackSourceResult,
    val fetchApp: suspend (requestedQn: Int, audioLang: String?) -> PlaybackSourceResult,
    val fetchLegacy: suspend (requestedQn: Int) -> PlaybackSourceResult,
    val fetchGuest: suspend () -> PlaybackSourceResult
)

internal interface PlaybackStrategy {
    suspend fun resolve(
        request: PlaybackStrategyRequest,
        gateway: PlaybackGateway
    ): PlayUrlFetchResult?
}

internal object QualitySelectionPolicy {
    fun dashAttemptQualities(targetQn: Int): List<Int> = buildDashAttemptQualities(targetQn)

    fun dashRetryDelays(targetQn: Int): List<Long> = resolveDashRetryDelays(targetQn)

    fun shouldRetryTrackRecovery(
        targetQn: Int,
        returnedQuality: Int,
        acceptQualities: List<Int>,
        dashVideoIds: List<Int>
    ): Boolean = shouldRetryDashTrackRecovery(
        targetQn = targetQn,
        returnedQuality = returnedQuality,
        acceptQualities = acceptQualities,
        dashVideoIds = dashVideoIds
    )

    fun shouldAcceptPlayableResult(
        requestKind: PlayUrlRequestKind,
        targetQn: Int,
        returnedQuality: Int,
        dashVideoIds: List<Int>
    ): Boolean = shouldAcceptAppApiResultForTargetQuality(
        requestKind = requestKind,
        targetQn = targetQn,
        returnedQuality = returnedQuality,
        dashVideoIds = dashVideoIds
    )
}

internal object AppApiFallbackPolicy {
    private const val TAG = "AppApiFallback"

    suspend fun resolve(
        request: PlaybackStrategyRequest,
        gateway: PlaybackGateway,
        attemptQualities: List<Int>
    ): PlayUrlFetchResult? {
        if (!PlaybackSessionManager.canUseAppApi(request.hasAccessToken)) {
            com.bbttvv.app.core.util.Logger.d(TAG, "Skip APP fallback: no access token or cooldown active")
            return null
        }

        com.bbttvv.app.core.util.Logger.d(TAG, "WBI chain exhausted, trying APP access_token fallback")
        for (requestedQn in attemptQualities) {
            val result = gateway.fetchApp(requestedQn, request.audioLang)
            val payload = result.data
            if (payload == null) {
                result.diagnosis?.let { diagnosis ->
                    com.bbttvv.app.core.util.Logger.w(
                        TAG,
                        "APP fallback qn=$requestedQn failed: ${diagnosis.userMessage}"
                    )
                }
                continue
            }
            val dashVideoIds = payload.resolveDashVideoIds()
            if (!QualitySelectionPolicy.shouldAcceptPlayableResult(
                    requestKind = request.requestKind,
                    targetQn = requestedQn,
                    returnedQuality = payload.quality,
                    dashVideoIds = dashVideoIds
                )
            ) {
                com.bbttvv.app.core.util.Logger.w(
                    TAG,
                    "Reject downgraded APP result: requestedQn=$requestedQn, quality=${payload.quality}, dashIds=$dashVideoIds"
                )
                continue
            }
            com.bbttvv.app.core.util.Logger.d(
                TAG,
                "APP fallback success: quality=${payload.quality}, requestedQn=$requestedQn"
            )
            return PlayUrlFetchResult(payload, PlayUrlSource.APP)
        }

        return null
    }
}

internal object LoggedInPlaybackStrategy : PlaybackStrategy {
    private const val TAG = "LoggedInPlayback"

    override suspend fun resolve(
        request: PlaybackStrategyRequest,
        gateway: PlaybackGateway
    ): PlayUrlFetchResult? {
        val fallbackOrder = buildLoggedInPlaybackFallbackOrder()
        val dashQualities = QualitySelectionPolicy.dashAttemptQualities(request.targetQn)
        com.bbttvv.app.core.util.Logger.d(TAG, "DASH-first strategy, qn=${request.targetQn}")

        for (dashQn in dashQualities) {
            val retryDelays = QualitySelectionPolicy.dashRetryDelays(dashQn)
            for ((attemptIndex, delayMs) in retryDelays.withIndex()) {
                if (delayMs > 0L) {
                    com.bbttvv.app.core.util.Logger.d(TAG, "DASH retry ${attemptIndex + 1} for qn=$dashQn")
                    delay(delayMs)
                }

                val result = gateway.fetchDash(dashQn, request.audioLang)
                val payload = result.data
                if (payload != null && hasPlayableStreams(payload)) {
                    val dashVideoIds = payload.resolveDashVideoIds()
                    val shouldRetryTrackRecovery = QualitySelectionPolicy.shouldRetryTrackRecovery(
                        targetQn = dashQn,
                        returnedQuality = payload.quality,
                        acceptQualities = payload.accept_quality,
                        dashVideoIds = dashVideoIds
                    )
                    if (shouldRetryTrackRecovery && attemptIndex < retryDelays.lastIndex) {
                        com.bbttvv.app.core.util.Logger.d(
                            TAG,
                            "DASH track recovery retry: requestedQn=$dashQn, returnedQuality=${payload.quality}, accept=${payload.accept_quality}, dashIds=$dashVideoIds"
                        )
                        continue
                    }
                    if (!QualitySelectionPolicy.shouldAcceptPlayableResult(
                            requestKind = request.requestKind,
                            targetQn = dashQn,
                            returnedQuality = payload.quality,
                            dashVideoIds = dashVideoIds
                        )
                    ) {
                        com.bbttvv.app.core.util.Logger.d(
                            TAG,
                            "Reject downgraded DASH result: requestedQn=$dashQn, quality=${payload.quality}, dashIds=$dashVideoIds"
                        )
                        continue
                    }
                    com.bbttvv.app.core.util.Logger.d(TAG, "DASH success: quality=${payload.quality}, requestedQn=$dashQn")
                    return PlayUrlFetchResult(payload, PlayUrlSource.DASH)
                }

                result.diagnosis?.let { diagnosis ->
                    com.bbttvv.app.core.util.Logger.w(
                        TAG,
                        "DASH qn=$dashQn attempt=${attemptIndex + 1} failed: ${diagnosis.userMessage}"
                    )
                    if (diagnosis.shouldInvalidateWbiKeys && attemptIndex < retryDelays.lastIndex) {
                        PlaybackSessionManager.invalidateWbiKeys()
                    }
                }
            }
        }

        if (PlayUrlSource.APP in fallbackOrder) {
            AppApiFallbackPolicy.resolve(
                request = request,
                gateway = gateway,
                attemptQualities = dashQualities
            )?.let { return it }
        }

        if (PlayUrlSource.LEGACY in fallbackOrder) {
            com.bbttvv.app.core.util.Logger.d(TAG, "DASH failed, trying Legacy API")
            val legacyResult = gateway.fetchLegacy(80)
            val payload = legacyResult.data
            if (payload != null && hasPlayableStreams(payload)) {
                com.bbttvv.app.core.util.Logger.d(TAG, "Legacy API success: quality=${payload.quality}")
                return PlayUrlFetchResult(payload, PlayUrlSource.LEGACY)
            }
            legacyResult.diagnosis?.let { diagnosis ->
                com.bbttvv.app.core.util.Logger.w(TAG, "Legacy API failed: ${diagnosis.userMessage}")
            }
        }

        if (PlayUrlSource.GUEST in fallbackOrder) {
            com.bbttvv.app.core.util.Logger.d(TAG, "All auth methods failed, trying guest fallback")
            val guestResult = gateway.fetchGuest()
            val payload = guestResult.data
            if (payload != null && hasPlayableStreams(payload)) {
                com.bbttvv.app.core.util.Logger.d(TAG, "Guest fallback success: quality=${payload.quality}")
                return PlayUrlFetchResult(payload, PlayUrlSource.GUEST)
            }
            guestResult.diagnosis?.let { diagnosis ->
                com.bbttvv.app.core.util.Logger.w(TAG, "Guest fallback failed: ${diagnosis.userMessage}")
            }
        }

        com.bbttvv.app.core.util.Logger.e(TAG, "All attempts failed for bvid=${request.bvid}")
        return null
    }
}

internal object GuestPlaybackStrategy : PlaybackStrategy {
    private const val TAG = "GuestPlayback"

    override suspend fun resolve(
        request: PlaybackStrategyRequest,
        gateway: PlaybackGateway
    ): PlayUrlFetchResult? {
        val fallbackOrder = buildGuestPlaybackFallbackOrder()
        com.bbttvv.app.core.util.Logger.d(TAG, "WBI-first strategy")

        for (source in fallbackOrder) {
            when (source) {
                PlayUrlSource.DASH -> {
                    val result = gateway.fetchDash(request.targetQn, null)
                    val payload = result.data
                    if (payload != null && hasPlayableStreams(payload)) {
                        val dashVideoIds = payload.resolveDashVideoIds()
                        if (!QualitySelectionPolicy.shouldAcceptPlayableResult(
                                requestKind = request.requestKind,
                                targetQn = request.targetQn,
                                returnedQuality = payload.quality,
                                dashVideoIds = dashVideoIds
                            )
                        ) {
                            com.bbttvv.app.core.util.Logger.d(
                                TAG,
                                "Reject downgraded DASH result: requestedQn=${request.targetQn}, quality=${payload.quality}, dashIds=$dashVideoIds"
                            )
                        } else {
                            com.bbttvv.app.core.util.Logger.d(TAG, "DASH success: quality=${payload.quality}")
                            return PlayUrlFetchResult(payload, PlayUrlSource.DASH)
                        }
                    } else {
                        result.diagnosis?.let { diagnosis ->
                            com.bbttvv.app.core.util.Logger.w(TAG, "DASH failed: ${diagnosis.userMessage}")
                        }
                    }
                }

                PlayUrlSource.LEGACY -> {
                    com.bbttvv.app.core.util.Logger.d(TAG, "DASH failed, trying legacy playurl API")
                    val result = gateway.fetchLegacy(80)
                    val payload = result.data
                    if (payload != null && hasPlayableStreams(payload)) {
                        val dashVideoIds = payload.resolveDashVideoIds()
                        if (!QualitySelectionPolicy.shouldAcceptPlayableResult(
                                requestKind = request.requestKind,
                                targetQn = request.targetQn,
                                returnedQuality = payload.quality,
                                dashVideoIds = dashVideoIds
                            )
                        ) {
                            com.bbttvv.app.core.util.Logger.d(
                                TAG,
                                "Reject downgraded legacy result: requestedQn=${request.targetQn}, quality=${payload.quality}, dashIds=$dashVideoIds"
                            )
                        } else {
                            com.bbttvv.app.core.util.Logger.d(TAG, "Legacy API success: quality=${payload.quality}")
                            return PlayUrlFetchResult(payload, PlayUrlSource.LEGACY)
                        }
                    } else {
                        result.diagnosis?.let { diagnosis ->
                            com.bbttvv.app.core.util.Logger.w(TAG, "Legacy API failed: ${diagnosis.userMessage}")
                        }
                    }
                }

                else -> Unit
            }
        }

        com.bbttvv.app.core.util.Logger.e(TAG, "All attempts failed for bvid=${request.bvid}")
        return null
    }
}

private fun hasPlayableStreams(data: PlayUrlData?): Boolean {
    if (data == null) return false
    return !data.durl.isNullOrEmpty() || !data.dash?.video.isNullOrEmpty()
}

private fun PlayUrlData.resolveDashVideoIds(): List<Int> {
    return dash?.video?.map { it.id }?.distinct() ?: emptyList()
}
