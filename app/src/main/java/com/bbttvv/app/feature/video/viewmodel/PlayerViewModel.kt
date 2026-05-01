package com.bbttvv.app.feature.video.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.player.BasePlayerViewModel
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.store.PlaybackResumeCandidate
import com.bbttvv.app.core.store.PlaybackResumeStore
import com.bbttvv.app.core.store.PlayerSettingsCache
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.store.resolveStoredResumeCandidate
import com.bbttvv.app.core.store.player.PlayerSettingsStore
import com.bbttvv.app.core.util.MediaUtils
import com.bbttvv.app.core.util.NetworkUtils
import com.bbttvv.app.core.util.resolvePlaybackDefaultQualityId
import com.bbttvv.app.data.model.response.Page
import com.bbttvv.app.data.model.response.ReplyItem
import com.bbttvv.app.data.model.response.ViewInfo
import com.bbttvv.app.data.repository.PlaybackRepository
import com.bbttvv.app.feature.plugin.findSponsorBlockPluginInfo
import com.bbttvv.app.feature.video.usecase.PlaybackLoadResult
import com.bbttvv.app.feature.video.usecase.ResumePlaybackCandidate as RemoteResumePlaybackCandidate
import com.bbttvv.app.feature.video.usecase.PlaybackSource
import com.bbttvv.app.feature.video.usecase.VideoPlaybackUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val DANMAKU_RETRY_INTERVAL_MS = 2_500L
private const val PLAYER_RESUME_MIN_POSITION_MS = 5_000L
private const val PLAYER_RESUME_END_GUARD_MS = 10_000L

private data class PlaybackRuntimeState(
    val bvid: String = "",
    val aid: Long = 0L,
    val cid: Long = 0L,
    val source: PlaybackSource? = null,
) {
    val sessionKey: String
        get() = "$bvid:$cid"
}

internal sealed interface PlayerEvent {
    data class ExitPlayer(val reason: String) : PlayerEvent
}

class PlayerViewModel : BasePlayerViewModel() {
    private val playbackUseCase = VideoPlaybackUseCase()

