// 文件路径: feature/video/VideoPlayerState.kt
package com.android.purebilibili.feature.video.state

import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.VideoActivity
import com.android.purebilibili.feature.video.viewmodel.PlayerViewModel
import com.android.purebilibili.feature.video.viewmodel.PlayerUiState

import android.app.NotificationChannel
import android.support.v4.media.session.PlaybackStateCompat
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import com.android.purebilibili.R
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.util.NetworkUtils
import com.android.purebilibili.core.store.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "media_playback_channel"
private const val THEME_COLOR = 0xFFFB7299.toInt()

internal data class PlayerBufferPolicy(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int
)

internal fun resolvePlayerBufferPolicy(isOnWifi: Boolean): PlayerBufferPolicy {
    return if (isOnWifi) {
        PlayerBufferPolicy(
            minBufferMs = 10000,
            maxBufferMs = 40000,
            bufferForPlaybackMs = 900,
            bufferForPlaybackAfterRebufferMs = 1800
        )
    } else {
        PlayerBufferPolicy(
            minBufferMs = 15000,
            maxBufferMs = 50000,
            bufferForPlaybackMs = 1600,
            bufferForPlaybackAfterRebufferMs = 3000
        )
    }
}

internal fun shouldReuseMiniPlayerAtEntry(
    isMiniPlayerActive: Boolean,
    miniPlayerBvid: String?,
    miniPlayerCid: Long,
    hasMiniPlayerInstance: Boolean,
    requestBvid: String,
    requestCid: Long
): Boolean {
    if (!isMiniPlayerActive || !hasMiniPlayerInstance) return false
    if (miniPlayerBvid != requestBvid) return false
    if (requestCid <= 0L) return false
    return miniPlayerCid > 0L && miniPlayerCid == requestCid
}

internal fun shouldRestoreCachedUiState(
    cachedBvid: String?,
    cachedCid: Long,
    requestBvid: String,
    requestCid: Long
): Boolean {
    if (cachedBvid != requestBvid) return false
    if (requestCid <= 0L) return false
    return cachedCid > 0L && cachedCid == requestCid
}

