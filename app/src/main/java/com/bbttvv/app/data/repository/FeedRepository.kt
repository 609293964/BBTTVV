package com.bbttvv.app.data.repository

import com.bbttvv.app.core.coroutines.AppScope
import com.bbttvv.app.core.network.AppSignUtils
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.network.WbiKeyManager
import com.bbttvv.app.core.network.WbiUtils
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.data.model.response.*
import com.bbttvv.app.data.service.video.VideoFeedStateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

object FeedRepository {
    private val api get() = NetworkModule.api
    private var preloadedHomeVideos: Result<List<VideoItem>>?
        get() = VideoFeedStateService.preloadedHomeVideos
        set(value) {
            VideoFeedStateService.preloadedHomeVideos = value
        }
    private var homePreloadDeferred
        get() = VideoFeedStateService.homePreloadDeferred
        set(value) {
            VideoFeedStateService.homePreloadDeferred = value
        }
    private var hasCompletedHomePreload: Boolean
        get() = VideoFeedStateService.hasCompletedHomePreload
        set(value) {
            VideoFeedStateService.hasCompletedHomePreload = value
        }

    fun isHomeDataReady(): Boolean {
        return shouldReportHomeDataReadyForSplash(
            hasCompletedPreload = hasCompletedHomePreload,
            hasPreloadedData = preloadedHomeVideos != null
        )
    }

    fun preloadHomeData(scope: CoroutineScope = AppScope.ioScope) {
        val activePreloadTask = homePreloadDeferred?.takeIf { it.isActive } != null
        if (!shouldStartHomePreload(preloadedHomeVideos != null, activePreloadTask)) return
        hasCompletedHomePreload = false

        com.bbttvv.app.core.util.Logger.d("FeedRepo", "Starting home data preload")

        homePreloadDeferred = scope.async {
            try {
                val feedApiType = NetworkModule.appContext
                    ?.let { SettingsManager.getFeedApiTypeSync(it) }
                    ?: SettingsManager.FeedApiType.WEB
                if (shouldPrimeBuvidForHomePreload(feedApiType)) {
                    SubtitleAndAuxRepository.ensureBuvid3()
                }

                val result = getHomeVideosInternal(idx = 0)
                preloadedHomeVideos = result
                result
            } catch (e: Exception) {
                Result.failure<List<VideoItem>>(e).also { preloadedHomeVideos = it }
            } finally {
                hasCompletedHomePreload = true
            }
        }
    }

    private suspend fun awaitHomePreloadResult(): Result<List<VideoItem>>? {
        val deferred = homePreloadDeferred ?: return null
        return runCatching { deferred.await() }.getOrNull()
    }

    private fun consumePreloadedHomeVideos(): Result<List<VideoItem>>? {
        val cached = preloadedHomeVideos ?: return null
        preloadedHomeVideos = null
        homePreloadDeferred = null
        return cached
    }

    suspend fun getHomeVideos(idx: Int = 0): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        if (idx == 0) {
            consumePreloadedHomeVideos()?.let { return@withContext it }

            val hasActivePreloadTask = homePreloadDeferred?.isActive == true
            if (shouldReuseInFlightPreloadForHomeRequest(idx, hasActivePreloadTask, hasPreloadedData = false)) {
                val awaited = awaitHomePreloadResult()
                if (awaited != null) {
                    consumePreloadedHomeVideos()
                    return@withContext awaited
                }
            }
        }

