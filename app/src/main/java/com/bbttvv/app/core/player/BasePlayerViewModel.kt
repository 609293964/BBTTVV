package com.bbttvv.app.core.player

import android.os.Looper
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.SponsorSegment
import com.bbttvv.app.data.repository.SponsorBlockRepository
import com.bbttvv.app.feature.plugin.SponsorBlockConfig
import com.bbttvv.app.feature.plugin.findSponsorBlockPlugin
import com.bbttvv.app.feature.video.danmaku.DanmakuRenderPayload
import com.bbttvv.app.feature.video.danmaku.ParsedDanmaku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放器基类 ViewModel
 *
 * 提供 PlayerViewModel 和 BangumiPlayerViewModel 共用的功能：
 * 1. ExoPlayer 管理
 * 2. 空降助手 (SponsorBlock) 逻辑
 * 3. DASH 视频播放
 * 4. 弹幕数据加载
 */
abstract class BasePlayerViewModel : ViewModel() {
    // ========== 播放器引用 ==========
    protected var exoPlayer: ExoPlayer? = null
    protected var playerEngine: PlayerEngine? = null
    private val mediaSourceCoordinator = PlayerMediaSourceCoordinator(
        playerProvider = { playerEngine },
        tag = TAG,
    )
    private var danmakuSource: ParsedDanmaku? = null
    private var danmakuFilterContext: PlayerDanmakuFilterContext = PlayerDanmakuFilterContext()
    private var danmakuPluginObserverJob: Job? = null
    private var danmakuLoadJob: Job? = null
    private var danmakuLoadSequence: Long = 0L
    private var danmakuSourceVersion: Long = 0L

    init {
        observeDanmakuPluginUpdates()
    }

    /**
     * 绑定播放器实例
     */
    @MainThread
    open fun attachPlayer(player: ExoPlayer) {
        ensureMainThread("attachPlayer")
        this.exoPlayer = player
        this.playerEngine = ExoPlayerEngine(player)
        playerEngine?.volume = 1.0f
    }

    /**
     * 获取播放器当前位置
     */
    @MainThread
    fun getPlayerCurrentPosition(): Long {
        ensureMainThread("getPlayerCurrentPosition")
        return playerEngine?.currentPosition ?: 0L
    }

    /**
     * 获取播放器总时长
     */
    @MainThread
    fun getPlayerDuration(): Long {
        ensureMainThread("getPlayerDuration")
        val duration = playerEngine?.duration ?: 0L
        return if (duration < 0) 0L else duration
    }

    /**
     * 跳转到指定位置
     */
    @MainThread
    fun seekTo(position: Long) {
        ensureMainThread("seekTo")
        playerEngine?.seekTo(position)
    }

    // ========== 空降助手 (SponsorBlock) ==========

    private val _sponsorSegments = MutableStateFlow<List<SponsorSegment>>(emptyList())
    val sponsorSegments: StateFlow<List<SponsorSegment>> = _sponsorSegments.asStateFlow()

    private val _currentSponsorSegment = MutableStateFlow<SponsorSegment?>(null)
    val currentSponsorSegment: StateFlow<SponsorSegment?> = _currentSponsorSegment.asStateFlow()

    private val _showSkipButton = MutableStateFlow(false)
    val showSkipButton: StateFlow<Boolean> = _showSkipButton.asStateFlow()

    private val skippedSegmentIds = mutableSetOf<String>()

    /**
     * 加载空降片段
     */
    @MainThread
    protected fun loadSponsorSegments(bvid: String) {
        ensureMainThread("loadSponsorSegments")
        viewModelScope.launch {
            try {
                val segments = SponsorBlockRepository.getSegments(bvid)
                _sponsorSegments.value = segments
                skippedSegmentIds.clear()
                Logger.d(TAG, " SponsorBlock: loaded ${segments.size} segments for $bvid")
            } catch (e: Exception) {
                Logger.w(TAG, " SponsorBlock: load failed: ${e.message}")
            }
        }
    }

    /**
     * 检查当前播放位置是否在空降片段内，并执行跳过逻辑
     *
     * @return 是否执行了自动跳过
     */
    @MainThread
    suspend fun checkAndSkipSponsor(): Boolean {
        ensureMainThread("checkAndSkipSponsor")
        val engine = playerEngine ?: return false
        val segments = _sponsorSegments.value
        if (segments.isEmpty()) return false

        val sponsorConfig = resolveSponsorBlockConfig()
        val filteredSegments = segments.filter { segment ->
            segment.isSkipType && sponsorConfig.isCategoryEnabled(segment.category)
        }
        if (filteredSegments.isEmpty()) {
            _currentSponsorSegment.value = null
            _showSkipButton.value = false
            return false
        }

        val currentPos = engine.currentPosition
        val segment = SponsorBlockRepository.findSegmentAtPosition(filteredSegments, currentPos)

        if (segment != null && segment.UUID !in skippedSegmentIds) {
            _currentSponsorSegment.value = segment

            if (sponsorConfig.autoSkip) {
                engine.seekTo(segment.endTimeMs)
                skippedSegmentIds.add(segment.UUID)
                _currentSponsorSegment.value = null
                _showSkipButton.value = false
                onSponsorSkipped(segment)
                return true
            } else {
                _showSkipButton.value = true
            }
        } else if (segment == null) {
            _currentSponsorSegment.value = null
            _showSkipButton.value = false
        }

        return false
    }

