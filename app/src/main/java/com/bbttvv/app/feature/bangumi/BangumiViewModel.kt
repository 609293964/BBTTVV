package com.bbttvv.app.feature.bangumi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbttvv.app.data.model.response.BangumiDetail
import com.bbttvv.app.data.model.response.BangumiEpisode
import com.bbttvv.app.data.repository.BangumiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BangumiDetailUiState(
    val seasonId: Long = 0L,
    val epId: Long = 0L,
    val detail: BangumiDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isFollowed: Boolean = false,
    val selectedSeasonId: Long = 0L,
    val episodes: List<BangumiEpisode> = emptyList()
)

class BangumiViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BangumiDetailUiState())
    val uiState: StateFlow<BangumiDetailUiState> = _uiState.asStateFlow()

    private var currentSeasonId: Long = 0L
    private var currentEpId: Long = 0L

    fun load(seasonId: Long, epId: Long) {
        if (seasonId <= 0L && epId <= 0L) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "番剧 ID 错误"
                )
            }
            return
        }

        // 避免重复加载
        if (currentSeasonId == seasonId && currentEpId == epId && _uiState.value.detail != null) {
            return
        }

        currentSeasonId = seasonId
        currentEpId = epId

        _uiState.update {
            it.copy(
                seasonId = seasonId,
                epId = epId,
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            BangumiRepository.getSeasonDetail(seasonId = seasonId, epId = epId)
                .onSuccess { detail ->
                    if (currentSeasonId != seasonId || currentEpId != epId) return@onSuccess
                    _uiState.update {
                        it.copy(
                            detail = detail,
                            isLoading = false,
                            error = null,
                            isFollowed = detail.userStatus?.follow == 1,
                            selectedSeasonId = detail.seasonId,
                            episodes = detail.episodes.orEmpty()
                        )
                    }
                }
                .onFailure { exception ->
                    if (currentSeasonId != seasonId || currentEpId != epId) return@onFailure
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = exception.message ?: "加载番剧详情失败"
                        )
                    }
                }
        }
    }

    /**
     * 切换关联季度
     */
    fun switchSeason(seasonId: Long) {
        if (seasonId <= 0L || _uiState.value.selectedSeasonId == seasonId) return

        _uiState.update {
            it.copy(
                selectedSeasonId = seasonId,
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            BangumiRepository.getSeasonDetail(seasonId = seasonId, epId = 0L)
                .onSuccess { detail ->
                    if (_uiState.value.selectedSeasonId != seasonId) return@onSuccess
                    _uiState.update {
                        it.copy(
                            detail = detail,
                            isLoading = false,
                            error = null,
                            isFollowed = detail.userStatus?.follow == 1,
                            episodes = detail.episodes.orEmpty()
                        )
                    }
                }
                .onFailure { exception ->
                    if (_uiState.value.selectedSeasonId != seasonId) return@onFailure
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = exception.message ?: "加载季度数据失败"
                        )
                    }
                }
        }
    }

    /**
     * 追番/取消追番 (乐观更新)
     */
    fun toggleFollow() {
        val state = _uiState.value
        val detail = state.detail ?: return
        val previousFollowState = state.isFollowed
        val nextFollowState = !previousFollowState

        // 乐观更新 UI
        _uiState.update {
            it.copy(isFollowed = nextFollowState)
        }

        viewModelScope.launch {
            val result = if (nextFollowState) {
                BangumiRepository.followBangumi(detail.seasonId)
            } else {
                BangumiRepository.unfollowBangumi(detail.seasonId)
            }

            result.onFailure {
                // 如果失败了，则回滚状态
                _uiState.update {
                    it.copy(isFollowed = previousFollowState)
                }
            }
        }
    }
}
