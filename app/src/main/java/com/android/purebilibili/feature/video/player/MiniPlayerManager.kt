// 文件路径: feature/video/player/MiniPlayerManager.kt
package com.android.purebilibili.feature.video.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import com.android.purebilibili.core.util.Logger
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import com.android.purebilibili.R
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.store.normalizeAppIconKey
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.MediaUtils
import com.android.purebilibili.core.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.android.purebilibili.feature.video.viewmodel.PlayerUiState
import com.android.purebilibili.feature.video.VideoActivity
import com.android.purebilibili.feature.video.danmaku.DanmakuManager
import com.android.purebilibili.feature.video.playback.policy.resolvePlaybackWakeMode
import com.android.purebilibili.feature.video.state.isPlaybackActiveForLifecycle
import com.android.purebilibili.feature.video.usecase.VideoLoadResult
import com.android.purebilibili.feature.video.usecase.VideoPlaybackUseCase

private const val TAG = "MiniPlayerManager"
private const val NOTIFICATION_ID = 1002
private const val CHANNEL_ID = "mini_player_channel"
private const val THEME_COLOR = 0xFFFB7299.toInt()
private const val FOREGROUND_START_DEBOUNCE_MS = 1500L
private const val USER_LEAVE_HINT_WINDOW_MS = 1500L
private const val SESSION_COMMAND_SKIP_TO_PREVIOUS = "SKIP_TO_PREVIOUS"
private const val SESSION_COMMAND_SKIP_TO_NEXT = "SKIP_TO_NEXT"

internal fun shouldShowInAppMiniPlayerByPolicy(
    mode: SettingsManager.MiniPlayerMode,
    isActive: Boolean,
    isNavigatingToVideo: Boolean,
    stopPlaybackOnExit: Boolean
): Boolean {
    if (stopPlaybackOnExit) return false
    return mode == SettingsManager.MiniPlayerMode.IN_APP_ONLY && isActive && !isNavigatingToVideo
}

internal fun shouldEnterPipByPolicy(
    mode: SettingsManager.MiniPlayerMode,
    isActive: Boolean,
    stopPlaybackOnExit: Boolean
): Boolean {
    if (stopPlaybackOnExit) return false
    return mode == SettingsManager.MiniPlayerMode.SYSTEM_PIP && isActive
}

internal fun shouldContinueBackgroundAudioByPolicy(
    backgroundPlaybackEnabled: Boolean,
    mode: SettingsManager.MiniPlayerMode,
    isActive: Boolean,
    isLeavingByNavigation: Boolean,
    stopPlaybackOnExit: Boolean
): Boolean {
    if (!backgroundPlaybackEnabled) return false
    if (stopPlaybackOnExit) return false
    return when (mode) {
        SettingsManager.MiniPlayerMode.OFF -> isActive && !isLeavingByNavigation
        SettingsManager.MiniPlayerMode.IN_APP_ONLY -> false
        SettingsManager.MiniPlayerMode.SYSTEM_PIP -> false
    }
}

internal fun resolveHandleAudioFocusByPolicy(audioFocusEnabled: Boolean): Boolean {
    return audioFocusEnabled
}

internal fun shouldClearPlaybackNotificationOnNavigationExit(
    mode: SettingsManager.MiniPlayerMode,
    stopPlaybackOnExit: Boolean
): Boolean {
    if (stopPlaybackOnExit) return true
    return mode == SettingsManager.MiniPlayerMode.OFF ||
        mode == SettingsManager.MiniPlayerMode.SYSTEM_PIP
}

internal fun shouldHandleNavigationLeaveForBvid(
    expectedBvid: String?,
    currentBvid: String?
): Boolean {
    val expected = expectedBvid?.trim().orEmpty()
    val current = currentBvid?.trim().orEmpty()
    if (expected.isBlank() || current.isBlank()) return true
    return expected == current
}

internal fun shouldContinuePlaybackDuringPause(
    isMiniMode: Boolean,
    isPip: Boolean,
    isBackgroundAudio: Boolean,
    wasPlaybackActive: Boolean
): Boolean {
    if (isMiniMode || isPip) return true
    return isBackgroundAudio && wasPlaybackActive
}

internal fun shouldDisableVideoTrackOnEnterBackground(
    shouldPauseBuffering: Boolean,
    shouldContinueBackgroundAudio: Boolean
): Boolean {
    return shouldPauseBuffering || shouldContinueBackgroundAudio
}

internal fun shouldClearVideoSurfaceOnEnterBackground(
    shouldDisableVideoTrack: Boolean,
    shouldContinueBackgroundAudio: Boolean
): Boolean {
    return shouldDisableVideoTrack && shouldContinueBackgroundAudio
}

internal fun shouldTrimDanmakuCachesOnEnterBackground(
    shouldDisableVideoTrack: Boolean
): Boolean {
    return shouldDisableVideoTrack
}

internal fun shouldRefreshVideoFrameOnEnterForeground(
    hadSavedTrackParams: Boolean,
    hasMediaItems: Boolean,
    playbackState: Int
): Boolean {
    return hadSavedTrackParams && hasMediaItems && playbackState != Player.STATE_IDLE
}

internal fun shouldKickPlaybackAfterForegroundTrackRestore(
    hadSavedTrackParams: Boolean,
    playWhenReady: Boolean,
    playbackState: Int
): Boolean {
    return hadSavedTrackParams && playWhenReady && playbackState != Player.STATE_IDLE
}

internal fun shouldResumePlaybackOnEnterForeground(
    playWhenReady: Boolean,
    isPlaying: Boolean,
    playbackState: Int
): Boolean {
    return playWhenReady && !isPlaying && playbackState == Player.STATE_READY
}

internal fun resolveTrackSelectionParametersForBackground(
    currentTrackSelectionParameters: androidx.media3.common.TrackSelectionParameters,
    shouldDisableVideoTrack: Boolean
): androidx.media3.common.TrackSelectionParameters {
    if (!shouldDisableVideoTrack) return currentTrackSelectionParameters
    return currentTrackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
        .setMaxVideoSize(0, 0)
        .build()
}

internal fun shouldPauseBackgroundBuffering(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int
): Boolean {
    return !isPlaybackActiveForLifecycle(
        isPlaying = isPlaying,
        playWhenReady = playWhenReady,
        playbackState = playbackState
    )
}

internal fun resolveNotificationIsPlaying(
    playerIsPlaying: Boolean?,
    cachedIsPlaying: Boolean
): Boolean {
    return playerIsPlaying ?: cachedIsPlaying
}

internal fun shouldRefreshNotificationOnPlaybackStateChange(
    isActive: Boolean,
    title: String
): Boolean {
    return isActive && title.isNotBlank()
}

internal fun shouldKeepPlaybackNotificationVisible(
    isActive: Boolean,
    title: String,
    isPlaying: Boolean
): Boolean {
    return isActive && title.isNotBlank() && isPlaying
}

internal fun shouldClearStalePlaybackNotificationOnAppResume(
    isActive: Boolean,
    playerIsPlaying: Boolean
): Boolean {
    return !isActive || !playerIsPlaying
}

internal fun resolveEffectiveNotificationCoverUrl(
    incomingCoverUrl: String,
    cachedCoverUrl: String
): String {
    return incomingCoverUrl.takeIf { it.isNotBlank() } ?: cachedCoverUrl
}

internal fun <T> resolveEffectiveNotificationArtwork(
    incomingArtwork: T?,
    cachedArtwork: T?
): T? {
    return incomingArtwork ?: cachedArtwork
}

internal fun resolveNotificationIconResByPriority(
    launcherIconRes: Int,
    fallbackIconKey: String
): Int {
    return if (launcherIconRes != 0) launcherIconRes else resolveNotificationSmallIconRes(fallbackIconKey)
}

internal fun resolveLaunchActivityIconRes(context: Context): Int {
    return runCatching {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val component = launchIntent?.component ?: return@runCatching 0
        context.packageManager.getActivityInfo(component, 0).iconResource
    }.getOrDefault(0)
}

internal fun shouldRebindMediaSessionPlayer(
    sessionPlayer: Any?,
    playbackPlayer: Any?
): Boolean {
    val unwrappedSessionPlayer = when (sessionPlayer) {
        is SessionPlayerBindingHandle -> sessionPlayer.boundPlayer
        else -> sessionPlayer
    }
    return playbackPlayer != null && unwrappedSessionPlayer !== playbackPlayer
}