    /**
     * 手动跳过当前空降片段
     */
    @MainThread
    fun skipCurrentSponsorSegment() {
        ensureMainThread("skipCurrentSponsorSegment")
        val segment = _currentSponsorSegment.value ?: return
        val engine = playerEngine ?: return

        engine.seekTo(segment.endTimeMs)
        skippedSegmentIds.add(segment.UUID)
        _currentSponsorSegment.value = null
        _showSkipButton.value = false

        onSponsorSkipped(segment)
    }

    /**
     * 忽略当前空降片段（不跳过）
     */
    @MainThread
    fun dismissSponsorSkipButton() {
        ensureMainThread("dismissSponsorSkipButton")
        val segment = _currentSponsorSegment.value ?: return
        skippedSegmentIds.add(segment.UUID)
        _currentSponsorSegment.value = null
        _showSkipButton.value = false
    }

    /**
     * 重置空降片段状态（切换视频时调用）
     */
    @MainThread
    protected fun resetSponsorState() {
        ensureMainThread("resetSponsorState")
        _sponsorSegments.value = emptyList()
        _currentSponsorSegment.value = null
        _showSkipButton.value = false
        skippedSegmentIds.clear()
    }

    /**
     * 空降片段被跳过后的回调（子类可覆盖以显示 toast 等）
     */
    protected open fun onSponsorSkipped(segment: SponsorSegment) {
        // 子类可覆盖
    }

    private fun resolveSponsorBlockConfig(): SponsorBlockConfig {
        val sponsorPlugin = PluginManager.plugins.findSponsorBlockPlugin()
        return sponsorPlugin?.configState?.value ?: SponsorBlockConfig(autoSkip = false)
    }

    // ========== DASH 视频播放 ==========

    /**
     * 播放 DASH 格式视频（视频+音频分离）
     *
     * @param videoUrl 视频流 URL
     * @param audioUrl 音频流 URL（可选）
     * @param seekToMs 开始播放位置（毫秒）
     * @param resetPlayer 是否重置播放器状态（默认true，切换清晰度时可设为false以减少闪烁）
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @MainThread
    protected fun playDashVideo(
        videoUrl: String,
        audioUrl: String?,
        videoUrlCandidates: List<String> = emptyList(),
        audioUrlCandidates: List<String> = emptyList(),
        seekToMs: Long = 0L,
        resetPlayer: Boolean = true,
        referer: String = "https://www.bilibili.com",
        playWhenReady: Boolean = true
    ) {
        ensureMainThread("playDashVideo")
        mediaSourceCoordinator.playDashVideo(
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            videoUrlCandidates = videoUrlCandidates,
            audioUrlCandidates = audioUrlCandidates,
            seekToMs = seekToMs,
            resetPlayer = resetPlayer,
            referer = referer,
            playWhenReady = playWhenReady,
        )
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @MainThread
    protected fun playDashManifestVideo(
        manifestContent: String,
        seekToMs: Long = 0L,
        resetPlayer: Boolean = true,
        referer: String = "https://www.bilibili.com",
        playWhenReady: Boolean = true
    ): Boolean {
        ensureMainThread("playDashManifestVideo")
        return mediaSourceCoordinator.playDashManifestVideo(
            manifestContent = manifestContent,
            seekToMs = seekToMs,
            resetPlayer = resetPlayer,
            referer = referer,
            playWhenReady = playWhenReady,
        )
    }

    /**
     * 播放分段 durl 视频（多段 MP4）
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @MainThread
    protected fun playSegmentedVideo(
        segmentUrls: List<String>,
        segmentUrlCandidates: List<List<String>> = emptyList(),
        seekToMs: Long = 0L,
        resetPlayer: Boolean = true,
        referer: String = "https://www.bilibili.com",
        playWhenReady: Boolean = true
    ) {
        ensureMainThread("playSegmentedVideo")
        mediaSourceCoordinator.playSegmentedVideo(
            segmentUrls = segmentUrls,
            segmentUrlCandidates = segmentUrlCandidates,
            seekToMs = seekToMs,
            resetPlayer = resetPlayer,
            referer = referer,
            playWhenReady = playWhenReady,
        )
    }

    /**
     * 播放普通视频（单一 URL）
     *
     * @param url 视频 URL
     * @param seekToMs 开始播放位置（毫秒）
     */
    @MainThread
    protected fun playVideo(url: String, seekToMs: Long = 0L) {
        ensureMainThread("playVideo")
        mediaSourceCoordinator.playVideo(url = url, seekToMs = seekToMs)
    }

