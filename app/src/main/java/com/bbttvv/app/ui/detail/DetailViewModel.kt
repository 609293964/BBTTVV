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
import kotlinx.coroutines.coroutineScope
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
private const val DetailSupportingContentDelayMs = 180L

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
    val previewInfo: ViewInfo? = null,
    val viewInfo: ViewInfo? = null,
    val relatedVideos: List<RelatedVideo> = emptyList(),
    val isRelatedLoading: Boolean = false,
    val creatorFollowerCount: Int? = null,
    val accountCoinBalance: Int? = null,
    val isFollowing: Boolean = false,
    val isFollowActionLoading: Boolean = false,
    val isLiked: Boolean = false,
    val coinCount: Int = 0,
    val isFavoured: Boolean = false,
    val isActionLoading: Boolean = false,
    val isTripleActionLoading: Boolean = false,
    val coinDialogVisible: Boolean = false,
    val coinActionLoading: Boolean = false,
    val showTripleCelebration: Boolean = false,
    val actionFeedbackMessage: String? = null,
    val comments: DetailCommentsState = DetailCommentsState()
)

class DetailViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var currentBvid: String = ""
    private var detailLoadJob: Job? = null
    private var loadingIndicatorJob: Job? = null
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
        commentsJob?.cancel()
        supportingContentJob?.cancel()
        deferredCommentsJob?.cancel()
        interactionStatusJob?.cancel()
        relatedPrefetchJob?.cancel()

        val cachedFullInfo = VideoDetailRepository.getCachedFullDetailViewInfo(requestBvid)
        val cachedPreviewInfo = cachedFullInfo ?: VideoDetailRepository.getCachedDetailPreviewViewInfo(requestBvid)
        val cachedRelated = if (cachedFullInfo != null) {
            VideoDetailRepository.getCachedRelatedVideos(requestBvid).orEmpty()
        } else {
            emptyList()
        }
        val cachedCreatorStats = cachedFullInfo?.owner?.mid?.let(SubtitleAndAuxRepository::getCachedCreatorCardStats)
        val cachedAccountCoinBalance = SubtitleAndAuxRepository.getCachedNavInfo()
            ?.money
            ?.toInt()
            ?.coerceAtLeast(0)
        _uiState.update {
            DetailUiState(
                isLoading = false,
                previewInfo = cachedPreviewInfo,
                viewInfo = cachedFullInfo,
                relatedVideos = cachedRelated,
                isRelatedLoading = cachedFullInfo != null && cachedRelated.isEmpty(),
                creatorFollowerCount = cachedCreatorStats?.followerCount,
                accountCoinBalance = cachedAccountCoinBalance,
                comments = DetailCommentsState(
                    totalCount = cachedFullInfo?.stat?.reply ?: cachedPreviewInfo?.stat?.reply ?: 0,
                    isLoading = cachedFullInfo != null && loadCommentsEnabled
                )
            )
        }

        if (cachedPreviewInfo == null) {
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
                            previewInfo = state.previewInfo ?: viewInfo,
                            viewInfo = viewInfo,
                            isRelatedLoading = state.relatedVideos.isEmpty(),
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
            VideoDetailRepository.prefetchDetailSummary(video, scope = viewModelScope)
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

    fun openCoinDialog() {
        val currentState = _uiState.value
        if (currentState.viewInfo == null || currentState.isActionLoading || currentState.coinActionLoading) return
        if (currentState.coinCount >= DetailTripleTargetCoinCount) {
            _uiState.update {
                it.copy(actionFeedbackMessage = "已投满硬币")
            }
            return
        }
        _uiState.update {
            it.copy(
                coinDialogVisible = true,
                actionFeedbackMessage = null,
            )
        }
    }

    fun closeCoinDialog() {
        _uiState.update {
            if (it.coinActionLoading) {
                it
            } else {
                it.copy(coinDialogVisible = false)
            }
        }
    }

    fun performCoinAction(count: Int, alsoLike: Boolean) {
        val currentState = _uiState.value
        val viewInfo = currentState.viewInfo ?: return
        if (viewInfo.aid <= 0L || currentState.isActionLoading || currentState.coinActionLoading) return

        val requestedCoinCount = resolveDetailCoinActionRequestCount(
            currentCoinCount = currentState.coinCount,
            requestedCount = count,
        )
        if (requestedCoinCount <= 0) {
            _uiState.update {
                it.copy(
                    coinDialogVisible = false,
                    actionFeedbackMessage = "已投满硬币",
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                coinDialogVisible = false,
                coinActionLoading = true,
                isActionLoading = true,
                actionFeedbackMessage = "正在投币",
            )
        }

        viewModelScope.launch {
            val result = ActionRepository.coinVideo(viewInfo.aid, requestedCoinCount, alsoLike)
            if (currentBvid != viewInfo.bvid) return@launch

            result.fold(
                onSuccess = {
                    _uiState.update { state ->
                        val previousCoinCount = state.coinCount
                        val coinsAdded = resolveDetailCoinStatIncrement(
                            currentCoinCount = previousCoinCount,
                            coinsAdded = requestedCoinCount,
                        )
                        val nextCoinCount = resolveDetailCoinCountAfterCoinAction(
                            currentCoinCount = previousCoinCount,
                            coinsAdded = coinsAdded,
                        )
                        val likedByCoin = alsoLike && !state.isLiked
                        val updatedViewInfo = state.viewInfo?.withCoinActionStats(
                            likeDelta = if (likedByCoin) 1 else 0,
                            coinDelta = coinsAdded,
                        )
                        state.copy(
                            viewInfo = updatedViewInfo,
                            isLiked = state.isLiked || alsoLike,
                            coinCount = nextCoinCount,
                            accountCoinBalance = state.accountCoinBalance?.let { balance ->
                                (balance - coinsAdded).coerceAtLeast(0)
                            },
                            isActionLoading = false,
                            coinActionLoading = false,
                            actionFeedbackMessage = if (likedByCoin) {
                                "投币成功，已点赞"
                            } else {
                                "投币成功"
                            },
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        val nextCoinCount = resolveDetailCoinCountAfterCoinAction(
                            currentCoinCount = state.coinCount,
                            coinsAdded = 0,
                            coinFailureMessage = error.message,
                        )
                        state.copy(
                            coinCount = nextCoinCount,
                            isActionLoading = false,
                            coinActionLoading = false,
                            actionFeedbackMessage = error.message ?: "投币失败",
                        )
                    }
                },
            )
        }
    }

    fun performTripleAction() {
        val currentState = _uiState.value
        val viewInfo = currentState.viewInfo ?: return
        if (viewInfo.aid <= 0L || currentState.isActionLoading || currentState.coinActionLoading) return

        val currentVisualState = DetailTripleActionVisualState(
            isLiked = currentState.isLiked,
            coinCount = currentState.coinCount,
            isFavoured = currentState.isFavoured,
        )
        if (currentVisualState.isSatisfied) {
            _uiState.update {
                it.copy(
                    showTripleCelebration = true,
                    actionFeedbackMessage = "已完成三连",
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isActionLoading = true,
                isTripleActionLoading = true,
                coinDialogVisible = false,
                actionFeedbackMessage = "正在三连",
            )
        }

        viewModelScope.launch {
            val result = ActionRepository.tripleAction(
                aid = viewInfo.aid,
                currentCoinCount = currentState.coinCount,
            )
            if (currentBvid != viewInfo.bvid) return@launch

            result.fold(
                onSuccess = { tripleResult ->
                    _uiState.update { state ->
                        val coinStatIncrement = resolveDetailCoinStatIncrement(
                            currentCoinCount = state.coinCount,
                            coinsAdded = tripleResult.coinsAdded,
                        )
                        val visualState = resolveDetailTripleActionVisualState(
                            currentLiked = state.isLiked,
                            currentCoinCount = state.coinCount,
                            currentFavoured = state.isFavoured,
                            likeSuccess = tripleResult.likeSuccess,
                            coinSuccess = tripleResult.coinSuccess,
                            coinFailureMessage = tripleResult.coinMessage,
                            favouriteSuccess = tripleResult.favoriteSuccess,
                        )
                        state.copy(
                            viewInfo = state.viewInfo?.withTripleActionStats(
                                previousState = state,
                                visualState = visualState,
                                coinsAdded = coinStatIncrement,
                            ),
                            isLiked = visualState.isLiked,
                            coinCount = visualState.coinCount,
                            accountCoinBalance = state.accountCoinBalance?.let { balance ->
                                (balance - coinStatIncrement).coerceAtLeast(0)
                            },
                            isFavoured = visualState.isFavoured,
                            isActionLoading = false,
                            isTripleActionLoading = false,
                            showTripleCelebration = visualState.isSatisfied,
                            actionFeedbackMessage = resolveDetailTripleActionFeedbackMessage(
                                visualState = visualState,
                                likeSuccess = tripleResult.likeSuccess,
                                coinSuccess = tripleResult.coinSuccess,
                                coinFailureMessage = tripleResult.coinMessage,
                                favouriteSuccess = tripleResult.favoriteSuccess,
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isActionLoading = false,
                            isTripleActionLoading = false,
                            actionFeedbackMessage = error.message ?: "三连失败",
                        )
                    }
                },
            )
        }
    }

    fun dismissTripleCelebration() {
        _uiState.update { it.copy(showTripleCelebration = false) }
    }

    fun clearActionFeedback(message: String? = null) {
        _uiState.update { state ->
            if (message != null && state.actionFeedbackMessage != message) {
                state
            } else {
                state.copy(actionFeedbackMessage = null)
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
            delay(DetailSupportingContentDelayMs)
            if (currentBvid != requestBvid) return@launch
            loadRelatedVideos(requestBvid)
            if (currentBvid != requestBvid) return@launch
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
            val coinTask = async {
                runCatching { ActionRepository.checkCoinStatus(viewInfo.aid) }
                    .getOrDefault(0)
            }
            val liked = likeTask.await()
            val favoured = favouriteTask.await()
            val coinCount = coinTask.await().coerceIn(0, DetailTripleTargetCoinCount)
            if (currentBvid == requestBvid) {
                _uiState.update {
                    it.copy(
                        isLiked = liked,
                        coinCount = if (it.isTripleActionLoading || it.coinActionLoading) {
                            it.coinCount
                        } else {
                            coinCount
                        },
                        isFavoured = favoured
                    )
                }
            }
        }
    }

    private suspend fun loadAuxiliaryState(requestBvid: String, viewInfo: ViewInfo) = coroutineScope {
        val ownerMid = viewInfo.owner.mid

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

    private suspend fun loadRelatedVideos(requestBvid: String) {
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
        if (currentBvid != requestBvid) return
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

private fun ViewInfo.withTripleActionStats(
    previousState: DetailUiState,
    visualState: DetailTripleActionVisualState,
    coinsAdded: Int,
): ViewInfo {
    val likeDelta = if (!previousState.isLiked && visualState.isLiked) 1 else 0
    val coinDelta = resolveDetailCoinStatIncrement(
        currentCoinCount = previousState.coinCount,
        coinsAdded = coinsAdded,
    )
    val favouriteDelta = if (!previousState.isFavoured && visualState.isFavoured) 1 else 0
    return copy(
        stat = stat.copy(
            like = (stat.like + likeDelta).coerceAtLeast(0),
            coin = (stat.coin + coinDelta).coerceAtLeast(0),
            favorite = (stat.favorite + favouriteDelta).coerceAtLeast(0),
        ),
    )
}

private fun ViewInfo.withCoinActionStats(
    likeDelta: Int,
    coinDelta: Int,
): ViewInfo {
    return copy(
        stat = stat.copy(
            like = (stat.like + likeDelta).coerceAtLeast(0),
            coin = (stat.coin + coinDelta).coerceAtLeast(0),
        ),
    )
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