internal fun resolveApiDimensionIsVertical(
    width: Int,
    height: Int,
    rotate: Int = 0
): Boolean {
    if (width <= 0 || height <= 0) return false
    val normalizedRotate = ((rotate % 360) + 360) % 360
    val shouldSwap = normalizedRotate == 90 || normalizedRotate == 270
    val effectiveWidth = if (shouldSwap) height else width
    val effectiveHeight = if (shouldSwap) width else height
    return effectiveHeight > effectiveWidth
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoPlayerState(
    val context: Context,
    val player: ExoPlayer,
    //  性能优化：传入受管理的 CoroutineScope，避免内存泄漏
    private val scope: CoroutineScope
) {
    // 🎯 [修复] 使用 MiniPlayerManager 管理的全局 MediaSession
    private val miniPlayerManager = MiniPlayerManager.getInstance(context)
    // 📱 竖屏视频状态 - 双重验证机制
    // 来源1: API dimension 字段（预判断，快速可用）
    // 来源2: 播放器 onVideoSizeChanged（精确验证，需要等待加载）
    
    private val _isVerticalVideo = MutableStateFlow(false)
    val isVerticalVideo: StateFlow<Boolean> = _isVerticalVideo.asStateFlow()
    
    // 📐 视频尺寸（来自播放器回调，精确值）
    private val _videoSize = MutableStateFlow(Pair(0, 0))
    val videoSize: StateFlow<Pair<Int, Int>> = _videoSize.asStateFlow()
    
    // 🎯 API 预判断值（用于视频加载前的 UI 显示）
    private val _apiDimension = MutableStateFlow<Pair<Int, Int>?>(null)
    val apiDimension: StateFlow<Pair<Int, Int>?> = _apiDimension.asStateFlow()
    
    // 📱 竖屏全屏模式状态
    private val _isPortraitFullscreen = MutableStateFlow(false)
    val isPortraitFullscreen: StateFlow<Boolean> = _isPortraitFullscreen.asStateFlow()
    
    // 🔍 验证来源标记
    enum class VerticalVideoSource {
        UNKNOWN,  // 未知
        API,      // 来自 API dimension 字段
        PLAYER    // 来自播放器回调（精确）
    }
    private val _verticalVideoSource = MutableStateFlow(VerticalVideoSource.UNKNOWN)
    val verticalVideoSource: StateFlow<VerticalVideoSource> = _verticalVideoSource.asStateFlow()
    
    // 🎵 缓存元数据用于状态更新
    private var currentTitle: String = ""
    private var currentArtist: String = ""
    private var currentCoverUrl: String = ""
    private var currentBitmap: Bitmap? = null

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                _videoSize.value = Pair(videoSize.width, videoSize.height)
                val isVertical = videoSize.height > videoSize.width
                _isVerticalVideo.value = isVertical
                _verticalVideoSource.value = VerticalVideoSource.PLAYER
                
                // 🔍 双重验证：检查是否与 API 预判断一致
                val apiSize = _apiDimension.value
                if (apiSize != null) {
                    val apiVertical = apiSize.second > apiSize.first
                    if (apiVertical != isVertical) {
                        com.android.purebilibili.core.util.Logger.w(
                            "VideoPlayerState",
                            "⚠️ 竖屏判断不一致! API: ${apiSize.first}x${apiSize.second}=$apiVertical, 播放器: ${videoSize.width}x${videoSize.height}=$isVertical"
                        )
                    }
                }
                
                com.android.purebilibili.core.util.Logger.d(
                    "VideoPlayerState",
                    "📱 VideoSize(PLAYER): ${videoSize.width}x${videoSize.height}, isVertical=$isVertical"
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // 当播放状态改变时，更新通知栏（主要是播放/暂停按钮）
            if (currentTitle.isNotEmpty()) {
                scope.launch(Dispatchers.Main) {
                    // 使用统一的管理方法
                    miniPlayerManager.updateMediaMetadata(currentTitle, currentArtist, currentCoverUrl)
                }
            }
        }
    }
    
    init {
        player.addListener(playerListener) // 使用统一的 listener
        // 初始检查
        val size = player.videoSize
        if (size.width > 0 && size.height > 0) {
            _videoSize.value = Pair(size.width, size.height)
            _isVerticalVideo.value = size.height > size.width
            _verticalVideoSource.value = VerticalVideoSource.PLAYER
        }
    }
    
    /**
     * 📱 从 API dimension 字段设置预判断值
     * 在视频加载完成但播放器还未解析时调用
     */
    fun setApiDimension(width: Int, height: Int, rotate: Int = 0) {
        if (width > 0 && height > 0) {
            val normalizedRotate = ((rotate % 360) + 360) % 360
            val shouldSwap = normalizedRotate == 90 || normalizedRotate == 270
            val effectiveWidth = if (shouldSwap) height else width
            val effectiveHeight = if (shouldSwap) width else height
            val apiIsVertical = resolveApiDimensionIsVertical(width = width, height = height, rotate = rotate)
            _apiDimension.value = Pair(effectiveWidth, effectiveHeight)
            // 只有在播放器还没提供精确值时才使用 API 值
            if (_verticalVideoSource.value != VerticalVideoSource.PLAYER) {
                _isVerticalVideo.value = apiIsVertical
                _verticalVideoSource.value = VerticalVideoSource.API
                com.android.purebilibili.core.util.Logger.d(
                    "VideoPlayerState",
                    "📱 VideoSize(API): raw=${width}x${height}, rotate=$rotate, effective=${effectiveWidth}x${effectiveHeight}, isVertical=$apiIsVertical"
                )
            }
        }
    }
    
    /**
     * 📱 进入/退出竖屏全屏模式
     */
    fun setPortraitFullscreen(enabled: Boolean) {
        _isPortraitFullscreen.value = enabled
        com.android.purebilibili.core.util.Logger.d(
            "VideoPlayerState",
            "📱 PortraitFullscreen: $enabled"
        )
    }
    
    /**
     * 🔄 重置视频尺寸状态（切换视频时调用）
     * 注意：不重置 isPortraitFullscreen，允许连续切换视频时保持竖屏全屏模式
     */
    fun resetVideoSize() {
        _videoSize.value = Pair(0, 0)
        _apiDimension.value = null
        _isVerticalVideo.value = false
        _verticalVideoSource.value = VerticalVideoSource.UNKNOWN
        // 不重置 _isPortraitFullscreen，保持竖屏全屏状态
    }
    
    fun release() {
        player.removeListener(playerListener)
    }

    fun updateMediaMetadata(title: String, artist: String, coverUrl: String) {
        // 缓存元数据
        currentTitle = title
        currentArtist = artist
        currentCoverUrl = coverUrl
        
        // 🎯 [核心修复] 将元数据同步到全局 MiniPlayerManager，由其统一推送通知
        // 这样即使当前 Activity 销毁，全局 Service 也能持有正确的元数据和 Session
        miniPlayerManager.updateMediaMetadata(title, artist, coverUrl)
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? = null
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun rememberVideoPlayerState(
    context: Context,
    viewModel: PlayerViewModel,
    bvid: String,
    cid: Long = 0L,
    startPaused: Boolean = false
): VideoPlayerState {

    //  尝试复用 MiniPlayerManager 中已加载的 player
    val miniPlayerManager = MiniPlayerManager.getInstance(context)
    // 仅在页面进入时判断一次，避免 setVideoInfo 更新状态后触发“同页重建 player”
    val reuseFromMiniPlayerAtEntry = remember(bvid, cid) {
        shouldReuseMiniPlayerAtEntry(
            isMiniPlayerActive = miniPlayerManager.isActive,
            miniPlayerBvid = miniPlayerManager.currentBvid,
            miniPlayerCid = miniPlayerManager.currentCid,
            hasMiniPlayerInstance = miniPlayerManager.player != null,
            requestBvid = bvid,
            requestCid = cid
        )
    }
    LaunchedEffect(bvid, cid, reuseFromMiniPlayerAtEntry) {
        Logger.d(
            "VideoPlayerState",
            "SUB_DBG remember entry: request=$bvid/$cid, miniActive=${miniPlayerManager.isActive}, mini=${miniPlayerManager.currentBvid}/${miniPlayerManager.currentCid}, reuse=$reuseFromMiniPlayerAtEntry"
        )
    }
    
    //  [修复] 添加唯一 key 强制在每次进入时重新创建 player
    // 解决重复打开同一视频时 player 已被释放导致无声音的问题
    val playerCreationKey = remember { System.currentTimeMillis() }
    val preferredPlaybackSpeed = remember(context) {
        SettingsManager.getPreferredPlaybackSpeedSync(context)
    }
    
    val player = remember(context, bvid, playerCreationKey) {
        // 如果小窗有这个视频的 player，直接复用
        if (reuseFromMiniPlayerAtEntry) {
            miniPlayerManager.player?.also {
                com.android.purebilibili.core.util.Logger.d("VideoPlayerState", " 复用小窗 player: bvid=$bvid")
            }
        } else {
            null
        } ?: run {
            // 创建新的 player
            com.android.purebilibili.core.util.Logger.d("VideoPlayerState", " 创建新 player: bvid=$bvid")
            val headers = mapOf(
                "Referer" to "https://www.bilibili.com",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
            )
            val dataSourceFactory = OkHttpDataSource.Factory(NetworkModule.okHttpClient)
                .setDefaultRequestProperties(headers)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            //  [性能优化] 使用 PlayerSettingsCache 直接从内存读取，避免 I/O
            val hwDecodeEnabled = com.android.purebilibili.core.store.PlayerSettingsCache.isHwDecodeEnabled(context)
            val seekFastEnabled = com.android.purebilibili.core.store.PlayerSettingsCache.isSeekFastEnabled(context)
            val bufferPolicy = resolvePlayerBufferPolicy(
                isOnWifi = NetworkUtils.isWifi(context)
            )
            Logger.d(
                "VideoPlayerState",
                "🎬 BufferPolicy: min=${bufferPolicy.minBufferMs}, max=${bufferPolicy.maxBufferMs}, " +
                    "start=${bufferPolicy.bufferForPlaybackMs}, rebuffer=${bufferPolicy.bufferForPlaybackAfterRebufferMs}"
            )

            //  根据设置选择 RenderersFactory
            val renderersFactory = if (hwDecodeEnabled) {
                // 默认 Factory，优先使用硬件解码
                androidx.media3.exoplayer.DefaultRenderersFactory(context)
                    .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            } else {
                // 强制使用软件解码
                androidx.media3.exoplayer.DefaultRenderersFactory(context)
                    .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                    .setEnableDecoderFallback(true)
            }

            ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                //  性能优化：自定义缓冲策略，改善播放流畅度
                .setLoadControl(
                    androidx.media3.exoplayer.DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            bufferPolicy.minBufferMs,
                            bufferPolicy.maxBufferMs,
                            bufferPolicy.bufferForPlaybackMs,
                            bufferPolicy.bufferForPlaybackAfterRebufferMs
                        )
                        .setPrioritizeTimeOverSizeThresholds(true)  // 优先保证播放时长
                        .build()
                )
                //  [性能优化] 快速 Seek：跳转到最近的关键帧而非精确位置
                .setSeekParameters(
                    if (seekFastEnabled) {
                        androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC
                    } else {
                        androidx.media3.exoplayer.SeekParameters.DEFAULT
                    }
                )
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                // 🔋 [修复] 防止息屏时音频停止，保持网络连接和 CPU 唤醒
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()
                .apply {
                    //  [修复] 确保音量正常，解决第二次播放静音问题
                    //  如果 startPaused 为 true，则静音
                    volume = if (startPaused) 0f else 1.0f
                    setPlaybackSpeed(preferredPlaybackSpeed)
                    //  [重构] 不在此处调用 prepare()，因为还没有媒体源
                    // prepare() 和 playWhenReady 将在 attachPlayer/loadVideo 设置媒体源后调用
                    playWhenReady = !startPaused
                }
        }
    }

    val sessionActivityPendingIntent = remember(context, bvid) {
        //  [修复] 点击通知跳转到 MainActivity 而不是新建 VideoActivity
        // 这样可以复用应用内的导航栈，保持"单 Activity"架构
        val intent = Intent(context, com.android.purebilibili.MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("https://www.bilibili.com/video/$bvid")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    val scope = rememberCoroutineScope()

    val holder = remember(player, scope) {
        VideoPlayerState(context, player, scope)
    }

    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState) {
        if (uiState is PlayerUiState.Success) {
            val info = (uiState as PlayerUiState.Success).info
            holder.updateMediaMetadata(info.title, info.owner.name, info.pic)
        }
    }

    DisposableEffect(player) {
        onDispose {
            //  [新增] 保存播放进度到 ViewModel 缓存
            viewModel.saveCurrentPosition()
            
            //  检查是否有小窗在使用这个 player
            val miniPlayerManager = MiniPlayerManager.getInstance(context)
            // 仅当当前实例仍被 MiniPlayerManager 持有时才保留
            val shouldKeepPlayer = miniPlayerManager.isActive && miniPlayerManager.isPlayerManaged(player)
            
            if (shouldKeepPlayer) {
                // 小窗模式下不释放 player，只释放其他资源
                com.android.purebilibili.core.util.Logger.d("VideoPlayerState", " MiniPlayerManager 正在使用此 player，不释放")
            } else {
                // ⚡ [性能优化] 释放视频尺寸监听器（快速，main thread）
                holder.release()
                // 仅当引用匹配时才清理，避免误清理新页面正在使用的 player
                miniPlayerManager.clearExternalPlayerIfMatches(player)
                
                // ⚡ [性能优化] 将重量级的 player.release() 和通知清理延迟到下一帧
                // ExoPlayer 要求 release() 在主线程调用，所以用 Handler.post 而非后台线程
                // 这样不阻塞当前 onDispose 栈，让导航转场动画先完成
                val playerToRelease = player
                val appContext = context.applicationContext
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    com.android.purebilibili.core.util.Logger.d("VideoPlayerState", "⚡ 延迟释放播放器资源")
                    playerToRelease.release()
                    val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(NOTIFICATION_ID)
                }
            }
            
            (context as? ComponentActivity)?.window?.attributes?.screenBrightness =
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    //  [后台恢复优化] 监听生命周期，保存/恢复播放状态
    var savedPosition by remember { mutableStateOf(-1L) }
    var wasPlaying by remember { mutableStateOf(false) }
    //  [修复] 记录是否从后台音频模式恢复（后台音频时不应 seek 回旧位置）
    var wasBackgroundAudio by remember { mutableStateOf(false) }
    
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val miniPlayerManager = MiniPlayerManager.getInstance(context)
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    //  [修复] 保存进度到 ViewModel 缓存（用于跨导航恢复）
                    viewModel.saveCurrentPosition()
                    
                    //  保存播放状态（用于本地恢复）
                    savedPosition = player.currentPosition
                    wasPlaying = isPlaybackActiveForLifecycle(
                        isPlaying = player.isPlaying,
                        playWhenReady = player.playWhenReady,
                        playbackState = player.playbackState
                    )
                    
                    //  [新增] 判断是否应该继续播放
                    // 1. 应用内小窗模式 - 继续播放
                    // 2. 系统 PiP 模式 - 用户按 Home 键返回桌面时继续播放
                    // 3. 后台音频模式 - 继续播放音频
                    val isMiniMode = miniPlayerManager.isMiniMode
                    val isPip = miniPlayerManager.shouldEnterPip()
                    val isBackgroundAudio = miniPlayerManager.shouldContinueBackgroundAudio()
                    val hasRecentUserLeaveHint = miniPlayerManager.hasRecentUserLeaveHint()
                    val shouldContinuePlayback = com.android.purebilibili.feature.video.player
                        .shouldContinuePlaybackDuringPause(
                            isMiniMode = isMiniMode,
                            isPip = isPip,
                            isBackgroundAudio = isBackgroundAudio,
                            wasPlaybackActive = wasPlaying
                        )
                    
                    //  [修复] 记录后台音频状态，恢复时不要 seek 回旧位置
                    wasBackgroundAudio = isBackgroundAudio && hasRecentUserLeaveHint
                    
                    if (!shouldContinuePlayback) {
                        // 非小窗/PiP/后台模式下暂停
                        player.pause()
                        com.android.purebilibili.core.util.Logger.d("VideoPlayerState", " ON_PAUSE: 暂停播放")
                    } else {
                        com.android.purebilibili.core.util.Logger.d("VideoPlayerState", "🎵 ON_PAUSE: 保持播放 (miniMode=$isMiniMode, pip=$isPip, bg=$isBackgroundAudio, leaveHint=$hasRecentUserLeaveHint)")
                    }
                    com.android.purebilibili.core.util.Logger.d("VideoPlayerState", " ON_PAUSE: pos=$savedPosition, wasPlaying=$wasPlaying, bgAudio=$wasBackgroundAudio")
                }
                // 🔋 注意: ON_STOP/ON_START 的视频轨道禁用/恢复由 MiniPlayerManager 通过 BackgroundManager 统一处理
                // 避免重复处理导致 savedTrackParams 被覆盖
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    //  [修复] 恢复前台时，只在确实暂停了的情况下恢复播放
                    //  如果是在 PiP 或后台音频模式下，播放器一直在运行，不需要干预
                    val shouldResume = shouldResumeAfterLifecyclePause(
                        wasPlaybackActive = wasPlaying,
                        isPlaying = player.isPlaying,
                        playWhenReady = player.playWhenReady,
                        playbackState = player.playbackState
                    )
                    val shouldEnsureAudibleOnForeground =
                        !miniPlayerManager.isMiniMode && !miniPlayerManager.shouldEnterPip()

                    if (shouldRestorePlayerVolumeOnResume(
                            shouldResume = shouldResume,
                            currentVolume = player.volume,
                            shouldEnsureAudible = shouldEnsureAudibleOnForeground
                        )
                    ) {
                        player.volume = 1.0f
                        com.android.purebilibili.core.util.Logger.d(
                            "VideoPlayerState",
                            "🔊 ON_RESUME: Restored player volume to avoid silent playback"
                        )
                    }
                    
                    if (shouldResume) {
                        // 只有当完全暂停时才检查是否需要恢复
                        // 移除 seekTo(savedPosition)，因为 player.currentPosition 才是最新的（即使暂停了也还在该位置）
                        // 且 seekTo 会导致 PiP 返回时回退到进入 PiP 前的旧位置
                        if (shouldEnsureAudibleOnForeground) {
                             player.play()
                             com.android.purebilibili.core.util.Logger.d("VideoPlayerState", " ON_RESUME: Resuming playback")
                        }
                    } else {
                        com.android.purebilibili.core.util.Logger.d("VideoPlayerState", " ON_RESUME: Player already running or was not playing, skipping resume")
                    }
                    
                    // 重置标志
                    wasBackgroundAudio = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    //  [修复3] 监听播放器错误，智能重试（网络错误 → CDN 切换 → 重试）
    val retryCountRef = remember { object { 
        var count = 0 
        var cdnSwitchCount = 0  // 📡 [新增] CDN 切换计数
    } }
    val maxRetries = 3
    val maxCdnSwitches = 2  // 📡 [新增] 最多尝试切换 2 次 CDN
    
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val causeName = error.cause?.javaClass?.name
                val errorCodeName = androidx.media3.common.PlaybackException.getErrorCodeName(error.errorCode)
                com.android.purebilibili.core.util.Logger.e(
                    "VideoPlayerState",
                    "❌ Player error: code=${error.errorCode}($errorCodeName), message=${error.message}, cause=$causeName",
                    error
                )

                val currentState = viewModel.uiState.value
                val hasCdnAlternatives = currentState is com.android.purebilibili.feature.video.viewmodel.PlayerUiState.Success 
                    && currentState.cdnCount > 1

                val action = decidePlayerErrorRecovery(
                    errorCode = error.errorCode,
                    hasCdnAlternatives = hasCdnAlternatives,
                    retryCount = retryCountRef.count,
                    maxRetries = maxRetries,
                    cdnSwitchCount = retryCountRef.cdnSwitchCount,
                    maxCdnSwitches = maxCdnSwitches,
                    isDecoderLikeFailure = isDecoderLikeFailure(
                        errorMessage = error.message,
                        causeClassName = causeName
                    )
                )

                when (action) {
                    PlayerErrorRecoveryAction.SWITCH_CDN -> {
                        retryCountRef.cdnSwitchCount++
                        com.android.purebilibili.core.util.Logger.d(
                            "VideoPlayerState",
                            "📡 Network error, switching CDN (${retryCountRef.cdnSwitchCount}/$maxCdnSwitches)"
                        )
                        scope.launch {
                            kotlinx.coroutines.delay(500)
                            viewModel.switchCdn()
                        }
                    }

                    PlayerErrorRecoveryAction.RETRY_NETWORK -> {
                        retryCountRef.count++
                        val delayMs = (1000L * (1 shl (retryCountRef.count - 1))).coerceAtMost(8000L)
                        com.android.purebilibili.core.util.Logger.d(
                            "VideoPlayerState",
                            "🔄 Network error, retry ${retryCountRef.count}/$maxRetries in ${delayMs}ms"
                        )
                        scope.launch {
                            kotlinx.coroutines.delay(delayMs)
                            viewModel.retry()
                        }
                    }

                    PlayerErrorRecoveryAction.RETRY_DECODER_FALLBACK -> {
                        retryCountRef.count++
                        com.android.purebilibili.core.util.Logger.w(
                            "VideoPlayerState",
                            "🛟 Decoder-like error, retrying with safe codec fallback (AVC)"
                        )
                        scope.launch {
                            viewModel.retryWithCodecFallback()
                        }
                    }

                    PlayerErrorRecoveryAction.RETRY_NON_NETWORK -> {
                        retryCountRef.count++
                        com.android.purebilibili.core.util.Logger.d(
                            "VideoPlayerState",
                            " Auto-retrying video load (non-network error)..."
                        )
                        scope.launch {
                            viewModel.retry()
                        }
                    }

                    PlayerErrorRecoveryAction.GIVE_UP -> {
                        com.android.purebilibili.core.util.Logger.w(
                            "VideoPlayerState",
                            "⚠️ Retry budget exhausted, waiting for manual user action"
                        )
                    }
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    // 播放成功，重置所有计数
                    retryCountRef.count = 0
                    retryCountRef.cdnSwitchCount = 0
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    //  [重构] 合并为单个 LaunchedEffect 确保执行顺序
    // 必须先 attachPlayer，再 loadVideo，否则 ViewModel 中的 exoPlayer 引用无效
    LaunchedEffect(player, bvid, cid, reuseFromMiniPlayerAtEntry) {
        // 1️⃣ 首先绑定 player
        viewModel.attachPlayer(player)
        Logger.d(
            "VideoPlayerState",
            "SUB_DBG attach player + decide restore/load: request=$bvid/$cid, reuse=$reuseFromMiniPlayerAtEntry"
        )
        
        // 2️⃣ 尝试从缓存恢复 UI 状态 (仅当复用播放器时)
        // 解决从小窗/后台返回时的网络请求错误问题
        var restored = false
        if (reuseFromMiniPlayerAtEntry) {
            val cachedState = miniPlayerManager.consumeCachedUiState()
            Logger.d(
                "VideoPlayerState",
                "SUB_DBG cached state peek: cached=${cachedState?.info?.bvid}/${cachedState?.info?.cid}"
            )
            if (cachedState != null && shouldRestoreCachedUiState(
                    cachedBvid = cachedState.info.bvid,
                    cachedCid = cachedState.info.cid,
                    requestBvid = bvid,
                    requestCid = cid
                )
            ) {
                com.android.purebilibili.core.util.Logger.d("VideoPlayerState", "♻️ Restoring cached UI state for $bvid")
                viewModel.restoreUiState(cachedState)
                restored = true
            } else if (cachedState != null) {
                Logger.d(
                    "VideoPlayerState",
                    "SUB_DBG skip cached restore by policy: request=$bvid/$cid, cached=${cachedState.info.bvid}/${cachedState.info.cid}"
                )
            }
        }
        
        // 3️⃣ 如果没有恢复成功，则调用 loadVideo
        if (!restored) {
            com.android.purebilibili.core.util.Logger.d("VideoPlayerState", "SUB_DBG call loadVideo: request=$bvid/$cid")
            viewModel.loadVideo(bvid, cid = cid)
        }
    }

    return holder
}