internal fun resolveNotificationSmallIconRes(iconKey: String): Int {
    val normalizedKey = normalizeAppIconKey(iconKey)
    return when (normalizedKey) {
        "icon_blue" -> R.mipmap.ic_launcher_blue
        "icon_neon" -> R.mipmap.ic_launcher_neon
        "icon_retro" -> R.mipmap.ic_launcher_retro
        "icon_flat" -> R.mipmap.ic_launcher_flat
        "icon_flat_material" -> R.mipmap.ic_launcher_flat_material
        "icon_anime" -> R.mipmap.ic_launcher_anime
        "icon_telegram_blue" -> R.mipmap.ic_launcher_telegram_blue
        "icon_telegram_blue_coin" -> R.mipmap.ic_launcher_telegram_blue_coin
        "icon_telegram_green" -> R.mipmap.ic_launcher_telegram_green
        "icon_telegram_pink" -> R.mipmap.ic_launcher_telegram_pink
        "icon_telegram_purple" -> R.mipmap.ic_launcher_telegram_purple
        "icon_telegram_dark" -> R.mipmap.ic_launcher_telegram_dark
        "Headphone" -> R.mipmap.ic_launcher_headphone
        "Yuki" -> R.mipmap.ic_launcher
        "icon_3d" -> R.mipmap.ic_launcher_3d
        else -> R.mipmap.ic_launcher_3d
    }
}

internal enum class MediaControlType {
    PREVIOUS,
    PLAY_PAUSE,
    NEXT
}

internal interface SessionPlayerBindingHandle {
    val boundPlayer: Any?
}

internal enum class PlaylistSkipExecutionMode {
    CALLBACK,
    DIRECT_BACKGROUND,
    NONE
}

internal fun resolveMediaControlType(controlType: Int): MediaControlType? {
    return when (controlType) {
        MiniPlayerManager.ACTION_PREVIOUS -> MediaControlType.PREVIOUS
        MiniPlayerManager.ACTION_PLAY_PAUSE -> MediaControlType.PLAY_PAUSE
        MiniPlayerManager.ACTION_NEXT -> MediaControlType.NEXT
        else -> null
    }
}

internal fun resolveMediaButtonControlType(keyCode: Int, action: Int): MediaControlType? {
    if (action != KeyEvent.ACTION_DOWN) return null
    return when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> MediaControlType.PREVIOUS
        KeyEvent.KEYCODE_MEDIA_NEXT -> MediaControlType.NEXT
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> MediaControlType.PLAY_PAUSE
        else -> null
    }
}

internal fun resolveExternalTransportActionOrder(): IntArray {
    return intArrayOf(
        MiniPlayerManager.ACTION_NEXT,
        MiniPlayerManager.ACTION_PLAY_PAUSE,
        MiniPlayerManager.ACTION_PREVIOUS
    )
}

internal fun shouldEnableQueueNavigation(playlistSize: Int): Boolean {
    return playlistSize > 1
}

internal fun resolveQueueCurrentIndexForPlaylistRebuild(
    playlist: List<PlaylistItem>,
    currentBvid: String?,
    fallbackIndex: Int
): Int {
    if (playlist.isEmpty()) return -1
    val targetBvid = currentBvid?.trim().orEmpty()
    if (targetBvid.isNotEmpty()) {
        val matchedIndex = playlist.indexOfFirst { it.bvid == targetBvid }
        if (matchedIndex >= 0) return matchedIndex
    }
    return fallbackIndex.coerceIn(0, playlist.lastIndex)
}

internal fun resolvePlaylistIndexSyncFromQueue(
    playerCurrentIndex: Int,
    playlistSize: Int,
    currentPlaylistIndex: Int
): Int? {
    if (playerCurrentIndex !in 0 until playlistSize) return null
    if (playerCurrentIndex == currentPlaylistIndex) return null
    return playerCurrentIndex
}

internal fun buildQueueMetadataItems(playlist: List<PlaylistItem>): List<MediaItem> {
    return playlist.map { item ->
        MediaItem.Builder()
            .setMediaId(item.bvid)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setDisplayTitle(item.title)
                    .setArtist(item.owner)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }
}

internal data class QueueNavigationAvailability(
    val hasNext: Boolean,
    val hasPrevious: Boolean
) {
    val isEnabled: Boolean
        get() = hasNext || hasPrevious
}

internal fun resolveQueueNavigationCommandIds(
    hasNext: Boolean,
    hasPrevious: Boolean
): Set<Int> {
    val commands = mutableSetOf<Int>()
    if (hasNext) {
        commands.add(Player.COMMAND_SEEK_TO_NEXT)
        commands.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
    }
    if (hasPrevious) {
        commands.add(Player.COMMAND_SEEK_TO_PREVIOUS)
        commands.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
    }
    return commands
}

internal fun buildQueueAwarePlayerCommands(
    baseCommands: Player.Commands,
    hasNext: Boolean,
    hasPrevious: Boolean
): Player.Commands {
    val builder = baseCommands.buildUpon()
    builder.remove(Player.COMMAND_SEEK_TO_NEXT)
    builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS)
    builder.remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
    builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
    resolveQueueNavigationCommandIds(
        hasNext = hasNext,
        hasPrevious = hasPrevious
    ).forEach { command ->
        builder.add(command)
    }
    return builder.build()
}

internal fun shouldUseStandardQueueNavigation(
    playlist: List<PlaylistItem>,
    isLiveMode: Boolean
): Boolean {
    return !isLiveMode && playlist.isNotEmpty() && playlist.none { it.isBangumi }
}

internal fun shouldExposeVirtualQueueToSession(
    playlistSize: Int,
    timelineWindowCount: Int,
    isLiveMode: Boolean
): Boolean {
    if (isLiveMode || playlistSize <= 0) return false
    return timelineWindowCount == playlistSize
}

internal fun resolvePlaylistSkipExecutionMode(
    item: PlaylistItem?,
    callbackAvailable: Boolean,
    hasDirectPlaybackContext: Boolean
): PlaylistSkipExecutionMode {
    if (item == null) return PlaylistSkipExecutionMode.NONE
    if (item.isBangumi) {
        return if (callbackAvailable) PlaylistSkipExecutionMode.CALLBACK else PlaylistSkipExecutionMode.NONE
    }
    if (callbackAvailable) return PlaylistSkipExecutionMode.CALLBACK
    return if (hasDirectPlaybackContext) {
        PlaylistSkipExecutionMode.DIRECT_BACKGROUND
    } else {
        PlaylistSkipExecutionMode.NONE
    }
}

internal fun dispatchPlaylistNavigation(
    item: PlaylistItem?,
    callback: ((PlaylistItem) -> Unit)?
): Boolean {
    if (item == null || item.isBangumi || callback == null) return false
    callback(item)
    return true
}

internal fun dispatchBangumiNavigation(
    item: PlaylistItem?,
    callback: ((PlaylistItem) -> Unit)?
): Boolean {
    if (item == null || !item.isBangumi || callback == null) return false
    val seasonId = item.seasonId ?: 0L
    val epId = item.epId ?: 0L
    if (seasonId <= 0L || epId <= 0L) return false
    callback(item)
    return true
}