    /**
     * 播放通用流媒体地址（如直播 HLS/FLV）。
     *
     * 使用 DefaultMediaSourceFactory，让 Media3 按 URL/响应头自动选择 HLS/渐进式等格式，
     * 同时带上业务页 Referer，避免直播流被服务端拒绝。
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @MainThread
    protected fun playStreamingUrl(
        url: String,
        seekToMs: Long = 0L,
        resetPlayer: Boolean = true,
        referer: String = "https://live.bilibili.com",
        playWhenReady: Boolean = true
    ) {
        ensureMainThread("playStreamingUrl")
        mediaSourceCoordinator.playStreamingUrl(
            url = url,
            seekToMs = seekToMs,
            resetPlayer = resetPlayer,
            referer = referer,
            playWhenReady = playWhenReady,
        )
    }

    // ========== 弹幕数据 ==========

    private val _danmakuData = MutableStateFlow<ByteArray?>(null)
    val danmakuData: StateFlow<ByteArray?> = _danmakuData.asStateFlow()

    private val _danmakuPayload = MutableStateFlow<DanmakuRenderPayload?>(null)
    val danmakuPayload: StateFlow<DanmakuRenderPayload?> = _danmakuPayload.asStateFlow()

    private val _isDanmakuLoading = MutableStateFlow(false)
    val isDanmakuLoading: StateFlow<Boolean> = _isDanmakuLoading.asStateFlow()

    private val _isDanmakuEnabled = MutableStateFlow(resolveInitialDanmakuEnabled())
    val isDanmakuEnabled: StateFlow<Boolean> = _isDanmakuEnabled.asStateFlow()

    private val loadedDanmakuSegments = mutableSetOf<Int>()
    private var currentDanmakuCid: Long = 0L
    private var currentDanmakuAid: Long = 0L

    /**
     * 初始化加载弹幕数据（根据起始时间加载第一段）
     */
    @MainThread
    protected fun loadDanmaku(cid: Long, aid: Long = 0L, startPositionMs: Long = 0L) {
        ensureMainThread("loadDanmaku")
        danmakuLoadJob?.cancel()
        currentDanmakuCid = cid
        currentDanmakuAid = aid
        loadedDanmakuSegments.clear()
        
        val initialSegmentIndex = (startPositionMs / 360000L).coerceAtLeast(0L).toInt() + 1
        
        val loadSequence = ++danmakuLoadSequence
        danmakuLoadJob = viewModelScope.launch {
            _isDanmakuLoading.value = true
            val loadResult = withContext(Dispatchers.IO) {
                PlayerDanmakuPipeline.loadSegmentSource(
                    cid = cid,
                    aid = aid,
                    segmentIndex = initialSegmentIndex,
                )
            }
            if (!isActive || loadSequence != danmakuLoadSequence) {
                return@launch
            }
            loadedDanmakuSegments.add(initialSegmentIndex)
            
            _danmakuData.value = loadResult.rawData
            danmakuSource = loadResult.parsed
            danmakuFilterContext = loadResult.filterContext
            danmakuSourceVersion += 1
            val payload = loadResult.parsed?.let {
                PlayerDanmakuPipeline.buildRenderPayload(
                    parsed = it,
                    sourceLabel = loadResult.sourceLabel,
                    filterContext = danmakuFilterContext,
                )
            }
            _danmakuPayload.value = payload
            _isDanmakuLoading.value = false
            payload?.let {
                val firstShowAt = it.standardList.firstOrNull()?.showAtTime ?: -1L
                val lastShowAt = it.standardList.lastOrNull()?.showAtTime ?: -1L
                Logger.w(
                    TAG,
                    "Danmaku ready source=${it.sourceLabel} cid=$cid count=${it.totalCount} first=$firstShowAt last=$lastShowAt segment=$initialSegmentIndex"
                )
            }
        }
    }

