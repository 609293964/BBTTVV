package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.store.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 用户操作相关 Repository
 * - 关注/取关 UP 主
 * - 收藏/取消收藏视频
 */
object ActionRepository {
    private val api = NetworkModule.api
    private const val SPECIAL_FOLLOW_TAG_ID = -10L
    private const val FOLLOW_GROUP_BATCH_SIZE = 20
    private const val FOLLOW_GROUP_MAX_RETRIES = 3
    private const val FOLLOW_GROUP_REQUEST_INTERVAL_MS = 220L
    private const val FOLLOW_GROUP_RETRY_BASE_DELAY_MS = 900L
    private const val FOLLOW_GROUP_QUERY_MAX_RETRIES = 3
    private const val FOLLOW_GROUP_QUERY_RETRY_BASE_DELAY_MS = 600L
    private const val FOLLOW_GROUP_TAG_MEMBERS_PAGE_SIZE = 100
    private const val FOLLOW_GROUP_TAG_MEMBERS_MAX_PAGES = 120

    private fun normalizeRelationTagIds(raw: Set<Long>): Set<Long> {
        return raw.asSequence().filter { it != 0L }.toSet()
    }

    private fun normalizeRelationTags(
        raw: List<com.bbttvv.app.data.model.response.RelationTagItem>
    ): List<com.bbttvv.app.data.model.response.RelationTagItem> {
        val merged = raw.distinctBy { it.tagid }.toMutableList()
        if (merged.none { it.tagid == SPECIAL_FOLLOW_TAG_ID }) {
            merged += com.bbttvv.app.data.model.response.RelationTagItem(
                tagid = SPECIAL_FOLLOW_TAG_ID,
                name = "特别关注",
                count = 0,
                tip = "第一时间收到该分组下用户更新稿件的通知"
            )
        }
        return merged.sortedBy { it.tagid != SPECIAL_FOLLOW_TAG_ID }
    }

    internal fun chunkFollowGroupTargetMids(
        targetMids: Set<Long>,
        chunkSize: Int = FOLLOW_GROUP_BATCH_SIZE
    ): List<List<Long>> {
        if (targetMids.isEmpty()) return emptyList()
        val effectiveChunkSize = chunkSize.coerceAtLeast(1)
        return targetMids
            .asSequence()
            .filter { it > 0L }
            .distinct()
            .toList()
            .chunked(effectiveChunkSize)
    }

    internal fun isFollowGroupRetryableError(code: Int, message: String): Boolean {
        if (code in setOf(-412, -352, -509, 22015)) return true
        if (message.isBlank()) return false
        return message.contains("频繁") ||
            message.contains("过快") ||
            message.contains("风控") ||
            message.contains("稍后") ||
            message.contains("too many", ignoreCase = true) ||
            message.contains("rate", ignoreCase = true)
    }

    private suspend fun addUsersToRelationTagsWithRetry(
        fids: String,
        tagIds: String,
        csrf: String
    ): Result<Unit> {
        var lastCode = Int.MIN_VALUE
        var lastMessage = ""

        repeat(FOLLOW_GROUP_MAX_RETRIES) { attempt ->
            val response = api.addUsersToRelationTags(
                fids = fids,
                tagIds = tagIds,
                csrf = csrf
            )
            if (response.code == 0) {
                return Result.success(Unit)
            }

            lastCode = response.code
            lastMessage = response.message
            val retryable = isFollowGroupRetryableError(response.code, response.message)
            if (!retryable || attempt >= FOLLOW_GROUP_MAX_RETRIES - 1) {
                return Result.failure(
                    Exception(response.message.ifEmpty { "分组设置失败: ${response.code}" })
                )
            }

            val backoffMs = FOLLOW_GROUP_RETRY_BASE_DELAY_MS * (attempt + 1)
            delay(backoffMs)
        }

        return Result.failure(
            Exception(if (lastMessage.isNotEmpty()) lastMessage else "分组设置失败: $lastCode")
        )
    }

