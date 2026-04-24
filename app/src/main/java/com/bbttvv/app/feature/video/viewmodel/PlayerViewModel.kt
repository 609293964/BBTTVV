package com.bbttvv.app.feature.video.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.os.SystemClock
import com.bbttvv.app.core.cache.PlayUrlCache
import com.bbttvv.app.core.coroutines.AppScope
import com.bbttvv.app.core.history.PlaybackHistorySyncBus
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.player.BasePlayerViewModel
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.store.PlaybackResumeCandidate
import com.bbttvv.app.core.store.PlaybackResumeStore
import com.bbttvv.app.core.store.PlayerSettingsCache
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.store.TodayWatchProfileStore
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.store.resolveStoredResumeCandidate
import com.bbttvv.app.core.store.player.PlayerSettingsStore
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.core.util.MediaUtils
import com.bbttvv.app.core.util.NetworkUtils
import com.bbttvv.app.core.util.resolvePlaybackDefaultQualityId
import com.bbttvv.app.data.model.AudioQuality
import com.bbttvv.app.data.model.response.DashAudio
import com.bbttvv.app.data.model.response.DashVideo
import com.bbttvv.app.data.model.response.Page
import com.bbttvv.app.data.model.response.RelatedVideo
import com.bbttvv.app.data.model.response.ReplyData
import com.bbttvv.app.data.model.response.ReplyItem
import com.bbttvv.app.data.model.response.ViewInfo
import com.bbttvv.app.data.repository.CommentRepository
import com.bbttvv.app.data.repository.PlaybackRepository
import com.bbttvv.app.data.repository.SubtitleAndAuxRepository
import com.bbttvv.app.feature.plugin.findSponsorBlockPluginInfo
import com.bbttvv.app.feature.video.usecase.PlaybackLoadResult
import com.bbttvv.app.feature.video.usecase.ResumePlaybackCandidate as RemoteResumePlaybackCandidate
import com.bbttvv.app.feature.video.usecase.PlaybackSource
import com.bbttvv.app.feature.video.usecase.VideoPlaybackUseCase
import com.bbttvv.app.feature.video.videoshot.VideoShot
import com.bbttvv.app.feature.video.videoshot.VideoShotFrame
import java.net.URI
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val DANMAKU_RETRY_INTERVAL_MS = 2_500L
private const val HISTORY_HEARTBEAT_INITIAL_DELAY_MS = 3_000L
private const val HISTORY_HEARTBEAT_INTERVAL_MS = 15_000L
private const val PLAYER_RESUME_MIN_POSITION_MS = 5_000L
private const val PLAYER_RESUME_END_GUARD_MS = 10_000L
private const val PLAYER_COMMENT_PAGE_SIZE = 10

private data class PlaybackHeartbeatSnapshot(
    val playedTimeSec: Long,
    val realPlayedTimeSec: Long,
    val durationMs: Long,
)

private data class PlaybackHeartbeatReport(
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val startTsSec: Long,
    val snapshot: PlaybackHeartbeatSnapshot,
    val creatorMid: Long,
    val creatorName: String,
    val deltaWatchSec: Long
)

private data class PlaybackRuntimeState(
    val bvid: String = "",
    val aid: Long = 0L,
    val cid: Long = 0L,
    val source: PlaybackSource? = null,
)

private data class HeartbeatRuntimeState(
    val aid: Long = 0L,
    val bvid: String = "",
    val cid: Long = 0L,
    val startTsSec: Long = 0L,
    val creatorMid: Long = 0L,
    val creatorName: String = "",
    val accumulatedPlayMs: Long = 0L,
    val activePlayStartElapsedMs: Long? = null,
    val lastReportedSnapshot: PlaybackHeartbeatSnapshot? = null,
) {
    val hasSession: Boolean
        get() = bvid.isNotBlank() && cid > 0L
}

class PlayerViewModel : BasePlayerViewModel() {
    private val playbackUseCase = VideoPlaybackUseCase()

