// 文件路径: data/repository/DynamicRepository.kt
package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.DynamicFeedResponse
import com.bbttvv.app.data.model.response.DynamicItem
import com.bbttvv.app.data.model.response.DynamicPortalUpItem
import com.bbttvv.app.data.model.response.FollowedLiveRoom
import com.bbttvv.app.data.model.response.FollowingUser
import com.bbttvv.app.data.model.response.FollowingsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val DYNAMIC_ABSOLUTE_PAGE_FETCH_LIMIT = 10
private const val DYNAMIC_FEED_REQUEST_TYPE = "all"
private const val DYNAMIC_USER_PAGINATION_STATE_LIMIT = 64
private const val DYNAMIC_FOLLOW_UPDATES_PAGE_SIZE = 15
private const val DYNAMIC_FOLLOW_UPDATES_UP_LIMIT = 15
private const val DYNAMIC_FOLLOW_UPDATES_TAG = "DynamicFollowUpdates"

sealed interface DynamicFollowUpdateItem {
    val key: String
}

enum class DynamicFollowUpdateFixedKind(
    val title: String,
    val iconText: String,
    val stableKey: String
) {
    SPECIAL(title = "特别关注", iconText = "特", stableKey = "fixed:special"),
    DEFAULT_GROUP(title = "默认分组", iconText = "组", stableKey = "fixed:default")
}

data class DynamicFollowUpdateFixedItem(
    val kind: DynamicFollowUpdateFixedKind
) : DynamicFollowUpdateItem {
    override val key: String = kind.stableKey
    val title: String = kind.title
    val iconText: String = kind.iconText
}

data class DynamicFollowUpdateUpItem(
    val mid: Long,
    val name: String,
    val face: String,
    val hasUpdate: Boolean
) : DynamicFollowUpdateItem {
    override val key: String = "up:$mid"
}

private val DynamicFollowUpdateFixedItems = listOf(
    DynamicFollowUpdateFixedItem(DynamicFollowUpdateFixedKind.SPECIAL),
    DynamicFollowUpdateFixedItem(DynamicFollowUpdateFixedKind.DEFAULT_GROUP)
)

internal fun defaultDynamicFollowUpdateItems(): List<DynamicFollowUpdateItem> {
    return DynamicFollowUpdateFixedItems
}

internal fun buildDynamicFollowUpdateItems(
    followings: List<FollowingUser>,
    portalItems: List<DynamicPortalUpItem>
): List<DynamicFollowUpdateItem> {
    val followingsByMid = LinkedHashMap<Long, FollowingUser>()
    followings.forEach { user ->
        if (user.mid > 0L && !followingsByMid.containsKey(user.mid)) {
            followingsByMid[user.mid] = user
        }
    }

    val addedMids = linkedSetOf<Long>()
    val upItems = mutableListOf<DynamicFollowUpdateUpItem>()
    portalItems.forEach { portalItem ->
        val mid = portalItem.mid
        if (mid <= 0L || !portalItem.has_update || !addedMids.add(mid)) return@forEach
        val following = followingsByMid[mid]
        upItems += DynamicFollowUpdateUpItem(
            mid = mid,
            name = portalItem.name.ifBlank { following?.uname.orEmpty() },
            face = portalItem.face.ifBlank { following?.face.orEmpty() },
            hasUpdate = true
        )
    }

    followingsByMid.values.forEach { following ->
        if (addedMids.add(following.mid)) {
            upItems += DynamicFollowUpdateUpItem(
                mid = following.mid,
                name = following.uname,
                face = following.face,
                hasUpdate = false
            )
        }
    }

    return DynamicFollowUpdateFixedItems + upItems.take(DYNAMIC_FOLLOW_UPDATES_UP_LIMIT)
}

