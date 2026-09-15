package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.data.model.response.ReplyData
import com.bbttvv.app.data.model.response.ReplyItem
import com.bbttvv.app.data.repository.CommentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class PlayerCommentController(
    private val scope: CoroutineScope,
    private val currentAid: () -> Long,
    private val fallbackCommentCount: () -> Int,
    private val isVideoLoading: () -> Boolean,
    private val commentsLoader: suspend (Long, Int, Int, Int) -> Result<ReplyData> =
        { aid, page, pageSize, mode -> CommentRepository.getComments(aid, page, pageSize, mode) },
    private val repliesLoader: suspend (Long, Long, Int, Int) -> Result<ReplyData> =
        { aid, rootId, page, pageSize -> CommentRepository.getSubComments(aid, rootId, page, pageSize) },
) {
    private val _uiState = MutableStateFlow(PlayerCommentsUiState())
    val uiState: StateFlow<PlayerCommentsUiState> = _uiState.asStateFlow()

    private var commentsJob: Job? = null
    private var commentRepliesJob: Job? = null
    private val sortCache = mutableMapOf<PlayerCommentSortMode, PlayerCommentsUiState>()
    private var commentsGeneration = 0L

    fun ensureLoaded() {
        val current = _uiState.value
        if (current.items.isNotEmpty() || current.isLoading || current.isAppending) return
        loadComments(
            page = 1,
            sortMode = current.sortMode,
            append = false,
        )
    }

    fun refresh() {
        val current = _uiState.value
        if (current.isViewingThread) {
            loadCommentReplies(page = 1, append = false)
        } else {
            sortCache.remove(current.sortMode)
            loadComments(
                page = 1,
                sortMode = current.sortMode,
                append = false,
                preserveExistingItems = true,
            )
        }
    }

    fun changeSort(sortMode: PlayerCommentSortMode) {
        val current = _uiState.value
        if (current.sortMode == sortMode && current.items.isNotEmpty()) return
        cacheCurrentSort(current)
        commentsJob?.cancel()
        commentsGeneration += 1
        sortCache[sortMode]?.let { cached ->
            commentRepliesJob?.cancel()
            _uiState.value = cached.withoutActiveThread().copy(sortMode = sortMode)
            return
        }
        loadComments(
            page = 1,
            sortMode = sortMode,
            append = false,
        )
    }

    fun toggleSort() {
        val nextSortMode = when (_uiState.value.sortMode) {
            PlayerCommentSortMode.Hot -> PlayerCommentSortMode.Time
            PlayerCommentSortMode.Time -> PlayerCommentSortMode.Hot
        }
        changeSort(nextSortMode)
    }

    fun loadMore() {
        val current = _uiState.value
        if (current.isViewingThread) {
            loadMoreReplies()
            return
        }
        if (current.isLoading || current.isAppending || !current.hasMore) return
        loadComments(
            page = current.currentPage + 1,
            sortMode = current.sortMode,
            append = true,
        )
    }

    fun openThread(rootReply: ReplyItem) {
        if (rootReply.rpid <= 0L) return
        commentRepliesJob?.cancel()
        val pageSize = _uiState.value.pageSize
        val fallbackTotalCount = maxOf(rootReply.rcount, rootReply.replies.orEmpty().size)
        _uiState.update {
            it.copy(
                activeThreadRoot = rootReply,
                threadItems = emptyList(),
                threadCurrentPage = 1,
                threadTotalCount = fallbackTotalCount,
                threadTotalPages = calculateCommentTotalPages(fallbackTotalCount, pageSize),
                isThreadLoading = false,
                isThreadAppending = false,
                threadHasMore = fallbackTotalCount > 0,
                threadErrorMessage = null,
            )
        }
        loadCommentReplies(page = 1, append = false)
    }

    fun closeThread() {
        commentRepliesJob?.cancel()
        _uiState.update {
            it.copy(
                activeThreadRoot = null,
                threadItems = emptyList(),
                threadCurrentPage = 1,
                threadTotalCount = 0,
                threadTotalPages = 1,
                isThreadLoading = false,
                isThreadAppending = false,
                threadHasMore = true,
                threadErrorMessage = null,
            )
        }
    }

    fun loadMoreReplies() {
        val current = _uiState.value
        if (current.activeThreadRoot == null || current.isThreadLoading || current.isThreadAppending || !current.threadHasMore) {
            return
        }
        loadCommentReplies(
            page = current.threadCurrentPage + 1,
            append = true,
        )
    }

    fun reset() {
        commentsJob?.cancel()
        commentRepliesJob?.cancel()
        commentsJob = null
        commentRepliesJob = null
        commentsGeneration += 1
        sortCache.clear()
        _uiState.value = PlayerCommentsUiState()
    }

    private fun loadComments(
        page: Int,
        sortMode: PlayerCommentSortMode,
        append: Boolean,
        preserveExistingItems: Boolean = false,
    ) {
        val aid = currentAid()
        val current = _uiState.value
        val fallbackTotalCount = fallbackCommentCount()
        if (aid <= 0L) {
            _uiState.update {
                it.copy(
                    sortMode = sortMode,
                    isLoading = false,
                    isAppending = false,
                    hasMore = false,
                    errorMessage = if (isVideoLoading()) {
                        "视频信息加载中，请稍后再试"
                    } else {
                        "当前视频暂无评论数据"
                    },
                )
            }
            return
        }

        if (append) {
            if (current.isLoading || current.isAppending || !current.hasMore) return
            _uiState.update {
                it.copy(
                    isAppending = true,
                    errorMessage = null,
                )
            }
        } else {
            commentsJob?.cancel()
            commentRepliesJob?.cancel()
            val initialTotalCount = maxOf(current.totalCount, fallbackTotalCount)
            _uiState.update {
                it.copy(
                    sortMode = sortMode,
                    currentPage = 1,
                    items = if (preserveExistingItems && it.sortMode == sortMode) it.items else emptyList(),
                    totalCount = initialTotalCount,
                    totalPages = calculateCommentTotalPages(initialTotalCount, current.pageSize),
                    isLoading = true,
                    isAppending = false,
                    hasMore = true,
                    errorMessage = null,
                    activeThreadRoot = null,
                    threadItems = emptyList(),
                    threadCurrentPage = 1,
                    threadTotalCount = 0,
                    threadTotalPages = 1,
                    isThreadLoading = false,
                    isThreadAppending = false,
                    threadHasMore = true,
                    threadErrorMessage = null,
                )
            }
        }

        commentsJob?.cancel()
        val generation = ++commentsGeneration
        commentsJob = scope.launch {
            val result = commentsLoader(aid, page, current.pageSize, sortMode.apiMode)
            if (
                currentAid() != aid ||
                generation != commentsGeneration ||
                _uiState.value.sortMode != sortMode
            ) return@launch

            result.onSuccess { data ->
                val pageItems = resolveDisplayComments(data, current.pageSize)
                val totalCount = data.getAllCount().takeIf { it > 0 }
                    ?: maxOf(fallbackTotalCount, fallbackCommentCount())
                val totalPages = calculateCommentTotalPages(totalCount, current.pageSize)
                val updatedCurrentPage = if (append && pageItems.isEmpty()) current.currentPage else page
                _uiState.update { state ->
                    val mergedItems = if (append) {
                        (state.items + pageItems).distinctBy { it.rpid }
                    } else {
                        pageItems
                    }
                    state.copy(
                        sortMode = sortMode,
                        currentPage = updatedCurrentPage,
                        items = mergedItems,
                        totalCount = totalCount,
                        totalPages = totalPages,
                        isLoading = false,
                        isAppending = false,
                        hasMore = updatedCurrentPage < totalPages && pageItems.isNotEmpty(),
                        errorMessage = null,
                    )
                }
                cacheCurrentSort(_uiState.value)
            }.onFailure { error ->
                _uiState.update { state ->
                    if (append && state.items.isNotEmpty()) {
                        state.copy(isAppending = false)
                    } else {
                        state.copy(
                            isLoading = false,
                            isAppending = false,
                            errorMessage = error.message ?: "评论加载失败",
                        )
                    }
                }
                cacheCurrentSort(_uiState.value)
            }
        }
    }

    private fun cacheCurrentSort(state: PlayerCommentsUiState) {
        if (state.isViewingThread) return
        if (state.items.isEmpty() && state.errorMessage == null) return
        sortCache[state.sortMode] = state.withoutActiveThread().copy(
            isLoading = false,
            isAppending = false,
        )
    }

    private fun PlayerCommentsUiState.withoutActiveThread(): PlayerCommentsUiState = copy(
        activeThreadRoot = null,
        threadItems = emptyList(),
        threadCurrentPage = 1,
        threadTotalCount = 0,
        threadTotalPages = 1,
        isThreadLoading = false,
        isThreadAppending = false,
        threadHasMore = true,
        threadErrorMessage = null,
    )

    private fun loadCommentReplies(
        page: Int,
        append: Boolean,
    ) {
        val current = _uiState.value
        val rootReply = current.activeThreadRoot ?: return
        val aid = currentAid()
        if (aid <= 0L || rootReply.rpid <= 0L) return

        if (append) {
            if (current.isThreadLoading || current.isThreadAppending || !current.threadHasMore) return
            _uiState.update {
                it.copy(
                    isThreadAppending = true,
                    threadErrorMessage = null,
                )
            }
        } else {
            commentRepliesJob?.cancel()
            val initialTotalCount = maxOf(current.threadTotalCount, rootReply.rcount)
            _uiState.update {
                it.copy(
                    threadItems = emptyList(),
                    threadCurrentPage = 1,
                    threadTotalCount = initialTotalCount,
                    threadTotalPages = calculateCommentTotalPages(initialTotalCount, current.pageSize),
                    isThreadLoading = true,
                    isThreadAppending = false,
                    threadHasMore = true,
                    threadErrorMessage = null,
                )
            }
        }

        commentRepliesJob?.cancel()
        commentRepliesJob = scope.launch {
            val result = repliesLoader(aid, rootReply.rpid, page, current.pageSize)
            val latestRoot = _uiState.value.activeThreadRoot
            if (currentAid() != aid || latestRoot?.rpid != rootReply.rpid) return@launch

            result.onSuccess { data ->
                val replies = data.replies.orEmpty().take(current.pageSize)
                val updatedCurrentPage = if (append && replies.isEmpty()) current.threadCurrentPage else page
                _uiState.update { state ->
                    val mergedReplies = if (append) {
                        (state.threadItems + replies).distinctBy { it.rpid }
                    } else {
                        replies
                    }
                    val totalCount = data.getSubReplyCount().takeIf { it > 0 }
                        ?: maxOf(rootReply.rcount, mergedReplies.size)
                    val totalPages = calculateCommentTotalPages(totalCount, current.pageSize)
                    state.copy(
                        threadItems = mergedReplies,
                        threadCurrentPage = updatedCurrentPage,
                        threadTotalCount = totalCount,
                        threadTotalPages = totalPages,
                        isThreadLoading = false,
                        isThreadAppending = false,
                        threadHasMore = updatedCurrentPage < totalPages && replies.isNotEmpty(),
                        threadErrorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    if (append && state.threadItems.isNotEmpty()) {
                        state.copy(isThreadAppending = false)
                    } else {
                        state.copy(
                            isThreadLoading = false,
                            isThreadAppending = false,
                            threadErrorMessage = error.message ?: "回复加载失败",
                        )
                    }
                }
            }
        }
    }

    private fun resolveDisplayComments(
        data: ReplyData,
        pageSize: Int,
    ): List<ReplyItem> {
        val replies = data.replies.orEmpty()
        if (replies.isNotEmpty()) return replies.take(pageSize)

        val hotReplies = data.hots.orEmpty()
        if (hotReplies.isNotEmpty()) return hotReplies.take(pageSize)

        return data.collectTopReplies().take(pageSize)
    }

    private fun calculateCommentTotalPages(
        count: Int,
        pageSize: Int,
    ): Int {
        if (count <= 0 || pageSize <= 0) return 1
        return ((count - 1) / pageSize) + 1
    }
}
