package com.bbttvv.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbttvv.app.data.model.response.ReplyItem
import com.bbttvv.app.data.repository.CommentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DetailReplyPageSize = 10

data class CommentRepliesUiState(
    val rootComment: ReplyItem? = null,
    val items: List<ReplyItem> = emptyList(),
    val currentPage: Int = 1,
    val pageSize: Int = DetailReplyPageSize,
    val totalCount: Int = 0,
    val totalPages: Int = 1,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class CommentRepliesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CommentRepliesUiState())
    val uiState: StateFlow<CommentRepliesUiState> = _uiState.asStateFlow()

    private var currentAid: Long = 0L
    private var currentRootRpid: Long = 0L
    private var loadJob: Job? = null

    fun loadThread(
        aid: Long,
        rootRpid: Long,
        rootReply: ReplyItem?
    ) {
        if (aid <= 0L || rootRpid <= 0L) {
            _uiState.update {
                CommentRepliesUiState(
                    rootComment = rootReply,
                    errorMessage = "加载回复失败"
                )
            }
            return
        }

        val isNewThread = currentAid != aid || currentRootRpid != rootRpid
        currentAid = aid
        currentRootRpid = rootRpid

        val fallbackRoot = rootReply ?: ReplyItem(rpid = rootRpid, oid = aid)
        if (isNewThread) {
            _uiState.update { CommentRepliesUiState(rootComment = fallbackRoot) }
            loadReplies(page = 1)
        } else if (_uiState.value.rootComment == null && rootReply != null) {
            _uiState.update { it.copy(rootComment = rootReply) }
        }
    }

    fun goToPage(page: Int) {
        val totalPages = _uiState.value.totalPages.coerceAtLeast(1)
        val safePage = page.coerceIn(1, totalPages)
        if (safePage == _uiState.value.currentPage && _uiState.value.items.isNotEmpty()) {
            return
        }
        loadReplies(page = safePage)
    }

    private fun loadReplies(page: Int) {
        val aid = currentAid
        val rootRpid = currentRootRpid
        if (aid <= 0L || rootRpid <= 0L) return

        loadJob?.cancel()
        val previous = _uiState.value
        _uiState.update {
            it.copy(
                currentPage = page,
                isLoading = true,
                errorMessage = null
            )
        }

        loadJob = viewModelScope.launch {
            val result = CommentRepository.getSubComments(
                aid = aid,
                rootId = rootRpid,
                page = page,
                ps = previous.pageSize
            )

            if (aid != currentAid || rootRpid != currentRootRpid) {
                return@launch
            }

            result.onSuccess { data ->
                val replies = data.replies.orEmpty().take(previous.pageSize)
                _uiState.update { state ->
                    val totalCount = data.getAllCount().takeIf { it > 0 }
                        ?: state.rootComment?.rcount
                        ?: replies.size
                    state.copy(
                        items = replies,
                        currentPage = page,
                        totalCount = totalCount,
                        totalPages = calculateTotalPages(totalCount, previous.pageSize),
                        isLoading = false,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "加载回复失败"
                    )
                }
            }
        }
    }

    private fun calculateTotalPages(count: Int, pageSize: Int): Int {
        if (count <= 0 || pageSize <= 0) return 1
        return ((count - 1) / pageSize) + 1
    }
}