/**
 *  全局小窗管理器
 * 
 * 负责管理跨导航的视频播放状态，支持：
 * 1. 在视频详情页和首页之间保持播放连续性
 * 2. 小窗模式下的播放控制
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MiniPlayerManager private constructor(private val context: Context) : 
    com.android.purebilibili.core.lifecycle.BackgroundManager.BackgroundStateListener {

    companion object {
        @Volatile
        private var INSTANCE: MiniPlayerManager? = null

        fun getInstance(context: Context): MiniPlayerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MiniPlayerManager(context.applicationContext).also { 
                    INSTANCE = it 
                }
            }
        }
        
        //  [新增] 媒体控制常量
        const val ACTION_MEDIA_CONTROL = "com.android.purebilibili.MEDIA_CONTROL"
        const val EXTRA_CONTROL_TYPE = "control_type"
        const val ACTION_PREVIOUS = 1
        const val ACTION_PLAY_PAUSE = 2
        const val ACTION_NEXT = 3
    }

    // --- 协程作用域 ---
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val backgroundPlaybackUseCase = VideoPlaybackUseCase()
    private var backgroundSkipJob: kotlinx.coroutines.Job? = null
    
    // 🔋 [后台优化] 低内存模式状态
    private var isLowMemoryMode = false
    private var savedTrackParams: androidx.media3.common.TrackSelectionParameters? = null
    
    //  [新增] 媒体控制广播接收器
    private val mediaControlReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_MEDIA_CONTROL) {
                val controlType = resolveMediaControlType(intent.getIntExtra(EXTRA_CONTROL_TYPE, 0))
                if (controlType != null) {
                    Logger.d(TAG, "🔔 通知栏控制: $controlType")
                    performMediaControl(controlType)
                }
            }
        }
    }
    private var mediaControlReceiverRegistered = false
    private var backgroundListenerRegistered = false
    
    init {
        backgroundPlaybackUseCase.initWithContext(context)
        //  注册媒体控制广播接收器
        val filter = android.content.IntentFilter(ACTION_MEDIA_CONTROL)
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            mediaControlReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        mediaControlReceiverRegistered = true
        Logger.d(TAG, " 媒体控制广播接收器已注册")
        
        // 🔋 注册后台状态监听
        com.android.purebilibili.core.lifecycle.BackgroundManager.addListener(this)
        backgroundListenerRegistered = true
        Logger.d(TAG, "🔋 后台状态监听器已注册")
    }
    
    // ========== 🔋 后台状态回调 ==========
    
    override fun onEnterBackground() {
        if (!isActive) return
        
        isLowMemoryMode = true
        val currentPlayer = player ?: return
        
        // 🔧 [优化] 如果未在播放，直接暂停/停止缓冲，避免浪费 CDN 请求
        val shouldPauseBuffering = shouldPauseBackgroundBuffering(
            isPlaying = currentPlayer.isPlaying,
            playWhenReady = currentPlayer.playWhenReady,
            playbackState = currentPlayer.playbackState
        )
        val shouldKeepBackgroundAudio = shouldContinueBackgroundAudio()
        val shouldDisableVideoTrack = shouldDisableVideoTrackOnEnterBackground(
            shouldPauseBuffering = shouldPauseBuffering,
            shouldContinueBackgroundAudio = shouldKeepBackgroundAudio
        )
        if (shouldDisableVideoTrack) {
            if (savedTrackParams == null) {
                savedTrackParams = currentPlayer.trackSelectionParameters
            }
            currentPlayer.trackSelectionParameters = resolveTrackSelectionParametersForBackground(
                currentTrackSelectionParameters = currentPlayer.trackSelectionParameters,
                shouldDisableVideoTrack = true
            )
        }
        if (shouldClearVideoSurfaceOnEnterBackground(
                shouldDisableVideoTrack = shouldDisableVideoTrack,
                shouldContinueBackgroundAudio = shouldKeepBackgroundAudio
            )
        ) {
            currentPlayer.clearVideoSurface()
        }
        if (shouldTrimDanmakuCachesOnEnterBackground(shouldDisableVideoTrack)) {
            DanmakuManager.trimCachesForBackgroundIfPresent()
        }
        if (shouldPauseBuffering) {
            currentPlayer.pause()
            Logger.d(TAG, "🔋 后台模式：未播放，暂停缓冲并禁用视频轨道")
            return
        }
        
        // 判断是否需要后台音频
        if (shouldKeepBackgroundAudio) {
            Logger.d(TAG, "🔋 后台模式：禁用视频轨道，仅保留音频")
        }
    }
    
    override fun onEnterForeground() {
        if (!isLowMemoryMode) return
        
        isLowMemoryMode = false
        val currentPlayer = player ?: return
        val hadSavedTrackParams = savedTrackParams != null
        
        // 恢复视频轨道
        savedTrackParams?.let { originalParams ->
            currentPlayer.trackSelectionParameters = originalParams
            savedTrackParams = null
            Logger.d(TAG, "🌅 前台模式：恢复视频轨道")
        }

        if (shouldRefreshVideoFrameOnEnterForeground(
                hadSavedTrackParams = hadSavedTrackParams,
                hasMediaItems = currentPlayer.mediaItemCount > 0,
                playbackState = currentPlayer.playbackState
            )
        ) {
            val restorePositionMs = currentPlayer.currentPosition.coerceAtLeast(0L)
            currentPlayer.seekTo(restorePositionMs)
            Logger.d(TAG, "🎬 前台模式：请求重新渲染当前帧，避免返回视频页黑屏")
        }

        if (shouldKickPlaybackAfterForegroundTrackRestore(
                hadSavedTrackParams = hadSavedTrackParams,
                playWhenReady = currentPlayer.playWhenReady,
                playbackState = currentPlayer.playbackState
            )
        ) {
            currentPlayer.play()
            Logger.d(TAG, "▶️ 前台模式：恢复视频轨道后主动唤醒渲染链路")
        } else if (shouldResumePlaybackOnEnterForeground(
                playWhenReady = currentPlayer.playWhenReady,
                isPlaying = currentPlayer.isPlaying,
                playbackState = currentPlayer.playbackState
            )
        ) {
            currentPlayer.play()
            Logger.d(TAG, "▶️ 前台模式：恢复卡在 READY 的播放会话")
        }
    }


    // --- 播放器状态 (可观察) ---
    var isActive by mutableStateOf(false)
        private set
    
    var isMiniMode by mutableStateOf(false)
        private set
    
    // 🚀 [新增] 导航抑制标志：在导航到视频页面期间不显示小窗
    var isNavigatingToVideo by mutableStateOf(false)
    
    // 🎯 [新增] 导航离开标志：区分"应用导航离开"和"应用进入后台"
    // true = 用户通过返回按钮离开视频页面，应该停止播放
    // false = 用户按 Home 键离开应用，应该继续后台播放
    var isLeavingByNavigation by mutableStateOf(false)
    @Volatile
    private var lastUserLeaveHintAtMs: Long = 0L

    var isPlaying by mutableStateOf(false)
        private set

    var currentPosition by mutableLongStateOf(0L)
        private set

    var duration by mutableLongStateOf(0L)
        private set
    
    var progress by mutableFloatStateOf(0f)
        private set

    @Volatile
    private var playbackServiceRequested = false
    @Volatile
    private var lastForegroundStartAtMs = 0L

    // --- 当前视频信息 ---
    var currentBvid by mutableStateOf<String?>(null)
        private set

    var currentTitle by mutableStateOf("")
        private set

    var currentCover by mutableStateOf("")
        private set

    var currentOwner by mutableStateOf("")
        private set
    
    //  [新增] 当前视频的 cid，用于弹幕加载
    var currentCid by mutableLongStateOf(0L)
        private set

    //  [新增] 当前视频的 aid，用于弹幕元数据加载
    var currentAid by mutableLongStateOf(0L)
        private set
    
    //  [新增] 缓存的视频详情页 UI 状态，用于从小窗返回时恢复
    var cachedUiState: PlayerUiState.Success? = null
        private set
    
    //  [新增] 小窗入场方向：true=从左边进入，false=从右边进入
    var entryFromLeft by mutableStateOf(false)
        private set

    // 📺 [新增] 直播小窗模式
    var isLiveMode by mutableStateOf(false)
        private set
    var currentRoomId by mutableLongStateOf(0L)
        private set
    // 直播主播名（展开时传回 LivePlayerScreen）
    var currentLiveUname by mutableStateOf("")
        private set

    // [新增] 保存当前通知实例，供 PlaybackService 使用
    var currentNotification: android.app.Notification? = null
        private set
    private var cachedArtworkBitmap: Bitmap? = null
    
    //  [新增] 缓存 UI 状态
    fun cacheUiState(state: PlayerUiState.Success) {
        cachedUiState = state
        com.android.purebilibili.core.util.Logger.d(TAG, " 缓存 UI 状态: ${state.info.title}")
    }
    
    //  [新增] 获取并清除缓存的 UI 状态
    fun consumeCachedUiState(): PlayerUiState.Success? {
        val state = cachedUiState
        // 不清除缓存，允许多次复用
        return state
    }

    // --- ExoPlayer 实例 ---
    private var _player: ExoPlayer? = null
    //  外部播放器引用（来自 VideoDetailScreen 的 VideoPlayerState）
    private var _externalPlayer: ExoPlayer? = null
    //  优先使用外部播放器（如果存在）
    val player: ExoPlayer?
        get() = _externalPlayer ?: _player
    
    //  [修复2] 检查是否有外部播放器
    val hasExternalPlayer: Boolean
        get() = _externalPlayer != null

    /**
     * 判断指定 player 是否仍由 MiniPlayerManager 持有。
     * 仅用于销毁阶段的身份校验，避免误保留旧实例。
     */
    fun isPlayerManaged(target: ExoPlayer): Boolean {
        return _externalPlayer === target || _player === target
    }

    /**
     * 仅当外部播放器引用匹配目标实例时才清理，避免误清理新播放器引用。
     */
    fun clearExternalPlayerIfMatches(target: ExoPlayer): Boolean {
        if (_externalPlayer === target) {
            Logger.d(TAG, "clearExternalPlayerIfMatches: cleared external player ${target.hashCode()}")
            _externalPlayer = null
            return true
        }
        return false
    }
    
    //  [修复2] 清除外部播放器引用（从小窗返回全屏时调用）
    fun resetExternalPlayer() {
        Logger.d(TAG, " resetExternalPlayer: clearing external player reference")
        _externalPlayer = null
    }

    // --- MediaSession ---
    var mediaSession: MediaSession? = null
    private var mediaSessionNavigationAvailability: QueueNavigationAvailability? = null

    private inner class QueueAwareSessionPlayer(
        private val delegatePlayer: Player
    ) : ForwardingPlayer(delegatePlayer), SessionPlayerBindingHandle {

        override val boundPlayer: Any?
            get() = delegatePlayer

        private fun queueItems(): List<MediaItem> {
            val playlist = PlaylistManager.playlist.value
            return if (shouldUseStandardQueueNavigation(playlist, isLiveMode)) {
                buildQueueMetadataItems(playlist)
            } else {
                emptyList()
            }
        }

        private fun sessionQueueItems(): List<MediaItem> {
            val items = queueItems()
            return if (
                shouldExposeVirtualQueueToSession(
                    playlistSize = items.size,
                    timelineWindowCount = delegatePlayer.currentTimeline.windowCount,
                    isLiveMode = isLiveMode
                )
            ) {
                items
            } else {
                emptyList()
            }
        }

        private fun queueCurrentIndex(): Int {
            val items = sessionQueueItems()
            return resolveQueueCurrentIndexForPlaylistRebuild(
                playlist = PlaylistManager.playlist.value,
                currentBvid = currentBvid,
                fallbackIndex = PlaylistManager.currentIndex.value
                    .takeIf { it >= 0 }
                    ?: delegatePlayer.currentMediaItemIndex
            ).coerceIn(-1, (items.size - 1).coerceAtLeast(-1))
        }

        override fun getAvailableCommands(): Player.Commands {
            val navigationAvailability = resolveSessionNavigationAvailability()
            return buildQueueAwarePlayerCommands(
                baseCommands = super.getAvailableCommands(),
                hasNext = navigationAvailability.hasNext,
                hasPrevious = navigationAvailability.hasPrevious
            )
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return getAvailableCommands().contains(command)
        }

        override fun getMediaItemCount(): Int {
            val items = sessionQueueItems()
            return if (items.isNotEmpty()) items.size else super.getMediaItemCount()
        }

        override fun getCurrentMediaItemIndex(): Int {
            val items = sessionQueueItems()
            return if (items.isNotEmpty()) queueCurrentIndex() else super.getCurrentMediaItemIndex()
        }

        override fun getCurrentMediaItem(): MediaItem? {
            val items = sessionQueueItems()
            val index = queueCurrentIndex()
            return items.getOrNull(index) ?: super.getCurrentMediaItem()
        }

        override fun getMediaItemAt(index: Int): MediaItem {
            val items = sessionQueueItems()
            return items.getOrNull(index) ?: super.getMediaItemAt(index)
        }

        override fun hasNextMediaItem(): Boolean {
            val items = queueItems()
            return if (items.isNotEmpty()) {
                resolveSessionNavigationAvailability().hasNext
            } else {
                super.hasNextMediaItem()
            }
        }

        override fun hasPreviousMediaItem(): Boolean {
            val items = queueItems()
            return if (items.isNotEmpty()) {
                resolveSessionNavigationAvailability().hasPrevious
            } else {
                super.hasPreviousMediaItem()
            }
        }

        override fun seekToNextMediaItem() {
            val items = queueItems()
            if (items.isNotEmpty()) {
                playNext()
            } else {
                super.seekToNextMediaItem()
            }
        }

        override fun seekToPreviousMediaItem() {
            val items = queueItems()
            if (items.isNotEmpty()) {
                playPrevious()
            } else {
                super.seekToPreviousMediaItem()
            }
        }

        override fun seekToNext() {
            val items = queueItems()
            if (items.isNotEmpty()) {
                playNext()
            } else {
                super.seekToNext()
            }
        }

        override fun seekToPrevious() {
            val items = queueItems()
            if (items.isNotEmpty()) {
                playPrevious()
            } else {
                super.seekToPrevious()
            }
        }
    }
    
    //  [新增] MediaSession 回调处理器，支持系统媒体控件
    private val mediaSessionCallback = object : MediaSession.Callback {
        //  处理系统媒体按钮事件
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            Logger.d(TAG, " onMediaButtonEvent: action=${intent.action}")
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
            }
            val controlType = keyEvent?.let { resolveMediaButtonControlType(it.keyCode, it.action) }
            if (controlType != null) {
                Logger.d(TAG, "🎮 媒体按键控制: $controlType")
                performMediaControl(controlType)
                return true
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }
        
        //  处理自定义命令（上一首/下一首）
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: android.os.Bundle
        ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
            Logger.d(TAG, " onCustomCommand: ${customCommand.customAction}")
            when (customCommand.customAction) {
                SESSION_COMMAND_SKIP_TO_PREVIOUS -> performMediaControl(MediaControlType.PREVIOUS)
                SESSION_COMMAND_SKIP_TO_NEXT -> performMediaControl(MediaControlType.NEXT)
            }
            return com.google.common.util.concurrent.Futures.immediateFuture(
                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
            )
        }
        
        //  设置可用操作
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            Logger.d(TAG, " onConnect: ${controller.packageName}")
            val navigationAvailability = resolveSessionNavigationAvailability()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                )
                .setAvailablePlayerCommands(
                    buildQueueAwarePlayerCommands(
                        baseCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
                        hasNext = navigationAvailability.hasNext,
                        hasPrevious = navigationAvailability.hasPrevious
                    )
                )
                .build()
        }
    }
    
    // ==========  小窗模式判断方法 ==========
    
    /**
     * 获取当前小窗模式设置
     */
    fun getCurrentMode(): com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode {
        return com.android.purebilibili.core.store.SettingsManager.getMiniPlayerModeSync(context)
    }
    
    /**
     * 判断是否应该显示应用内小窗（返回首页时）
     *  只有 IN_APP_ONLY 模式才显示应用内悬浮小窗
     */
    fun shouldShowInAppMiniPlayer(): Boolean {
        val mode = getCurrentMode()
        val stopPlaybackOnExit = SettingsManager.getStopPlaybackOnExitSync(context)
        val result = shouldShowInAppMiniPlayerByPolicy(
            mode = mode,
            isActive = isActive,
            isNavigatingToVideo = isNavigatingToVideo,
            stopPlaybackOnExit = stopPlaybackOnExit
        )
        Logger.d(TAG, "📲 shouldShowInAppMiniPlayer: mode=$mode, isActive=$isActive, navigating=$isNavigatingToVideo, result=$result")
        return result
    }
    
    /**
     * 判断是否应该进入系统画中画模式（按 Home 键时）
     */
    fun shouldEnterPip(): Boolean {
        val mode = getCurrentMode()
        val stopPlaybackOnExit = SettingsManager.getStopPlaybackOnExitSync(context)
        val result = shouldEnterPipByPolicy(
            mode = mode,
            isActive = isActive,
            stopPlaybackOnExit = stopPlaybackOnExit
        )
        Logger.d(TAG, " shouldEnterPip: mode=$mode, isActive=$isActive, result=$result")
        return result
    }
    
    /**
     * 🎯 判断是否应该继续后台音频播放
     * 
     * OFF模式（官方B站行为）：
     * - 切到桌面 → 继续后台播放
     * - 通过返回按钮离开视频页 → 停止播放
     */
    fun shouldContinueBackgroundAudio(): Boolean {
        val mode = getCurrentMode()
        val backgroundPlaybackEnabled = SettingsManager.getBackgroundPlaybackEnabledSync(context)
        val stopPlaybackOnExit = SettingsManager.getStopPlaybackOnExitSync(context)
        return shouldContinueBackgroundAudioByPolicy(
            backgroundPlaybackEnabled = backgroundPlaybackEnabled,
            mode = mode,
            isActive = isActive,
            isLeavingByNavigation = isLeavingByNavigation,
            stopPlaybackOnExit = stopPlaybackOnExit
        )
    }

    fun refreshMediaSessionBinding() {
        val currentPlayer = player ?: return
        val navigationAvailability = resolveSessionNavigationAvailability()
        if (
            shouldRebindMediaSessionPlayer(mediaSession?.player, currentPlayer) ||
            mediaSessionNavigationAvailability != navigationAvailability
        ) {
            updateMediaSession(currentPlayer)
        }
        isPlaying = resolveNotificationIsPlaying(
            playerIsPlaying = currentPlayer.isPlaying,
            cachedIsPlaying = isPlaying
        )
    }

    fun clearPlaybackNotificationIfIdleOnResume() {
        if (!shouldClearStalePlaybackNotificationOnAppResume(
                isActive = isActive,
                playerIsPlaying = player?.isPlaying == true
            )
        ) {
            return
        }
        playbackServiceRequested = false
        clearPlaybackNotificationArtifacts()
    }
    
    /**
     * 🔄 重置导航离开标志（在视频页计入时调用）
     */
    fun resetNavigationFlag() {
        isLeavingByNavigation = false
        Logger.d(TAG, "🔄 resetNavigationFlag: isLeavingByNavigation=false")
    }

    fun markUserLeaveHint() {
        lastUserLeaveHintAtMs = SystemClock.elapsedRealtime()
    }

    fun clearUserLeaveHint() {
        lastUserLeaveHintAtMs = 0L
    }

    fun hasRecentUserLeaveHint(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
        val last = lastUserLeaveHintAtMs
        if (last <= 0L) return false
        val delta = nowElapsedMs - last
        return delta in 0L..USER_LEAVE_HINT_WINDOW_MS
    }
    
    /**
     * 🎯 标记通过导航离开（在返回按钮点击时调用）
     *  [修复] 在默认模式和画中画模式下立即暂停播放，解决生命周期时序问题
     */
    fun markLeavingByNavigation(expectedBvid: String? = null) {
        if (!shouldHandleNavigationLeaveForBvid(expectedBvid = expectedBvid, currentBvid = currentBvid)) {
            Logger.d(
                TAG,
                "⏭️ markLeavingByNavigation ignored: expected=$expectedBvid, current=$currentBvid"
            )
            return
        }
        isLeavingByNavigation = true
        Logger.d(TAG, "🎯 markLeavingByNavigation: isLeavingByNavigation=true")
        
        //  [修复] 默认模式和画中画模式下，通过导航离开时应立即停止播放
        // 原因：ON_PAUSE 事件可能在此标志设置之前触发，导致音频继续播放
        // 画中画模式说明："切到桌面进入系统画中画"，返回主页时应停止
        val mode = getCurrentMode()
        val stopPlaybackOnExit = SettingsManager.getStopPlaybackOnExitSync(context)
        if (shouldClearPlaybackNotificationOnNavigationExit(mode, stopPlaybackOnExit)) {
            Logger.d(TAG, "🔇 ${mode.label}：通过导航离开，立即停止播放")
            // 停止所有播放器（外部和内部）
            _externalPlayer?.let { player ->
                player.volume = 0f
                player.playWhenReady = false
                player.pause()
            }
            _player?.let { player ->
                player.volume = 0f
                player.playWhenReady = false
                player.pause()
            }
            
            // 🔧 [修复] 标记非活跃状态，允许 VideoPlayerState.onDispose 正确释放资源
            // 解决音频泄漏问题：返回首页后音频仍继续播放
            isActive = false
            playbackServiceRequested = false
            _externalPlayer = null
            clearPlaybackNotificationArtifacts()
            Logger.d(TAG, "🔧 标记 isActive=false，清除外部播放器引用")
        }
    }
    
    /**
     * 判断小窗功能是否完全关闭
     * 🔄 [简化] 现在只有 OFF 和 SYSTEM_PIP，OFF 模式下返回 false（因为支持后台播放）
     */
    fun isMiniPlayerDisabled(): Boolean {
        // 两种模式都支持某种形式的后台播放，所以不再"完全关闭"
        return false
    }


    /**
     * 初始化播放器（如果尚未初始化）
     */
    fun ensurePlayer(): ExoPlayer {
        if (_player == null) {
            Logger.d(TAG) { "Creating new ExoPlayer instance" }
            
            val headers = mapOf(
                "Referer" to "https://www.bilibili.com",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            val dataSourceFactory = OkHttpDataSource.Factory(NetworkModule.playbackOkHttpClient)
                .setDefaultRequestProperties(headers)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            val miniPlayerMode = SettingsManager.getMiniPlayerModeSync(context)
            val stopPlaybackOnExit = SettingsManager.getStopPlaybackOnExitSync(context)
            val audioFocusEnabled = SettingsManager.getAudioFocusEnabledSync(context)

            _player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .setAudioAttributes(
                    audioAttributes,
                    resolveHandleAudioFocusByPolicy(audioFocusEnabled = audioFocusEnabled)
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(
                    resolvePlaybackWakeMode(
                        miniPlayerMode = miniPlayerMode,
                        stopPlaybackOnExit = stopPlaybackOnExit
                    )
                )
                .build()
                .apply {
                    addListener(playerListener)
                    //  [修复] 确保音量正常
                    volume = 1.0f
                    setPlaybackSpeed(SettingsManager.getPreferredPlaybackSpeedSync(context))
                    prepare()
                }
            
            // 创建 MediaSession
            // 🎯 [修复] 使用 MainActivity 以保持单一任务栈，防止进入 VideoActivity 导致状态丢失
            val sessionIntent = Intent(context, com.android.purebilibili.MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                // 占位符 URL，实际点击时会复用 Activity 栈顶
                data = Uri.parse("https://www.bilibili.com/video/")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, sessionIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            mediaSession = MediaSession.Builder(context, QueueAwareSessionPlayer(_player!!))
                .setSessionActivity(pendingIntent)
                .setCallback(mediaSessionCallback)  //  支持系统媒体控件
                .build()
            mediaSessionNavigationAvailability = resolveSessionNavigationAvailability()
        }
        return _player!!
    }


    /**
     * 开始播放新视频
     */
    fun startVideo(
        bvid: String,
        title: String,
        cover: String,
        owner: String,
        videoUrl: String,
        audioUrl: String?
    ) {
        Logger.d(TAG) { "startVideo: bvid=$bvid, title=$title" }
        
        ensurePlayer()
        
        // 如果是同一个视频，不重新加载
        if (currentBvid == bvid && _player?.isPlaying == true) {
            Logger.d(TAG) { "Same video already playing, skip reload" }
            return
        }

        currentBvid = bvid
        currentTitle = title
        currentCover = cover
        currentOwner = owner
        isActive = true
        isMiniMode = false

        // 构建媒体源
        val headers = mapOf(
            "Referer" to "https://www.bilibili.com",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        )
        val dataSourceFactory = OkHttpDataSource.Factory(NetworkModule.playbackOkHttpClient)
            .setDefaultRequestProperties(headers)

        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUrl))

        if (audioUrl != null) {
            val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(audioUrl))
            val mergedSource = MergingMediaSource(videoSource, audioSource)
            _player?.setMediaSource(mergedSource)
        } else {
            _player?.setMediaSource(videoSource)
        }

        //  [修复] 确保音量正常
        _player?.volume = 1.0f
        _player?.prepare()
        _player?.playWhenReady = true

        // 更新媒体元数据
        updateMediaMetadata(title, owner, cover)
    }

    /**
     * 进入小窗模式
     * @param forced 强制进入（点击小窗按钮时使用），忽略模式检查
     */
    fun enterMiniMode(forced: Boolean = false) {
        val mode = getCurrentMode()
        Logger.d(TAG) { "📲 enterMiniMode called: isActive=$isActive, forced=$forced, mode=$mode" }
        
        // 非强制模式下，只有 IN_APP_ONLY 才自动进入小窗
        if (!forced && mode != com.android.purebilibili.core.store.SettingsManager.MiniPlayerMode.IN_APP_ONLY) {
            Logger.d(TAG) { "⚠️ Auto mini player only works in IN_APP_ONLY mode, current mode=$mode" }
            return
        }
        
        if (!isActive) {
            Logger.w(TAG, "⚠️ Cannot enter mini mode: isActive is false!")
            return
        }
        Logger.d(TAG) { "📲 Entering mini mode for video: $currentTitle (forced=$forced)" }
        isMiniMode = true
        
        // 🔔 [修复] 进入小窗时更新媒体通知（系统控制中心显示）
        if (currentTitle.isNotEmpty()) {
            updateMediaMetadata(currentTitle, currentOwner, currentCover)
        }
    }

    //  [新增] 是否执行退出动画 (用于在点击新视频时瞬间消失，避免闪烁)
    var shouldAnimateExit by mutableStateOf(true)
        private set

    /**
     * 退出小窗模式（返回全屏详情页）
     * @param animate 是否执行退出动画
     */
    fun exitMiniMode(animate: Boolean = true) {
        Logger.d(TAG) { "Exiting mini mode, animate=$animate" }
        shouldAnimateExit = animate
        isMiniMode = false
    }

    /**
     * 停止播放并关闭小窗
     */
    fun dismiss() {
        Logger.d(TAG) { "Dismissing mini player (isLiveMode=$isLiveMode)" }
        
        //  [修复] 先停止所有播放器的声音
        _externalPlayer?.let { 
            it.pause()
            it.stop()
            Logger.d(TAG) { "🔇 Stopped external player" }
        }
        _player?.let {
            it.pause()
            it.stop()
            Logger.d(TAG, "🔇 Stopped internal player")
        }
        
        // ⚡ [性能优化] player 延迟释放，避免阻塞关闭动画
        val playerToRelease = _externalPlayer
        if (playerToRelease != null) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    playerToRelease.release()
                    Logger.d(TAG, "⚡ 延迟释放外部播放器")
                } catch (e: Exception) {
                    Logger.e(TAG, "释放外部播放器失败", e)
                }
            }
        }
        
        isMiniMode = false
        isActive = false
        playbackServiceRequested = false
        lastForegroundStartAtMs = 0L
        isPlaying = false  //  [修复] 同步播放状态
        _externalPlayer = null
        currentBvid = null
        cachedUiState = null  //  [修复] 清除缓存的 UI 状态
        isLiveMode = false  // 📺 清除直播模式
        currentRoomId = 0L
        currentLiveUname = ""
        
        releaseMediaSession()
        clearPlaybackNotificationArtifacts()
    }

    private fun releaseMediaSession() {
        mediaSession?.release()
        mediaSession = null
        mediaSessionNavigationAvailability = null
    }

    private fun clearPlaybackNotificationArtifacts() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        currentNotification = null
        cachedArtworkBitmap = null

        try {
            val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_STOP_FOREGROUND
            }
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to stop playback service", e)
        }
    }



    /**
     *  设置视频信息并关联外部播放器（用于小窗模式）
     * 这个方法不创建新播放器，而是使用 VideoDetailScreen 的播放器
     * @param fromLeft  是否从左边进入（用于小窗动画方向）
     */
    fun setVideoInfo(
        bvid: String,
        title: String,
        cover: String,
        owner: String,
        cid: Long,  //  [新增] cid 用于弹幕加载
        aid: Long = 0, // [新增] aid
        externalPlayer: ExoPlayer,
        fromLeft: Boolean = false  //  [新增] 入场方向
    ) {
        Logger.d(TAG, "setVideoInfo: bvid=$bvid, title=$title, cid=$cid, aid=$aid, fromLeft=$fromLeft")
        currentBvid = bvid
        currentTitle = title
        currentCover = cover
        currentOwner = owner
        currentCid = cid  //  保存 cid
        currentAid = aid  //  保存 aid
        entryFromLeft = fromLeft  //  保存入场方向
        isLiveMode = false  // 📺 视频模式
        
        // 🛑 [修复] 如果存在旧的外部播放器且不同于新的（切换视频场景），必须释放旧的防止泄漏/重音
        if (_externalPlayer != null && _externalPlayer != externalPlayer) {
            Logger.d(TAG, "🛑 Releasing old external player: ${_externalPlayer.hashCode()} -> ${externalPlayer.hashCode()}")
            try {
                _externalPlayer?.stop()
                _externalPlayer?.release()
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to release old external player", e)
            }
        }
        
        _externalPlayer = externalPlayer
        isActive = true
        isMiniMode = false
        
        // 🎯 [修复] 统一 MediaSession 管理：将外部播放器关联到全局 Session
        // 这样在 Activity 销毁后，后台服务仍能通过此 Session 控制播放
        updateMediaSession(externalPlayer)
        
        // 同步播放状态
        isPlaying = resolveNotificationIsPlaying(
            playerIsPlaying = externalPlayer.isPlaying,
            cachedIsPlaying = isPlaying
        )
        duration = externalPlayer.duration.coerceAtLeast(0L)
    }
    
    /**
     * 📺 [新增] 设置直播信息并关联外部播放器（用于直播小窗模式）
     * 与 setVideoInfo 类似，但使用 roomId 标识直播间
     */
    fun setLiveInfo(
        roomId: Long,
        title: String,
        cover: String,
        uname: String,
        externalPlayer: ExoPlayer,
        fromLeft: Boolean = false
    ) {
        Logger.d(TAG, "📺 setLiveInfo: roomId=$roomId, title=$title, uname=$uname")
        currentRoomId = roomId
        currentTitle = title
        currentCover = cover
        currentOwner = uname
        currentLiveUname = uname
        currentBvid = null  // 直播没有 bvid
        currentCid = 0L
        currentAid = 0L
        isLiveMode = true
        entryFromLeft = fromLeft
        
        // 释放旧的外部播放器（如果有且不同）
        if (_externalPlayer != null && _externalPlayer != externalPlayer) {
            try {
                _externalPlayer?.stop()
                _externalPlayer?.release()
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to release old external player", e)
            }
        }
        
        _externalPlayer = externalPlayer
        isActive = true
        isMiniMode = false
        
        updateMediaSession(externalPlayer)
        isPlaying = resolveNotificationIsPlaying(
            playerIsPlaying = externalPlayer.isPlaying,
            cachedIsPlaying = isPlaying
        )
        duration = 0L  // 直播没有固定时长

        // 📺 直播也需要推送媒体元数据与前台通知，避免后台被系统快速回收。
        updateMediaMetadata(
            title = title.ifBlank { "直播中" },
            artist = uname.ifBlank { "直播" },
            coverUrl = cover
        )
    }
    
    /**
     * 🎯 [新增] 更新 MediaSession 关联的播放器
     * 允许在内部播放器和外部播放器（Activity 提供）之间平滑切换
     */
    private fun updateMediaSession(newPlayer: Player) {
        val navigationAvailability = resolveSessionNavigationAvailability()
        if (
            shouldRebindMediaSessionPlayer(mediaSession?.player, newPlayer) ||
            mediaSessionNavigationAvailability != navigationAvailability
        ) {
            Logger.d(
                TAG,
                "🎯 Updating MediaSession player: ${mediaSession?.player.hashCode()} -> ${newPlayer.hashCode()}, navigation=$navigationAvailability"
            )
            
            // 如果已经存在 session，先释放旧的
            mediaSession?.release()
            
            // 构建新的 Session
            val sessionActivityPendingIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, com.android.purebilibili.MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("https://www.bilibili.com/video/$currentBvid")
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val sessionId = "bilipai_shared_session" // 使用固定 ID 保持一致性
            mediaSession = MediaSession.Builder(context, QueueAwareSessionPlayer(newPlayer))
                .setId(sessionId)
                .setSessionActivity(sessionActivityPendingIntent)
                .setCallback(mediaSessionCallback)
                .build()
            mediaSessionNavigationAvailability = navigationAvailability
            
            Logger.d(TAG, "✅ MediaSession updated and bound to new player")
        }
    }
    
    /**
     *  设置小窗入场方向
     */
    fun setEntryDirection(fromLeft: Boolean) {
        entryFromLeft = fromLeft
        Logger.d(TAG, "setEntryDirection: fromLeft=$fromLeft")
    }

    /**
     * 暂停/播放切换
     */
    fun togglePlayPause() {
        val currentPlayer = player ?: return
        if (currentPlayer.isPlaying) {
            currentPlayer.pause()
        } else {
            currentPlayer.play()
        }
    }

    private fun performMediaControl(controlType: MediaControlType) {
        when (controlType) {
            MediaControlType.PREVIOUS -> playPrevious()
            MediaControlType.PLAY_PAUSE -> togglePlayPause()
            MediaControlType.NEXT -> playNext()
        }
    }

    private fun hasDirectBackgroundPlaybackContext(): Boolean {
        return player != null
    }

    private fun resolveSessionNavigationAvailability(): QueueNavigationAvailability {
        val playlist = PlaylistManager.playlist.value
        if (!shouldUseStandardQueueNavigation(playlist, isLiveMode)) {
            return QueueNavigationAvailability(hasNext = false, hasPrevious = false)
        }
        val hasNext = onHasNextNavigationCallback?.invoke()
            ?: (shouldEnableQueueNavigation(playlist.size) && PlaylistManager.hasNext())
        val hasPrevious = onHasPreviousNavigationCallback?.invoke()
            ?: (shouldEnableQueueNavigation(playlist.size) && PlaylistManager.hasPrevious())
        return QueueNavigationAvailability(
            hasNext = hasNext,
            hasPrevious = hasPrevious
        )
    }

    private fun isStandardQueueNavigationEnabled(): Boolean {
        return resolveSessionNavigationAvailability().isEnabled
    }

    private fun restorePlaylistIndexIfNeeded(index: Int) {
        if (index >= 0) {
            PlaylistManager.playAt(index)
        }
    }

    private fun playVideoDirectlyFromPlaylistItem(
        item: PlaylistItem,
        rollbackIndex: Int
    ): Boolean {
        val currentPlayer = player ?: return false
        backgroundSkipJob?.cancel()
        backgroundSkipJob = scope.launch {
            backgroundPlaybackUseCase.attachPlayer(currentPlayer)
            val isLoggedIn = !TokenManager.sessDataCache.isNullOrEmpty() ||
                !TokenManager.accessTokenCache.isNullOrEmpty()
            val defaultQuality = NetworkUtils.getPlayableDefaultQualityId(
                context = context,
                isLoggedIn = isLoggedIn,
                isVip = TokenManager.isVipCache
            )
            val audioQualityPreference = SettingsManager.getAudioQualitySync(context)
            val videoCodecPreference = SettingsManager.getVideoCodecSync(context)
            val videoSecondCodecPreference = SettingsManager.getVideoSecondCodecSync(context)

            when (
                val loadResult = backgroundPlaybackUseCase.loadVideo(
                    bvid = item.bvid,
                    defaultQuality = defaultQuality,
                    audioQualityPreference = audioQualityPreference,
                    videoCodecPreference = videoCodecPreference,
                    videoSecondCodecPreference = videoSecondCodecPreference,
                    playWhenReady = true,
                    isHdrSupportedOverride = MediaUtils.isHdrSupported(context),
                    isDolbyVisionSupportedOverride = MediaUtils.isDolbyVisionSupported(context)
                )
            ) {
                is VideoLoadResult.Success -> {
                    if (loadResult.audioUrl != null) {
                        backgroundPlaybackUseCase.playDashVideo(
                            videoUrl = loadResult.playUrl,
                            audioUrl = loadResult.audioUrl,
                            seekTo = 0L,
                            playWhenReady = true
                        )
                    } else {
                        backgroundPlaybackUseCase.playVideo(
                            url = loadResult.playUrl,
                            seekTo = 0L,
                            playWhenReady = true
                        )
                    }
                    currentBvid = loadResult.info.bvid
                    currentCid = loadResult.info.cid
                    currentAid = loadResult.info.aid
                    currentTitle = loadResult.info.title.ifBlank { item.title }
                    currentOwner = loadResult.info.owner.name.ifBlank { item.owner }
                    currentCover = resolveEffectiveNotificationCoverUrl(
                        incomingCoverUrl = loadResult.info.pic,
                        cachedCoverUrl = item.cover
                    )
                    cachedUiState = null
                    isActive = true
                    isLiveMode = false
                    currentRoomId = 0L
                    currentLiveUname = ""
                    duration = loadResult.duration.coerceAtLeast(0L)
                    isPlaying = true
                    updateMediaMetadata(currentTitle, currentOwner, currentCover)
                    Logger.d(TAG, "🎵 direct background skip: ${currentTitle}")
                }
                is VideoLoadResult.Error -> {
                    restorePlaylistIndexIfNeeded(rollbackIndex)
                    Logger.w(TAG, "⚠️ direct background skip failed: ${item.bvid}, error=${loadResult.error}")
                }
            }
        }
        return true
    }

    /**
     * Seek 到指定位置
     */
    fun seekTo(position: Long) {
        player?.seekTo(position)
    }
    
    // ==========  [新增] 播放列表控制 ==========
    
    /**
     *  播放下一曲
     */
    fun playNext(): Boolean {
        onNavigateNextCallback?.let { navigate ->
            if (navigate()) {
                Logger.d(TAG, "🎵 navigation callback handled next request")
                return true
            }
        }
        val previousIndex = PlaylistManager.currentIndex.value
        val nextItem = PlaylistManager.playNext()
        if (nextItem?.isBangumi == true) {
            return when (
                resolvePlaylistSkipExecutionMode(
                    item = nextItem,
                    callbackAvailable = onPlayNextBangumiCallback != null,
                    hasDirectPlaybackContext = false
                )
            ) {
                PlaylistSkipExecutionMode.CALLBACK -> {
                    val handled = dispatchBangumiNavigation(nextItem, onPlayNextBangumiCallback)
                    if (!handled) {
                        restorePlaylistIndexIfNeeded(previousIndex)
                        Logger.w(TAG, "⚠️ playNext bangumi ignored: callback/context missing")
                    }
                    handled
                }
                PlaylistSkipExecutionMode.DIRECT_BACKGROUND,
                PlaylistSkipExecutionMode.NONE -> {
                    restorePlaylistIndexIfNeeded(previousIndex)
                    Logger.w(TAG, "⚠️ playNext bangumi ignored: callback/context missing")
                    false
                }
            }
        }
        return when (
            resolvePlaylistSkipExecutionMode(
                item = nextItem,
                callbackAvailable = false,
                hasDirectPlaybackContext = hasDirectBackgroundPlaybackContext()
            )
        ) {
            PlaylistSkipExecutionMode.CALLBACK -> false
            PlaylistSkipExecutionMode.DIRECT_BACKGROUND -> {
                val handled = nextItem?.let { playVideoDirectlyFromPlaylistItem(it, previousIndex) } ?: false
                if (handled) {
                    Logger.d(TAG, "🎵 直切下一曲: ${nextItem.title}")
                } else {
                    restorePlaylistIndexIfNeeded(previousIndex)
                    Logger.w(TAG, "⚠️ playNext ignored: direct playback context missing")
                }
                handled
            }
            PlaylistSkipExecutionMode.NONE -> {
                restorePlaylistIndexIfNeeded(previousIndex)
                Logger.w(TAG, "⚠️ playNext ignored: no callback bound")
                false
            }
        }
    }
    
    /**
     *  播放上一曲
     */
    fun playPrevious(): Boolean {
        onNavigatePreviousCallback?.let { navigate ->
            if (navigate()) {
                Logger.d(TAG, "🎵 navigation callback handled previous request")
                return true
            }
        }
        val previousIndex = PlaylistManager.currentIndex.value
        val prevItem = PlaylistManager.playPrevious()
        if (prevItem?.isBangumi == true) {
            return when (
                resolvePlaylistSkipExecutionMode(
                    item = prevItem,
                    callbackAvailable = onPlayPreviousBangumiCallback != null,
                    hasDirectPlaybackContext = false
                )
            ) {
                PlaylistSkipExecutionMode.CALLBACK -> {
                    val handled = dispatchBangumiNavigation(prevItem, onPlayPreviousBangumiCallback)
                    if (!handled) {
                        restorePlaylistIndexIfNeeded(previousIndex)
                        Logger.w(TAG, "⚠️ playPrevious bangumi ignored: callback/context missing")
                    }
                    handled
                }
                PlaylistSkipExecutionMode.DIRECT_BACKGROUND,
                PlaylistSkipExecutionMode.NONE -> {
                    restorePlaylistIndexIfNeeded(previousIndex)
                    Logger.w(TAG, "⚠️ playPrevious bangumi ignored: callback/context missing")
                    false
                }
            }
        }
        return when (
            resolvePlaylistSkipExecutionMode(
                item = prevItem,
                callbackAvailable = false,
                hasDirectPlaybackContext = hasDirectBackgroundPlaybackContext()
            )
        ) {
            PlaylistSkipExecutionMode.CALLBACK -> false
            PlaylistSkipExecutionMode.DIRECT_BACKGROUND -> {
                val handled = prevItem?.let { playVideoDirectlyFromPlaylistItem(it, previousIndex) } ?: false
                if (handled) {
                    Logger.d(TAG, "🎵 直切上一曲: ${prevItem.title}")
                } else {
                    restorePlaylistIndexIfNeeded(previousIndex)
                    Logger.w(TAG, "⚠️ playPrevious ignored: direct playback context missing")
                }
                handled
            }
            PlaylistSkipExecutionMode.NONE -> {
                restorePlaylistIndexIfNeeded(previousIndex)
                Logger.w(TAG, "⚠️ playPrevious ignored: no callback bound")
                false
            }
        }
    }

    /**
     *  切换播放模式
     */
    fun togglePlayMode(): PlayMode {
        return PlaylistManager.togglePlayMode()
    }
    
    /**
     *  获取当前播放模式
     */
    fun getPlayMode(): PlayMode = PlaylistManager.playMode.value
    
    // 回调函数（由 PlayerViewModel 设置）
    var onNavigateNextCallback: (() -> Boolean)? = null
    var onNavigatePreviousCallback: (() -> Boolean)? = null
    var onHasNextNavigationCallback: (() -> Boolean)? = null
    var onHasPreviousNavigationCallback: (() -> Boolean)? = null
    var onPlayNextBangumiCallback: ((PlaylistItem) -> Unit)? = null
    var onPlayPreviousBangumiCallback: ((PlaylistItem) -> Unit)? = null


    /**
     * 释放所有资源
     */
    fun release() {
        Logger.d(TAG, "Releasing all resources")
        dismiss()
        if (mediaControlReceiverRegistered) {
            runCatching { context.unregisterReceiver(mediaControlReceiver) }
                .onFailure { Logger.w(TAG, "Failed to unregister media control receiver: ${it.message}") }
            mediaControlReceiverRegistered = false
        }
        if (backgroundListenerRegistered) {
            com.android.purebilibili.core.lifecycle.BackgroundManager.removeListener(this)
            backgroundListenerRegistered = false
        }
        _player?.removeListener(playerListener)
        _player?.release()
        _player = null
        scope.cancel()
        INSTANCE = null
    }

    // --- 播放器监听器 ---
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            Logger.d(TAG, "isPlaying changed: $playing")
            if (!shouldRefreshNotificationOnPlaybackStateChange(isActive = isActive, title = currentTitle)) {
                return
            }

            val titleSnapshot = currentTitle
            val ownerSnapshot = currentOwner
            val coverSnapshot = currentCover
            val artworkMissing = cachedArtworkBitmap == null && coverSnapshot.isNotBlank()

            if (artworkMissing) {
                scope.launch(Dispatchers.IO) {
                    val bitmap = loadBitmap(coverSnapshot)
                    launch(Dispatchers.Main) {
                        pushNotification(titleSnapshot, ownerSnapshot, bitmap)
                    }
                }
            } else {
                pushNotification(titleSnapshot, ownerSnapshot, bitmap = null)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    duration = _player?.duration ?: 0L
                    Logger.d(TAG, "Player ready, duration=$duration")
                }
                Player.STATE_ENDED -> {
                    Logger.d(TAG, "Playback ended")
                }
            }
        }
    }

    /**
     * 更新媒体元数据和通知
     */
    fun updateMediaMetadata(title: String, artist: String, coverUrl: String) {
        val currentPlayer = player ?: return
        val navigationAvailability = resolveSessionNavigationAvailability()
        if (
            shouldRebindMediaSessionPlayer(mediaSession?.player, currentPlayer) ||
            mediaSessionNavigationAvailability != navigationAvailability
        ) {
            updateMediaSession(currentPlayer)
        }
        val previousCoverUrl = currentCover
        val effectiveCoverUrl = resolveEffectiveNotificationCoverUrl(
            incomingCoverUrl = coverUrl,
            cachedCoverUrl = currentCover
        )
        currentTitle = title
        currentOwner = artist
        if (effectiveCoverUrl.isNotBlank()) {
            currentCover = effectiveCoverUrl
        }
        isPlaying = resolveNotificationIsPlaying(
            playerIsPlaying = currentPlayer.isPlaying,
            cachedIsPlaying = isPlaying
        )
        val currentItem = currentPlayer.currentMediaItem ?: return

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setDisplayTitle(title)
            .setIsPlayable(true)
        if (effectiveCoverUrl.isNotBlank()) {
            metadataBuilder.setArtworkUri(Uri.parse(FormatUtils.fixImageUrl(effectiveCoverUrl)))
        }
        val metadata = metadataBuilder.build()

        val newItem = currentItem.buildUpon()
            .setMediaMetadata(metadata)
            .build()

        currentPlayer.replaceMediaItem(currentPlayer.currentMediaItemIndex, newItem)

        // 异步加载封面并推送通知
        val shouldReloadArtwork = effectiveCoverUrl.isNotBlank() &&
            (cachedArtworkBitmap == null || previousCoverUrl != effectiveCoverUrl)
        scope.launch(Dispatchers.IO) {
            val bitmap = if (shouldReloadArtwork) {
                loadBitmap(effectiveCoverUrl)
            } else {
                null
            }
            launch(Dispatchers.Main) {
                pushNotification(title, artist, bitmap)
            }
        }
    }

    private suspend fun loadBitmap(url: String): Bitmap? {
        return try {
            val loader = context.imageLoader
            val request = ImageRequest.Builder(context)
                .data(FormatUtils.fixImageUrl(url))
                .allowHardware(false)
                .scale(Scale.FILL)
                .transformations(RoundedCornersTransformation(16f))
                .size(512, 512)
                .build()
            val result = loader.execute(request)
            (result as? SuccessResult)?.drawable?.let { (it as BitmapDrawable).bitmap }
        } catch (e: Exception) {
            com.android.purebilibili.core.util.Logger.e(TAG, "Failed to load bitmap", e)
            null
        }
    }

    private fun pushNotification(title: String, artist: String, bitmap: Bitmap?) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationIsPlaying = resolveNotificationIsPlaying(
            playerIsPlaying = player?.isPlaying,
            cachedIsPlaying = isPlaying
        )
        isPlaying = notificationIsPlaying
        if (!shouldKeepPlaybackNotificationVisible(
                isActive = isActive,
                title = title,
                isPlaying = notificationIsPlaying
            )
        ) {
            playbackServiceRequested = false
            clearPlaybackNotificationArtifacts()
            return
        }
        val effectiveArtworkBitmap = resolveEffectiveNotificationArtwork(
            incomingArtwork = bitmap,
            cachedArtwork = cachedArtworkBitmap
        )
        if (effectiveArtworkBitmap != null) {
            cachedArtworkBitmap = effectiveArtworkBitmap
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, "小窗播放", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "小窗播放控制"
                    setShowBadge(false)
                    setSound(null, null)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        val compactActionOrder = resolveExternalTransportActionOrder()
        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession?.sessionCompatToken)
            .setShowActionsInCompactView(0, 1, 2)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                resolveNotificationIconResByPriority(
                    launcherIconRes = resolveLaunchActivityIconRes(context),
                    fallbackIconKey = SettingsManager.getAppIconSync(context)
                )
            )
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(effectiveArtworkBitmap)
            .setStyle(style)
            .setColor(THEME_COLOR)
            .setColorized(true)
            .setOngoing(notificationIsPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setContentIntent(mediaSession?.sessionActivity)
        
        // 🎯 [修复] 确保点击通知本体也能正确跳转（覆盖 setContentIntent 作为双重保障）
        val intent = Intent(context, com.android.purebilibili.MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("https://www.bilibili.com/video/$currentBvid") // 携带 BVID
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.setContentIntent(contentIntent)
        
        compactActionOrder.forEach { actionType ->
            val intent = android.app.PendingIntent.getBroadcast(
                context,
                actionType,
                android.content.Intent(ACTION_MEDIA_CONTROL)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_CONTROL_TYPE, actionType),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            val action = when (actionType) {
                ACTION_NEXT -> NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "下一曲",
                    intent
                ).build()
                ACTION_PLAY_PAUSE -> {
                    val playPauseIcon = if (notificationIsPlaying) {
                        android.R.drawable.ic_media_pause
                    } else {
                        android.R.drawable.ic_media_play
                    }
                    val playPauseText = if (notificationIsPlaying) "暂停" else "播放"
                    NotificationCompat.Action.Builder(
                        playPauseIcon,
                        playPauseText,
                        intent
                    ).build()
                }
                ACTION_PREVIOUS -> NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "上一曲",
                    intent
                ).build()
                else -> null
            }
            if (action != null) {
                builder.addAction(action)
            }
        }

        try {
            val notification = builder.build()
            currentNotification = notification
            
            requestForegroundServiceIfNeeded()
            
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            com.android.purebilibili.core.util.Logger.e(TAG, "Failed to show notification", e)
        }
    }

    private fun requestForegroundServiceIfNeeded() {
        if (!isActive) return
        val now = SystemClock.elapsedRealtime()
        if (playbackServiceRequested && now - lastForegroundStartAtMs < FOREGROUND_START_DEBOUNCE_MS) {
            return
        }

        val serviceIntent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_START_FOREGROUND
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
            playbackServiceRequested = true
            lastForegroundStartAtMs = now
        } catch (e: Exception) {
            playbackServiceRequested = false
            Logger.e(TAG, "Failed to request foreground playback service", e)
        }
    }
}