    private val _uiState = MutableStateFlow(
        PlayerUiState(playbackSpeed = PlayerSettingsCache.getPreferredPlaybackSpeed())
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private val _playbackState = MutableStateFlow(PlayerPlaybackState())
    val playbackState: StateFlow<PlayerPlaybackState> = _playbackState.asStateFlow()
    private val _commentsUiState = MutableStateFlow(PlayerCommentsUiState())
    val commentsUiState: StateFlow<PlayerCommentsUiState> = _commentsUiState.asStateFlow()

    private var playbackRuntime = PlaybackRuntimeState()
    private var playerPollingJob: Job? = null
    private var settingsObservationJob: Job? = null
    private var videoShotLoadJob: Job? = null
    private var videoShotFrameJob: Job? = null
    private var commentsJob: Job? = null
    private var commentRepliesJob: Job? = null
    private var videoShot: VideoShot? = null
    private var sponsorBlockEnabled: Boolean = true
    private var lastDanmakuCid: Long = 0L
    private var lastDanmakuDurationMs: Long = 0L
    private var lastDanmakuRequestAtMs: Long = 0L
    private var heartbeatJob: Job? = null
    private var heartbeatRuntime = HeartbeatRuntimeState()

    private val _seekPreviewFrame = MutableStateFlow<VideoShotFrame?>(null)
    val seekPreviewFrame: StateFlow<VideoShotFrame?> = _seekPreviewFrame.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            ensureMainThread("Player.Listener.onIsPlayingChanged")
            syncHeartbeatPlaybackTracking(isActivelyPlaying = isPlaying)
            refreshPlayerSnapshot()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            ensureMainThread("Player.Listener.onPlaybackStateChanged")
            refreshPlayerSnapshot()
        }
    }

    @MainThread
    override fun attachPlayer(player: ExoPlayer) {
        ensureMainThread("PlayerViewModel.attachPlayer")
        if (exoPlayer === player) return
        exoPlayer?.removeListener(playerListener)
        super.attachPlayer(player)
        player.addListener(playerListener)
        player.playbackParameters = PlaybackParameters(_uiState.value.playbackSpeed)
        startPlaybackSettingsObservation()
        startPlayerPolling()
        refreshPlayerSnapshot()
    }

    @MainThread
    fun loadVideo(
        bvid: String,
        aid: Long = 0L,
        cid: Long = 0L,
        startPositionMs: Long = 0L,
        force: Boolean = false,
        resumeFromPrompt: Boolean = false,
    ) {
        ensureMainThread("loadVideo")
        if (!force && playbackRuntime.bvid == bvid && playbackRuntime.cid == cid && _uiState.value.info != null) {
            return
        }

        if (heartbeatRuntime.hasSession) {
            flushPlaybackHistory(reason = "switch_video", closeSession = true)
        }

        commentsJob?.cancel()
        commentRepliesJob?.cancel()
        _commentsUiState.update { PlayerCommentsUiState() }

        playbackRuntime = PlaybackRuntimeState(
            bvid = bvid,
            aid = aid,
            cid = cid
        )
        lastDanmakuCid = 0L
        lastDanmakuDurationMs = 0L
        lastDanmakuRequestAtMs = 0L
        clearDanmaku()
        clearVideoShot()
        updatePlaybackState(PlayerPlaybackState())

        viewModelScope.launch {
            val appContext = NetworkModule.appContext
            val hdrSupported = MediaUtils.isHdrSupported(appContext)
            val dolbyVisionSupported = MediaUtils.isDolbyVisionSupported(appContext)
            val hevcSupported = MediaUtils.isHevcSupported()
            val av1Supported = MediaUtils.isAv1Supported()
            val preferredQuality = resolveInitialPreferredQuality()
            val cdnPreference = resolvePlayerCdnPreference()

            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = null,
                    resumePrompt = null,
                )
            }

            when (
                val result = playbackUseCase.loadVideo(
                    bvid = bvid,
                    aid = aid,
                    cid = cid,
                    preferredQuality = preferredQuality,
                    cdnPreference = cdnPreference,
                    isHevcSupported = hevcSupported,
                    isAv1Supported = av1Supported,
                    isHdrSupported = hdrSupported,
                    isDolbyVisionSupported = dolbyVisionSupported
                )
            ) {
                is PlaybackLoadResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }

                is PlaybackLoadResult.Success -> {
                    playbackRuntime = playbackRuntime.copy(
                        aid = result.info.aid,
                        cid = result.info.cid
                    )
                    val localResumeCandidate = resolveLocalResumeCandidate(result)
                    if (
                        shouldAutoSwitchToResumeCandidate(
                            explicitStartPositionMs = startPositionMs,
                            localResumeCandidate = localResumeCandidate,
                            remoteResumeCandidate = result.resumeCandidate,
                            resumeFromPrompt = resumeFromPrompt,
                        )
                    ) {
                        loadVideo(
                            bvid = result.info.bvid,
                            aid = result.info.aid,
                            cid = localResumeCandidate!!.cid,
                            startPositionMs = localResumeCandidate.positionMs,
                            force = true,
                            resumeFromPrompt = true,
                        )
                        return@launch
                    }
                    val resumePrompt = resolveResumePlaybackPrompt(
                        explicitStartPositionMs = startPositionMs,
                        result = result,
                        resumeFromPrompt = resumeFromPrompt,
                        localResumeCandidate = localResumeCandidate,
                    )
                    val initialSeekPositionMs = resolveInitialPlaybackStartPosition(
                        explicitStartPositionMs = startPositionMs,
                        sourceResumePositionMs = result.source.resumePositionMs,
                        resumeFromPrompt = resumeFromPrompt,
                        localResumePositionMs = localResumeCandidate
                            ?.takeIf { !it.isCrossPage && it.cid == result.info.cid }
                            ?.positionMs
                            ?: 0L,
                    )
                    applyPlaybackSource(
                        source = result.source,
                        seekToMs = initialSeekPositionMs,
                        playWhenReady = resumePrompt == null,
                    )
                    updatePlaybackDuration(result.source.durationMs)
                    beginHeartbeatSession(
                        aid = result.info.aid,
                        bvid = result.info.bvid,
                        cid = result.info.cid,
                        creatorMid = result.info.owner.mid,
                        creatorName = result.info.owner.name
                    )
                    if (isDanmakuEnabled.value) {
                        requestDanmakuLoad(
                            cid = result.info.cid,
                            aid = result.info.aid,
                            startPositionMs = initialSeekPositionMs,
                            force = true
                        )
                    }
                    loadSponsorSegments(result.info.bvid)
                    loadVideoShot(result.info.bvid, result.info.cid)
                    refreshPlayerSnapshot()

                    _uiState.update { state ->
                        state.withPlaybackSource(
                            source = result.source,
                            isLoading = false,
                            statusMessage = null
                        ).copy(
                            errorMessage = null,
                            info = result.info,
                            relatedVideos = result.relatedVideos,
                            pages = result.pages,
                            currentPageIndex = result.currentPageIndex,
                            playbackSpeed = state.playbackSpeed,
                            onlineCountText = "",
                            resumePrompt = resumePrompt,
                        )
                    }
                    loadOnlineCount(result.info.bvid, result.info.cid)
                }
            }
        }
    }

    @MainThread
    fun togglePlayback() {
        ensureMainThread("togglePlayback")
        val engine = playerEngine ?: return
        if (engine.isPlaying) engine.pause() else engine.play()
        refreshPlayerSnapshot()
    }

    @MainThread
    fun seekBy(deltaMs: Long) {
        ensureMainThread("seekBy")
        val target = (getPlayerCurrentPosition() + deltaMs).coerceIn(0L, getPlayerDuration())
        seekTo(target)
        refreshPlayerSnapshot()
    }

    @MainThread
    fun pauseForSeekScrub(): Boolean {
        ensureMainThread("pauseForSeekScrub")
        val engine = playerEngine ?: return false
        val shouldResume = engine.playWhenReady
        engine.pause()
        refreshPlayerSnapshot()
        return shouldResume
    }

    @MainThread
    fun finishSeekScrub(targetPositionMs: Long, resumePlayback: Boolean) {
        ensureMainThread("finishSeekScrub")
        val engine = playerEngine ?: return
        val duration = getPlayerDuration()
        val target = if (duration > 0L) {
            targetPositionMs.coerceIn(0L, duration)
        } else {
            targetPositionMs.coerceAtLeast(0L)
        }
        engine.seekTo(target)
        engine.playWhenReady = resumePlayback
        refreshPlayerSnapshot()
    }

    @MainThread
    fun requestSeekPreview(positionMs: Long) {
        ensureMainThread("requestSeekPreview")
        val shot = videoShot ?: return
        videoShotFrameJob?.cancel()
        videoShotFrameJob = viewModelScope.launch {
            delay(80L)
            _seekPreviewFrame.value = runCatching {
                shot.getFrame(positionMs)
            }.getOrNull()
        }
    }

    @MainThread
    fun clearSeekPreview() {
        ensureMainThread("clearSeekPreview")
        videoShotFrameJob?.cancel()
        videoShotFrameJob = null
        _seekPreviewFrame.value = null
    }

    @MainThread
    fun switchPage(page: Page) {
        ensureMainThread("switchPage")
        val info = _uiState.value.info ?: return
        loadVideo(
            bvid = info.bvid,
            aid = info.aid,
            cid = page.cid,
            force = true
        )
    }

    @MainThread
    fun changeQuality(qualityId: Int) {
        ensureMainThread("changeQuality")
        val info = _uiState.value.info ?: return
        _uiState.value.qualityOptions.firstOrNull { it.id == qualityId && !it.isSupported }?.let { option ->
            _uiState.update {
                it.copy(
                    statusMessage = option.unsupportedReason ?: "当前设备暂不支持该画质"
                )
            }
            return
        }

        viewModelScope.launch {
            val appContext = NetworkModule.appContext
            val hdrSupported = MediaUtils.isHdrSupported(appContext)
            val dolbyVisionSupported = MediaUtils.isDolbyVisionSupported(appContext)
            val hevcSupported = MediaUtils.isHevcSupported()
            val av1Supported = MediaUtils.isAv1Supported()
            val previousSource = playbackRuntime.source
            val cdnPreference = resolvePlayerCdnPreference()

            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = "正在切换画质..."
                )
            }

            val loadedSource = playbackUseCase.changeQuality(
                bvid = info.bvid,
                cid = info.cid,
                qualityId = qualityId,
                cdnPreference = cdnPreference,
                isHevcSupported = hevcSupported,
                isAv1Supported = av1Supported,
                isHdrSupported = hdrSupported,
                isDolbyVisionSupported = dolbyVisionSupported
            )
            if (loadedSource == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "无法切换到该画质。"
                    )
                }
                return@launch
            }

            val nextSource = playbackUseCase.selectDashTracks(
                source = loadedSource,
                qualityId = loadedSource.actualQuality,
                videoCodecId = previousSource?.selectedVideoCodecId ?: loadedSource.selectedVideoCodecId,
                audioQualityId = previousSource?.selectedAudioQualityId ?: loadedSource.selectedAudioQualityId,
                cdnPreference = cdnPreference,
                isHevcSupported = hevcSupported,
                isAv1Supported = av1Supported
            ) ?: loadedSource

            switchPlaybackSource(
                source = nextSource,
                statusMessage = "画质：${nextSource.qualityOptions.firstOrNull { it.id == nextSource.actualQuality }?.label ?: nextSource.actualQuality}"
            )
        }
    }

    @MainThread
    fun changeAudioQuality(audioQualityId: Int) {
        ensureMainThread("changeAudioQuality")
        val source = playbackRuntime.source
        if (source == null || source.dashAudios.isEmpty() || source.segmentUrls.isNotEmpty()) {
            _uiState.update { it.copy(statusMessage = "当前格式不支持音频音质切换") }
            return
        }
        val targetAudio = source.dashAudios.firstOrNull { it.id == audioQualityId && it.getValidUrl().isNotBlank() }
        if (targetAudio == null) {
            _uiState.update { it.copy(statusMessage = "未找到可用音频轨道") }
            return
        }
        val nextSource = playbackUseCase.selectDashTracks(
            source = source,
            audioQualityId = audioQualityId,
            cdnPreference = resolvePlayerCdnPreference(),
            isHevcSupported = MediaUtils.isHevcSupported(),
            isAv1Supported = MediaUtils.isAv1Supported()
        ) ?: run {
            _uiState.update { it.copy(statusMessage = "音频音质切换失败") }
            return
        }
        switchPlaybackSource(
            source = nextSource,
            statusMessage = "音频音质：${resolveAudioLabel(targetAudio)}"
        )
    }

    @MainThread
    fun changeVideoCodec(videoCodecId: Int) {
        ensureMainThread("changeVideoCodec")
        val source = playbackRuntime.source
        if (source == null || source.dashVideos.isEmpty() || source.segmentUrls.isNotEmpty()) {
            _uiState.update { it.copy(statusMessage = "当前格式不支持编码切换") }
            return
        }
        val option = _uiState.value.videoCodecOptions.firstOrNull { it.key == videoCodecId.toString() }
        if (option != null && !option.isEnabled) {
            _uiState.update { it.copy(statusMessage = option.disabledReason ?: "当前设备暂不支持该编码") }
            return
        }
        val nextSource = playbackUseCase.selectDashTracks(
            source = source,
            videoCodecId = videoCodecId,
            cdnPreference = resolvePlayerCdnPreference(),
            isHevcSupported = MediaUtils.isHevcSupported(),
            isAv1Supported = MediaUtils.isAv1Supported()
        ) ?: run {
            _uiState.update { it.copy(statusMessage = "编码切换失败") }
            return
        }
        switchPlaybackSource(
            source = nextSource,
            statusMessage = "格式编码：${nextSource.selectedVideoCodec.ifBlank { videoCodecId.toString() }}"
        )
    }

    @MainThread
    fun setPlaybackSpeed(speed: Float) {
        ensureMainThread("setPlaybackSpeed")
        val appContext = NetworkModule.appContext
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
        PlayerSettingsCache.updatePreferredPlaybackSpeed(speed)
        if (appContext != null) {
            viewModelScope.launch {
                PlayerSettingsStore.setLastPlaybackSpeed(appContext, speed)
            }
        }
        _uiState.update {
            it.copy(
                playbackSpeed = speed,
                statusMessage = "播放速度：${formatSpeed(speed)}"
            )
        }
    }

    @MainThread
    fun clearStatusMessage() {
        ensureMainThread("clearStatusMessage")
        _uiState.update { it.copy(statusMessage = null) }
    }

    @MainThread
    fun toggleDanmaku() {
        ensureMainThread("toggleDanmaku")
        toggleDanmakuEnabled()
        if (isDanmakuEnabled.value && danmakuPayload.value == null) {
            val info = _uiState.value.info
            if (info != null) {
                requestDanmakuLoad(
                    cid = info.cid,
                    aid = info.aid,
                    startPositionMs = getPlayerCurrentPosition(),
                    force = true
                )
            }
        }
        _uiState.update {
            it.copy(
                statusMessage = if (isDanmakuEnabled.value) "弹幕已开启" else "弹幕已关闭"
            )
        }
    }

    @MainThread
    fun skipSponsor() {
        ensureMainThread("skipSponsor")
        skipCurrentSponsorSegment()
        _uiState.update { it.copy(statusMessage = "已跳过片段") }
    }

    @MainThread
    fun dismissSponsorNotice() {
        ensureMainThread("dismissSponsorNotice")
        dismissSponsorSkipButton()
    }

    @MainThread
    fun confirmResumePlayback() {
        ensureMainThread("confirmResumePlayback")
        val prompt = _uiState.value.resumePrompt ?: return
        _uiState.update { it.copy(resumePrompt = null) }
        if (prompt.targetCid == playbackRuntime.cid) {
            val engine = playerEngine ?: return
            val duration = engine.duration.takeIf { it > 0L } ?: _playbackState.value.durationMs
            val targetPositionMs = if (duration > 0L) {
                prompt.positionMs.coerceIn(0L, duration)
            } else {
                prompt.positionMs.coerceAtLeast(0L)
            }
            engine.seekTo(targetPositionMs)
            engine.playWhenReady = true
            refreshPlayerSnapshot()
            return
        }
        loadVideo(
            bvid = playbackRuntime.bvid,
            aid = playbackRuntime.aid,
            cid = prompt.targetCid,
            startPositionMs = prompt.positionMs,
            force = true,
            resumeFromPrompt = true,
        )
    }

    @MainThread
    fun dismissResumePlaybackPrompt() {
        ensureMainThread("dismissResumePlaybackPrompt")
        if (_uiState.value.resumePrompt == null) return
        _uiState.update { it.copy(resumePrompt = null) }
        val engine = playerEngine ?: return
        engine.seekTo(0L)
        engine.playWhenReady = true
        refreshPlayerSnapshot()
    }

    @MainThread
    fun ensureCommentsLoaded() {
        ensureMainThread("ensureCommentsLoaded")
        val current = _commentsUiState.value
        if (current.items.isNotEmpty() || current.isLoading || current.isAppending) return
        loadComments(
            page = 1,
            sortMode = current.sortMode,
            append = false,
        )
    }

    @MainThread
    fun refreshComments() {
        ensureMainThread("refreshComments")
        val current = _commentsUiState.value
        if (current.isViewingThread) {
            loadCommentReplies(page = 1, append = false)
        } else {
            loadComments(
                page = 1,
                sortMode = current.sortMode,
                append = false,
            )
        }
    }

    @MainThread
    fun changeCommentSort(sortMode: PlayerCommentSortMode) {
        ensureMainThread("changeCommentSort")
        val current = _commentsUiState.value
        if (current.sortMode == sortMode && current.items.isNotEmpty()) return
        loadComments(
            page = 1,
            sortMode = sortMode,
            append = false,
        )
    }

    @MainThread
    fun toggleCommentSort() {
        ensureMainThread("toggleCommentSort")
        val nextSortMode = when (_commentsUiState.value.sortMode) {
            PlayerCommentSortMode.Hot -> PlayerCommentSortMode.Time
            PlayerCommentSortMode.Time -> PlayerCommentSortMode.Hot
        }
        changeCommentSort(nextSortMode)
    }

    @MainThread
    fun loadMoreComments() {
        ensureMainThread("loadMoreComments")
        val current = _commentsUiState.value
        if (current.isViewingThread) {
            loadMoreCommentReplies()
            return
        }
        if (current.isLoading || current.isAppending || !current.hasMore) return
        loadComments(
            page = current.currentPage + 1,
            sortMode = current.sortMode,
            append = true,
        )
    }

    @MainThread
    fun openCommentThread(rootReply: ReplyItem) {
        ensureMainThread("openCommentThread")
        if (rootReply.rpid <= 0L) return
        commentRepliesJob?.cancel()
        val pageSize = _commentsUiState.value.pageSize
        val fallbackTotalCount = maxOf(rootReply.rcount, rootReply.replies.orEmpty().size)
        _commentsUiState.update {
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

    @MainThread
    fun closeCommentThread() {
        ensureMainThread("closeCommentThread")
        commentRepliesJob?.cancel()
        _commentsUiState.update {
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

    @MainThread
    fun loadMoreCommentReplies() {
        ensureMainThread("loadMoreCommentReplies")
        val current = _commentsUiState.value
        if (current.activeThreadRoot == null || current.isThreadLoading || current.isThreadAppending || !current.threadHasMore) {
            return
        }
        loadCommentReplies(
            page = current.threadCurrentPage + 1,
            append = true,
        )
    }

    private fun loadComments(
        page: Int,
        sortMode: PlayerCommentSortMode,
        append: Boolean,
    ) {
        ensureMainThread("loadComments")
        val aid = playbackRuntime.aid.takeIf { it > 0L } ?: _uiState.value.info?.aid ?: 0L
        val current = _commentsUiState.value
        val fallbackTotalCount = _uiState.value.info?.stat?.reply ?: 0
        if (aid <= 0L) {
            _commentsUiState.update {
                it.copy(
                    sortMode = sortMode,
                    isLoading = false,
                    isAppending = false,
                    hasMore = false,
                    errorMessage = if (_uiState.value.isLoading) {
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
            _commentsUiState.update {
                it.copy(
                    isAppending = true,
                    errorMessage = null,
                )
            }
        } else {
            commentsJob?.cancel()
            commentRepliesJob?.cancel()
            val initialTotalCount = maxOf(current.totalCount, fallbackTotalCount)
            _commentsUiState.update {
                it.copy(
                    sortMode = sortMode,
                    currentPage = 1,
                    items = emptyList(),
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
        commentsJob = viewModelScope.launch {
            val result = CommentRepository.getComments(
                aid = aid,
                page = page,
                ps = current.pageSize,
                mode = sortMode.apiMode,
            )
            val latestAid = playbackRuntime.aid.takeIf { it > 0L } ?: _uiState.value.info?.aid ?: 0L
            if (latestAid != aid) return@launch

            result.onSuccess { data ->
                val pageItems = resolveDisplayComments(data, current.pageSize)
                val totalCount = data.getAllCount().takeIf { it > 0 }
                    ?: maxOf(fallbackTotalCount, _uiState.value.info?.stat?.reply ?: 0)
                val totalPages = calculateCommentTotalPages(totalCount, current.pageSize)
                val updatedCurrentPage = if (append && pageItems.isEmpty()) current.currentPage else page
                _commentsUiState.update { state ->
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
            }.onFailure { error ->
                _commentsUiState.update { state ->
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
            }
        }
    }

    private fun loadCommentReplies(
        page: Int,
        append: Boolean,
    ) {
        ensureMainThread("loadCommentReplies")
        val current = _commentsUiState.value
        val rootReply = current.activeThreadRoot ?: return
        val aid = playbackRuntime.aid.takeIf { it > 0L } ?: _uiState.value.info?.aid ?: 0L
        if (aid <= 0L || rootReply.rpid <= 0L) return

        if (append) {
            if (current.isThreadLoading || current.isThreadAppending || !current.threadHasMore) return
            _commentsUiState.update {
                it.copy(
                    isThreadAppending = true,
                    threadErrorMessage = null,
                )
            }
        } else {
            commentRepliesJob?.cancel()
            val initialTotalCount = maxOf(current.threadTotalCount, rootReply.rcount)
            _commentsUiState.update {
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
        commentRepliesJob = viewModelScope.launch {
            val result = CommentRepository.getSubComments(
                aid = aid,
                rootId = rootReply.rpid,
                page = page,
                ps = current.pageSize,
            )
            val latestAid = playbackRuntime.aid.takeIf { it > 0L } ?: _uiState.value.info?.aid ?: 0L
            val latestRoot = _commentsUiState.value.activeThreadRoot
            if (latestAid != aid || latestRoot?.rpid != rootReply.rpid) return@launch

            result.onSuccess { data ->
                val replies = data.replies.orEmpty().take(current.pageSize)
                val updatedCurrentPage = if (append && replies.isEmpty()) current.threadCurrentPage else page
                _commentsUiState.update { state ->
                    val mergedReplies = if (append) {
                        (state.threadItems + replies).distinctBy { it.rpid }
                    } else {
                        replies
                    }
                    val totalCount = data.getAllCount().takeIf { it > 0 }
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
                _commentsUiState.update { state ->
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

    override fun onSponsorSkipped(segment: com.bbttvv.app.data.model.response.SponsorSegment) {
        ensureMainThread("onSponsorSkipped")
        _uiState.update { it.copy(statusMessage = "已跳过片段") }
    }

    private suspend fun resolveInitialPreferredQuality(): Int {
        val appContext = NetworkModule.appContext ?: return 64
        val storedQuality = NetworkUtils.getDefaultQualityId(appContext)
        val autoHighestEnabled = SettingsManager.getAutoHighestQualitySync(appContext)
        val isLoggedIn = !TokenManager.sessDataCache.isNullOrBlank() ||
            !TokenManager.accessTokenCache.isNullOrBlank()
        val effectiveVip = PlaybackRepository.refreshVipStatusForPreferredQualityIfNeeded(
            isLoggedIn = isLoggedIn,
            cachedIsVip = TokenManager.isVipCache,
            storedQuality = storedQuality,
            autoHighestEnabled = autoHighestEnabled
        )
        return resolvePlaybackDefaultQualityId(
            storedQuality = storedQuality,
            autoHighestEnabled = autoHighestEnabled,
            isLoggedIn = isLoggedIn,
            isVip = effectiveVip
        )
    }

    private fun resolvePlayerCdnPreference(): SettingsManager.PlayerCdnPreference {
        val appContext = NetworkModule.appContext ?: return SettingsManager.PlayerCdnPreference.BILIVIDEO
        return SettingsManager.getPlayerCdnPreferenceSync(appContext)
    }

    private fun resolveInitialPlaybackStartPosition(
        explicitStartPositionMs: Long,
        sourceResumePositionMs: Long,
        resumeFromPrompt: Boolean,
        localResumePositionMs: Long,
    ): Long {
        if (resumeFromPrompt) {
            return explicitStartPositionMs.takeIf { it >= PLAYER_RESUME_MIN_POSITION_MS } ?: 0L
        }
        val appContext = NetworkModule.appContext ?: return 0L
        if (!SettingsManager.getPlayerAutoResumeEnabledSync(appContext)) return 0L
        return explicitStartPositionMs
            .takeIf { it >= PLAYER_RESUME_MIN_POSITION_MS }
            ?: sourceResumePositionMs.takeIf { it >= PLAYER_RESUME_MIN_POSITION_MS }
            ?: localResumePositionMs.takeIf { it >= PLAYER_RESUME_MIN_POSITION_MS }
            ?: 0L
    }

    private fun resolveResumePlaybackPrompt(
        explicitStartPositionMs: Long,
        result: PlaybackLoadResult.Success,
        resumeFromPrompt: Boolean,
        localResumeCandidate: PlaybackResumeCandidate?,
    ): ResumePlaybackPrompt? {
        if (resumeFromPrompt) return null
        val appContext = NetworkModule.appContext ?: return null
        if (SettingsManager.getPlayerAutoResumeEnabledSync(appContext)) return null
        val explicitResumePositionMs = normalizeResumePromptPosition(
            positionMs = explicitStartPositionMs,
            durationMs = result.source.durationMs,
        )
        if (explicitResumePositionMs > 0L) {
            return buildResumePlaybackPrompt(
                targetCid = result.info.cid,
                currentCid = result.info.cid,
                positionMs = explicitResumePositionMs,
                pages = result.pages,
            )
        }
        val sourceCandidate = result.resumeCandidate
            ?: localResumeCandidate?.let {
                RemoteResumePlaybackCandidate(
                    cid = it.cid,
                    positionMs = it.positionMs,
                )
            }
            ?: return null
        return buildResumePlaybackPrompt(
            targetCid = sourceCandidate.cid,
            currentCid = result.info.cid,
            positionMs = sourceCandidate.positionMs,
            pages = result.pages,
        )
    }

    private fun buildResumePlaybackPrompt(
        targetCid: Long,
        currentCid: Long,
        positionMs: Long,
        pages: List<Page>,
    ): ResumePlaybackPrompt {
        val targetPage = pages.firstOrNull { page -> page.cid == targetCid }
        return ResumePlaybackPrompt(
            targetCid = targetCid,
            positionMs = positionMs,
            pageLabel = targetPage
                ?.page
                ?.takeIf { it > 0 && pages.size > 1 }
                ?.let { "第${it}P" },
            isCrossPage = targetCid != currentCid,
        )
    }

    private fun normalizeResumePromptPosition(
        positionMs: Long,
        durationMs: Long,
    ): Long {
        if (positionMs < PLAYER_RESUME_MIN_POSITION_MS) return 0L
        if (durationMs <= 0L) return positionMs
        val maxAllowedPositionMs = (durationMs - PLAYER_RESUME_END_GUARD_MS).coerceAtLeast(0L)
        if (positionMs >= maxAllowedPositionMs) return 0L
        return positionMs.coerceAtMost(maxAllowedPositionMs)
    }

    private fun resolveLocalResumeCandidate(
        result: PlaybackLoadResult.Success,
    ): PlaybackResumeCandidate? {
        val appContext = NetworkModule.appContext ?: return null
        val record = PlaybackResumeStore.load(
            context = appContext,
            bvid = result.info.bvid,
        ) ?: return null
        return resolveStoredResumeCandidate(
            currentCid = result.info.cid,
            pages = result.pages,
            record = record,
        )
    }

    private fun shouldAutoSwitchToResumeCandidate(
        explicitStartPositionMs: Long,
        localResumeCandidate: PlaybackResumeCandidate?,
        remoteResumeCandidate: RemoteResumePlaybackCandidate?,
        resumeFromPrompt: Boolean,
    ): Boolean {
        if (resumeFromPrompt) return false
        if (explicitStartPositionMs >= PLAYER_RESUME_MIN_POSITION_MS) return false
        val appContext = NetworkModule.appContext ?: return false
        if (!SettingsManager.getPlayerAutoResumeEnabledSync(appContext)) return false
        if (remoteResumeCandidate != null) return false
        return localResumeCandidate?.isCrossPage == true
    }

    private fun switchPlaybackSource(
        source: PlaybackSource,
        statusMessage: String
    ) {
        ensureMainThread("switchPlaybackSource")
        val currentPosition = getPlayerCurrentPosition()
        val shouldPlay = playerEngine?.playWhenReady ?: true
        applyPlaybackSource(
            source = source,
            seekToMs = currentPosition,
            resetPlayer = false,
            playWhenReady = shouldPlay
        )
        exoPlayer?.playbackParameters = PlaybackParameters(_uiState.value.playbackSpeed)
        updatePlaybackDuration(source.durationMs)
        _uiState.update {
            it.withPlaybackSource(
                source = source,
                isLoading = false,
                statusMessage = statusMessage
            )
        }
        refreshPlayerSnapshot()
    }

    private fun applyPlaybackSource(
        source: PlaybackSource,
        seekToMs: Long = 0L,
        resetPlayer: Boolean = true,
        playWhenReady: Boolean = true
    ) {
        ensureMainThread("applyPlaybackSource")
        playbackRuntime = playbackRuntime.copy(source = source)
        if (source.segmentUrls.isNotEmpty()) {
            playSegmentedVideo(
                segmentUrls = source.segmentUrls,
                segmentUrlCandidates = source.segmentUrlCandidates,
                seekToMs = seekToMs,
                resetPlayer = resetPlayer,
                playWhenReady = playWhenReady
            )
        } else {
            playDashVideo(
                videoUrl = source.videoUrl,
                audioUrl = source.audioUrl,
                videoUrlCandidates = source.videoUrlCandidates,
                audioUrlCandidates = source.audioUrlCandidates,
                seekToMs = seekToMs,
                resetPlayer = resetPlayer,
                playWhenReady = playWhenReady
            )
        }
    }

    private fun startPlayerPolling() {
        ensureMainThread("startPlayerPolling")
        playerPollingJob?.cancel()
        playerPollingJob = viewModelScope.launch {
            while (isActive) {
                val currentCid = playbackRuntime.cid
                val currentAid = playbackRuntime.aid
                if (currentCid > 0L && isDanmakuEnabled.value) {
                    val pos = getPlayerCurrentPosition()
                    prefetchDanmakuSegmentIfNeeded(
                        positionMs = pos
                    )
                }

                val engine = playerEngine
                if (
                    engine?.isPlaying == true &&
                    sponsorBlockEnabled &&
                    sponsorSegments.value.isNotEmpty()
                ) {
                    checkAndSkipSponsor()
                }
                delay(if (isDanmakuEnabled.value) 500L else 1000L)
            }
        }
    }

    @MainThread
    fun snapshotPlaybackState(): PlayerPlaybackState {
        ensureMainThread("snapshotPlaybackState")
        val player = exoPlayer ?: return _playbackState.value
        return PlayerPlaybackState(
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0 } ?: _playbackState.value.durationMs,
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            playerState = player.playbackState,
        )
    }

    private fun startPlaybackSettingsObservation() {
        ensureMainThread("startPlaybackSettingsObservation")
        if (settingsObservationJob != null) return
        settingsObservationJob = viewModelScope.launch {
            PluginManager.pluginsFlow.collectLatest { plugins ->
                val isEnabled = plugins.findSponsorBlockPluginInfo()?.enabled == true
                sponsorBlockEnabled = isEnabled
                if (!isEnabled) {
                    dismissSponsorSkipButton()
                }
            }
        }
    }

    private fun loadOnlineCount(bvid: String, cid: Long) {
        ensureMainThread("loadOnlineCount")
        if (bvid.isBlank() || cid <= 0L) return
        viewModelScope.launch {
            val onlineCountText = playbackUseCase.loadOnlineCountText(bvid = bvid, cid = cid).orEmpty()
            if (playbackRuntime.bvid != bvid || playbackRuntime.cid != cid) {
                return@launch
            }
            _uiState.update { it.copy(onlineCountText = onlineCountText) }
        }
    }

    private fun loadVideoShot(bvid: String, cid: Long) {
        ensureMainThread("loadVideoShot")
        if (bvid.isBlank() || cid <= 0L) return
        videoShotLoadJob?.cancel()
        videoShotLoadJob = viewModelScope.launch {
            val data = SubtitleAndAuxRepository.getVideoshot(bvid = bvid, cid = cid) ?: return@launch
            if (playbackRuntime.bvid != bvid || playbackRuntime.cid != cid) {
                return@launch
            }
            videoShot?.clear()
            videoShot = VideoShot.fromData(data)
        }
    }

    private fun clearVideoShot() {
        ensureMainThread("clearVideoShot")
        videoShotLoadJob?.cancel()
        videoShotFrameJob?.cancel()
        videoShotLoadJob = null
        videoShotFrameJob = null
        videoShot?.clear()
        videoShot = null
        _seekPreviewFrame.value = null
    }

    private fun refreshPlayerSnapshot() {
        ensureMainThread("refreshPlayerSnapshot")
        val player = exoPlayer ?: return
        syncHeartbeatPlaybackTracking(isActivelyPlaying = player.isPlaying)
        updatePlaybackState(
            PlayerPlaybackState(
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.takeIf { it > 0 } ?: _playbackState.value.durationMs,
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                playerState = player.playbackState,
            )
        )
        val info = _uiState.value.info
        if (info != null && isDanmakuEnabled.value) {
            requestDanmakuLoad(
                cid = info.cid,
                aid = info.aid,
                startPositionMs = player.currentPosition,
                force = false
            )
        }
    }

    private fun updatePlaybackDuration(durationMs: Long) {
        ensureMainThread("updatePlaybackDuration")
        if (durationMs <= 0L) return
        val current = _playbackState.value
        if (current.durationMs == durationMs) return
        _playbackState.value = current.copy(durationMs = durationMs)
    }

    private fun updatePlaybackState(nextState: PlayerPlaybackState) {
        ensureMainThread("updatePlaybackState")
        if (_playbackState.value == nextState) return
        _playbackState.value = nextState
    }

    private fun requestDanmakuLoad(
        cid: Long,
        aid: Long,
        startPositionMs: Long,
        force: Boolean
    ) {
        ensureMainThread("requestDanmakuLoad")
        if (cid <= 0L || !isDanmakuEnabled.value) return
        val sameCid = cid == lastDanmakuCid
        val hasPayload = danmakuPayload.value != null
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force) {
            if (isDanmakuLoading.value && sameCid) {
                return
            }
            if (sameCid && hasPayload) {
                return
            }
            if (sameCid &&
                !hasPayload &&
                now - lastDanmakuRequestAtMs < DANMAKU_RETRY_INTERVAL_MS
            ) {
                return
            }
        }
        lastDanmakuCid = cid
        lastDanmakuRequestAtMs = now
        loadDanmaku(cid = cid, aid = aid, startPositionMs = startPositionMs)
    }

    @MainThread
    fun finishPlaybackSession(reason: String = "manual_exit") {
        ensureMainThread("finishPlaybackSession")
        flushPlaybackHistory(reason = reason, closeSession = true)
    }

    private fun beginHeartbeatSession(
        aid: Long,
        bvid: String,
        cid: Long,
        creatorMid: Long,
        creatorName: String,
        nowEpochSec: Long = System.currentTimeMillis() / 1000L
    ) {
        ensureMainThread("beginHeartbeatSession")
        heartbeatJob?.cancel()
        heartbeatRuntime = HeartbeatRuntimeState(
            aid = aid,
            bvid = bvid,
            cid = cid,
            startTsSec = nowEpochSec,
            creatorMid = creatorMid,
            creatorName = creatorName
        )

        if (bvid.isBlank() || cid <= 0L) {
            return
        }

        heartbeatJob = viewModelScope.launch {
            delay(HISTORY_HEARTBEAT_INITIAL_DELAY_MS)
            while (isActive && heartbeatRuntime.bvid == bvid && heartbeatRuntime.cid == cid) {
                reportPlaybackHeartbeat(forceFlush = false, reason = "interval")
                delay(HISTORY_HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun clearHeartbeatSession() {
        ensureMainThread("clearHeartbeatSession")
        heartbeatJob?.cancel()
        heartbeatJob = null
        heartbeatRuntime = HeartbeatRuntimeState()
    }

    private fun syncHeartbeatPlaybackTracking(
        isActivelyPlaying: Boolean,
        nowElapsedMs: Long = SystemClock.elapsedRealtime()
    ) {
        ensureMainThread("syncHeartbeatPlaybackTracking")
        val session = heartbeatRuntime
        if (!session.hasSession) return
        if (isActivelyPlaying) {
            if (session.activePlayStartElapsedMs == null) {
                heartbeatRuntime = session.copy(activePlayStartElapsedMs = nowElapsedMs)
            }
            return
        }

        val activePlayStartElapsedMs = session.activePlayStartElapsedMs ?: return
        heartbeatRuntime = session.copy(
            accumulatedPlayMs = session.accumulatedPlayMs + (nowElapsedMs - activePlayStartElapsedMs).coerceAtLeast(0L),
            activePlayStartElapsedMs = null
        )
    }

    private fun buildHeartbeatSnapshot(
        currentPositionMs: Long = currentHeartbeatPositionMs(),
        nowElapsedMs: Long = SystemClock.elapsedRealtime()
    ): PlaybackHeartbeatSnapshot {
        ensureMainThread("buildHeartbeatSnapshot")
        val session = heartbeatRuntime
        val activePlayMs = session.activePlayStartElapsedMs
            ?.let { (nowElapsedMs - it).coerceAtLeast(0L) }
            ?: 0L
        val currentDurationMs = if (
            session.bvid.isNotBlank() &&
            session.bvid == playbackRuntime.bvid &&
            session.cid == playbackRuntime.cid
        ) {
            getPlayerDuration()
        } else {
            _playbackState.value.durationMs
        }.coerceAtLeast(0L)
        return PlaybackHeartbeatSnapshot(
            playedTimeSec = (currentPositionMs.coerceAtLeast(0L) / 1000L),
            realPlayedTimeSec = ((session.accumulatedPlayMs + activePlayMs).coerceAtLeast(0L) / 1000L),
            durationMs = currentDurationMs,
        )
    }

    private fun currentHeartbeatPositionMs(): Long {
        ensureMainThread("currentHeartbeatPositionMs")
        val session = heartbeatRuntime
        return if (
            session.bvid.isNotBlank() &&
            session.bvid == playbackRuntime.bvid &&
            session.cid == playbackRuntime.cid
        ) {
            getPlayerCurrentPosition()
        } else {
            _playbackState.value.positionMs
        }.coerceAtLeast(0L)
    }

    private fun captureHeartbeatReport(forceFlush: Boolean): PlaybackHeartbeatReport? {
        ensureMainThread("captureHeartbeatReport")
        var session = heartbeatRuntime
        if ((session.aid <= 0L && session.bvid.isBlank()) || session.cid <= 0L) return null

        val nowElapsedMs = SystemClock.elapsedRealtime()
        val isActivelyPlaying = exoPlayer?.isPlaying == true
        syncHeartbeatPlaybackTracking(
            isActivelyPlaying = isActivelyPlaying,
            nowElapsedMs = nowElapsedMs
        )
        session = heartbeatRuntime
        val snapshot = buildHeartbeatSnapshot(nowElapsedMs = nowElapsedMs)
        val shouldSend = if (forceFlush) {
            snapshot.playedTimeSec > 0L ||
                snapshot.realPlayedTimeSec > 0L ||
                session.lastReportedSnapshot == null
        } else {
            isActivelyPlaying &&
                (
                    session.lastReportedSnapshot == null ||
                        snapshot.playedTimeSec > session.lastReportedSnapshot.playedTimeSec ||
                        snapshot.realPlayedTimeSec > session.lastReportedSnapshot.realPlayedTimeSec
                    )
        }
        if (!shouldSend) return null

        if (session.startTsSec <= 0L) {
            session = session.copy(startTsSec = System.currentTimeMillis() / 1000L)
            heartbeatRuntime = session
        }
        val deltaWatchSec = (
            snapshot.realPlayedTimeSec - session.lastReportedSnapshot?.realPlayedTimeSec.orZero()
            ).coerceAtLeast(0L)
        return PlaybackHeartbeatReport(
            aid = session.aid,
            bvid = session.bvid,
            cid = session.cid,
            startTsSec = session.startTsSec,
            snapshot = snapshot,
            creatorMid = session.creatorMid,
            creatorName = session.creatorName,
            deltaWatchSec = deltaWatchSec
        )
    }

    private suspend fun reportPlaybackHeartbeat(
        forceFlush: Boolean,
        reason: String
    ): Boolean {
        val report = captureHeartbeatReport(forceFlush = forceFlush) ?: return false
        return sendHeartbeatReport(report = report, reason = reason, updateSessionState = true)
    }

    private fun flushPlaybackHistory(
        reason: String,
        closeSession: Boolean
    ) {
        ensureMainThread("flushPlaybackHistory")
        val report = captureHeartbeatReport(forceFlush = true)
        if (closeSession) {
            clearHeartbeatSession()
        }
        if (report == null) return
        AppScope.ioScope.launch {
            sendHeartbeatReport(
                report = report,
                reason = reason,
                updateSessionState = false
            )
        }
    }

    private suspend fun sendHeartbeatReport(
        report: PlaybackHeartbeatReport,
        reason: String,
        updateSessionState: Boolean
    ): Boolean {
        val appContext = NetworkModule.appContext
        if (appContext != null) {
            PlaybackResumeStore.save(
                context = appContext,
                bvid = report.bvid,
                cid = report.cid,
                positionMs = report.snapshot.playedTimeSec * 1000L,
                durationMs = report.snapshot.durationMs,
            )
        }
        PlayUrlCache.invalidate(
            bvid = report.bvid,
            cid = report.cid,
        )
        val historyReported = if (report.aid > 0L) {
            PlaybackRepository.reportPlayHistoryProgress(
                aid = report.aid,
                cid = report.cid,
                progressSec = report.snapshot.playedTimeSec
            )
        } else {
            false
        }
        val heartbeatReported = PlaybackRepository.reportPlayHeartbeat(
            bvid = report.bvid,
            cid = report.cid,
            playedTime = report.snapshot.playedTimeSec,
            realPlayedTime = report.snapshot.realPlayedTimeSec,
            startTsSec = report.startTsSec
        )
        val reported = historyReported || heartbeatReported
        if (reported && updateSessionState &&
            heartbeatRuntime.bvid == report.bvid &&
            heartbeatRuntime.cid == report.cid
        ) {
            heartbeatRuntime = heartbeatRuntime.copy(lastReportedSnapshot = report.snapshot)
        }
        if (reported) {
            PlaybackHistorySyncBus.publish(
                mid = TokenManager.midCache ?: 0L,
                bvid = report.bvid,
                cid = report.cid
            )
            if (appContext != null && report.deltaWatchSec > 0L) {
                TodayWatchProfileStore.recordWatchProgress(
                    context = appContext,
                    mid = report.creatorMid,
                    creatorName = report.creatorName,
                    deltaWatchSec = report.deltaWatchSec,
                    watchedAtSec = System.currentTimeMillis() / 1000L
                )
            }
        }
        if (!historyReported) {
            Logger.w(
                "PlayerVM",
                "Remote history report failed; server-side resume state may stay stale " +
                    "even if heartbeat succeeds. reason=$reason aid=${report.aid} bvid=${report.bvid} cid=${report.cid}"
            )
        }
        Logger.d(
            "PlayerVM",
            "history sync result=$reported history=$historyReported heartbeat=$heartbeatReported " +
                "reason=$reason aid=${report.aid} bvid=${report.bvid} cid=${report.cid} " +
                "played=${report.snapshot.playedTimeSec} real=${report.snapshot.realPlayedTimeSec} " +
                "delta=${report.deltaWatchSec} startTs=${report.startTsSec}"
        )
        return reported
    }

    private fun Long?.orZero(): Long = this ?: 0L

    private fun PlayerUiState.withPlaybackSource(
        source: PlaybackSource,
        isLoading: Boolean,
        statusMessage: String?
    ): PlayerUiState {
        return copy(
            isLoading = isLoading,
            selectedQuality = source.actualQuality,
            qualityOptions = source.qualityOptions.map(::toQualityOption),
            audioOptions = buildAudioOptions(source),
            videoCodecOptions = buildVideoCodecOptions(source),
            selectedVideoCodecId = source.selectedVideoCodecId,
            selectedAudioQualityId = source.selectedAudioQualityId,
            selectedAudioCodecId = source.selectedAudioCodecId,
            statusMessage = statusMessage,
            playbackBadges = buildPlaybackBadges(source),
            capabilityHints = source.capabilityHints,
            selectedVideoCodecLabel = source.selectedVideoCodec,
            selectedAudioCodecLabel = source.selectedAudioCodec,
            activeDynamicRangeLabel = source.activeDynamicRangeLabel,
            videoCdnHost = source.videoUrl.toHostLabel(),
            audioCdnHost = source.audioUrl.orEmpty().toHostLabel(),
            selectedVideoWidth = source.selectedVideoWidth,
            selectedVideoHeight = source.selectedVideoHeight,
            selectedVideoFrameRate = source.selectedVideoFrameRate,
            selectedVideoBandwidth = source.selectedVideoBandwidth,
            selectedAudioBandwidth = source.selectedAudioBandwidth,
            selectedQualityLabel = source.qualityOptions
                .firstOrNull { it.id == source.actualQuality }
                ?.label
                ?: source.actualQuality.toString()
        )
    }

    private fun toQualityOption(option: com.bbttvv.app.feature.video.usecase.PlaybackQualityInfo): QualityOption {
        return QualityOption(
            id = option.id,
            label = option.label,
            isSupported = option.isSupported,
            unsupportedReason = option.unsupportedReason
        )
    }

    private fun buildAudioOptions(source: PlaybackSource): List<PlayerOption> {
        return source.dashAudios
            .filter { it.getValidUrl().isNotBlank() }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<DashAudio> { it.id }.thenByDescending { it.bandwidth })
            .map { audio ->
                PlayerOption(
                    key = audio.id.toString(),
                    label = resolveAudioLabel(audio),
                    subtitle = buildAudioSubtitle(audio),
                    isSelected = audio.id == source.selectedAudioQualityId
                )
            }
    }

    private fun buildVideoCodecOptions(source: PlaybackSource): List<PlayerOption> {
        val hevcSupported = MediaUtils.isHevcSupported()
        val av1Supported = MediaUtils.isAv1Supported()
        return source.dashVideos
            .filter { it.id == source.actualQuality && it.getValidUrl().isNotBlank() }
            .distinctBy { it.videoCodecSelectionKey() }
            .map { video ->
                val supported = video.isSupportedByDevice(
                    isHevcSupported = hevcSupported,
                    isAv1Supported = av1Supported
                )
                PlayerOption(
                    key = video.videoCodecSelectionKey().toString(),
                    label = resolveCodecLabel(video.codecs, video.codecid).ifBlank { "未知编码" },
                    subtitle = buildVideoCodecSubtitle(video),
                    isSelected = video.videoCodecSelectionKey() == source.selectedVideoCodecId,
                    isEnabled = supported,
                    disabledReason = if (supported) null else "当前设备暂不支持该编码"
                )
            }
    }

    private fun buildPlaybackBadges(source: PlaybackSource): List<PlaybackBadge> {
        return buildList {
            source.activeDynamicRangeLabel?.let { add(PlaybackBadge(it)) }
            if (source.hasDolbyVisionTrack && source.activeDynamicRangeLabel != "杜比视界") {
                add(PlaybackBadge("杜比视界可用", isActive = false))
            }
            if (source.hasHdrTrack && source.activeDynamicRangeLabel != "HDR 真彩") {
                add(PlaybackBadge("HDR 可用", isActive = false))
            }
            if (source.hasDolbyAudioTrack) {
                val isDolbyActive = source.selectedAudioCodec.contains("杜比", ignoreCase = true) ||
                    source.selectedAudioCodec.contains("Dolby", ignoreCase = true)
                add(PlaybackBadge("杜比音频", isActive = isDolbyActive))
            }
            source.selectedVideoCodec.takeIf { it.isNotBlank() }?.let {
                add(PlaybackBadge(it))
            }
            source.selectedAudioCodec.takeIf { it.isNotBlank() }?.let {
                add(PlaybackBadge(it))
            }
        }
    }

    private fun resolveAudioLabel(audio: DashAudio): String {
        return AudioQuality.fromCode(audio.id)?.description
            ?: audio.bandwidth.takeIf { it > 0 }?.let { "${it / 1000}K" }
            ?: "音频 ${audio.id}"
    }

    private fun buildAudioSubtitle(audio: DashAudio): String? {
        return buildList {
            resolveCodecLabel(audio.codecs, audio.codecid).takeIf { it.isNotBlank() }?.let(::add)
            audio.bandwidth.takeIf { it > 0 }?.let { add("${it / 1000} kbps") }
        }.joinToString(" · ").ifBlank { null }
    }

    private fun buildVideoCodecSubtitle(video: DashVideo): String? {
        return buildList {
            if (video.width > 0 && video.height > 0) {
                add("${video.width}x${video.height}")
            }
            video.frameRate.takeIf { it.isNotBlank() }?.let(::add)
            video.bandwidth.takeIf { it > 0 }?.let { add("${it / 1000} kbps") }
        }.joinToString(" · ").ifBlank { null }
    }

    private fun String.toHostLabel(): String {
        if (isBlank()) return ""
        return runCatching { URI(this).host.orEmpty() }
            .getOrElse {
                substringAfter("://", this)
                    .substringBefore("/")
                    .substringBefore("?")
            }
    }

    private fun DashVideo.videoCodecSelectionKey(): Int {
        return codecid ?: codecs.lowercase().hashCode()
    }

    private fun DashVideo.isSupportedByDevice(
        isHevcSupported: Boolean,
        isAv1Supported: Boolean
    ): Boolean {
        val codecText = codecs.lowercase()
        return when {
            codecText.startsWith("avc") -> true
            codecText.startsWith("hev") || codecText.startsWith("hvc") -> isHevcSupported
            codecText.startsWith("av01") -> isAv1Supported
            codecText.startsWith("dvh1") || codecText.startsWith("dvhe") -> isHevcSupported
            else -> true
        }
    }

    private fun resolveCodecLabel(codecs: String?, codecId: Int?): String {
        val codecText = codecs.orEmpty().lowercase()
        return when {
            codecText.startsWith("dvh1") || codecText.startsWith("dvhe") -> "Dolby Vision"
            codecText.startsWith("av01") -> "AV1"
            codecText.startsWith("hev1") || codecText.startsWith("hvc1") -> "HEVC"
            codecText.startsWith("avc1") -> "AVC"
            codecText.contains("ec-3") || codecText.contains("eac-3") -> "杜比音频"
            codecText.contains("flac") -> "FLAC"
            codecText.contains("mp4a") -> "AAC"
            codecId == 12 -> "HEVC"
            codecId == 13 -> "AV1"
            codecId == 7 -> "AVC"
            else -> codecs.orEmpty()
        }
    }

    private fun formatSpeed(speed: Float): String {
        return if (speed % 1f == 0f) {
            "${speed.toInt()}倍"
        } else {
            "${speed}倍"
        }
    }

    override fun onCleared() {
        ensureMainThread("PlayerViewModel.onCleared")
        finishPlaybackSession(reason = "viewmodel_cleared")
        exoPlayer?.removeListener(playerListener)
        playerPollingJob?.cancel()
        settingsObservationJob?.cancel()
        commentsJob?.cancel()
        commentRepliesJob?.cancel()
        clearVideoShot()
        super.onCleared()
    }
}
