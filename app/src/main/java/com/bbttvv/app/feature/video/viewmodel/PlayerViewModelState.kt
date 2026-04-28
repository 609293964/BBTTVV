package com.bbttvv.app.feature.video.viewmodel

import androidx.media3.common.Player
import com.bbttvv.app.data.model.response.Page
import com.bbttvv.app.data.model.response.RelatedVideo
import com.bbttvv.app.data.model.response.ReplyItem
import com.bbttvv.app.data.model.response.ViewInfo

private const val PLAYER_COMMENT_PAGE_SIZE = 10

data class QualityOption(
    val id: Int,
    val label: String,
    val isSupported: Boolean = true,
    val unsupportedReason: String? = null
)

data class PlayerOption(
    val key: String,
    val label: String,
    val subtitle: String? = null,
    val isSelected: Boolean = false,
    val isEnabled: Boolean = true,
    val disabledReason: String? = null
)

data class PlaybackBadge(
    val label: String,
    val isActive: Boolean = true
)

data class PlayerPlaybackState(
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val playerState: Int = Player.STATE_IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
) {
    val isPlaybackActive: Boolean
        get() = playWhenReady && playerState != Player.STATE_IDLE && playerState != Player.STATE_ENDED

    val isPaused: Boolean
        get() = !isPlaybackActive && !isBuffering && playerState != Player.STATE_ENDED
}

data class ResumePlaybackPrompt(
    val targetCid: Long,
    val positionMs: Long,
    val pageLabel: String? = null,
    val isCrossPage: Boolean = false,
)

enum class PlayerCommentSortMode(val apiMode: Int, val label: String) {
    Hot(apiMode = 3, label = "热度"),
    Time(apiMode = 2, label = "时间"),
}

data class PlayerCommentsUiState(
    val sortMode: PlayerCommentSortMode = PlayerCommentSortMode.Hot,
    val items: List<ReplyItem> = emptyList(),
    val currentPage: Int = 1,
    val pageSize: Int = PLAYER_COMMENT_PAGE_SIZE,
    val totalCount: Int = 0,
    val totalPages: Int = 1,
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val activeThreadRoot: ReplyItem? = null,
    val threadItems: List<ReplyItem> = emptyList(),
    val threadCurrentPage: Int = 1,
    val threadTotalCount: Int = 0,
    val threadTotalPages: Int = 1,
    val isThreadLoading: Boolean = false,
    val isThreadAppending: Boolean = false,
    val threadHasMore: Boolean = true,
    val threadErrorMessage: String? = null,
) {
    val isViewingThread: Boolean
        get() = activeThreadRoot != null
}

data class PlayerUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val info: ViewInfo? = null,
    val relatedVideos: List<RelatedVideo> = emptyList(),
    val pages: List<Page> = emptyList(),
    val currentPageIndex: Int = 0,
    val selectedQuality: Int = 0,
    val qualityOptions: List<QualityOption> = emptyList(),
    val audioOptions: List<PlayerOption> = emptyList(),
    val videoCodecOptions: List<PlayerOption> = emptyList(),
    val selectedVideoCodecId: Int = 0,
    val selectedAudioQualityId: Int = 0,
    val selectedAudioCodecId: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val statusMessage: String? = null,
    val playbackBadges: List<PlaybackBadge> = emptyList(),
    val capabilityHints: List<String> = emptyList(),
    val selectedVideoCodecLabel: String = "",
    val selectedAudioCodecLabel: String = "",
    val activeDynamicRangeLabel: String? = null,
    val onlineCountText: String = "",
    val videoCdnHost: String = "",
    val audioCdnHost: String = "",
    val selectedVideoWidth: Int = 0,
    val selectedVideoHeight: Int = 0,
    val selectedVideoFrameRate: String = "",
    val selectedVideoBandwidth: Int = 0,
    val selectedAudioBandwidth: Int = 0,
    val selectedQualityLabel: String = "",
    val resumePrompt: ResumePlaybackPrompt? = null,
)
