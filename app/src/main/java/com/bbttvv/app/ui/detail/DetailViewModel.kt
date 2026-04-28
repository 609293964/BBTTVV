package com.bbttvv.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbttvv.app.data.model.response.RelatedVideo
import com.bbttvv.app.data.model.response.ReplyData
import com.bbttvv.app.data.model.response.ReplyItem
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.model.response.ViewInfo
import com.bbttvv.app.data.repository.ActionRepository
import com.bbttvv.app.data.repository.CommentRepository
import com.bbttvv.app.data.repository.DanmakuRepository
import com.bbttvv.app.data.repository.SubtitleAndAuxRepository
import com.bbttvv.app.data.repository.VideoDetailRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DetailCommentPageSize = 10
private const val DetailCommentsDelayMs = 900L
private const val DetailInteractionStatusDelayMs = 260L
private const val DetailRelatedPrefetchDelayMs = 240L

enum class DetailCommentSortMode(val apiMode: Int, val label: String) {
    Hot(apiMode = 3, label = "热度"),
    Time(apiMode = 2, label = "时间")
}

data class DetailCommentsState(
    val sortMode: DetailCommentSortMode = DetailCommentSortMode.Hot,
    val items: List<ReplyItem> = emptyList(),
    val currentPage: Int = 1,
    val pageSize: Int = DetailCommentPageSize,
    val totalCount: Int = 0,
    val totalPages: Int = 1,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class DetailUiState(
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val errorMsg: String? = null,
    val viewInfo: ViewInfo? = null,
    val relatedVideos: List<RelatedVideo> = emptyList(),
    val isRelatedLoading: Boolean = false,
    val creatorFollowerCount: Int? = null,
    val accountCoinBalance: Int? = null,
    val isFollowing: Boolean = false,
    val isFollowActionLoading: Boolean = false,
    val isLiked: Boolean = false,
    val isFavoured: Boolean = false,
    val isActionLoading: Boolean = false,
    val comments: DetailCommentsState = DetailCommentsState()
)

class DetailViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var currentBvid: String = ""
    private var detailLoadJob: Job? = null
    private var loadingIndicatorJob: Job? = null
    private var relatedLoadJob: Job? = null
    private var commentsJob: Job? = null
    private var supportingContentJob: Job? = null
    private var deferredCommentsJob: Job? = null
    private var interactionStatusJob: Job? = null
    private var relatedPrefetchJob: Job? = null
    private var pendingPrefetchBvid: String? = null
    private var lastPrefetchedBvid: String? = null

    fun loadDetail(
        bvid: String,
        loadCommentsEnabled: Boolean = true
    ) {
        val requestBvid = bvid.trim()
        if (requestBvid.isBlank()) {
            _uiState.update {
                DetailUiState(
                    isError = true,
                    errorMsg = "缺少视频 ID"
                )
            }
            return
        }

        currentBvid = requestBvid
        detailLoadJob?.cancel()
        loadingIndicatorJob?.cancel()
        relatedLoadJob?.cancel()
        commentsJob?.cancel()
        supportingContentJob?.cancel()
        deferredCommentsJob?.cancel()
        interactionStatusJob?.cancel()
        relatedPrefetchJob?.cancel()

        val cachedInfo = VideoDetailRepository.getCachedDetailViewInfo(requestBvid)
        val cachedRelated = VideoDetailRepository.getCachedRelatedVideos(requestBvid).orEmpty()
        val cachedCreatorStats = cachedInfo?.owner?.mid?.let(SubtitleAndAuxRepository::getCachedCreatorCardStats)
        val cachedAccountCoinBalance = SubtitleAndAuxRepository.getCachedNavInfo()
            ?.money
            ?.toInt()
            ?.coerceAtLeast(0)
        _uiState.update {
            DetailUiState(
                isLoading = false,
                viewInfo = cachedInfo,
                relatedVideos = cachedRelated,
                isRelatedLoading = cachedRelated.isEmpty(),
                creatorFollowerCount = cachedCreatorStats?.followerCount,
                accountCoinBalance = cachedAccountCoinBalance,
                comments = DetailCommentsState(
                    totalCount = cachedInfo?.stat?.reply ?: 0,
                    isLoading = loadCommentsEnabled
                )
            )
        }

        cachedInfo?.let { info ->
            scheduleSupportingContentLoads(
                requestBvid = requestBvid,
                viewInfo = info,
                loadCommentsEnabled = loadCommentsEnabled
            )
        }

        if (cachedInfo == null) {
            loadingIndicatorJob = viewModelScope.launch {
                delay(180L)
                if (currentBvid == requestBvid && _uiState.value.viewInfo == null) {
                    _uiState.update { it.copy(isLoading = true) }
                }
            }
        }

        detailLoadJob = viewModelScope.launch {
            try {
                val infoResp = VideoDetailRepository.getVideoInfo(requestBvid)
                if (currentBvid != requestBvid) return@launch
                loadingIndicatorJob?.cancel()

                if (infoResp.code == 0 && infoResp.data != null) {
                    val viewInfo = infoResp.data
                    val cachedFollowerCount = SubtitleAndAuxRepository.getCachedCreatorCardStats(viewInfo.owner.mid)
                        ?.followerCount
                    val cachedAccountBalance = SubtitleAndAuxRepository.getCachedNavInfo()
                        ?.money
                        ?.toInt()
                        ?.coerceAtLeast(0)
                    _uiState.update { state ->
                        val totalCount = maxOf(state.comments.totalCount, viewInfo.stat.reply)
                        state.copy(
                            isLoading = false,
                            isError = false,
                            errorMsg = null,
                            viewInfo = viewInfo,
                            creatorFollowerCount = cachedFollowerCount ?: state.creatorFollowerCount,
                            accountCoinBalance = cachedAccountBalance ?: state.accountCoinBalance,
                            comments = state.comments.copy(
                                totalCount = totalCount,
                                totalPages = calculateTotalPages(
                                    count = totalCount,
                                    pageSize = state.comments.pageSize
                                )
                            )
                        )
                    }

                    scheduleInteractionStatusLoad(requestBvid, viewInfo)
                    scheduleSupportingContentLoads(
                        requestBvid = requestBvid,
                        viewInfo = viewInfo,
                        sortMode = _uiState.value.comments.sortMode,
                        loadCommentsEnabled = loadCommentsEnabled
                    )
                } else {
                    applyLoadError(infoResp.message.ifBlank { "未知错误" })
                }
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                loadingIndicatorJob?.cancel()
                if (currentBvid == requestBvid) {
                    applyLoadError(e.message ?: "网络错误")
                }
            }
        }
    }

    fun prefetchDetail(video: RelatedVideo) {
        if (video.bvid.isBlank()) return
        VideoDetailRepository.cacheVideoPreview(video)
        if (video.bvid == pendingPrefetchBvid || video.bvid == lastPrefetchedBvid) return
        relatedPrefetchJob?.cancel()
        pendingPrefetchBvid = video.bvid
        relatedPrefetchJob = viewModelScope.launch {
            delay(DetailRelatedPrefetchDelayMs)
            VideoDetailRepository.prefetchDetailLanding(video, scope = viewModelScope)
            lastPrefetchedBvid = video.bvid
            if (pendingPrefetchBvid == video.bvid) {
                pendingPrefetchBvid = null
            }
        }
    }

    fun prefetchPlaybackDanmaku(cid: Long) {
        if (cid <= 0L) return
        val viewInfo = _uiState.value.viewInfo ?: return
        val durationMs = viewInfo.pages
            .firstOrNull { page -> page.cid == cid }
            ?.duration
            ?.times(1000L)
            ?.coerceAtLeast(0L)
            ?: 0L

        viewModelScope.launch {
            runCatching {
                DanmakuRepository.warmUpDanmaku(
                    cid = cid,
                    durationMs = durationMs
                )
            }
        }
    }

    fun toggleFollow() {
        val currentState = _uiState.value
        val viewInfo = currentState.viewInfo ?: return
        val ownerMid = viewInfo.owner.mid
        if (ownerMid <= 0L || currentState.isFollowActionLoading) return

        val previousFollowing = currentState.isFollowing
        _uiState.update {
            it.copy(
                isFollowActionLoading = true,
                isFollowing = !previousFollowing,
                creatorFollowerCount = it.creatorFollowerCount?.let { count ->
                    if (previousFollowing) maxOf(0, count - 1) else count + 1
                }
            )
        }

        viewModelScope.launch {
            val result = ActionRepository.followUser(ownerMid, !previousFollowing)
            if (currentBvid != viewInfo.bvid) return@launch

            if (result.isSuccess) {
                _uiState.update { it.copy(isFollowActionLoading = false) }
            } else {
                _uiState.update {
                    it.copy(
                        isFollowActionLoading = false,
                        isFollowing = previousFollowing,
                        creatorFollowerCount = currentState.creatorFollowerCount
                    )
                }
            }
        }
    }

    fun toggleLike() {
        val currentState = _uiState.value
        val viewInfo = currentState.viewInfo ?: return
        if (currentState.isActionLoading) return

        _uiState.update { it.copy(isActionLoading = true) }
        viewModelScope.launch {
            val newStatus = !currentState.isLiked
            val result = ActionRepository.likeVideo(viewInfo.aid, newStatus)
            if (currentBvid != viewInfo.bvid) return@launch

            if (result.isSuccess) {
                _uiState.update { state ->
                    val currentViewInfo = state.viewInfo
                    val updatedViewInfo = currentViewInfo?.copy(
                        stat = currentViewInfo.stat.copy(
                            like = if (newStatus) {
                                currentViewInfo.stat.like + 1
                            } else {
                                maxOf(0, currentViewInfo.stat.like - 1)
                            }
                        )
                    )
                    state.copy(
                        isLiked = newStatus,
                        isActionLoading = false,
                        viewInfo = updatedViewInfo
                    )
                }
            } else {
                _uiState.update { it.copy(isActionLoading = false) }
            }
        }
    }

    fun toggleFavourite() {
        val currentState = _uiState.value
        val viewInfo = currentState.viewInfo ?: return
        if (currentState.isActionLoading) return

        _uiState.update { it.copy(isActionLoading = true) }
        viewModelScope.launch {
            val newStatus = !currentState.isFavoured
            val result = ActionRepository.favoriteVideo(viewInfo.aid, newStatus)
            if (currentBvid != viewInfo.bvid) return@launch

            if (result.isSuccess) {
                _uiState.update { state ->
                    val currentViewInfo = state.viewInfo
                    val updatedViewInfo = currentViewInfo?.copy(
                        stat = currentViewInfo.stat.copy(
                            favorite = if (newStatus) {
                                currentViewInfo.stat.favorite + 1
                            } else {
                                maxOf(0, currentViewInfo.stat.favorite - 1)
                            }
                        )
                    )
                    state.copy(
                        isFavoured = newStatus,
                        isActionLoading = false,
                        viewInfo = updatedViewInfo
                    )
                }
            } else {
                _uiState.update { it.copy(isActionLoading = false) }
            }
        }
    }

    fun changeCommentSort(sortMode: DetailCommentSortMode) {
        val current = _uiState.value
        if (current.comments.sortMode == sortMode && current.comments.items.isNotEmpty()) return
        val aid = current.viewInfo?.aid ?: return
        loadComments(
            requestBvid = currentBvid,
            aid = aid,
            page = 1,
            sortMode = sortMode,
            fallbackTotalCount = current.viewInfo.stat.reply
        )
    }

    fun goToCommentPage(page: Int) {
        val current = _uiState.value
        val aid = current.viewInfo?.aid ?: return
        val safePage = page.coerceIn(1, maxOf(current.comments.totalPages, 1))
        if (safePage == current.comments.currentPage && current.comments.items.isNotEmpty()) return
        loadComments(
            requestBvid = currentBvid,
            aid = aid,
            page = safePage,
            sortMode = current.comments.sortMode,
            fallbackTotalCount = current.viewInfo.stat.reply
        )
    }

    private fun scheduleSupportingContentLoads(
        requestBvid: String,
        viewInfo: ViewInfo,
        sortMode: DetailCommentSortMode = DetailCommentSortMode.Hot,
        loadCommentsEnabled: Boolean = true
    ) {
        supportingContentJob?.cancel()
        deferredCommentsJob?.cancel()
        supportingContentJob = viewModelScope.launch {
            if (currentBvid != requestBvid) return@launch
            loadRelatedVideos(requestBvid)
            loadAuxiliaryState(requestBvid, viewInfo)
        }
        if (!loadCommentsEnabled) return
        deferredCommentsJob = viewModelScope.launch {
            delay(DetailCommentsDelayMs)
            if (currentBvid != requestBvid) return@launch
            loadComments(
                requestBvid = requestBvid,
                aid = viewInfo.aid,
                page = 1,
                sortMode = sortMode,
                fallbackTotalCount = viewInfo.stat.reply
            )
        }
    }

    private fun scheduleInteractionStatusLoad(
        requestBvid: String,
        viewInfo: ViewInfo
    ) {
        interactionStatusJob?.cancel()
        if (viewInfo.aid <= 0L) return

        interactionStatusJob = viewModelScope.launch {
            delay(DetailInteractionStatusDelayMs)
            if (currentBvid != requestBvid) return@launch

            val likeTask = async {
                runCatching { ActionRepository.checkLikeStatus(viewInfo.aid) }
                    .getOrDefault(false)
            }
            val favouriteTask = async {
                runCatching { ActionRepository.checkFavoriteStatus(viewInfo.aid) }
                    .getOrDefault(false)
            }
            val liked = likeTask.await()
            val favoured = favouriteTask.await()
            if (currentBvid == requestBvid) {
                _uiState.update {
                    it.copy(
                        isLiked = liked,
                        isFavoured = favoured
                    )
                }
            }
        }
    }

    private fun loadAuxiliaryState(requestBvid: String, viewInfo: ViewInfo) {
        val ownerMid = viewInfo.owner.mid

        viewModelScope.launch {
            val statsTask = async {
                if (ownerMid > 0L) {
                    SubtitleAndAuxRepository.getCreatorCardStats(ownerMid).getOrNull()
                } else {
                    null
                }
            }
            val followTask = async {
                if (ownerMid > 0L) {
                    runCatching { ActionRepository.checkFollowStatus(ownerMid) }.getOrDefault(false)
                } else {
                    false
                }
            }
            val navTask = async { SubtitleAndAuxRepository.getNavInfo().getOrNull() }
            val creatorStats = statsTask.await()
            val isFollowing = followTask.await()
            val accountCoinBalance = navTask.await()?.money?.toInt()?.coerceAtLeast(0)

            if (currentBvid == requestBvid) {
                _uiState.update { state ->
                    state.copy(
                        creatorFollowerCount = creatorStats?.followerCount ?: state.creatorFollowerCount,
                        accountCoinBalance = accountCoinBalance ?: state.accountCoinBalance,
                        isFollowing = isFollowing
                    )
                }
            }
        }
    }

    private fun loadRelatedVideos(requestBvid: String) {
        val cachedRelated = VideoDetailRepository.getCachedRelatedVideos(requestBvid).orEmpty()
        if (cachedRelated.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    relatedVideos = cachedRelated,
                    isRelatedLoading = false
                )
            }
            return
        }

        _uiState.update { it.copy(isRelatedLoading = true) }
        relatedLoadJob?.cancel()
        relatedLoadJob = viewModelScope.launch {
            if (currentBvid != requestBvid) return@launch
            val relatedVideos = VideoDetailRepository.getRelatedVideos(requestBvid)
            if (currentBvid == requestBvid) {
                _uiState.update {
                    it.copy(
                        relatedVideos = relatedVideos,
                        isRelatedLoading = false
                    )
                }
            }
        }
    }

    private fun loadComments(
        requestBvid: String,
        aid: Long,
        page: Int,
        sortMode: DetailCommentSortMode,
        fallbackTotalCount: Int
    ) {
        if (aid <= 0L) {
            _uiState.update { state ->
                state.copy(
                    comments = state.comments.copy(
                        isLoading = false,
                        totalCount = maxOf(state.comments.totalCount, fallbackTotalCount)
                    )
                )
            }
            return
        }
        deferredCommentsJob?.cancel()
        commentsJob?.cancel()

        val previousComments = _uiState.value.comments
        _uiState.update { state ->
            val currentComments = state.comments
            val totalCount = maxOf(currentComments.totalCount, fallbackTotalCount)
            state.copy(
                comments = currentComments.copy(
                    sortMode = sortMode,
                    currentPage = page,
                    totalCount = totalCount,
                    totalPages = calculateTotalPages(
                        count = totalCount,
                        pageSize = currentComments.pageSize
                    ),
                    isLoading = true,
                    errorMessage = null
                )
            )
        }

        commentsJob = viewModelScope.launch {
            val result = CommentRepository.getComments(
                aid = aid,
                page = page,
                ps = previousComments.pageSize,
                mode = sortMode.apiMode
            )

            if (currentBvid != requestBvid) return@launch

            result.onSuccess { data ->
                val totalCount = data.getAllCount().takeIf { it > 0 }
                _uiState.update { state ->
                    val resolvedTotalCount = totalCount
                        ?: maxOf(fallbackTotalCount, state.viewInfo?.stat?.reply ?: 0)
                    state.copy(
                        comments = state.comments.copy(
                            sortMode = sortMode,
                            currentPage = page,
                            items = resolveDisplayComments(data, previousComments.pageSize),
                            totalCount = resolvedTotalCount,
                            totalPages = calculateTotalPages(resolvedTotalCount, previousComments.pageSize),
                            isLoading = false,
                            errorMessage = null
                        )
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        comments = state.comments.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "评论加载失败"
                        )
                    )
                }
            }
        }
    }

    private fun applyLoadError(message: String) {
        _uiState.update { currentState ->
            if (currentState.viewInfo != null) {
                currentState.copy(
                    isLoading = false,
                    errorMsg = message
                )
            } else {
                DetailUiState(
                    isLoading = false,
                    isError = true,
                    errorMsg = message
                )
            }
        }
    }

    private fun resolveDisplayComments(data: ReplyData, pageSize: Int): List<ReplyItem> {
        val replies = data.replies.orEmpty()
        if (replies.isNotEmpty()) return replies.take(pageSize)

        val hotReplies = data.hots.orEmpty()
        if (hotReplies.isNotEmpty()) return hotReplies.take(pageSize)

        return data.collectTopReplies().take(pageSize)
    }

    private fun calculateTotalPages(count: Int, pageSize: Int): Int {
        if (count <= 0 || pageSize <= 0) return 1
        return ((count - 1) / pageSize) + 1
    }
}

private fun RelatedVideo.toVideoItem(): VideoItem {
    return VideoItem(
        id = aid,
        bvid = bvid,
        aid = aid,
        cid = cid,
        title = title,
        pic = pic,
        owner = owner,
        stat = stat,
        duration = duration
    )
}
