// 文件路径: data/repository/DynamicRepository.kt
package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.data.model.response.DynamicFeedResponse
import com.bbttvv.app.data.model.response.DynamicItem
import com.bbttvv.app.data.model.response.FollowedLiveRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val DYNAMIC_ABSOLUTE_PAGE_FETCH_LIMIT = 10
private const val DYNAMIC_USER_PAGINATION_STATE_LIMIT = 64

/**
 *  动态数据仓库
 * 
 * 负责从 B站 API 获取动态 Feed 数据
 */
object DynamicRepository {
    private val feedPagination = DynamicFeedPaginationRegistry()
    private val userFeedPagination = DynamicUserPaginationRegistry()
    
    /**
     * 获取动态列表
     * @param refresh 是否刷新 (重置分页)
     */
    suspend fun getDynamicFeed(
        refresh: Boolean = false,
        scope: DynamicFeedScope = DynamicFeedScope.DYNAMIC_SCREEN
    ): Result<List<DynamicItem>> = withContext(Dispatchers.IO) {
        try {
            val requestGeneration = if (refresh) {
                feedPagination.reset(scope)
            } else {
                feedPagination.generation(scope)
            }

            if (!feedPagination.hasMore(scope) && !refresh) {
                return@withContext Result.success(emptyList())
            }

            val visibleItems = mutableListOf<DynamicItem>()
            var pagesFetched = 0
            while (pagesFetched < DYNAMIC_ABSOLUTE_PAGE_FETCH_LIMIT) {
                val previousOffset = feedPagination.offset(scope)
                val response = fetchDynamicFeedPageWithRetry {
                    NetworkModule.dynamicApi.getDynamicFeed(
                        type = "video", // [MODIFY] Filter strictly to video-only for TV simplicity
                        offset = previousOffset
                    )
                }.getOrElse { error ->
                    return@withContext Result.failure(error)
                }

                val data = response.data
                if (data == null) {
                    feedPagination.update(
                        scope = scope,
                        offset = previousOffset,
                        hasMore = false,
                        generation = requestGeneration
                    )
                    break
                }

                // 更新分页状态
                val updated = feedPagination.update(
                    scope = scope,
                    offset = data.offset,
                    hasMore = data.has_more,
                    generation = requestGeneration
                )
                if (!updated) {
                    return@withContext Result.success(visibleItems)
                }

                // 过滤不可见的动态
                visibleItems += data.items.filter { it.visible }
                pagesFetched += 1

                if (!shouldContinueDynamicFetchAfterFilter(
                        accumulatedVisibleCount = visibleItems.size,
                        hasMore = data.has_more,
                        previousOffset = previousOffset,
                        nextOffset = data.offset,
                        pagesFetched = pagesFetched
                    )
                ) {
                    break
                }
            }

            Result.success(visibleItems)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     *  [新增] 获取指定用户的动态列表
     * @param hostMid UP主 mid
     * @param refresh 是否刷新 (重置分页)
     */
    suspend fun getUserDynamicFeed(hostMid: Long, refresh: Boolean = false): Result<List<DynamicItem>> = withContext(Dispatchers.IO) {
        try {
            val requestGeneration = if (refresh) {
                userFeedPagination.reset(hostMid)
            } else {
                userFeedPagination.generation(hostMid)
            }

            if (!userFeedPagination.hasMore(hostMid) && !refresh) {
                return@withContext Result.success(emptyList())
            }

            val visibleItems = mutableListOf<DynamicItem>()
            var pagesFetched = 0
            while (pagesFetched < DYNAMIC_ABSOLUTE_PAGE_FETCH_LIMIT) {
                val previousOffset = userFeedPagination.offset(hostMid)
                val response = fetchDynamicFeedPageWithRetry {
                    NetworkModule.dynamicApi.getUserDynamicFeed(
                        params = buildSelectedUserDynamicFeedParams(
                            hostMid = hostMid,
                            offset = previousOffset
                        )
                    )
                }.getOrElse { error ->
                    return@withContext Result.failure(error)
                }

                val data = response.data
                if (data == null) {
                    userFeedPagination.update(
                        hostMid = hostMid,
                        offset = previousOffset,
                        hasMore = false,
                        generation = requestGeneration
                    )
                    break
                }

                // 更新分页状态
                userFeedPagination.update(
                    hostMid = hostMid,
                    offset = data.offset,
                    hasMore = data.has_more,
                    generation = requestGeneration
                ).also { updated ->
                    if (!updated) {
                        return@withContext Result.success(visibleItems)
                    }
                }

                // 过滤不可见的动态
                visibleItems += data.items.filter { it.visible }
                pagesFetched += 1

                if (!shouldContinueDynamicFetchAfterFilter(
                        accumulatedVisibleCount = visibleItems.size,
                        hasMore = data.has_more,
                        previousOffset = previousOffset,
                        nextOffset = data.offset,
                        pagesFetched = pagesFetched
                    )
                ) {
                    break
                }
            }

            Result.success(visibleItems)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     *  [新增] 获取单条动态详情（桌面端详情接口）
     */
    suspend fun getDynamicDetail(dynamicId: String): Result<DynamicItem> = withContext(Dispatchers.IO) {
        try {
            val cleanedId = dynamicId.trim()
            if (cleanedId.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("dynamicId 不能为空"))
            }

            val desktopResponse = NetworkModule.dynamicApi.getDynamicDetail(id = cleanedId)
            if (desktopResponse.code == 0) {
                val item = desktopResponse.data?.item
                    ?: return@withContext Result.failure(Exception("动态详情为空"))
                if (!shouldFallbackForDynamicDetail(item)) {
                    return@withContext Result.success(item)
                }

                val fallbackResponse = NetworkModule.dynamicApi.getDynamicDetailFallback(id = cleanedId)
                if (fallbackResponse.code == 0) {
                    val fallbackItem = fallbackResponse.data?.item
                    if (fallbackItem != null) {
                        return@withContext Result.success(fallbackItem)
                    }
                }
                // fallback 失败时保底返回 desktop 结果，避免直接报错
                return@withContext Result.success(item)
            }

            // desktop 接口失败时降级到 web 详情接口（兼容更多动态类型）
            val fallbackResponse = NetworkModule.dynamicApi.getDynamicDetailFallback(id = cleanedId)
            if (fallbackResponse.code == 0) {
                val item = fallbackResponse.data?.item
                    ?: return@withContext Result.failure(Exception("动态详情为空"))
                return@withContext Result.success(item)
            }

            Result.failure(
                Exception(
                    "API error: ${desktopResponse.message.ifBlank { "desktop=${desktopResponse.code}" }}; " +
                        "fallback=${fallbackResponse.message.ifBlank { fallbackResponse.code.toString() }}"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 是否还有更多数据
     */
    fun hasMoreData(scope: DynamicFeedScope = DynamicFeedScope.DYNAMIC_SCREEN): Boolean {
        return feedPagination.hasMore(scope)
    }
    
    /**
     *  [新增] 用户动态是否还有更多
     */
    fun userHasMoreData(hostMid: Long?): Boolean {
        if (hostMid == null || hostMid <= 0L) return true
        return userFeedPagination.hasMore(hostMid)
    }
    
    /**
     * 重置分页状态
     */
    fun resetPagination(scope: DynamicFeedScope = DynamicFeedScope.DYNAMIC_SCREEN) {
        feedPagination.reset(scope)
    }
    
    /**
     *  [新增] 重置用户动态分页状态
     */
    fun resetUserPagination(hostMid: Long? = null) {
        if (hostMid == null || hostMid <= 0L) {
            userFeedPagination.resetAll()
        } else {
            userFeedPagination.reset(hostMid)
        }
    }

    private fun buildSelectedUserDynamicFeedParams(
        hostMid: Long,
        offset: String
    ): Map<String, String> {
        return mapOf(
            "host_mid" to hostMid.toString(),
            "offset" to offset,
            "page" to "1",
            "features" to "itemOpusStyle,listOnlyfans",
            "timezone_offset" to "-480",
            "platform" to "web",
            "web_location" to "333.1387"
        )
    }

    /**
     *  [NEW] 获取关注的正在直播的主播列表
     */
    suspend fun getFollowedLiveUsers(page: Int = 1, pageSize: Int = 30): Result<List<FollowedLiveRoom>> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkModule.api.getFollowedLive(page = page, pageSize = pageSize)
            if (response.code == 0 && response.data != null) {
                Result.success(
                    response.data.list
                        ?.filter { it.liveStatus == 1 }
                        ?: emptyList()
                )
            } else {
                Result.failure(Exception(response.message.ifBlank { "Failed to fetch followed live" }))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun fetchDynamicFeedPageWithRetry(
        request: suspend () -> DynamicFeedResponse
    ): Result<DynamicFeedResponse> {
        var lastError: Throwable? = null
        for (attempt in 1..DYNAMIC_FETCH_MAX_ATTEMPTS) {
            try {
                val response = request()
                if (response.code == 0) {
                    return Result.success(response)
                }
                val shouldRetry = attempt < DYNAMIC_FETCH_MAX_ATTEMPTS &&
                    isRetryableDynamicApiError(response.code, response.message)
                if (shouldRetry) {
                    delay(resolveDynamicRetryDelayMs(attempt))
                    continue
                }
                val message = resolveDynamicFriendlyErrorMessage(response.code, response.message)
                return Result.failure(Exception(message))
            } catch (error: Exception) {
                lastError = error
                val shouldRetry = attempt < DYNAMIC_FETCH_MAX_ATTEMPTS &&
                    isRetryableDynamicException(error)
                if (shouldRetry) {
                    delay(resolveDynamicRetryDelayMs(attempt))
                    continue
                }
                val message = resolveDynamicFriendlyErrorMessage(code = -1, message = error.message.orEmpty())
                return Result.failure(Exception(message, error))
            }
        }
        val message = resolveDynamicFriendlyErrorMessage(code = -1, message = lastError?.message.orEmpty())
        return Result.failure(Exception(message, lastError))
    }
}

enum class DynamicFeedScope {
    DYNAMIC_SCREEN,
    HOME_FOLLOW
}

internal data class DynamicPaginationState(
    var offset: String = "",
    var hasMore: Boolean = true,
    var generation: Long = 0L
)

internal class DynamicFeedPaginationRegistry {
    private val lock = Any()
    private val stateByScope = mutableMapOf<DynamicFeedScope, DynamicPaginationState>()

    fun reset(scope: DynamicFeedScope): Long {
        return synchronized(lock) {
            val nextGeneration = (stateByScope[scope]?.generation ?: 0L) + 1L
            stateByScope[scope] = DynamicPaginationState(generation = nextGeneration)
            nextGeneration
        }
    }

    fun update(
        scope: DynamicFeedScope,
        offset: String,
        hasMore: Boolean,
        generation: Long? = null
    ): Boolean {
        return synchronized(lock) {
            val current = stateByScope[scope] ?: DynamicPaginationState()
            if (generation != null && current.generation != generation) {
                return@synchronized false
            }
            stateByScope[scope] = DynamicPaginationState(
                offset = offset,
                hasMore = hasMore,
                generation = current.generation
            )
            true
        }
    }

    fun generation(scope: DynamicFeedScope): Long {
        return synchronized(lock) {
            stateByScope[scope]?.generation ?: 0L
        }
    }

    fun offset(scope: DynamicFeedScope): String {
        return synchronized(lock) {
            stateByScope[scope]?.offset.orEmpty()
        }
    }

    fun hasMore(scope: DynamicFeedScope): Boolean {
        return synchronized(lock) {
            stateByScope[scope]?.hasMore ?: true
        }
    }
}

internal class DynamicUserPaginationRegistry {
    private val lock = Any()
    private var resetAllGeneration: Long = 0L
    private val stateByUser = object : LinkedHashMap<Long, DynamicPaginationState>(
        DYNAMIC_USER_PAGINATION_STATE_LIMIT,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Long, DynamicPaginationState>
        ): Boolean {
            return size > DYNAMIC_USER_PAGINATION_STATE_LIMIT
        }
    }

    fun reset(hostMid: Long): Long {
        return synchronized(lock) {
            val currentGeneration = stateByUser[hostMid]?.generation ?: resetAllGeneration
            val nextGeneration = currentGeneration + 1L
            stateByUser[hostMid] = DynamicPaginationState(generation = nextGeneration)
            nextGeneration
        }
    }

    fun resetAll() {
        synchronized(lock) {
            resetAllGeneration += 1L
            stateByUser.clear()
        }
    }

    fun update(
        hostMid: Long,
        offset: String,
        hasMore: Boolean,
        generation: Long? = null
    ): Boolean {
        return synchronized(lock) {
            val current = stateByUser[hostMid] ?: DynamicPaginationState(generation = resetAllGeneration)
            if (generation != null && current.generation != generation) {
                return@synchronized false
            }
            stateByUser[hostMid] = DynamicPaginationState(
                offset = offset,
                hasMore = hasMore,
                generation = current.generation
            )
            true
        }
    }

    fun generation(hostMid: Long): Long {
        return synchronized(lock) {
            stateByUser[hostMid]?.generation ?: resetAllGeneration
        }
    }

    fun offset(hostMid: Long): String {
        return synchronized(lock) {
            stateByUser[hostMid]?.offset.orEmpty()
        }
    }

    fun hasMore(hostMid: Long): Boolean {
        return synchronized(lock) {
            stateByUser[hostMid]?.hasMore ?: true
        }
    }
}