        getHomeVideosInternal(idx)
    }

    private suspend fun getHomeVideosInternal(idx: Int): Result<List<VideoItem>> {
        return try {
            val context = NetworkModule.appContext
            val feedApiType = if (context != null) {
                SettingsManager.getFeedApiTypeSync(context)
            } else {
                SettingsManager.FeedApiType.WEB
            }
            val refreshCount = if (context != null) {
                SettingsManager.getHomeRefreshCountSync(context)
            } else {
                20
            }

            when (feedApiType) {
                SettingsManager.FeedApiType.MOBILE -> {
                    val mobileResult = fetchMobileFeed(idx = idx, refreshCount = refreshCount)
                    if (mobileResult.isSuccess && mobileResult.getOrNull()?.isNotEmpty() == true) {
                        mobileResult
                    } else {
                        fetchWebFeed(idx = idx, refreshCount = refreshCount)
                    }
                }
                else -> fetchWebFeed(idx = idx, refreshCount = refreshCount)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchWebFeed(idx: Int, refreshCount: Int): Result<List<VideoItem>> {
        return try {
            val cachedKeys = WbiKeyManager.getWbiKeys().getOrNull()
            val navWbiImg = if (cachedKeys == null) api.getNavInfo().data?.wbi_img else null
            val resolvedKeys = resolveHomeFeedWbiKeys(
                cachedKeys = cachedKeys,
                navWbiImg = navWbiImg
            ) ?: return Result.failure(Exception("WBI 密钥获取失败"))

            val params = mapOf(
                "ps" to refreshCount.toString(),
                "fresh_type" to "3",
                "fresh_idx" to idx.toString(),
                "feed_version" to System.currentTimeMillis().toString(),
                "y_num" to idx.toString()
            )
            val signedParams = WbiUtils.sign(params, resolvedKeys.first, resolvedKeys.second)
            val feedResp = api.getRecommendParams(signedParams)
            val list = feedResp.data?.item
                ?.map { it.toVideoItem() }
                ?.filter { it.bvid.isNotEmpty() }
                ?: emptyList()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchMobileFeed(idx: Int, refreshCount: Int): Result<List<VideoItem>> {
        return try {
            TokenManager.awaitWarmup()
            val accessToken = TokenManager.accessTokenCache
            if (accessToken.isNullOrEmpty()) {
                return Result.failure(Exception("需要登录才能使用移动端推荐流"))
            }

            val params = mapOf(
                "idx" to idx.toString(),
                "pull" to if (idx == 0) "1" else "0",
                "column" to "4",
                "flush" to "5",
                "autoplay_card" to "11",
                "ps" to refreshCount.toString(),
                "access_key" to accessToken,
                "appkey" to AppSignUtils.TV_APP_KEY,
                "ts" to AppSignUtils.getTimestamp().toString(),
                "mobi_app" to "android",
                "device" to "android",
                "build" to "8130300"
            )

            val signedParams = AppSignUtils.signForTvLogin(params)
            val feedResp = api.getMobileFeed(signedParams)
            if (feedResp.code != 0) {
                return Result.failure(Exception(feedResp.message))
            }

            val list = feedResp.data?.items
                ?.filter { it.goto == "av" }
                ?.map { it.toVideoItem() }
                ?.filter { it.bvid.isNotEmpty() }
                ?: emptyList()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPopularVideos(page: Int = 1): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getPopularVideos(pn = page, ps = 30)
            val list = resp.data?.list?.map { it.toVideoItem() }?.filter { it.bvid.isNotEmpty() } ?: emptyList()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRankingVideos(rid: Int = 0, type: String = "all"): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getRankingVideos(rid = rid, type = type)
            if (resp.code != 0) {
                return@withContext Result.failure(Exception(resp.message.ifBlank { "排行榜加载失败(${resp.code})" }))
            }
            val list = resp.data?.list?.map { it.toVideoItem() }?.filter { it.bvid.isNotEmpty() } ?: emptyList()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPreciousVideos(): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getPopularPreciousVideos()
            if (resp.code != 0) {
                return@withContext Result.failure(Exception(resp.message.ifBlank { "入站必刷加载失败(${resp.code})" }))
            }
            val list = resp.data?.list?.map { it.toVideoItem() }?.filter { it.bvid.isNotEmpty() } ?: emptyList()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWeeklyMustWatchVideos(number: Int? = null): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            val targetNumber = number ?: run {
                val listResp = api.getWeeklySeriesList()
                if (listResp.code != 0) {
                    return@withContext Result.failure(Exception(listResp.message.ifBlank { "每周必看列表加载失败(${listResp.code})" }))
                }
                listResp.data?.list?.map { it.number }?.maxOrNull() ?: 1
            }
            val resp = api.getWeeklySeriesVideos(number = targetNumber)
            if (resp.code != 0) {
                return@withContext Result.failure(Exception(resp.message.ifBlank { "每周必看加载失败(${resp.code})" }))
            }
            val list = resp.data?.list?.map { it.toVideoItem() }?.filter { it.bvid.isNotEmpty() } ?: emptyList()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRegionVideos(tid: Int, page: Int = 1): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getRegionVideos(rid = tid, pn = page, ps = 30)
            val list = resp.data?.archives?.map { it.toVideoItem() }?.filter { it.bvid.isNotEmpty() } ?: emptyList()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
