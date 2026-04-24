package com.bbttvv.app.data.repository

import android.content.Context
import com.bbttvv.app.core.cache.PlayUrlCache
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.data.model.response.PlayUrlData
import com.bbttvv.app.data.model.response.ViewInfo
import com.bbttvv.app.data.service.video.VideoSessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PlaybackRepository {
    private val api get() = VideoSessionService.api

    fun init(context: Context) {
        PlaybackSessionManager.init(context)
    }

    suspend fun reportPlayHeartbeat(
        bvid: String,
        cid: Long,
        playedTime: Long,
        realPlayedTime: Long,
        startTsSec: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val context = com.bbttvv.app.core.network.NetworkModule.appContext
            if (context != null && com.bbttvv.app.core.store.SettingsManager.isPrivacyModeEnabledSync(context)) {
                com.bbttvv.app.core.util.Logger.d("PlaybackRepo", "Privacy mode enabled, skipping heartbeat report")
                return@withContext true
            }

            com.bbttvv.app.core.util.Logger.d(
                "PlaybackRepo",
                "Reporting heartbeat: bvid=$bvid, cid=$cid, playedTime=$playedTime, realPlayedTime=$realPlayedTime, startTs=$startTsSec"
            )
            val resp = api.reportHeartbeat(
                bvid = bvid,
                cid = cid,
                playedTime = playedTime,
                realPlayedTime = realPlayedTime,
                startTs = startTsSec
            )
            com.bbttvv.app.core.util.Logger.d("PlaybackRepo", "Heartbeat response: code=${resp.code}, msg=${resp.message}")
            resp.code == 0
        } catch (e: Exception) {
            android.util.Log.e("PlaybackRepo", "Heartbeat failed: ${e.message}")
            false
        }
    }

    suspend fun reportPlayHistoryProgress(
        aid: Long,
        cid: Long,
        progressSec: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val context = com.bbttvv.app.core.network.NetworkModule.appContext
            if (context != null && com.bbttvv.app.core.store.SettingsManager.isPrivacyModeEnabledSync(context)) {
                com.bbttvv.app.core.util.Logger.d("PlaybackRepo", "Privacy mode enabled, skipping history report")
                return@withContext true
            }
            if (aid <= 0L || cid <= 0L) {
                com.bbttvv.app.core.util.Logger.w(
                    "PlaybackRepo",
                    "Skip history report: invalid aid/cid aid=$aid cid=$cid"
                )
                return@withContext false
            }
            val csrf = TokenManager.csrfCache.orEmpty().trim()
            if (csrf.isBlank()) {
                com.bbttvv.app.core.util.Logger.w("PlaybackRepo", "Skip history report: csrf missing")
                return@withContext false
            }

            com.bbttvv.app.core.util.Logger.d(
                "PlaybackRepo",
                "Reporting history progress: aid=$aid, cid=$cid, progress=$progressSec"
            )
            val resp = api.reportHistoryProgress(
                aid = aid,
                cid = cid,
                progress = progressSec.coerceAtLeast(0L),
                platform = "android",
                csrf = csrf,
                csrfToken = csrf
            )
            com.bbttvv.app.core.util.Logger.d("PlaybackRepo", "History report response: code=${resp.code}, msg=${resp.message}")
            resp.code == 0
        } catch (e: Exception) {
            android.util.Log.e("PlaybackRepo", "History report failed: ${e.message}")
            false
        }
    }

    suspend fun refreshVipStatusForPreferredQualityIfNeeded(
        isLoggedIn: Boolean,
        cachedIsVip: Boolean,
        storedQuality: Int,
        autoHighestEnabled: Boolean
    ): Boolean {
        if (
            !com.bbttvv.app.core.util.shouldRefreshVipStatusBeforeResolvingDefaultQuality(
                storedQuality = storedQuality,
                autoHighestEnabled = autoHighestEnabled,
                isLoggedIn = isLoggedIn,
                cachedIsVip = cachedIsVip
            )
        ) {
            return cachedIsVip
        }

        return SubtitleAndAuxRepository.getNavInfo()
            .getOrNull()
            ?.takeIf { it.isLogin }
            ?.let { navData ->
                val isVip = navData.vip.status == 1
                TokenManager.isVipCache = isVip
                com.bbttvv.app.core.util.Logger.d(
                    "PlaybackRepo",
                    "Refreshed VIP status before quality resolution: cached=$cachedIsVip, refreshed=$isVip, storedQuality=$storedQuality, autoHighest=$autoHighestEnabled"
                )
                isVip
            }
            ?: cachedIsVip
    }

    suspend fun getVideoDetails(
        bvid: String,
        aid: Long = 0L,
        requestedCid: Long = 0L,
        targetQuality: Int = 80,
        audioLang: String? = null
    ): Result<Pair<ViewInfo, PlayUrlData>> = withContext(Dispatchers.IO) {
        try {
            val lookup = resolveVideoInfoLookupInput(rawBvid = bvid, aid = aid)
                ?: throw Exception("无效的视频标识: bvid=$bvid, aid=$aid")
            val viewResp = if (lookup.bvid.isNotEmpty()) {
                com.bbttvv.app.core.util.Logger.d("PlaybackRepo", "getVideoDetails: using bvid=${lookup.bvid}")
                VideoDetailRepository.getVideoInfo(lookup.bvid)
            } else {
                com.bbttvv.app.core.util.Logger.d("PlaybackRepo", "getVideoDetails: using aid=${lookup.aid}")
                api.getVideoInfoByAid(lookup.aid)
            }

            val rawInfo = viewResp.data ?: throw Exception("视频详情为空: ${viewResp.code}")
            val cid = resolveRequestedVideoCid(
                requestCid = requestedCid,
                infoCid = rawInfo.cid,
                pages = rawInfo.pages
            )
            val info = if (cid > 0L && cid != rawInfo.cid) {
                rawInfo.copy(cid = cid)
            } else {
                rawInfo
            }
            val cacheBvid = info.bvid.ifBlank { lookup.bvid.ifBlank { bvid } }

            com.bbttvv.app.core.util.Logger.d(
                "PlaybackRepo",
                "getVideoDetails: bvid=${info.bvid}, aid=${info.aid}, requestCid=$requestedCid, infoCid=${rawInfo.cid}, resolvedCid=$cid, title=${info.title.take(20)}..."
            )

            if (cid == 0L) throw Exception("CID 获取失败")

            val isAutoHighestQuality = targetQuality >= 127
            val isLogin = resolveVideoPlaybackAuthState(
                hasSessionCookie = !TokenManager.sessDataCache.isNullOrEmpty(),
                hasAccessToken = !TokenManager.accessTokenCache.isNullOrEmpty()
            )
            val isVip = TokenManager.isVipCache
            val auto1080pEnabled = try {
                val context = com.bbttvv.app.core.network.NetworkModule.appContext
                context?.let(SettingsManager::getAutoHighestQualitySync) ?: true
            } catch (_: Exception) {
                true
            }
            val autoResumeEnabled = try {
                val context = com.bbttvv.app.core.network.NetworkModule.appContext
                context?.let(SettingsManager::getPlayerAutoResumeEnabledSync) ?: true
            } catch (_: Exception) {
                true
            }

            val startQuality = resolveInitialStartQuality(
                targetQuality = targetQuality,
                isAutoHighestQuality = isAutoHighestQuality,
                isLogin = isLogin,
                isVip = isVip,
                auto1080pEnabled = auto1080pEnabled
            )
            com.bbttvv.app.core.util.Logger.d(
                "PlaybackRepo",
                buildStartQualityDecisionSummary(
                    bvid = cacheBvid.ifBlank { bvid },
                    cid = cid,
                    userSettingQuality = targetQuality,
                    startQuality = startQuality,
                    isAutoHighestQuality = isAutoHighestQuality,
                    isLoggedIn = isLogin,
                    isVip = isVip,
                    auto1080pEnabled = auto1080pEnabled,
                    audioLang = audioLang
                )
            )

            val shouldUsePlayUrlCache = !autoResumeEnabled &&
                !shouldSkipPlayUrlCache(isAutoHighestQuality, isVip, audioLang)
            if (shouldUsePlayUrlCache) {
                val cachedPlayData = PlayUrlCache.get(
                    bvid = cacheBvid,
                    cid = cid,
                    requestedQuality = startQuality
                )
                if (cachedPlayData != null) {
                    com.bbttvv.app.core.util.Logger.d(
                        "PlaybackRepo",
                        "Using cached PlayUrlData for bvid=$cacheBvid, requestedQuality=$startQuality"
                    )
                    return@withContext Result.success(Pair(info, cachedPlayData))
                }
            } else {
                com.bbttvv.app.core.util.Logger.d(
                    "PlaybackRepo",
                    "Skip cache: bvid=$cacheBvid, isAutoHighest=$isAutoHighestQuality, autoResume=$autoResumeEnabled, audioLang=${audioLang ?: "default"}"
                )
            }

            val playUrlBvid = cacheBvid.ifBlank { bvid }
            val fetchResult = PlayUrlResolver.fetch(
                bvid = playUrlBvid,
                cid = cid,
                targetQn = startQuality,
                audioLang = audioLang,
                requestKind = PlayUrlRequestKind.INITIAL
            ) ?: throw Exception("无法获取任何画质的播放地址")
            var effectiveInfo = info
            var effectiveCid = cid
            var effectiveFetchResult = fetchResult
            var effectivePlayData = fetchResult.data

            if (autoResumeEnabled) {
                val resumeCid = resolveAutoResumeTargetCid(
                    requestedCid = requestedCid,
                    currentCid = cid,
                    availablePages = info.pages,
                    playUrlData = effectivePlayData
                )
                if (resumeCid != null) {
                    val resumeFetchResult = PlayUrlResolver.fetch(
                        bvid = playUrlBvid,
                        cid = resumeCid,
                        targetQn = startQuality,
                        audioLang = audioLang,
                        requestKind = PlayUrlRequestKind.INITIAL
                    )
                    val resumePlayData = resumeFetchResult?.data
                    if (resumePlayData != null && hasPlayableStreamsForResume(resumePlayData)) {
                        effectiveInfo = info.copy(cid = resumeCid)
                        effectiveCid = resumeCid
                        effectiveFetchResult = resumeFetchResult
                        effectivePlayData = resumePlayData
                        com.bbttvv.app.core.util.Logger.d(
                            "PlaybackRepo",
                            "Auto resume switched cid: originalCid=$cid, resumeCid=$resumeCid, resumeMs=${resolveAutoResumePositionMs(resumeCid, resumePlayData)}"
                        )
                    }
                }
            }

            val playData = effectivePlayData

            val hasDash = !playData.dash?.video.isNullOrEmpty()
            val hasDurl = !playData.durl.isNullOrEmpty()
            val dashVideoIds = playData.dash?.video?.map { it.id }?.distinct()?.sortedDescending() ?: emptyList()
            com.bbttvv.app.core.util.Logger.d(
                "PlaybackRepo",
                buildPlayUrlFetchSummary(
                    bvid = playUrlBvid,
                    cid = effectiveCid,
                    source = effectiveFetchResult.source,
                    requestedQuality = startQuality,
                    returnedQuality = playData.quality,
                    acceptQualities = playData.accept_quality,
                    dashVideoIds = dashVideoIds,
                    hasDurl = hasDurl,
                    isLoggedIn = isLogin,
                    isVip = isVip,
                    audioLang = audioLang
                )
            )
            if (!hasDash && !hasDurl) throw Exception("播放地址解析失败 (无 dash/durl)")

            if (!autoResumeEnabled && shouldCachePlayUrlResult(effectiveFetchResult.source, audioLang)) {
                PlayUrlCache.put(
                    bvid = cacheBvid,
                    cid = effectiveCid,
                    data = playData,
                    quality = startQuality
                )
                com.bbttvv.app.core.util.Logger.d(
                    "PlaybackRepo",
                    "Cached PlayUrlData for bvid=$cacheBvid, cid=$effectiveCid, requestedQuality=$startQuality, actualQuality=${playData.quality}"
                )
            } else {
                com.bbttvv.app.core.util.Logger.d(
                    "PlaybackRepo",
                    "Skip cache write: source=${effectiveFetchResult.source}, autoResume=$autoResumeEnabled, audioLang=${audioLang ?: "default"}"
                )
            }

            Result.success(Pair(effectiveInfo, playData))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getPlayUrlData(
        bvid: String,
        cid: Long,
        qn: Int,
        audioLang: String? = null
    ): PlayUrlData? {
        return PlayUrlResolver.getPlayUrlData(bvid = bvid, cid = cid, qn = qn, audioLang = audioLang)
    }

    suspend fun getPreviewVideoUrl(bvid: String, cid: Long): String? {
        return PlayUrlResolver.getPreviewVideoUrl(bvid = bvid, cid = cid)
    }
}

private const val MIN_AUTO_RESUME_POSITION_MS = 5_000L
private const val AUTO_RESUME_END_GUARD_MS = 10_000L

internal fun normalizeAutoResumePositionMs(
    lastPlayTimeSeconds: Int?,
    durationMs: Long
): Long {
    val rawPositionMs = (lastPlayTimeSeconds ?: 0).toLong() * 1000L
    if (rawPositionMs < MIN_AUTO_RESUME_POSITION_MS) return 0L
    if (durationMs <= 0L) return rawPositionMs
    val maxAllowedPositionMs = (durationMs - AUTO_RESUME_END_GUARD_MS).coerceAtLeast(0L)
    if (rawPositionMs >= maxAllowedPositionMs) return 0L
    return rawPositionMs.coerceAtMost(maxAllowedPositionMs)
}

internal fun resolveAutoResumePositionMs(
    currentCid: Long,
    playUrlData: PlayUrlData
): Long {
    val resumeCid = playUrlData.lastPlayCid?.takeIf { it > 0L } ?: currentCid
    if (resumeCid != currentCid) return 0L
    return normalizeAutoResumePositionMs(
        lastPlayTimeSeconds = playUrlData.lastPlayTime,
        durationMs = playUrlData.timelength
    )
}

private fun resolveAutoResumeTargetCid(
    requestedCid: Long,
    currentCid: Long,
    availablePages: List<com.bbttvv.app.data.model.response.Page>,
    playUrlData: PlayUrlData
): Long? {
    if (requestedCid != 0L) return null
    val resumeCid = playUrlData.lastPlayCid?.takeIf { it > 0L && it != currentCid } ?: return null
    if (normalizeAutoResumePositionMs(playUrlData.lastPlayTime, playUrlData.timelength) <= 0L) {
        return null
    }
    return resumeCid.takeIf { cid -> availablePages.any { it.cid == cid } }
}

private fun hasPlayableStreamsForResume(playUrlData: PlayUrlData): Boolean {
    return !playUrlData.dash?.video.isNullOrEmpty() || !playUrlData.durl.isNullOrEmpty()
}
