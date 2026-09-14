package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.store.TokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class TripleActionRepositoryResult(
    val likeSuccess: Boolean,
    val coinSuccess: Boolean,
    val coinMessage: String?,
    val favoriteSuccess: Boolean,
    val coinsAdded: Int
)

interface UserActionRepository {
    suspend fun followUser(mid: Long, follow: Boolean): Result<Boolean>
    suspend fun favoriteVideo(aid: Long, favorite: Boolean): Result<Boolean>
    suspend fun likeVideo(aid: Long, like: Boolean): Result<Boolean>
    suspend fun coinVideo(aid: Long, count: Int, alsoLike: Boolean): Result<Boolean>
    suspend fun tripleAction(aid: Long, currentCoinCount: Int): Result<TripleActionRepositoryResult>
    suspend fun checkFollowStatus(mid: Long): Boolean
    suspend fun checkFavoriteStatus(aid: Long): Boolean
    suspend fun checkLikeStatus(aid: Long): Boolean
    suspend fun checkCoinStatus(aid: Long): Int
}

object ActionRepository : UserActionRepository {
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

    private fun normalizeRelationTagIds(raw: Set<Long>): Set<Long> =
        raw.asSequence().filter { it != 0L }.toSet()

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
        return targetMids
            .asSequence()
            .filter { it > 0L }
            .distinct()
            .toList()
            .chunked(chunkSize.coerceAtLeast(1))
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
            val response = api.addUsersToRelationTags(fids = fids, tagIds = tagIds, csrf = csrf)
            if (response.code == 0) return Result.success(Unit)
            lastCode = response.code
            lastMessage = response.message
            if (!isFollowGroupRetryableError(response.code, response.message) ||
                attempt >= FOLLOW_GROUP_MAX_RETRIES - 1
            ) {
                return Result.failure(Exception(response.message.ifEmpty { "分组设置失败: ${response.code}" }))
            }
            delay(FOLLOW_GROUP_RETRY_BASE_DELAY_MS * (attempt + 1))
        }
        return Result.failure(Exception(lastMessage.ifEmpty { "分组设置失败: $lastCode" }))
    }

    override suspend fun followUser(mid: Long, follow: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache.orEmpty()
            if (csrf.isEmpty()) return@withContext Result.failure(Exception("请先登录"))
            val response = api.modifyRelation(fid = mid, act = if (follow) 1 else 2, csrf = csrf)
            if (response.code == 0) Result.success(follow)
            else Result.failure(Exception(response.message.ifEmpty { "操作失败: ${response.code}" }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ActionRepository", "followUser failed", e)
            Result.failure(e)
        }
    }

    override suspend fun favoriteVideo(aid: Long, favorite: Boolean): Result<Boolean> =
        favoriteVideo(aid = aid, favorite = favorite, folderId = null)

    suspend fun favoriteVideo(aid: Long, favorite: Boolean, folderId: Long?): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache.orEmpty()
            if (csrf.isEmpty()) return@withContext Result.failure(Exception("请先登录"))
            val targetFolderId = folderId ?: getDefaultFolderId()
                ?: return@withContext Result.failure(Exception("无法获取收藏夹"))
            val response = if (favorite) {
                api.dealFavorite(rid = aid, addIds = targetFolderId.toString(), delIds = "", csrf = csrf)
            } else {
                api.dealFavorite(rid = aid, addIds = "", delIds = targetFolderId.toString(), csrf = csrf)
            }
            if (response.code == 0) Result.success(favorite)
            else Result.failure(Exception(response.message.ifEmpty { "操作失败: ${response.code}" }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ActionRepository", "favoriteVideo failed", e)
            Result.failure(e)
        }
    }

    private suspend fun getDefaultFolderId(): Long? {
        return try {
            val mid = TokenManager.midCache ?: return null
            api.getFavFolders(mid).data?.list?.firstOrNull()?.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ActionRepository", "getDefaultFolderId failed", e)
            null
        }
    }

    suspend fun updateFavoriteFolders(
        aid: Long,
        addFolderIds: Set<Long>,
        removeFolderIds: Set<Long>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache.orEmpty()
            if (csrf.isEmpty()) return@withContext Result.failure(Exception("请先登录"))
            if (addFolderIds.isEmpty() && removeFolderIds.isEmpty()) return@withContext Result.success(true)
            val response = api.dealFavorite(
                rid = aid,
                addIds = addFolderIds.sorted().joinToString(","),
                delIds = removeFolderIds.sorted().joinToString(","),
                csrf = csrf
            )
            if (response.code == 0) Result.success(true)
            else Result.failure(Exception(response.message.ifEmpty { "操作失败: ${response.code}" }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkFollowStatus(mid: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = api.getRelation(mid)
            response.code == 0 && (response.data?.isFollowing ?: false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getFollowGroupTags(): Result<List<com.bbttvv.app.data.model.response.RelationTagItem>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getRelationTags()
                if (response.code == 0) Result.success(normalizeRelationTags(response.data))
                else Result.failure(Exception(response.message.ifEmpty { "获取关注分组失败: ${response.code}" }))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getUserFollowGroupIds(mid: Long): Result<Set<Long>> = withContext(Dispatchers.IO) {
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
                if (!isFollowGroupRetryableError(response.code, response.message) ||
                    attempt >= FOLLOW_GROUP_QUERY_MAX_RETRIES - 1
                ) {
                    return@withContext Result.failure(
                        Exception(response.message.ifEmpty { "获取分组信息失败: ${response.code}" })
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt >= FOLLOW_GROUP_QUERY_MAX_RETRIES - 1) {
                    return@withContext Result.failure(e)
                }
            }
            delay(FOLLOW_GROUP_QUERY_RETRY_BASE_DELAY_MS * (attempt + 1))
        }
        Result.failure(lastError ?: Exception(lastMessage.ifBlank { "获取分组信息失败: $lastCode" }))
    }

    suspend fun getFollowGroupMemberMids(
        tagId: Long,
        targetMids: Set<Long> = emptySet()
    ): Result<Set<Long>> = withContext(Dispatchers.IO) {
        try {
            val targetSet = targetMids.takeIf { it.isNotEmpty() }
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
                val mids = response.data.asSequence().map { it.mid }.filter { it > 0L }.toList()
                if (targetSet == null) result.addAll(mids) else mids.filterTo(result) { targetSet.contains(it) }
                if (mids.size < FOLLOW_GROUP_TAG_MEMBERS_PAGE_SIZE) break
                page += 1
                delay(FOLLOW_GROUP_REQUEST_INTERVAL_MS)
            }
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun overwriteFollowGroupIds(
        targetMids: Set<Long>,
        selectedTagIds: Set<Long>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (targetMids.isEmpty()) return@withContext Result.success(true)
            val csrf = TokenManager.csrfCache.orEmpty()
            if (csrf.isEmpty()) return@withContext Result.failure(Exception("请先登录"))
            val normalizedSelection = normalizeRelationTagIds(selectedTagIds)
            val midChunks = chunkFollowGroupTargetMids(targetMids)
            if (midChunks.isEmpty()) return@withContext Result.success(true)
            val selectedTagIdsJoined = normalizedSelection.joinToString(",")
            midChunks.forEachIndexed { index, mids ->
                val fids = mids.joinToString(",")
                val resetResult = addUsersToRelationTagsWithRetry(fids = fids, tagIds = "0", csrf = csrf)
                if (resetResult.isFailure) {
                    return@withContext Result.failure(resetResult.exceptionOrNull() ?: Exception("分组设置失败"))
                }
                if (normalizedSelection.isNotEmpty()) {
                    val applyResult = addUsersToRelationTagsWithRetry(
                        fids = fids,
                        tagIds = selectedTagIdsJoined,
                        csrf = csrf
                    )
                    if (applyResult.isFailure) {
                        return@withContext Result.failure(applyResult.exceptionOrNull() ?: Exception("分组设置失败"))
                    }
                }
                if (index < midChunks.lastIndex) delay(FOLLOW_GROUP_REQUEST_INTERVAL_MS)
            }
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkFavoriteStatus(aid: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = api.checkFavoured(aid)
            response.code == 0 && (response.data?.favoured ?: false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getFavoriteFolders(aid: Long? = null): Result<List<com.bbttvv.app.data.model.response.FavFolder>> =
        withContext(Dispatchers.IO) {
            try {
                val mid = TokenManager.midCache ?: return@withContext Result.failure(Exception("请先登录"))
                val response = api.getFavFolders(mid = mid, type = aid?.let { 2 }, rid = aid)
                if (response.code == 0) Result.success(response.data?.list ?: emptyList())
                else Result.failure(Exception(response.message))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun createFavFolder(title: String, intro: String = "", isPrivate: Boolean = false): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                if (title.isBlank()) return@withContext Result.failure(Exception("标题不能为空"))
                val csrf = TokenManager.csrfCache ?: return@withContext Result.failure(Exception("未登录"))
                val response = api.createFavFolder(
                    title = title,
                    intro = intro,
                    privacy = if (isPrivate) 1 else 0,
                    csrf = csrf
                )
                if (response.code == 0) Result.success(true) else Result.failure(Exception(response.message))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun likeVideo(aid: Long, like: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache.orEmpty()
            if (csrf.isEmpty()) return@withContext Result.failure(Exception("请先登录"))
            val response = api.likeVideo(aid = aid, like = if (like) 1 else 2, csrf = csrf)
            if (response.code == 0) Result.success(like)
            else Result.failure(Exception(response.message.ifEmpty { "点赞失败: ${response.code}" }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkLikeStatus(aid: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = api.hasLiked(aid)
            response.code == 0 && response.data == 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun coinVideo(aid: Long, count: Int, alsoLike: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache.orEmpty()
            if (csrf.isEmpty()) return@withContext Result.failure(Exception("请先登录"))
            if (count !in 1..2) return@withContext Result.failure(Exception("投币数量无效"))
            val response = api.coinVideo(
                aid = aid,
                multiply = count,
                selectLike = if (alsoLike) 1 else 0,
                csrf = csrf
            )
            when (response.code) {
                0 -> Result.success(true)
                34004 -> Result.failure(Exception("操作太频繁，请稍后重试"))
                34005 -> Result.failure(Exception("已投满2个硬币"))
                -104 -> Result.failure(Exception("硬币余额不足"))
                else -> Result.failure(Exception(response.message.ifEmpty { "投币失败: ${response.code}" }))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkCoinStatus(aid: Long): Int = withContext(Dispatchers.IO) {
        try {
            val response = api.hasCoined(aid)
            if (response.code == 0) response.data?.multiply ?: 0 else 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            0
        }
    }

    suspend fun tripleAction(aid: Long): Result<TripleActionRepositoryResult> =
        tripleAction(aid = aid, currentCoinCount = 0)

    override suspend fun tripleAction(
        aid: Long,
        currentCoinCount: Int
    ): Result<TripleActionRepositoryResult> = withContext(Dispatchers.IO) {
        val csrf = TokenManager.csrfCache.orEmpty()
        if (csrf.isEmpty()) return@withContext Result.failure(Exception("请先登录"))
        val likeResult = likeVideo(aid, true)
        val coinRequestCount = (2 - currentCoinCount.coerceIn(0, 2)).coerceIn(0, 2)
        val coinResult = if (coinRequestCount > 0) coinVideo(aid, coinRequestCount, true) else null
        val favoriteResult = favoriteVideo(aid, true)
        Result.success(
            TripleActionRepositoryResult(
                likeSuccess = likeResult.isSuccess,
                coinSuccess = coinResult?.isSuccess == true,
                coinMessage = coinResult?.exceptionOrNull()?.message,
                favoriteSuccess = favoriteResult.isSuccess,
                coinsAdded = if (coinResult?.isSuccess == true) coinRequestCount else 0
            )
        )
    }

    suspend fun toggleWatchLater(aid: Long, add: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache.orEmpty()
            if (csrf.isEmpty()) return@withContext Result.failure(Exception("请先登录"))
            val response = if (add) {
                api.addToWatchLater(aid = aid, csrf = csrf)
            } else {
                api.deleteFromWatchLater(aid = aid, csrf = csrf)
            }
            when (response.code) {
                0 -> Result.success(add)
                90001 -> Result.failure(Exception("稍后再看列表已满"))
                90003 -> Result.failure(Exception("视频已被删除"))
                else -> Result.failure(Exception(response.message.ifEmpty { "操作失败: ${response.code}" }))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