internal fun clearDynamicFollowUpdatePrompt(
    items: List<DynamicFollowUpdateItem>,
    mid: Long
): List<DynamicFollowUpdateItem> {
    if (mid <= 0L) return items
    var changed = false
    val updated = items.map { item ->
        if (item is DynamicFollowUpdateUpItem && item.mid == mid && item.hasUpdate) {
            changed = true
            item.copy(hasUpdate = false)
        } else {
            item
        }
    }
    return if (changed) updated else items
}

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
     * @param incrementalRefresh 是否保留现有时间线，仅拉取更新基线之后的内容
     */
    suspend fun getDynamicFeed(
        refresh: Boolean = false,
        scope: DynamicFeedScope = DynamicFeedScope.DYNAMIC_SCREEN,
        incrementalRefresh: Boolean = false
    ): Result<List<DynamicItem>> = withContext(Dispatchers.IO) {
        try {
            val paginationBeforeRefresh = feedPagination.snapshot(scope)
            val useIncrementalRefresh = shouldUseDynamicIncrementalRefresh(
                refresh = refresh,
                incrementalRefreshEnabled = incrementalRefresh,
                updateBaseline = paginationBeforeRefresh.updateBaseline
            )
            val requestGeneration = if (refresh && !useIncrementalRefresh) {
                feedPagination.reset(scope)
            } else {
                feedPagination.generation(scope)
            }
            val paginationForPageUpdate = if (refresh && !useIncrementalRefresh) {
                DynamicPaginationState(generation = requestGeneration)
            } else {
                paginationBeforeRefresh
            }

            if (!feedPagination.hasMore(scope) && !refresh) {
                return@withContext Result.success(emptyList())
            }

            val visibleItems = mutableListOf<DynamicItem>()
            var pagesFetched = 0
            var requestOffset = if (refresh) "" else feedPagination.offset(scope)
            while (pagesFetched < DYNAMIC_ABSOLUTE_PAGE_FETCH_LIMIT) {
                val previousOffset = requestOffset
                val response = fetchDynamicFeedPageWithRetry {
                    NetworkModule.dynamicApi.getDynamicFeed(
                        type = DYNAMIC_FEED_REQUEST_TYPE,
                        offset = previousOffset,
                        updateBaseline = if (previousOffset.isBlank() && useIncrementalRefresh) {
                            paginationBeforeRefresh.updateBaseline
                        } else {
                            ""
                        }
                    )
                }.getOrElse { error ->
                    return@withContext Result.failure(error)
                }

                val data = response.data
                if (data == null) {
                    val updated = feedPagination.updateState(
                        scope = scope,
                        state = resolveDynamicPaginationStateAfterPage(
                            paginationBeforeRefresh = paginationForPageUpdate,
                            responseOffset = previousOffset,
                            responseUpdateBaseline = "",
                            responseHasMore = false,
                            preserveExistingPagination = useIncrementalRefresh
                        ),
                        generation = requestGeneration
                    )
                    if (!updated) {
                        return@withContext Result.success(visibleItems)
                    }
                    break
                }

                // 更新分页状态
                requestOffset = data.offset
                val updated = feedPagination.updateState(
                    scope = scope,
                    state = resolveDynamicPaginationStateAfterPage(
                        paginationBeforeRefresh = paginationForPageUpdate,
                        responseOffset = data.offset,
                        responseUpdateBaseline = data.update_baseline,
                        responseHasMore = data.has_more,
                        preserveExistingPagination = useIncrementalRefresh
                    ),
                    generation = requestGeneration
                )
                if (!updated) {
                    return@withContext Result.success(visibleItems)
                }

                // 动态接口的 video 类型过滤可能返回空，改为请求 all 后仅保留 TV 视频网格可渲染的卡片。
                visibleItems += data.items.filter { it.visible && it.hasRenderableVideoCard() }
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
            Logger.e("DynamicRepo", "getDynamicFeed failed: scope=$scope, refresh=$refresh", e)
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
            Logger.e("DynamicRepo", "getUserDynamicFeed failed: hostMid=$hostMid, refresh=$refresh", e)
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
            Logger.e("DynamicRepo", "getDynamicDetail failed: dynamicId=$dynamicId", e)
            Result.failure(e)
        }
    }
    
    /**
     * 是否还有更多数据
     */
    fun hasMoreData(scope: DynamicFeedScope = DynamicFeedScope.DYNAMIC_SCREEN): Boolean {
        return feedPagination.hasMore(scope)
    }

    fun canIncrementallyRefresh(
        scope: DynamicFeedScope = DynamicFeedScope.DYNAMIC_SCREEN
    ): Boolean {
        return feedPagination.updateBaseline(scope).isNotBlank()
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
            Logger.e("DynamicRepo", "getFollowedLiveUsers failed: page=$page, pageSize=$pageSize", e)
            Result.failure(e)
        }
    }

    suspend fun getFollowUpdateItems(): Result<List<DynamicFollowUpdateItem>> = withContext(Dispatchers.IO) {
        try {
            TokenManager.awaitWarmup()
            val currentMidResult = resolveCurrentMidForFollowUpdates()
            val followingsResult = currentMidResult
                .mapCatching { currentMid -> fetchDynamicFollowUpdateFollowings(currentMid) }
            val portalItemsResult = fetchDynamicPortalUpdateItems()

            val followings = followingsResult.getOrDefault(emptyList())
            val portalItems = portalItemsResult.getOrDefault(emptyList())
            if (followingsResult.isFailure) {
                Logger.w(
                    DYNAMIC_FOLLOW_UPDATES_TAG,
                    "followings unavailable: ${followingsResult.exceptionOrNull()?.message.orEmpty()}"
                )
            }
            if (portalItemsResult.isFailure) {
                Logger.w(
                    DYNAMIC_FOLLOW_UPDATES_TAG,
                    "portal unavailable: ${portalItemsResult.exceptionOrNull()?.message.orEmpty()}"
                )
            }

            if (followingsResult.isSuccess || portalItemsResult.isSuccess) {
                return@withContext Result.success(buildDynamicFollowUpdateItems(followings, portalItems))
            }

            val error = followingsResult.exceptionOrNull()
                ?: portalItemsResult.exceptionOrNull()
                ?: Exception("关注更新加载失败")
            Result.failure(error)
        } catch (e: Exception) {
            Logger.e(DYNAMIC_FOLLOW_UPDATES_TAG, "getFollowUpdateItems failed", e)
            Result.failure(e)
        }
    }

    suspend fun consumeFollowUpdatePrompt(mid: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (mid <= 0L) {
                return@withContext Result.failure(IllegalArgumentException("Invalid mid"))
            }
            val response = NetworkModule.dynamicApi.consumeUserDynamicUpdatePrompt(hostMid = mid)
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifBlank { "关注更新提示同步失败(${response.code})" }))
            }
        } catch (e: Exception) {
            Logger.e(DYNAMIC_FOLLOW_UPDATES_TAG, "consumeFollowUpdatePrompt failed: mid=$mid", e)
            Result.failure(e)
        }
    }

    private suspend fun resolveCurrentMidForFollowUpdates(): Result<Long> {
        val cachedMid = TokenManager.midCache?.takeIf { it > 0L }
        if (cachedMid != null) return Result.success(cachedMid)

        return runCatching {
            val navResponse = NetworkModule.api.getNavInfo()
            val navMid = navResponse.data?.mid?.takeIf { it > 0L }
            navMid ?: throw Exception("未登录")
        }
    }

    private suspend fun fetchDynamicFollowUpdateFollowings(currentMid: Long): List<FollowingUser> {
        val response: FollowingsResponse = NetworkModule.api.getFollowings(
            vmid = currentMid,
            pn = 1,
            ps = DYNAMIC_FOLLOW_UPDATES_PAGE_SIZE
        )
        if (response.code != 0) {
            throw Exception(response.message.ifBlank { "关注列表加载失败(${response.code})" })
        }
        return response.data?.list.orEmpty()
    }

    private suspend fun fetchDynamicPortalUpdateItems(): Result<List<DynamicPortalUpItem>> {
        return runCatching {
            val response = NetworkModule.dynamicApi.getDynamicPortal()
            if (response.code != 0) {
                throw Exception(response.message.ifBlank { "关注更新状态加载失败(${response.code})" })
            }
            response.data?.up_list?.items.orEmpty()
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

private fun DynamicItem.hasRenderableVideoCard(): Boolean {
    return resolveRenderableVideoSource() != null
}

internal fun DynamicItem.resolveRenderableVideoSource(): DynamicItem? {
    var candidate: DynamicItem? = this
    repeat(4) {
        val current = candidate ?: return null
        val major = current.modules.module_dynamic?.major
        if (
            major?.archive?.bvid?.isNotBlank() == true ||
            major?.ugc_season?.archive?.bvid?.isNotBlank() == true
        ) {
            return current
        }
        candidate = current.orig
    }
    return null
}

enum class DynamicFeedScope {
    DYNAMIC_SCREEN,
    HOME_FOLLOW
}

internal data class DynamicPaginationState(
    val offset: String = "",
    val updateBaseline: String = "",
    val hasMore: Boolean = true,
    val generation: Long = 0L
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
        updateBaseline: String = "",
        hasMore: Boolean,
        generation: Long? = null
    ): Boolean {
        val current = snapshot(scope)
        return updateState(
            scope = scope,
            state = DynamicPaginationState(
                offset = offset,
                updateBaseline = updateBaseline.ifBlank { current.updateBaseline },
                hasMore = hasMore,
                generation = current.generation
            ),
            generation = generation
        )
    }

    fun updateState(
        scope: DynamicFeedScope,
        state: DynamicPaginationState,
        generation: Long? = null
    ): Boolean {
        return synchronized(lock) {
            val current = stateByScope[scope] ?: DynamicPaginationState()
            if (generation != null && current.generation != generation) {
                return@synchronized false
            }
            stateByScope[scope] = state.copy(
                generation = current.generation
            )
            true
        }
    }

    fun snapshot(scope: DynamicFeedScope): DynamicPaginationState {
        return synchronized(lock) {
            stateByScope[scope]?.copy() ?: DynamicPaginationState()
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

    fun updateBaseline(scope: DynamicFeedScope): String {
        return synchronized(lock) {
            stateByScope[scope]?.updateBaseline.orEmpty()
        }
    }

    fun hasMore(scope: DynamicFeedScope): Boolean {
        return synchronized(lock) {
            stateByScope[scope]?.hasMore ?: true
        }
    }
}

internal fun shouldUseDynamicIncrementalRefresh(
    refresh: Boolean,
    incrementalRefreshEnabled: Boolean,
    updateBaseline: String
): Boolean {
    return refresh && incrementalRefreshEnabled && updateBaseline.isNotBlank()
}

internal fun resolveDynamicPaginationStateAfterPage(
    paginationBeforeRefresh: DynamicPaginationState,
    responseOffset: String,
    responseUpdateBaseline: String,
    responseHasMore: Boolean,
    preserveExistingPagination: Boolean
): DynamicPaginationState {
    val nextBaseline = responseUpdateBaseline.ifBlank {
        paginationBeforeRefresh.updateBaseline
    }
    return if (preserveExistingPagination) {
        paginationBeforeRefresh.copy(updateBaseline = nextBaseline)
    } else {
        DynamicPaginationState(
            offset = responseOffset,
            updateBaseline = nextBaseline,
            hasMore = responseHasMore,
            generation = paginationBeforeRefresh.generation
        )
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
