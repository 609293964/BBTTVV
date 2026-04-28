package com.bbttvv.app.feature.live

import androidx.annotation.MainThread
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.network.socket.DanmakuProtocol
import com.bbttvv.app.core.network.socket.LiveDanmakuClient
import com.bbttvv.app.core.network.socket.LiveDanmakuConnectionState
import com.bbttvv.app.core.player.BasePlayerViewModel
import com.bbttvv.app.core.player.PlayerAudioBalanceController
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.util.CrashReporter
import com.bbttvv.app.data.model.response.LivePlayUrlData
import com.bbttvv.app.data.model.response.LiveQuality
import com.bbttvv.app.data.model.response.LiveRoomDetailData
import com.bbttvv.app.data.repository.DanmakuRepository
import com.bbttvv.app.data.repository.LiveRepository
import com.bbttvv.app.feature.video.danmaku.ParsedDanmaku
import com.bbttvv.app.feature.video.danmaku.WeightedTextData
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_SCROLL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

private const val LIVE_DANMAKU_SOURCE_LABEL = "LIVE"
private const val LIVE_DANMAKU_MAX_COUNT = 180
private const val LIVE_DANMAKU_RETENTION_MS = 45_000L
private const val LIVE_DANMAKU_SHOW_LEAD_MS = 220L
private const val LIVE_DANMAKU_MIN_GAP_MS = 70L
private const val LIVE_STATUS_AUTO_CLEAR_MS = 1_800L
private const val LIVE_PLAYER_CORE_MEDIA3_KEY = "media3"

data class LiveQualityOption(
    val qn: Int,
    val label: String,
)

data class LiveLineOption(
    val key: String,
    val label: String,
    val subtitle: String? = null,
)

enum class LiveBitrateBoostMode(
    val key: String,
    val label: String,
    val shortLabel: String,
    val isEnabled: Boolean,
) {
    Standard(
        key = "standard",
        label = "标准码率",
        shortLabel = "标准",
        isEnabled = false
    ),
    Boost(
        key = "boost",
        label = "提高直播码率",
        shortLabel = "高码",
        isEnabled = true
    );

    companion object {
        fun fromKey(key: String?): LiveBitrateBoostMode {
            return entries.firstOrNull { it.key == key } ?: Standard
        }
    }
}

enum class LiveAudioBalanceMode(
    val key: String,
    val label: String,
    val shortLabel: String,
    val balance: Float,
) {
    Center(
        key = "center",
        label = "音频平衡：居中",
        shortLabel = "居中",
        balance = 0f
    ),
    Left(
        key = "left",
        label = "音频平衡：偏左",
        shortLabel = "偏左",
        balance = -0.45f
    ),
    Right(
        key = "right",
        label = "音频平衡：偏右",
        shortLabel = "偏右",
        balance = 0.45f
    );

    companion object {
        fun fromKey(key: String?): LiveAudioBalanceMode {
            return entries.firstOrNull { it.key == key } ?: Center
        }
    }
}

data class LivePlayerCoreOption(
    val key: String,
    val label: String,
    val shortLabel: String,
    val subtitle: String? = null,
)

val LIVE_PLAYER_CORE_OPTIONS = listOf(
    LivePlayerCoreOption(
        key = LIVE_PLAYER_CORE_MEDIA3_KEY,
        label = "Media3 ExoPlayer",
        shortLabel = "Media3",
        subtitle = "当前工程仅集成 Media3 内核。"
    )
)

private val DEFAULT_LIVE_BITRATE_MODE = LiveBitrateBoostMode.Standard
private val DEFAULT_LIVE_AUDIO_BALANCE = LiveAudioBalanceMode.Center
private val DEFAULT_LIVE_PLAYER_CORE = LIVE_PLAYER_CORE_OPTIONS.first()

data class LivePlayerPlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playerState: Int = Player.STATE_IDLE,
    val positionMs: Long = 0L,
)

data class LivePlayerUiState(
    val roomId: Long = 0L,
    val realRoomId: Long = 0L,
    val title: String = "",
    val anchorName: String = "",
    val cover: String = "",
    val areaName: String = "",
    val onlineText: String = "",
    val qualityOptions: List<LiveQualityOption> = emptyList(),
    val selectedQuality: Int = 0,
    val selectedQualityLabel: String = "",
    val lineOptions: List<LiveLineOption> = emptyList(),
    val selectedLineKey: String = "",
    val selectedLineLabel: String = "",
    val bitrateModeKey: String = DEFAULT_LIVE_BITRATE_MODE.key,
    val bitrateModeLabel: String = DEFAULT_LIVE_BITRATE_MODE.shortLabel,
    val audioBalanceKey: String = DEFAULT_LIVE_AUDIO_BALANCE.key,
    val audioBalanceLabel: String = DEFAULT_LIVE_AUDIO_BALANCE.shortLabel,
    val playerCoreKey: String = DEFAULT_LIVE_PLAYER_CORE.key,
    val playerCoreLabel: String = DEFAULT_LIVE_PLAYER_CORE.shortLabel,
    val streamProtocolLabel: String = "",
    val streamFormatLabel: String = "",
    val streamCodecLabel: String = "",
    val streamHostLabel: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val streamUrl: String = "",
)

private data class LiveStreamCandidate(
    val key: String,
    val lineKey: String,
    val lineBaseLabel: String,
    val lineSubtitle: String?,
    val protocolName: String,
    val protocolLabel: String,
    val formatName: String,
    val formatLabel: String,
    val codecName: String,
    val codecLabel: String,
    val currentQn: Int,
    val host: String,
    val hostLabel: String,
    val url: String,
)

private data class LiveStreamLoadResult(
    val streamUrl: String,
    val qualityOptions: List<LiveQualityOption>,
    val selectedQuality: Int,
    val selectedQualityLabel: String,
    val lineOptions: List<LiveLineOption>,
    val selectedLineKey: String,
    val selectedLineLabel: String,
    val selectedCandidate: LiveStreamCandidate,
    val candidates: List<LiveStreamCandidate>,
)