    private val _uiState = MutableStateFlow(
        PlayerUiState(playbackSpeed = PlayerSettingsCache.getPreferredPlaybackSpeed())
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private val _playbackState = MutableStateFlow(PlayerPlaybackState())
    val playbackState: StateFlow<PlayerPlaybackState> = _playbackState.asStateFlow()
    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 1)
    internal val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private var playbackRuntime = PlaybackRuntimeState()
    private val playbackHistoryReporter = PlaybackHistoryReporter(
        scope = viewModelScope,
        ensureMainThread = { caller -> ensureMainThread(caller) },
        playbackSnapshot = {
            val runtime = playbackRuntime
            val state = _playbackState.value
            PlaybackHistoryPlaybackSnapshot(
                bvid = runtime.bvid,
                cid = runtime.cid,
                currentPositionMs = getPlayerCurrentPosition(),
                currentDurationMs = getPlayerDuration(),
                fallbackPositionMs = state.positionMs,
                fallbackDurationMs = state.durationMs,
                isPlaying = exoPlayer?.isPlaying == true,
            )
        },
    )
    private val commentController = PlayerCommentController(
        scope = viewModelScope,
        currentAid = {
            playbackRuntime.aid.takeIf { it > 0L } ?: _uiState.value.info?.aid ?: 0L
        },
        fallbackCommentCount = { _uiState.value.info?.stat?.reply ?: 0 },
        isVideoLoading = { _uiState.value.isLoading },
    )
    private val videoShotController = VideoShotController(
        scope = viewModelScope,
        isCurrentPlayback = { bvid, cid ->
            playbackRuntime.bvid == bvid && playbackRuntime.cid == cid
        },
        currentSessionKey = { playbackRuntime.sessionKey },
    )
    private val progressHeatmapController = ProgressHeatmapController(
        scope = viewModelScope,
        isCurrentPlayback = { bvid, cid ->
            playbackRuntime.bvid == bvid && playbackRuntime.cid == cid
        },
        updateUiState = { transform -> _uiState.update(transform) },
    )
    private val playbackQualityController = PlaybackQualityController(
        scope = viewModelScope,
        playbackUseCase = playbackUseCase,
        ensureMainThread = { caller -> ensureMainThread(caller) },
        currentUiState = { _uiState.value },
        currentSource = { playbackRuntime.source },
        resolveCdnPreference = { resolvePlayerCdnPreference() },
        isCurrentPlayback = { bvid, cid ->
            playbackRuntime.bvid == bvid && playbackRuntime.cid == cid
        },
        updateUiState = { transform -> _uiState.update(transform) },
        switchPlaybackSource = { source, statusMessage ->
            switchPlaybackSource(source = source, statusMessage = statusMessage)
        },
    )
    private val playbackEndController = PlaybackEndController(
        scope = viewModelScope,
        ensureMainThread = { caller -> ensureMainThread(caller) },
        currentSession = {
            PlaybackEndSessionSnapshot(
                bvid = playbackRuntime.bvid,
                cid = playbackRuntime.cid,
            )
        },
        currentUiState = { _uiState.value },
        currentPlaybackState = { _playbackState.value },
        updateUiState = { transform -> _uiState.update(transform) },
        finishPlaybackSession = { reason -> finishPlaybackSession(reason) },
        restartCurrentVideoFromBeginning = { restartCurrentVideoFromBeginning() },
        loadVideo = { bvid, aid, cid, force ->
            loadVideo(
                bvid = bvid,
                aid = aid,
                cid = cid,
                force = force,
            )
        },
        requestExitPlayer = { reason -> requestExitPlayer(reason) },
    )
    private var playbackLoadJob: Job? = null
    private var playbackLoadGeneration: Long = 0L
    private var playerPollingJob: Job? = null
    private var settingsObservationJob: Job? = null
    private var sponsorBlockEnabled: Boolean = true
    private var lastDanmakuCid: Long = 0L
    private var lastDanmakuDurationMs: Long = 0L
    private var lastDanmakuRequestAtMs: Long = 0L

    val commentsUiState = commentController.uiState
    val seekPreviewFrame = videoShotController.seekPreviewFrame

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            ensureMainThread("Player.Listener.onIsPlayingChanged")
            playbackHistoryReporter.syncPlaybackTracking(isActivelyPlaying = isPlaying)
            refreshPlayerSnapshot()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            ensureMainThread("Player.Listener.onPlaybackStateChanged")
            refreshPlayerSnapshot()
            if (playbackState == Player.STATE_ENDED) {
                playbackEndController.handlePlaybackEnded()
            } else {
                playbackEndController.markPlaybackActive()
            }
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
    override fun detachPlayer(player: ExoPlayer?) {
        ensureMainThread("PlayerViewModel.detachPlayer")
        val currentPlayer = exoPlayer
        if (player == null || currentPlayer === player) {
            currentPlayer?.removeListener(playerListener)
            super.detachPlayer(player)
        } else {
            player.removeListener(playerListener)
        }
    }

    private fun isCurrentPlaybackLoad(
        generation: Long,
        bvid: String,
        requestedCid: Long,
    ): Boolean {
        return generation == playbackLoadGeneration &&
            playbackRuntime.bvid == bvid &&
            (requestedCid <= 0L || playbackRuntime.cid == requestedCid)
    }