    /**
     * 关注/取关 UP 主
     * @param mid UP 主的用户 ID
     * @param follow true=关注, false=取关
     */
    suspend fun followUser(mid: Long, follow: Boolean): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val csrf = TokenManager.csrfCache ?: ""
                com.bbttvv.app.core.util.Logger.d("ActionRepository", " followUser: mid=$mid, follow=$follow, csrf.length=${csrf.length}")
                if (csrf.isEmpty()) {
                    android.util.Log.e("ActionRepository", " CSRF token is empty!")
                    return@withContext Result.failure(Exception("请先登录"))
                }
                
                val act = if (follow) 1 else 2
                com.bbttvv.app.core.util.Logger.d("ActionRepository", " Calling modifyRelation...")
                val response = api.modifyRelation(fid = mid, act = act, csrf = csrf)
                com.bbttvv.app.core.util.Logger.d("ActionRepository", " Response: code=${response.code}, message=${response.message}")
                
                if (response.code == 0) {
                    Result.success(follow)
                } else {
                    Result.failure(Exception(response.message.ifEmpty { "操作失败: ${response.code}" }))
                }
            } catch (e: Exception) {
                android.util.Log.e("ActionRepository", "followUser failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 收藏/取消收藏视频
     * @param aid 视频的 aid
     * @param favorite true=收藏, false=取消收藏
     * @param folderId 收藏夹 ID，为空时使用默认收藏夹
     */
    suspend fun favoriteVideo(aid: Long, favorite: Boolean, folderId: Long? = null): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val csrf = TokenManager.csrfCache ?: ""
                if (csrf.isEmpty()) {
                    return@withContext Result.failure(Exception("请先登录"))
                }
                
                // 如果没有指定收藏夹，需要先获取默认收藏夹
                val targetFolderId = folderId ?: getDefaultFolderId()
                if (targetFolderId == null) {
                    return@withContext Result.failure(Exception("无法获取收藏夹"))
                }

                val response = if (favorite) {
                    api.dealFavorite(rid = aid, addIds = targetFolderId.toString(), delIds = "", csrf = csrf)
                } else {
                    api.dealFavorite(rid = aid, addIds = "", delIds = targetFolderId.toString(), csrf = csrf)
                }
                
                if (response.code == 0) {
                    Result.success(favorite)
                } else {
                    Result.failure(Exception(response.message.ifEmpty { "操作失败: ${response.code}" }))
                }
            } catch (e: Exception) {
                android.util.Log.e("ActionRepository", "favoriteVideo failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 获取用户默认收藏夹 ID
     */
    private suspend fun getDefaultFolderId(): Long? {
        return try {
            val mid = TokenManager.midCache ?: return null
            val response = api.getFavFolders(mid)
            response.data?.list?.firstOrNull()?.id
        } catch (e: Exception) {
            android.util.Log.e("ActionRepository", "getDefaultFolderId failed", e)
            null
        }
    }

    /**
     * 批量更新视频所属收藏夹（多选保存）
     */
    suspend fun updateFavoriteFolders(
        aid: Long,
        addFolderIds: Set<Long>,
        removeFolderIds: Set<Long>
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val csrf = TokenManager.csrfCache ?: ""
                if (csrf.isEmpty()) {
                    return@withContext Result.failure(Exception("请先登录"))
                }
                if (addFolderIds.isEmpty() && removeFolderIds.isEmpty()) {
                    return@withContext Result.success(true)
                }

                val addIds = addFolderIds.sorted().joinToString(",")
                val delIds = removeFolderIds.sorted().joinToString(",")
                val response = api.dealFavorite(rid = aid, addIds = addIds, delIds = delIds, csrf = csrf)

                if (response.code == 0) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(response.message.ifEmpty { "操作失败: ${response.code}" }))
                }
            } catch (e: Exception) {
                android.util.Log.e("ActionRepository", "updateFavoriteFolders failed", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     *  检查是否已关注 UP 主
     */
    suspend fun checkFollowStatus(mid: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getRelation(mid)
                if (response.code == 0) {
                    val isFollowing = response.data?.isFollowing ?: false
                    com.bbttvv.app.core.util.Logger.d("ActionRepository", " checkFollowStatus: mid=$mid, isFollowing=$isFollowing")
                    isFollowing
                } else {
                    false
                }
            } catch (e: Exception) {
                android.util.Log.e("ActionRepository", "checkFollowStatus failed", e)
                false
            }
        }
    }

    suspend fun getFollowGroupTags(): Result<List<com.bbttvv.app.data.model.response.RelationTagItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getRelationTags()
                if (response.code == 0) {
                    Result.success(normalizeRelationTags(response.data))
                } else {
                    Result.failure(Exception(response.message.ifEmpty { "获取关注分组失败: ${response.code}" }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getUserFollowGroupIds(mid: Long): Result<Set<Long>> {
        return withContext(Dispatchers.IO) {
            var lastCode = Int.MIN_VALUE
            var lastMessage = ""
            var lastError: Exception? = null

            repeat(FOLLOW_GROUP_QUERY_MAX_RETRIES) { attempt ->
                try {
                    val response = api.getRelationTagUser(mid)
                    if (response.code == 0) {
                        val ids = response.data.keys.mapNotNull { it.toLongOrNull() }.toSet()
                        return@withContext Result.success(normalizeRelationTagIds(ids))
                    }

                    lastCode = response.code
                    lastMessage = response.message
                    val retryable = isFollowGroupRetryableError(response.code, response.message)
                    if (!retryable || attempt >= FOLLOW_GROUP_QUERY_MAX_RETRIES - 1) {
                        return@withContext Result.failure(
                            Exception(response.message.ifEmpty { "获取分组信息失败: ${response.code}" })
                        )
                    }
                } catch (e: Exception) {
                    lastError = e
                    if (attempt >= FOLLOW_GROUP_QUERY_MAX_RETRIES - 1) {
                        return@withContext Result.failure(e)
                    }
                }

                val backoffMs = FOLLOW_GROUP_QUERY_RETRY_BASE_DELAY_MS * (attempt + 1)
                delay(backoffMs)
            }

            val fallbackMessage = if (lastMessage.isNotBlank()) {
                lastMessage
            } else {
                "获取分组信息失败: $lastCode"
            }
            Result.failure(lastError ?: Exception(fallbackMessage))
        }
    }

    suspend fun getFollowGroupMemberMids(
        tagId: Long,
        targetMids: Set<Long> = emptySet()
    ): Result<Set<Long>> {
        return withContext(Dispatchers.IO) {
            try {
                val targetSet = if (targetMids.isEmpty()) null else targetMids
                val result = linkedSetOf<Long>()
                var page = 1

                while (page <= FOLLOW_GROUP_TAG_MEMBERS_MAX_PAGES) {
                    val response = api.getRelationTagMembers(
                        tagId = tagId,
                        pageSize = FOLLOW_GROUP_TAG_MEMBERS_PAGE_SIZE,
                        page = page
                    )
                    if (response.code != 0) {
                        return@withContext Result.failure(
                            Exception(response.message.ifEmpty { "获取分组成员失败: ${response.code}" })
                        )
                    }

                    val mids = response.data
                        .asSequence()
                        .map { it.mid }
                        .filter { it > 0L }
                        .toList()

                    if (targetSet == null) {
                        result.addAll(mids)
                    } else {
                        mids.filterTo(result) { targetSet.contains(it) }
                    }

                    if (mids.size < FOLLOW_GROUP_TAG_MEMBERS_PAGE_SIZE) break
                    page += 1
                    delay(FOLLOW_GROUP_REQUEST_INTERVAL_MS)
                }

                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun overwriteFollowGroupIds(
        targetMids: Set<Long>,
        selectedTagIds: Set<Long>
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (targetMids.isEmpty()) return@withContext Result.success(true)
                val csrf = TokenManager.csrfCache ?: ""
                if (csrf.isEmpty()) {
                    return@withContext Result.failure(Exception("请先登录"))
                }
                val normalizedSelection = normalizeRelationTagIds(selectedTagIds)
                val midChunks = chunkFollowGroupTargetMids(targetMids)
                if (midChunks.isEmpty()) return@withContext Result.success(true)
                val selectedTagIdsJoined = normalizedSelection.joinToString(",")

                midChunks.forEachIndexed { index, mids ->
                    val fids = mids.joinToString(",")

                    // 先移动到默认分组，确保“完全覆盖”生效。
                    val resetResult = addUsersToRelationTagsWithRetry(
                        fids = fids,
                        tagIds = "0",
                        csrf = csrf
                    )
                    if (resetResult.isFailure) {
                        return@withContext Result.failure(
                            resetResult.exceptionOrNull() ?: Exception("分组设置失败")
                        )
                    }

                    if (normalizedSelection.isNotEmpty()) {
                        val applyResult = addUsersToRelationTagsWithRetry(
                            fids = fids,
                            tagIds = selectedTagIdsJoined,
                            csrf = csrf
                        )
                        if (applyResult.isFailure) {
                            return@withContext Result.failure(
                                applyResult.exceptionOrNull() ?: Exception("分组设置失败")
                            )
                        }
                    }

                    if (index < midChunks.lastIndex) {
                        delay(FOLLOW_GROUP_REQUEST_INTERVAL_MS)
                    }
                }

                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     *  检查视频是否已收藏
     */
    suspend fun checkFavoriteStatus(aid: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.checkFavoured(aid)
                if (response.code == 0) {
                    val isFavoured = response.data?.favoured ?: false
                    com.bbttvv.app.core.util.Logger.d("ActionRepository", " checkFavoriteStatus: aid=$aid, isFavoured=$isFavoured")
                    isFavoured
                } else {
                    false
                }
            } catch (e: Exception) {
                android.util.Log.e("ActionRepository", "checkFavoriteStatus failed", e)
                false
            }
        }
    }
    
    /**
     * 获取用户收藏夹列表
     */
    suspend fun getFavoriteFolders(aid: Long? = null): Result<List<com.bbttvv.app.data.model.response.FavFolder>> {
        return withContext(Dispatchers.IO) {
            try {
                val mid = TokenManager.midCache ?: return@withContext Result.failure(Exception("请先登录"))
                val response = api.getFavFolders(
                    mid = mid,
                    type = aid?.let { 2 },
                    rid = aid
                )
                if (response.code == 0) {
                    Result.success(response.data?.list ?: emptyList())
                } else {
                    Result.failure(Exception(response.message))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 创建收藏夹
     */
    suspend fun createFavFolder(title: String, intro: String = "", isPrivate: Boolean = false): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (title.isBlank()) return@withContext Result.failure(Exception("标题不能为空"))
                val privacy = if (isPrivate) 1 else 0
                val csrf = TokenManager.csrfCache ?: return@withContext Result.failure(Exception("未登录"))
                
                val response = api.createFavFolder(
                    title = title,
                    intro = intro,
                    privacy = privacy,
                    csrf = csrf
                )
                
                if (response.code == 0) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(response.message))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     *  点赞/取消点赞视频
     */
    suspend fun likeVideo(aid: Long, like: Boolean): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val csrf = TokenManager.csrfCache ?: ""
                if (csrf.isEmpty()) {
                    return@withContext Result.failure(Exception("请先登录"))
                }
                
                val likeAction = if (like) 1 else 2
                val response = api.likeVideo(aid = aid, like = likeAction, csrf = csrf)
                com.bbttvv.app.core.util.Logger.d("ActionRepository", " likeVideo: aid=$aid, like=$like, code=${response.code}")
                
                if (response.code == 0) {
                    Result.success(like)
                } else {
                    Result.failure(Exception(response.message.ifEmpty { "点赞失败: ${response.code}" }))
                }
            } catch (e: Exception) {
                android.util.Log.e("ActionRepository", "likeVideo failed", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     *  检查是否已点赞
     */
    suspend fun checkLikeStatus(aid: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.hasLiked(aid)
                if (response.code == 0) {
                    val isLiked = response.data == 1
                    com.bbttvv.app.core.util.Logger.d("ActionRepository", " checkLikeStatus: aid=$aid, isLiked=$isLiked")
                    isLiked
                } else {
                    false
                }
            } catch (e: Exception) {
                android.util.Log.e("ActionRepository", "checkLikeStatus failed", e)
                false
            }
        }
    }
    
    /**
     *  投币
     */
    suspend fun coinVideo(aid: Long, count: Int, alsoLike: Boolean): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val csrf = TokenManager.csrfCache ?: ""
                if (csrf.isEmpty()) {
                    return@withContext Result.failure(Exception("请先登录"))
                }
                
                val selectLike = if (alsoLike) 1 else 0
                val response = api.coinVideo(aid = aid, multiply = count, selectLike = selectLike, csrf = csrf)
                com.bbttvv.app.core.util.Logger.d("ActionRepository", " coinVideo: aid=$aid, count=$count, code=${response.code}")
                
                when (response.code) {
                    0 -> Result.success(true)
                    34004 -> Result.failure(Exception("操作太频繁，请稍后重试"))
                    34005 -> Result.failure(Exception("已投满2个硬币"))
                    -104 -> Result.failure(Exception("硬币余额不足"))
                    else -> Result.failure(Exception(response.message.ifEmpty { "投币失败: ${response.code}" }))
                }
            } catch (e: Exception) {
                android.util.Log.e("ActionRepository", "coinVideo failed", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     *  检查已投币数
     */
    suspend fun checkCoinStatus(aid: Long): Int {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.hasCoined(aid)
                if (response.code == 0) {
                    val coinCount = response.data?.multiply ?: 0
                    com.bbttvv.app.core.util.Logger.d("ActionRepository", " checkCoinStatus: aid=$aid, coinCount=$coinCount")
                    coinCount
                } else {
                    0
                }
            } catch (e: Exception) {
                android.util.Log.e("ActionRepository", "checkCoinStatus failed", e)
                0
            }
        }
    }
    
    /**
     *  一键三连 (点赞 + 投币2个 + 收藏)
     */
    data class TripleResult(
        val likeSuccess: Boolean,
        val coinSuccess: Boolean,
        val coinMessage: String?,
        val favoriteSuccess: Boolean
    )
    
    suspend fun tripleAction(aid: Long): Result<TripleResult> {
        return withContext(Dispatchers.IO) {
            val csrf = TokenManager.csrfCache ?: ""
            if (csrf.isEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            // 1. 点赞
            val likeResult = likeVideo(aid, true)
            val likeSuccess = likeResult.isSuccess
            
            // 2. 投币 (2个，同时点赞)
            val coinResult = coinVideo(aid, 2, true)
            val coinSuccess = coinResult.isSuccess
            val coinMessage = coinResult.exceptionOrNull()?.message
            
            // 3. 收藏
            val favoriteResult = favoriteVideo(aid, true)
            val favoriteSuccess = favoriteResult.isSuccess
            
            com.bbttvv.app.core.util.Logger.d("ActionRepository", " tripleAction: like=$likeSuccess, coin=$coinSuccess, fav=$favoriteSuccess")
            
            Result.success(TripleResult(
                likeSuccess = likeSuccess,
                coinSuccess = coinSuccess,
                coinMessage = coinMessage,
                favoriteSuccess = favoriteSuccess
            ))
        }
    }
    
    /**
     *  添加/移除稍后再看
     */
    suspend fun toggleWatchLater(aid: Long, add: Boolean): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val csrf = TokenManager.csrfCache ?: ""
                if (csrf.isEmpty()) {
                    return@withContext Result.failure(Exception("请先登录"))
                }
                
                val response = if (add) {
                    api.addToWatchLater(aid = aid, csrf = csrf)
                } else {
                    api.deleteFromWatchLater(aid = aid, csrf = csrf)
                }
                
                com.bbttvv.app.core.util.Logger.d("ActionRepository", " toggleWatchLater: aid=$aid, add=$add, code=${response.code}")
                
                when (response.code) {
                    0 -> Result.success(add)
                    90001 -> Result.failure(Exception("稍后再看列表已满"))
                    90003 -> Result.failure(Exception("视频已被删除"))
                    else -> Result.failure(Exception(response.message.ifEmpty { "操作失败: ${response.code}" }))
                }
            } catch (e: Exception) {
                android.util.Log.e("ActionRepository", "toggleWatchLater failed", e)
                Result.failure(e)
            }
        }
    }
}