private data class LivePlaybackRuntimeState(
    val roomId: Long = 0L,
    val realRoomId: Long = 0L,
    val sessionStarted: Boolean = false,
    val streamCandidates: List<LiveStreamCandidate> = emptyList(),
)

class LivePlayerViewModel : BasePlayerViewModel() {
    private val _uiState = MutableStateFlow(LivePlayerUiState())
    val uiState: StateFlow<LivePlayerUiState> = _uiState.asStateFlow()

    private val _playbackState = MutableStateFlow(LivePlayerPlaybackState())
    val playbackState: StateFlow<LivePlayerPlaybackState> = _playbackState.asStateFlow()

    private var runtimeState = LivePlaybackRuntimeState()
    private var liveLoadJob: Job? = null
    private var playerPollingJob: Job? = null
    private var statusClearJob: Job? = null
    private var danmakuCollectJob: Job? = null
    private var danmakuPublishJob: Job? = null
    private var liveDanmakuClient: LiveDanmakuClient? = null
    private var liveDanmakuSessionId: Long = 0L
    private var liveDanmakuRoomId: Long = 0L
    private val liveDanmakuBuffer = ArrayDeque<WeightedTextData>()
    private var lastLiveDanmakuShowAtMs: Long = 0L
    private var liveDanmakuSequenceId: Long = 1L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            ensureMainThread("LivePlayer.Listener.onIsPlayingChanged")
            refreshPlaybackState()
            if (isPlaying) {
                CrashReporter.markLivePlaybackStage("playing")
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            ensureMainThread("LivePlayer.Listener.onPlaybackStateChanged")
            refreshPlaybackState()
            when (playbackState) {
                Player.STATE_BUFFERING -> CrashReporter.markLivePlaybackStage("buffering")
                Player.STATE_READY -> CrashReporter.markLivePlaybackStage("ready")
                Player.STATE_ENDED -> CrashReporter.markLivePlaybackStage("ended")
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            ensureMainThread("LivePlayer.Listener.onPlayerError")
            val roomId = _uiState.value.realRoomId.takeIf { it > 0L } ?: runtimeState.roomId
            CrashReporter.markLivePlaybackStage("player_error")
            CrashReporter.reportLiveError(
                roomId = roomId,
                errorType = "player_error",
                errorMessage = error.message ?: "直播播放失败",
                exception = error
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "直播播放失败"
                )
            }
        }
    }

    @MainThread
    override fun attachPlayer(player: ExoPlayer) {
        ensureMainThread("LivePlayerViewModel.attachPlayer")
        if (exoPlayer === player) return
        exoPlayer?.removeListener(playerListener)
        super.attachPlayer(player)
        player.addListener(playerListener)
        applyAudioBalance(LiveAudioBalanceMode.fromKey(_uiState.value.audioBalanceKey))
        startPlayerPolling()
        refreshPlaybackState()
    }

    @MainThread
    fun loadLive(roomId: Long, force: Boolean = false) {
        ensureMainThread("loadLive")
        if (roomId <= 0L) {
            _uiState.update {
                LivePlayerUiState(
                    roomId = roomId,
                    isLoading = false,
                    errorMessage = "直播间号无效"
                )
            }
            return
        }
        if (!force && runtimeState.roomId == roomId && _uiState.value.streamUrl.isNotBlank()) {
            return
        }

        val isNewRoom = runtimeState.roomId != roomId
        val bitrateMode = if (isNewRoom) {
            DEFAULT_LIVE_BITRATE_MODE
        } else {
            LiveBitrateBoostMode.fromKey(_uiState.value.bitrateModeKey)
        }
        val audioBalanceMode = if (isNewRoom) {
            DEFAULT_LIVE_AUDIO_BALANCE
        } else {
            LiveAudioBalanceMode.fromKey(_uiState.value.audioBalanceKey)
        }
        val preferredLineKey = if (isNewRoom) {
            null
        } else {
            _uiState.value.selectedLineKey.takeIf { it.isNotBlank() }
        }

        if (isNewRoom) {
            applyAudioBalance(audioBalanceMode)
        }
        if (runtimeState.sessionStarted) {
            finishSession(reason = "reload")
        }
        liveLoadJob?.cancel()
        stopLiveDanmaku(clearPayload = true)
        runtimeState = LivePlaybackRuntimeState(roomId = roomId)
        _uiState.update {
            LivePlayerUiState(
                roomId = roomId,
                isLoading = true,
                bitrateModeKey = bitrateMode.key,
                bitrateModeLabel = bitrateMode.shortLabel,
                audioBalanceKey = audioBalanceMode.key,
                audioBalanceLabel = audioBalanceMode.shortLabel,
                playerCoreKey = DEFAULT_LIVE_PLAYER_CORE.key,
                playerCoreLabel = DEFAULT_LIVE_PLAYER_CORE.shortLabel,
            )
        }
        CrashReporter.markLivePlaybackStage("load_request")

        liveLoadJob = viewModelScope.launch {
            val detailTask = async { LiveRepository.getLiveRoomDetail(roomId) }
            val streamTask = async {
                loadStreamForQuality(
                    roomId = roomId,
                    preferredQuality = 10_000,
                    preferredLineKey = preferredLineKey,
                    preferHighBitrate = bitrateMode.isEnabled
                )
            }

            val detailResult = detailTask.await()
            val streamResult = streamTask.await()

            if (streamResult.isFailure) {
                val error = streamResult.exceptionOrNull()
                CrashReporter.reportLiveError(
                    roomId = roomId,
                    errorType = "no_play_url",
                    errorMessage = error?.message ?: "无法获取直播流",
                    exception = error
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error?.message ?: "无法获取直播流"
                    )
                }
                return@launch
            }

            val detail = detailResult.getOrNull()
            var roomInfo = detail?.roomInfo
            var anchorInfo = detail?.anchorInfo?.baseInfo
            var detailData = detail

            if (detail == null || anchorInfo == null) {
                com.bbttvv.app.core.util.Logger.w("LivePlayerVM", "🔴 LiveRoomDetail failed or empty. Starting Fallback...")
                try {
                    val roomInfoResp = NetworkModule.api.getRoomInfo(roomId)
                    if (roomInfoResp.code == 0 && roomInfoResp.data != null) {
                        val basicInfo = roomInfoResp.data
                        val fallbackUid = basicInfo.uid
                        
                        roomInfo = com.bbttvv.app.data.model.response.LiveRoomInfo(
                            roomId = basicInfo.room_id.takeIf { it > 0L } ?: roomId,
                            title = basicInfo.title,
                            online = basicInfo.online,
                            liveStatus = basicInfo.liveStatus,
                            areaName = basicInfo.areaName
                        )
                        
                        if (fallbackUid > 0) {
                            val cardResp = NetworkModule.api.getUserCard(fallbackUid)
                            if (cardResp.code == 0 && cardResp.data?.card != null) {
                                val card = cardResp.data.card
                                anchorInfo = com.bbttvv.app.data.model.response.LiveAnchorBaseInfo(
                                    uname = card.name,
                                    face = card.face
                                )
                                detailData = com.bbttvv.app.data.model.response.LiveRoomDetailData(
                                    roomInfo = roomInfo,
                                    anchorInfo = com.bbttvv.app.data.model.response.LiveAnchorInfo(baseInfo = anchorInfo)
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val stream = streamResult.getOrThrow()
            val resolvedRoomId = roomInfo?.roomId?.takeIf { it > 0L } ?: roomId
            val title = resolveDisplayText(roomInfo?.title) ?: "直播间 $resolvedRoomId"
            val anchorName = resolveDisplayText(anchorInfo?.uname).orEmpty()

            runtimeState = runtimeState.copy(
                realRoomId = resolvedRoomId,
                streamCandidates = stream.candidates
            )
            _uiState.update {
                LivePlayerUiState(
                    roomId = roomId,
                    realRoomId = resolvedRoomId,
                    title = title,
                    anchorName = anchorName,
                    cover = resolveDisplayText(roomInfo?.cover).orEmpty(),
                    areaName = listOf(
                        resolveDisplayText(roomInfo?.parentAreaName),
                        resolveDisplayText(roomInfo?.areaName)
                    )
                        .filterNotNull()
                        .joinToString(" / "),
                    onlineText = formatOnlineText(detailData),
                    qualityOptions = stream.qualityOptions,
                    selectedQuality = stream.selectedQuality,
                    selectedQualityLabel = stream.selectedQualityLabel,
                    lineOptions = stream.lineOptions,
                    selectedLineKey = stream.selectedLineKey,
                    selectedLineLabel = stream.selectedLineLabel,
                    bitrateModeKey = bitrateMode.key,
                    bitrateModeLabel = bitrateMode.shortLabel,
                    audioBalanceKey = audioBalanceMode.key,
                    audioBalanceLabel = audioBalanceMode.shortLabel,
                    playerCoreKey = DEFAULT_LIVE_PLAYER_CORE.key,
                    playerCoreLabel = DEFAULT_LIVE_PLAYER_CORE.shortLabel,
                    streamProtocolLabel = stream.selectedCandidate.protocolLabel,
                    streamFormatLabel = stream.selectedCandidate.formatLabel,
                    streamCodecLabel = stream.selectedCandidate.codecLabel,
                    streamHostLabel = stream.selectedCandidate.hostLabel,
                    isLoading = false,
                    errorMessage = null,
                    streamUrl = stream.streamUrl
                )
            }

            if (!runtimeState.sessionStarted) {
                CrashReporter.markLiveSessionStart(
                    roomId = resolvedRoomId,
                    title = title,
                    uname = anchorName.ifBlank { "unknown" }
                )
                runtimeState = runtimeState.copy(sessionStarted = true)
            }
            CrashReporter.markLivePlaybackStage("source_ready")
            playStreamingUrl(
                url = stream.streamUrl,
                referer = "https://live.bilibili.com/$resolvedRoomId",
                resetPlayer = true,
                playWhenReady = true
            )
            refreshPlaybackState()
            if (isDanmakuEnabled.value) {
                connectLiveDanmakuIfNeeded(resolvedRoomId)
            }
        }
    }

    @MainThread
    fun changeQuality(qualityId: Int) {
        ensureMainThread("changeQuality")
        val roomId = runtimeState.roomId.takeIf { it > 0L } ?: return
        if (qualityId <= 0) return
        if (qualityId == _uiState.value.selectedQuality && _uiState.value.streamUrl.isNotBlank()) {
            postStatus("清晰度：${_uiState.value.selectedQualityLabel.ifBlank { qualityId.toString() }}")
            return
        }

        liveLoadJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                statusMessage = "正在切换清晰度..."
            )
        }

        liveLoadJob = viewModelScope.launch {
            val streamResult = loadStreamForQuality(
                roomId = roomId,
                preferredQuality = qualityId,
                preferredLineKey = _uiState.value.selectedLineKey.takeIf { it.isNotBlank() },
                preferHighBitrate = LiveBitrateBoostMode.fromKey(_uiState.value.bitrateModeKey).isEnabled
            )
            if (streamResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = streamResult.exceptionOrNull()?.message ?: "清晰度切换失败"
                    )
                }
                return@launch
            }

            val stream = streamResult.getOrThrow()
            applyResolvedStream(
                stream = stream,
                statusMessage = "清晰度：${stream.selectedQualityLabel}"
            )
        }
    }

    @MainThread
    fun changeLine(lineKey: String) {
        ensureMainThread("changeLine")
        val line = _uiState.value.lineOptions.firstOrNull { it.key == lineKey } ?: return
        if (lineKey == _uiState.value.selectedLineKey && _uiState.value.streamUrl.isNotBlank()) {
            postStatus("线路：${line.label}")
            return
        }
        applyStreamSelection(
            preferredLineKey = lineKey,
            preferHighBitrate = LiveBitrateBoostMode.fromKey(_uiState.value.bitrateModeKey).isEnabled,
            statusMessage = "线路：${line.label}"
        )
    }

    @MainThread
    fun changeBitrateMode(modeKey: String) {
        ensureMainThread("changeBitrateMode")
        val mode = LiveBitrateBoostMode.fromKey(modeKey)
        if (mode.key == _uiState.value.bitrateModeKey) {
            postStatus("直播码率：${mode.label}")
            return
        }
        _uiState.update {
            it.copy(
                bitrateModeKey = mode.key,
                bitrateModeLabel = mode.shortLabel
            )
        }
        applyStreamSelection(
            preferredLineKey = _uiState.value.selectedLineKey.takeIf { it.isNotBlank() },
            preferHighBitrate = mode.isEnabled,
            statusMessage = "直播码率：${mode.label}"
        )
    }

    @MainThread
    fun changeAudioBalance(modeKey: String) {
        ensureMainThread("changeAudioBalance")
        val mode = LiveAudioBalanceMode.fromKey(modeKey)
        if (mode.key == _uiState.value.audioBalanceKey) {
            postStatus(mode.label)
            return
        }
        applyAudioBalance(mode)
        _uiState.update {
            it.copy(
                audioBalanceKey = mode.key,
                audioBalanceLabel = mode.shortLabel
            )
        }
        postStatus(mode.label)
    }

    @MainThread
    fun changePlayerCore(coreKey: String) {
        ensureMainThread("changePlayerCore")
        val selectedCore = LIVE_PLAYER_CORE_OPTIONS.firstOrNull { it.key == coreKey } ?: DEFAULT_LIVE_PLAYER_CORE
        _uiState.update {
            it.copy(
                playerCoreKey = selectedCore.key,
                playerCoreLabel = selectedCore.shortLabel
            )
        }
        postStatus("播放器内核：${selectedCore.label}")
    }

    @MainThread
    fun togglePlayback() {
        ensureMainThread("togglePlayback")
        val engine = playerEngine ?: return
        if (engine.isPlaying) {
            engine.pause()
        } else {
            engine.play()
        }
        refreshPlaybackState()
    }

    @MainThread
    fun toggleDanmaku() {
        ensureMainThread("toggleDanmaku")
        val wasEnabled = isDanmakuEnabled.value
        toggleDanmakuEnabled()
        if (wasEnabled) {
            stopLiveDanmaku(clearPayload = true)
            postStatus("弹幕已关闭")
        } else {
            postStatus("弹幕已开启")
            connectLiveDanmakuIfNeeded(runtimeState.realRoomId.takeIf { it > 0L } ?: runtimeState.roomId)
        }
    }

    @MainThread
    fun retry() {
        ensureMainThread("retry")
        loadLive(runtimeState.roomId, force = true)
    }

    @MainThread
    fun clearStatusMessage() {
        ensureMainThread("clearStatusMessage")
        if (_uiState.value.statusMessage == null) return
        _uiState.update { it.copy(statusMessage = null) }
    }

    @MainThread
    fun resetTransientPlaybackTuning() {
        ensureMainThread("resetTransientPlaybackTuning")
        applyAudioBalance(DEFAULT_LIVE_AUDIO_BALANCE)
    }

    @MainThread
    fun finishSession(reason: String) {
        ensureMainThread("finishSession")
        stopLiveDanmaku(clearPayload = true)
        if (!runtimeState.sessionStarted) return
        runtimeState = runtimeState.copy(sessionStarted = false)
        CrashReporter.markLiveSessionEnd(reason)
    }

    override fun onCleared() {
        ensureMainThread("LivePlayerViewModel.onCleared")
        liveLoadJob?.cancel()
        playerPollingJob?.cancel()
        statusClearJob?.cancel()
        stopLiveDanmaku(clearPayload = true)
        exoPlayer?.removeListener(playerListener)
        resetTransientPlaybackTuning()
        finishSession(reason = "viewmodel_cleared")
        super.onCleared()
    }

    private fun startPlayerPolling() {
        ensureMainThread("startPlayerPolling")
        playerPollingJob?.cancel()
        playerPollingJob = viewModelScope.launch {
            while (isActive) {
                refreshPlaybackState()
                delay(300L)
            }
        }
    }

    private fun refreshPlaybackState() {
        ensureMainThread("refreshPlaybackState")
        val player = exoPlayer
        _playbackState.value = if (player == null) {
            LivePlayerPlaybackState()
        } else {
            LivePlayerPlaybackState(
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                playerState = player.playbackState,
                positionMs = player.currentPosition.coerceAtLeast(0L)
            )
        }
    }

    private fun applyAudioBalance(mode: LiveAudioBalanceMode) {
        ensureMainThread("applyAudioBalance")
        PlayerAudioBalanceController.setBalance(mode.balance)
    }

    private fun applyStreamSelection(
        preferredLineKey: String?,
        preferHighBitrate: Boolean,
        statusMessage: String,
    ) {
        ensureMainThread("applyStreamSelection")
        val currentCandidates = runtimeState.streamCandidates
        val currentState = _uiState.value
        val selectedQualityLabel = currentState.selectedQualityLabel.ifBlank {
            currentState.qualityOptions.firstOrNull { it.qn == currentState.selectedQuality }?.label ?: "自动"
        }
        val localResult = buildStreamLoadResultFromCandidates(
            candidates = currentCandidates,
            qualityOptions = currentState.qualityOptions,
            selectedQuality = currentState.selectedQuality,
            selectedQualityLabel = selectedQualityLabel,
            preferredLineKey = preferredLineKey,
            preferHighBitrate = preferHighBitrate
        )
        if (localResult != null) {
            applyResolvedStream(localResult, statusMessage = statusMessage)
            return
        }
        reloadCurrentQuality(
            preferredLineKey = preferredLineKey,
            preferHighBitrate = preferHighBitrate,
            statusMessage = statusMessage
        )
    }

    private fun reloadCurrentQuality(
        preferredLineKey: String?,
        preferHighBitrate: Boolean,
        statusMessage: String,
    ) {
        ensureMainThread("reloadCurrentQuality")
        val roomId = runtimeState.roomId.takeIf { it > 0L } ?: return
        val qualityId = _uiState.value.selectedQuality.takeIf { it > 0 } ?: 10_000
        liveLoadJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null
            )
        }
        liveLoadJob = viewModelScope.launch {
            val streamResult = loadStreamForQuality(
                roomId = roomId,
                preferredQuality = qualityId,
                preferredLineKey = preferredLineKey,
                preferHighBitrate = preferHighBitrate
            )
            if (streamResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = streamResult.exceptionOrNull()?.message ?: "直播流切换失败"
                    )
                }
                return@launch
            }
            applyResolvedStream(
                stream = streamResult.getOrThrow(),
                statusMessage = statusMessage
            )
        }
    }

    private fun applyResolvedStream(
        stream: LiveStreamLoadResult,
        statusMessage: String? = null,
    ) {
        ensureMainThread("applyResolvedStream")
        runtimeState = runtimeState.copy(streamCandidates = stream.candidates)
        val refererRoomId = runtimeState.realRoomId.takeIf { it > 0L } ?: runtimeState.roomId
        resetLiveDanmakuBuffer(clearPayload = true)
        playStreamingUrl(
            url = stream.streamUrl,
            referer = "https://live.bilibili.com/$refererRoomId",
            resetPlayer = true,
            playWhenReady = true
        )
        _uiState.update {
            it.copy(
                qualityOptions = stream.qualityOptions,
                selectedQuality = stream.selectedQuality,
                selectedQualityLabel = stream.selectedQualityLabel,
                lineOptions = stream.lineOptions,
                selectedLineKey = stream.selectedLineKey,
                selectedLineLabel = stream.selectedLineLabel,
                streamProtocolLabel = stream.selectedCandidate.protocolLabel,
                streamFormatLabel = stream.selectedCandidate.formatLabel,
                streamCodecLabel = stream.selectedCandidate.codecLabel,
                streamHostLabel = stream.selectedCandidate.hostLabel,
                isLoading = false,
                errorMessage = null,
                statusMessage = statusMessage,
                streamUrl = stream.streamUrl
            )
        }
        refreshPlaybackState()
        if (!statusMessage.isNullOrBlank()) {
            scheduleStatusAutoClear()
        }
    }

    private suspend fun loadStreamForQuality(
        roomId: Long,
        preferredQuality: Int,
        preferredLineKey: String?,
        preferHighBitrate: Boolean,
    ): Result<LiveStreamLoadResult> = withContext(Dispatchers.IO) {
        val dataResult = LiveRepository.getLivePlayUrlWithQuality(roomId = roomId, qn = preferredQuality)
        dataResult.mapCatching { data ->
            resolveStreamLoadResult(
                data = data,
                preferredLineKey = preferredLineKey,
                preferHighBitrate = preferHighBitrate
            )
        }
    }

    private fun resolveStreamLoadResult(
        data: LivePlayUrlData,
        preferredLineKey: String?,
        preferHighBitrate: Boolean,
    ): LiveStreamLoadResult {
        val qualityOptions = resolveQualityOptions(data)
        val selectedQuality = resolveSelectedQuality(data, qualityOptions)
        val selectedQualityLabel = qualityOptions.firstOrNull { it.qn == selectedQuality }?.label
            ?: selectedQuality.takeIf { it > 0 }?.toString()
            ?: "自动"
        val candidates = resolveStreamCandidates(data)
        return buildStreamLoadResultFromCandidates(
            candidates = candidates,
            qualityOptions = qualityOptions,
            selectedQuality = selectedQuality,
            selectedQualityLabel = selectedQualityLabel,
            preferredLineKey = preferredLineKey,
            preferHighBitrate = preferHighBitrate
        ) ?: throw IllegalStateException("无法解析直播流地址")
    }

    private fun buildStreamLoadResultFromCandidates(
        candidates: List<LiveStreamCandidate>,
        qualityOptions: List<LiveQualityOption>,
        selectedQuality: Int,
        selectedQualityLabel: String,
        preferredLineKey: String?,
        preferHighBitrate: Boolean,
    ): LiveStreamLoadResult? {
        if (candidates.isEmpty()) return null
        val lineOptions = resolveLineOptions(candidates)
        val selectedCandidate = selectStreamCandidate(
            candidates = candidates,
            preferredLineKey = preferredLineKey,
            preferHighBitrate = preferHighBitrate
        ) ?: return null
        val selectedLineLabel = lineOptions.firstOrNull { it.key == selectedCandidate.lineKey }?.label
            ?: selectedCandidate.lineBaseLabel
        return LiveStreamLoadResult(
            streamUrl = selectedCandidate.url,
            qualityOptions = qualityOptions,
            selectedQuality = selectedQuality,
            selectedQualityLabel = selectedQualityLabel,
            lineOptions = lineOptions,
            selectedLineKey = selectedCandidate.lineKey,
            selectedLineLabel = selectedLineLabel,
            selectedCandidate = selectedCandidate,
            candidates = candidates
        )
    }

    private fun resolveStreamCandidates(data: LivePlayUrlData): List<LiveStreamCandidate> {
        val playInfoCandidates = buildList {
            data.playurl_info?.playurl?.stream.orEmpty().forEach { stream ->
                stream.format.orEmpty().forEach { format ->
                    format.codec.orEmpty().forEach { codec ->
                        if (format.formatName == "flv" && codec.codecName.lowercase() == "hevc") return@forEach
                        codec.url_info.orEmpty().forEach { urlInfo ->
                            val host = urlInfo.host.trim()
                            val url = (host + codec.baseUrl + urlInfo.extra).trim()
                            if (url.isBlank()) return@forEach
                            val hostLabel = host
                                .removePrefix("https://")
                                .removePrefix("http://")
                                .substringBefore('/')
                                .substringBefore('?')
                                .ifBlank { "default" }
                            add(
                                LiveStreamCandidate(
                                    key = listOf(
                                        stream.protocolName,
                                        format.formatName,
                                        codec.codecName,
                                        hostLabel,
                                        codec.baseUrl
                                    ).joinToString("|"),
                                    lineKey = hostLabel.lowercase(),
                                    lineBaseLabel = resolveLineBaseLabel(hostLabel),
                                    lineSubtitle = hostLabel,
                                    protocolName = stream.protocolName,
                                    protocolLabel = resolveProtocolLabel(stream.protocolName),
                                    formatName = format.formatName,
                                    formatLabel = resolveFormatLabel(format.formatName),
                                    codecName = codec.codecName,
                                    codecLabel = resolveCodecLabel(codec.codecName),
                                    currentQn = codec.currentQn,
                                    host = host,
                                    hostLabel = hostLabel,
                                    url = url
                                )
                            )
                        }
                    }
                }
            }
        }
        if (playInfoCandidates.isNotEmpty()) {
            return playInfoCandidates.distinctBy { it.key }
        }

        return data.durl.orEmpty()
            .mapIndexedNotNull { index, durl ->
                durl.url.takeIf { it.isNotBlank() }?.let { url ->
                    LiveStreamCandidate(
                        key = "legacy|$index|${durl.order}",
                        lineKey = "legacy",
                        lineBaseLabel = "默认线路",
                        lineSubtitle = "旧接口",
                        protocolName = "legacy",
                        protocolLabel = "legacy",
                        formatName = "legacy",
                        formatLabel = "legacy",
                        codecName = "legacy",
                        codecLabel = "Legacy",
                        currentQn = data.current_quality,
                        host = url,
                        hostLabel = "legacy",
                        url = url
                    )
                }
            }
    }

    private fun resolveLineOptions(candidates: List<LiveStreamCandidate>): List<LiveLineOption> {
        val grouped = LinkedHashMap<String, LiveStreamCandidate>()
        candidates.forEach { candidate ->
            grouped.putIfAbsent(candidate.lineKey, candidate)
        }
        val labelCount = mutableMapOf<String, Int>()
        return grouped.values.map { candidate ->
            val baseLabel = candidate.lineBaseLabel.ifBlank { "线路" }
            val nextIndex = (labelCount[baseLabel] ?: 0) + 1
            labelCount[baseLabel] = nextIndex
            val label = if (nextIndex == 1) baseLabel else "$baseLabel $nextIndex"
            LiveLineOption(
                key = candidate.lineKey,
                label = label,
                subtitle = candidate.lineSubtitle
            )
        }
    }

    private fun selectStreamCandidate(
        candidates: List<LiveStreamCandidate>,
        preferredLineKey: String?,
        preferHighBitrate: Boolean,
    ): LiveStreamCandidate? {
        val scopedCandidates = preferredLineKey
            ?.takeIf { key -> candidates.any { it.lineKey == key } }
            ?.let { key -> candidates.filter { it.lineKey == key } }
            ?: candidates
        val cdnPreference = resolvePlayerCdnPreference()
        return scopedCandidates.maxWithOrNull(
            compareByDescending<LiveStreamCandidate> {
                candidateScore(
                    candidate = it,
                    preferHighBitrate = preferHighBitrate,
                    cdnPreference = cdnPreference
                )
            }
                .thenByDescending { it.currentQn }
                .thenBy { it.hostLabel }
        )
    }

    private fun candidateScore(
        candidate: LiveStreamCandidate,
        preferHighBitrate: Boolean,
        cdnPreference: SettingsManager.PlayerCdnPreference,
    ): Int {
        val host = candidate.hostLabel.lowercase()
        val matchesPreferredCdn = when (cdnPreference) {
            SettingsManager.PlayerCdnPreference.BILIVIDEO -> {
                host.contains("bilivideo") || host.contains("upos") || host.contains("cn-")
            }
            SettingsManager.PlayerCdnPreference.MCDN -> host.contains("mcdn")
        }
        val protocolScore = when (candidate.protocolName) {
            "http_hls" -> if (preferHighBitrate) 120 else 220
            "http_stream" -> if (preferHighBitrate) 220 else 140
            else -> 80
        }
        val formatScore = when (candidate.formatName) {
            "ts" -> if (preferHighBitrate) 240 else 200
            "flv" -> if (preferHighBitrate) 220 else 160
            "fmp4" -> if (preferHighBitrate) 160 else 190
            else -> 100
        }
        val codecScore = when (candidate.codecName.lowercase()) {
            "hevc" -> if (preferHighBitrate) 200 else 120
            "avc" -> if (preferHighBitrate) 150 else 190
            else -> 110
        }
        return candidate.currentQn * 1000 +
            protocolScore +
            formatScore +
            codecScore +
            if (matchesPreferredCdn) 300 else 0
    }

    private fun resolveQualityOptions(data: LivePlayUrlData): List<LiveQualityOption> {
        val supportedQns = mutableSetOf<Int>()
        data.playurl_info?.playurl?.stream?.forEach { stream ->
            stream.format?.forEach { format ->
                format.codec?.forEach { codec ->
                    codec.acceptQn?.let { supportedQns.addAll(it) }
                }
            }
        }

        var options = (data.quality_description.orEmpty() + data.playurl_info?.playurl?.gQnDesc.orEmpty())
            .filter { it.qn > 0 }
            .distinctBy { it.qn }

        if (supportedQns.isNotEmpty()) {
            options = options.filter { supportedQns.contains(it.qn) }
        }

        return options.sortedByDescending { it.qn }
            .map { quality ->
                LiveQualityOption(
                    qn = quality.qn,
                    label = quality.desc.ifBlank { fallbackQualityLabel(quality) }
                )
            }
    }

    private fun resolveSelectedQuality(
        data: LivePlayUrlData,
        options: List<LiveQualityOption>,
    ): Int {
        val fromResponse = data.current_quality.takeIf { it > 0 }
        if (fromResponse != null) return fromResponse

        val fromCodec = data.playurl_info?.playurl?.stream
            ?.flatMap { it.format.orEmpty() }
            ?.flatMap { it.codec.orEmpty() }
            ?.firstOrNull { it.currentQn > 0 }
            ?.currentQn
        if (fromCodec != null && fromCodec > 0) return fromCodec

        return options.firstOrNull()?.qn ?: 0
    }

    private fun fallbackQualityLabel(quality: LiveQuality): String {
        return when (quality.qn) {
            80 -> "流畅"
            150 -> "高清"
            250 -> "超清"
            400 -> "蓝光"
            10000 -> "原画"
            else -> quality.qn.toString()
        }
    }

    private fun resolvePlayerCdnPreference(): SettingsManager.PlayerCdnPreference {
        val appContext = NetworkModule.appContext ?: return SettingsManager.PlayerCdnPreference.BILIVIDEO
        return SettingsManager.getPlayerCdnPreferenceSync(appContext)
    }

    private fun connectLiveDanmakuIfNeeded(roomId: Long) {
        ensureMainThread("connectLiveDanmakuIfNeeded")
        if (roomId <= 0L || !isDanmakuEnabled.value) return
        if (liveDanmakuClient?.isConnected == true && liveDanmakuRoomId == roomId) return

        val shouldClearPayload = liveDanmakuRoomId > 0L && liveDanmakuRoomId != roomId
        stopLiveDanmaku(clearPayload = shouldClearPayload)
        val sessionId = ++liveDanmakuSessionId
        liveDanmakuRoomId = roomId
        danmakuCollectJob = viewModelScope.launch {
            var collectedClient: LiveDanmakuClient? = null
            var stateJob: Job? = null
            try {
                val result = DanmakuRepository.startLiveDanmaku(
                    scope = viewModelScope,
                    roomId = roomId
                )
                val client = result.getOrNull()
                collectedClient = client
                ensureActive()
                if (client != null && isLiveDanmakuSessionActive(sessionId, roomId)) {
                    liveDanmakuClient = client
                    stateJob = launch {
                        client.connectionState.collect { state ->
                            handleLiveDanmakuConnectionState(
                                state = state,
                                roomId = roomId,
                                sessionId = sessionId,
                            )
                        }
                    }
                    client.messageFlow.collect { packet ->
                        if (isLiveDanmakuSessionActive(sessionId, roomId)) {
                            handleLiveDanmakuPacket(packet)
                        }
                    }
                } else if (isLiveDanmakuSessionActive(sessionId, roomId)) {
                    val error = result.exceptionOrNull()
                    liveDanmakuClient = null
                    CrashReporter.reportLiveError(
                        roomId = roomId,
                        errorType = "danmaku_connect",
                        errorMessage = error?.message ?: "直播弹幕连接失败",
                        exception = error
                    )
                    postStatus("直播弹幕连接失败")
                }
            } finally {
                stateJob?.cancel()
                collectedClient?.let { client ->
                    if (liveDanmakuClient === client) {
                        liveDanmakuClient = null
                    }
                    client.release()
                }
            }
        }
    }

    private fun isLiveDanmakuSessionActive(sessionId: Long, roomId: Long): Boolean {
        return liveDanmakuSessionId == sessionId &&
            liveDanmakuRoomId == roomId &&
            isDanmakuEnabled.value
    }

    private fun handleLiveDanmakuConnectionState(
        state: LiveDanmakuConnectionState,
        roomId: Long,
        sessionId: Long,
    ) {
        ensureMainThread("handleLiveDanmakuConnectionState")
        if (!isLiveDanmakuSessionActive(sessionId, roomId)) return
        when (state) {
            LiveDanmakuConnectionState.AUTHENTICATED -> postStatus("弹幕已连接")
            LiveDanmakuConnectionState.RECONNECTING -> postStatus("弹幕重连中...")
            LiveDanmakuConnectionState.FAILED -> {
                CrashReporter.reportLiveError(
                    roomId = roomId,
                    errorType = "danmaku_connect",
                    errorMessage = "直播弹幕连接失败",
                )
                postStatus("直播弹幕连接失败")
            }
            else -> Unit
        }
    }

    private fun stopLiveDanmaku(clearPayload: Boolean) {
        ensureMainThread("stopLiveDanmaku")
        liveDanmakuSessionId += 1L
        liveDanmakuRoomId = 0L
        danmakuCollectJob?.cancel()
        danmakuCollectJob = null
        danmakuPublishJob?.cancel()
        danmakuPublishJob = null
        liveDanmakuClient?.release()
        liveDanmakuClient = null
        resetLiveDanmakuBuffer(clearPayload = clearPayload)
    }

    private fun resetLiveDanmakuBuffer(clearPayload: Boolean) {
        ensureMainThread("resetLiveDanmakuBuffer")
        liveDanmakuBuffer.clear()
        lastLiveDanmakuShowAtMs = 0L
        liveDanmakuSequenceId = 1L
        if (clearPayload) {
            clearDanmaku()
        }
    }

    private fun handleLiveDanmakuPacket(packet: DanmakuProtocol.Packet) {
        ensureMainThread("handleLiveDanmakuPacket")
        if (!isDanmakuEnabled.value) return
        val item = parseLiveDanmaku(packet.body) ?: return
        val currentPosition = max(_playbackState.value.positionMs, getPlayerCurrentPosition())
        val showAtTime = max(
            currentPosition + LIVE_DANMAKU_SHOW_LEAD_MS,
            lastLiveDanmakuShowAtMs + LIVE_DANMAKU_MIN_GAP_MS
        )
        item.showAtTime = showAtTime
        lastLiveDanmakuShowAtMs = showAtTime

        liveDanmakuBuffer.addLast(item)
        trimLiveDanmakuBuffer(nowPositionMs = currentPosition)
        scheduleDanmakuPublish()
    }

    private fun trimLiveDanmakuBuffer(nowPositionMs: Long) {
        ensureMainThread("trimLiveDanmakuBuffer")
        val minShowAt = (nowPositionMs - LIVE_DANMAKU_RETENTION_MS).coerceAtLeast(0L)
        while (liveDanmakuBuffer.isNotEmpty() && liveDanmakuBuffer.first().showAtTime < minShowAt) {
            liveDanmakuBuffer.removeFirst()
        }
        while (liveDanmakuBuffer.size > LIVE_DANMAKU_MAX_COUNT) {
            liveDanmakuBuffer.removeFirst()
        }
    }

    private fun scheduleDanmakuPublish() {
        ensureMainThread("scheduleDanmakuPublish")
        if (danmakuPublishJob?.isActive == true) return
        danmakuPublishJob = viewModelScope.launch {
            delay(120L)
            val parsed = ParsedDanmaku(
                standardList = liveDanmakuBuffer.toList(),
                advancedList = emptyList()
            )
            publishDanmakuPayload(
                parsed = parsed,
                sourceLabel = LIVE_DANMAKU_SOURCE_LABEL
            )
        }
    }

    private fun parseLiveDanmaku(body: ByteArray): WeightedTextData? {
        val bodyText = body.toString(Charsets.UTF_8)
            .replace("\u0000", "")
            .trim()
        if (bodyText.isBlank()) return null

        val root = when {
            bodyText.startsWith("{") -> JSONObject(bodyText)
            bodyText.startsWith("[") -> {
                val array = JSONArray(bodyText)
                array.optJSONObject(0) ?: return null
            }
            else -> return null
        }

        return runCatching {
            val cmd = root.optString("cmd").substringBefore(':')
            if (cmd != "DANMU_MSG") return null

            val info = root.optJSONArray("info") ?: return null
            val text = info.opt(1)?.toString()?.trim().orEmpty()
            if (text.isBlank()) return null

            val meta = info.optJSONArray(0)
            val user = info.optJSONArray(2)
            val color = resolveLiveDanmakuColor(meta)
            val userId = user?.opt(0)?.toString().orEmpty()
            val danmakuId = (
                meta.optLongCompat(5)
                    ?: meta.optLongCompat(6)
                    ?: meta.optLongCompat(7)
                    ?: nextLiveDanmakuId()
                ).takeIf { it > 0L } ?: nextLiveDanmakuId()

            WeightedTextData().apply {
                this.text = text
                this.textColor = 0xFF000000.toInt() or (color and 0x00FFFFFF)
                this.textSize = 25f
                this.layerType = LAYER_TYPE_SCROLL
                this.danmakuId = danmakuId
                this.userHash = userId.ifBlank { "live-$danmakuId" }
                this.weight = 10
                this.pool = 0
            }
        }.getOrNull()
    }

    private fun nextLiveDanmakuId(): Long {
        ensureMainThread("nextLiveDanmakuId")
        val next = liveDanmakuSequenceId
        liveDanmakuSequenceId += 1L
        return next
    }

    private fun resolveLiveDanmakuColor(meta: JSONArray?): Int {
        if (meta == null) return 0x00FFFFFF
        val candidates = listOf(3, 2, 4)
        candidates.forEach { index ->
            val value = meta.optIntCompat(index)
            if (value != null && value in 1..0x00FFFFFF) {
                return value
            }
        }
        return 0x00FFFFFF
    }

    private fun formatOnlineText(detail: LiveRoomDetailData?): String {
        val watchedText = resolveDisplayText(detail?.watchedShow?.textLarge)
            ?: resolveDisplayText(detail?.watchedShow?.textSmall)
        if (!watchedText.isNullOrBlank()) return watchedText

        val online = detail?.roomInfo?.online ?: 0
        return if (online > 0) "${online} 人气" else ""
    }

    private fun postStatus(message: String) {
        ensureMainThread("postStatus")
        _uiState.update { it.copy(statusMessage = message) }
        scheduleStatusAutoClear()
    }

    private fun scheduleStatusAutoClear() {
        ensureMainThread("scheduleStatusAutoClear")
        statusClearJob?.cancel()
        statusClearJob = viewModelScope.launch {
            delay(LIVE_STATUS_AUTO_CLEAR_MS)
            clearStatusMessage()
        }
    }
}