    private fun isCurrentPlaybackSession(
        generation: Long,
        bvid: String,
        cid: Long,
    ): Boolean {
        return generation == playbackLoadGeneration &&
            playbackRuntime.bvid == bvid &&
            playbackRuntime.cid == cid
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
        val requestBvid = bvid.trim()
        val requestedCid = cid
        if (requestBvid.isBlank()) return
        if (!force && playbackRuntime.bvid == requestBvid && playbackRuntime.cid == requestedCid && _uiState.value.info != null) {
            return
        }

        if (playbackHistoryReporter.hasSession) {
            playbackHistoryReporter.finishSession(reason = "switch_video", closeSession = true)
        }

        playbackLoadJob?.cancel()
        playbackQualityController.cancelPending()
        val loadGeneration = ++playbackLoadGeneration
        playbackEndController.resetForNewVideo()
        clearProgressHeatmap()
        commentController.reset()

        playbackRuntime = PlaybackRuntimeState(
            bvid = requestBvid,
            aid = aid,
            cid = requestedCid
        )
        lastDanmakuCid = 0L
        lastDanmakuDurationMs = 0L
        lastDanmakuRequestAtMs = 0L
        clearDanmaku()
        resetSponsorState()
        clearVideoShot()
        updatePlaybackState(PlayerPlaybackState())

        playbackLoadJob = viewModelScope.launch {
            val appContext = NetworkModule.appContext
            val hdrSupported = MediaUtils.isHdrSupported(appContext)
            val dolbyVisionSupported = MediaUtils.isDolbyVisionSupported(appContext)
            val hevcSupported = MediaUtils.isHevcSupported()
            val av1Supported = MediaUtils.isAv1Supported()
            val preferredQuality = resolveInitialPreferredQuality()
            val cdnPreference = resolvePlayerCdnPreference()

            if (!isCurrentPlaybackLoad(loadGeneration, requestBvid, requestedCid)) {
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = null,
                    resumePrompt = null,
                    autoNextPrompt = null,
                )
            }

            when (
                val result = playbackUseCase.loadVideo(
                    bvid = requestBvid,
                    aid = aid,
                    cid = requestedCid,
                    preferredQuality = preferredQuality,
                    cdnPreference = cdnPreference,
                    isHevcSupported = hevcSupported,
                    isAv1Supported = av1Supported,
                    isHdrSupported = hdrSupported,
                    isDolbyVisionSupported = dolbyVisionSupported
                )
            ) {
                is PlaybackLoadResult.Error -> {
                    if (!isCurrentPlaybackLoad(loadGeneration, requestBvid, requestedCid)) {
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }

                is PlaybackLoadResult.Success -> {
                    if (!isCurrentPlaybackLoad(loadGeneration, requestBvid, requestedCid)) {
                        return@launch
                    }
                    playbackRuntime = playbackRuntime.copy(
                        aid = result.info.aid,
                        cid = result.info.cid
                    )
                    if (!isCurrentPlaybackSession(loadGeneration, result.info.bvid, result.info.cid)) {
                        return@launch
                    }
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
                    if (!isCurrentPlaybackSession(loadGeneration, result.info.bvid, result.info.cid)) {
                        return@launch
                    }
                    updatePlaybackDuration(result.source.durationMs)
                    playbackHistoryReporter.beginSession(
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
                    loadProgressHeatmap(
                        bvid = result.info.bvid,
                        aid = result.info.aid,
                        cid = result.info.cid,
                        durationMs = result.source.durationMs,
                    )
                    loadOnlineCount(result.info.bvid, result.info.cid)
                    playbackEndController.prefetchRelatedVideosForAutoNextIfNeeded(result.info.bvid)
                }
            }
        }
    }

    @MainThread
    fun togglePlayback() {
        ensureMainThread("togglePlayback")
        val engine = playerEngine ?: return
        if (exoPlayer?.playbackState == Player.STATE_ENDED) {
            restartCurrentVideoFromBeginning()
            return
        }
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
        videoShotController.requestFrame(positionMs)
    }

    @MainThread
    fun clearSeekPreview() {
        ensureMainThread("clearSeekPreview")
        videoShotController.clearPreview()
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
        playbackQualityController.changeQuality(qualityId)
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
    fun cancelAutoNextPrompt() {
        ensureMainThread("cancelAutoNextPrompt")
        playbackEndController.cancelAutoNextPrompt()
    }

    @MainThread
    fun confirmAutoNextPrompt(promptId: Long) {
        ensureMainThread("confirmAutoNextPrompt")
        playbackEndController.confirmAutoNextPrompt(promptId)
    }

    @MainThread
    fun ensureCommentsLoaded() {
        ensureMainThread("ensureCommentsLoaded")
        commentController.ensureLoaded()
    }

    @MainThread
    fun refreshComments() {
        ensureMainThread("refreshComments")
        commentController.refresh()
    }

    @MainThread
    fun changeCommentSort(sortMode: PlayerCommentSortMode) {
        ensureMainThread("changeCommentSort")
        commentController.changeSort(sortMode)
    }

    @MainThread
    fun toggleCommentSort() {
        ensureMainThread("toggleCommentSort")
        commentController.toggleSort()
    }

    @MainThread
    fun loadMoreComments() {
        ensureMainThread("loadMoreComments")
        commentController.loadMore()
    }

    @MainThread
    fun openCommentThread(rootReply: ReplyItem) {
        ensureMainThread("openCommentThread")
        commentController.openThread(rootReply)
    }

    @MainThread
    fun closeCommentThread() {
        ensureMainThread("closeCommentThread")
        commentController.closeThread()
    }

    @MainThread
    fun loadMoreCommentReplies() {
        ensureMainThread("loadMoreCommentReplies")
        commentController.loadMoreReplies()
    }

    override fun onSponsorSkipped(segment: com.bbttvv.app.data.model.response.SponsorSegment) {
        ensureMainThread("onSponsorSkipped")
        _uiState.update { it.copy(statusMessage = "已跳过片段") }
    }

    @MainThread
    private fun restartCurrentVideoFromBeginning() {
        ensureMainThread("restartCurrentVideoFromBeginning")
        val engine = playerEngine ?: return
        val info = _uiState.value.info
        _uiState.update { it.copy(autoNextPrompt = null) }
        engine.seekTo(0L)
        engine.playWhenReady = true
        if (info != null && info.cid > 0L) {
            playbackHistoryReporter.beginSession(
                aid = info.aid,
                bvid = info.bvid,
                cid = info.cid,
                creatorMid = info.owner.mid,
                creatorName = info.owner.name,
            )
        }
        refreshPlayerSnapshot()
    }

    private fun requestExitPlayer(reason: String) {
        if (!_events.tryEmit(PlayerEvent.ExitPlayer(reason))) {
            viewModelScope.launch {
                _events.emit(PlayerEvent.ExitPlayer(reason))
            }
        }
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
        return SettingsManager.PlayerCdnPreference.BILIVIDEO
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
        videoShotController.load(bvid = bvid, cid = cid)
    }

    private fun loadProgressHeatmap(
        bvid: String,
        aid: Long,
        cid: Long,
        durationMs: Long,
    ) {
        ensureMainThread("loadProgressHeatmap")
        progressHeatmapController.load(
            bvid = bvid,
            aid = aid,
            cid = cid,
            durationMs = durationMs,
        )
    }

    private fun clearProgressHeatmap() {
        ensureMainThread("clearProgressHeatmap")
        progressHeatmapController.clear()
    }

    private fun clearVideoShot() {
        ensureMainThread("clearVideoShot")
        videoShotController.clear()
    }

    private fun refreshPlayerSnapshot() {
        ensureMainThread("refreshPlayerSnapshot")
        val player = exoPlayer ?: return
        playbackHistoryReporter.syncPlaybackTracking(isActivelyPlaying = player.isPlaying)
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
        playbackHistoryReporter.finishSession(reason = reason, closeSession = true)
    }

    override fun onCleared() {
        ensureMainThread("PlayerViewModel.onCleared")
        finishPlaybackSession(reason = "viewmodel_cleared")
        detachPlayer()
        playbackLoadJob?.cancel()
        playbackQualityController.cancelPending()
        playerPollingJob?.cancel()
        settingsObservationJob?.cancel()
        playbackEndController.clear()
        commentController.reset()
        clearProgressHeatmap()
        clearVideoShot()
        super.onCleared()
    }
}