    /**
     * 根据当前播放进度，预取下一个弹幕分段或目标分段
     */
    @MainThread
    fun prefetchDanmakuSegmentIfNeeded(positionMs: Long) {
        ensureMainThread("prefetchDanmakuSegmentIfNeeded")
        val cid = currentDanmakuCid
        val aid = currentDanmakuAid
        val currentPayload = _danmakuPayload.value
        if (cid <= 0L || !isDanmakuEnabled.value || currentPayload == null) return
        if (currentPayload.sourceLabel.startsWith("XML")) return
        
        val currentSegmentIndex = (positionMs / 360000L).coerceAtLeast(0L).toInt() + 1
        
        val targetSegmentIndex = if (!loadedDanmakuSegments.contains(currentSegmentIndex)) {
            currentSegmentIndex
        } else {
            val msInCurrentSegment = positionMs % 360000L
            if (msInCurrentSegment > 300000L) {
                currentSegmentIndex + 1
            } else {
                -1
            }
        }
        
        if (targetSegmentIndex <= 0 || loadedDanmakuSegments.contains(targetSegmentIndex)) {
            return
        }
        
        loadedDanmakuSegments.add(targetSegmentIndex)
        Logger.d(TAG, "Prefetching Danmaku Segment: $targetSegmentIndex for cid=$cid")
        
        viewModelScope.launch {
            val loadResult = withContext(Dispatchers.IO) {
                PlayerDanmakuPipeline.loadSegmentSource(
                    cid = cid,
                    aid = aid,
                    segmentIndex = targetSegmentIndex,
                )
            }
            if (!isActive || currentDanmakuCid != cid) return@launch
            
            val newParsed = loadResult.parsed ?: return@launch
            val currentSource = danmakuSource
            danmakuSource = currentSource?.mergeWith(newParsed) ?: newParsed
            danmakuSourceVersion += 1
            
            val payload = danmakuSource?.let {
                PlayerDanmakuPipeline.buildRenderPayload(
                    parsed = it,
                    sourceLabel = "SEG_MERGED_$targetSegmentIndex",
                    filterContext = danmakuFilterContext,
                )
            }
            _danmakuPayload.value = payload
            Logger.d(TAG, "Merged Danmaku Segment: $targetSegmentIndex, new total=${payload?.totalCount}")
        }
    }

    @MainThread
    fun toggleDanmakuEnabled() {
        ensureMainThread("toggleDanmakuEnabled")
        _isDanmakuEnabled.update { !it }
    }

    @MainThread
    protected fun publishDanmakuPayload(
        parsed: ParsedDanmaku?,
        sourceLabel: String,
    ) {
        ensureMainThread("publishDanmakuPayload")
        danmakuSource = parsed
        danmakuFilterContext = PlayerDanmakuFilterContext()
        danmakuSourceVersion += 1
        _danmakuPayload.value = parsed?.let {
            PlayerDanmakuPipeline.buildRenderPayload(
                parsed = it,
                sourceLabel = sourceLabel,
                filterContext = danmakuFilterContext,
            )
        }
    }

    /**
     * 清除弹幕数据
     */
    @MainThread
    protected fun clearDanmaku() {
        ensureMainThread("clearDanmaku")
        danmakuLoadJob?.cancel()
        danmakuLoadJob = null
        danmakuLoadSequence += 1
        currentDanmakuCid = 0L
        currentDanmakuAid = 0L
        loadedDanmakuSegments.clear()
        _danmakuData.value = null
        _danmakuPayload.value = null
        _isDanmakuLoading.value = false
        danmakuSource = null
        danmakuFilterContext = PlayerDanmakuFilterContext()
        danmakuSourceVersion += 1
    }

    private fun observeDanmakuPluginUpdates() {
        if (danmakuPluginObserverJob != null) return
        danmakuPluginObserverJob = viewModelScope.launch {
            PluginManager.danmakuPluginUpdateToken.collectLatest { token ->
                if (token <= 0L) return@collectLatest
                rebuildDanmakuPayloadFromSource()
            }
        }
    }

    private suspend fun rebuildDanmakuPayloadFromSource() {
        ensureMainThread("rebuildDanmakuPayloadFromSource")
        val parsed = danmakuSource ?: return
        val currentPayload = _danmakuPayload.value ?: return
        val filterContext = danmakuFilterContext
        val sourceVersion = danmakuSourceVersion
        val rebuiltPayload = withContext(Dispatchers.Default) {
            PlayerDanmakuPipeline.buildRenderPayload(
                parsed = parsed,
                sourceLabel = currentPayload.sourceLabel,
                filterContext = filterContext,
            )
        }
        currentCoroutineContext().ensureActive()
        if (sourceVersion != danmakuSourceVersion) return
        _danmakuPayload.value = rebuiltPayload
    }

    // ========== 生命周期 ==========

    override fun onCleared() {
        ensureMainThread("onCleared")
        super.onCleared()
        mediaSourceCoordinator.clear()
        danmakuPluginObserverJob?.cancel()
        playerEngine = null
        exoPlayer = null
    }

    companion object {
        private const val TAG = "BasePlayerVM"
    }

    @MainThread
    protected fun ensureMainThread(caller: String) {
        check(Looper.getMainLooper().thread === Thread.currentThread()) {
            "$caller must run on the main thread"
        }
    }
}