private fun resolveDisplayText(value: String?): String? {
    val normalized = value
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.takeUnless { it.equals("null", ignoreCase = true) }
        ?.takeUnless { it == "--" }
    return normalized
}

private fun resolveLineBaseLabel(hostLabel: String): String {
    val host = hostLabel.lowercase()
    return when {
        host.contains("mcdn") -> "mcdn"
        host.contains("bilivideo") || host.contains("upos") || host.contains("cn-") -> "bilivideo"
        host == "legacy" -> "默认线路"
        else -> hostLabel
    }
}

private fun resolveProtocolLabel(protocolName: String): String {
    return when (protocolName) {
        "http_hls" -> "HLS"
        "http_stream" -> "HTTP"
        else -> protocolName.ifBlank { "--" }
    }
}

private fun resolveFormatLabel(formatName: String): String {
    return when (formatName) {
        "ts" -> "TS"
        "fmp4" -> "fMP4"
        "flv" -> "FLV"
        else -> formatName.ifBlank { "--" }
    }
}

private fun resolveCodecLabel(codecName: String): String {
    return when (codecName.lowercase()) {
        "avc" -> "AVC"
        "hevc" -> "HEVC"
        "legacy" -> "Legacy"
        else -> codecName.ifBlank { "--" }.uppercase()
    }
}

private fun JSONArray?.optIntCompat(index: Int): Int? {
    val value = this?.opt(index) ?: return null
    return when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
}

private fun JSONArray?.optLongCompat(index: Int): Long? {
    val value = this?.opt(index) ?: return null
    return when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
}
