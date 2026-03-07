package com.android.purebilibili.feature.live

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.util.AnalyticsHelper
import com.android.purebilibili.core.util.CrashReporter
import com.android.purebilibili.data.model.response.LiveQuality
import com.android.purebilibili.feature.live.components.LiveChatSection
import com.android.purebilibili.feature.live.components.LandscapeChatOverlay
import com.android.purebilibili.feature.live.components.LivePlayerControls
import com.android.purebilibili.feature.video.player.shouldContinuePlaybackDuringPause
import com.android.purebilibili.feature.video.state.isPlaybackActiveForLifecycle
import com.android.purebilibili.feature.video.state.shouldResumeAfterLifecyclePause
import com.android.purebilibili.feature.video.ui.overlay.LiveDanmakuOverlay
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.Checkmark
import io.github.alexzhirkevich.cupertino.icons.outlined.Plus
import io.github.alexzhirkevich.cupertino.icons.outlined.ChevronDown
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope

private const val TAG = "LivePlayerScreen"

@OptIn(UnstableApi::class, ExperimentalHazeMaterialsApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun LivePlayerScreen(
    roomId: Long,
    title: String,
    uname: String,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit,
    viewModel: LivePlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    // 📺 [新增] 获取小窗管理器
    val miniPlayerManager = remember { com.android.purebilibili.feature.video.player.MiniPlayerManager.getInstance(context) }
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Shared Element Transition Scopes
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    val uiState by viewModel.uiState.collectAsState()
    
    // 状态
    var showQualityMenu by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var isChatVisible by remember { mutableStateOf(true) } // 控制侧边栏显示
    var isPipRequested by remember { mutableStateOf(false) }
    var wasPlaybackActiveBeforePause by remember { mutableStateOf(false) }
    val showLivePipButton = remember { shouldShowLivePipButton(android.os.Build.VERSION.SDK_INT) }
    
    // Haze blur 状态 (用于侧边栏实时模糊)
    val hazeState = remember { HazeState() }
    
    // 平板判断: 宽度 > 600dp 且为横屏
    val isTablet = configuration.screenWidthDp > 600
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    // 强制横屏切换
    fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        activity?.requestedOrientation = if (isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    fun enterLivePip() {
        if (!showLivePipButton) return
        val hostActivity = activity ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
            hostActivity.isInPictureInPictureMode
        ) {
            return
        }
        try {
            isPipRequested = true
            val paramsBuilder = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                paramsBuilder.setSeamlessResizeEnabled(true)
            }
            hostActivity.enterPictureInPictureMode(paramsBuilder.build())
        } catch (e: Exception) {
            isPipRequested = false
            Logger.e(TAG, "Enter live PiP failed", e)
        }
    }
    
    // 仅在全屏模式下拦截返回键，竖屏模式下允许系统预测性返回 gesture 工作
    BackHandler(enabled = isFullscreen) {
        if (isFullscreen) toggleFullscreen()
    }

    DisposableEffect(roomId) {
        miniPlayerManager.resetNavigationFlag()
        CrashReporter.setLastScreen("live_player")
        CrashReporter.markLiveSessionStart(roomId = roomId, title = title, uname = uname)
        CrashReporter.markLivePlaybackStage("screen_enter")
        onDispose {
            CrashReporter.markLiveSessionEnd("screen_dispose")
        }
    }

    // 播放器相关逻辑
    // ... (保持不变)
    val dataSourceFactory = remember(roomId) {
        val sessData = com.android.purebilibili.core.store.TokenManager.sessDataCache ?: ""
        val buvid3 = com.android.purebilibili.core.store.TokenManager.buvid3Cache ?: ""
        val cookies = "SESSDATA=$sessData; buvid3=$buvid3"
        
        DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf(
                "Referer" to "https://live.bilibili.com/$roomId",
                "User-Agent" to "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                "Cookie" to cookies
            ))
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
    }

    val exoPlayer = remember(dataSourceFactory) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply { playWhenReady = true }
    }
    
    // 📺 [新增] 将播放器注册到 MiniPlayerManager
    val liveCover = (uiState as? LivePlayerState.Success)?.roomInfo?.cover ?: ""
    val liveTitle = title.ifEmpty { (uiState as? LivePlayerState.Success)?.roomInfo?.title ?: "" }
    LaunchedEffect(exoPlayer, liveCover, liveTitle, uname) {
        miniPlayerManager.setLiveInfo(
            roomId = roomId,
            title = liveTitle,
            cover = liveCover,
            uname = uname,
            externalPlayer = exoPlayer
        )
    }
    
    // ... (播放监听与 URL 管理保持不变)
    
    // 播放状态监听
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Logger.e(TAG, "ExoPlayer Error: ${error.message}")
                CrashReporter.markLivePlaybackStage("player_error")
                CrashReporter.reportLiveError(
                    roomId = roomId,
                    errorType = "exo_player_error",
                    errorMessage = error.message ?: "unknown",
                    exception = error
                )
                if (error.cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                    val cause = error.cause as androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
                    if (cause.responseCode == 403) viewModel.tryNextUrl()
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                CrashReporter.markLivePlaybackStage(if (playing) "playing" else "not_playing")
            }
            // 📺 [新增] 直播流结束时自动关闭小窗
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && miniPlayerManager.isMiniMode && miniPlayerManager.isLiveMode) {
                    Logger.d(TAG, "📺 直播流结束，自动关闭小窗")
                    miniPlayerManager.dismiss()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }
    
    // 播放 URL 管理
    LaunchedEffect(roomId) { viewModel.loadLiveStream(roomId) }
    // 播放 URL 管理 - 只在 playUrl 变化时重新加载
    val playUrl = (uiState as? LivePlayerState.Success)?.playUrl
    LaunchedEffect(playUrl) {
        if (!playUrl.isNullOrEmpty()) {
            CrashReporter.markLivePlaybackStage("prepare_media_source")
            try {
                val mediaSource = if (playUrl.contains(".m3u8") || playUrl.contains("hls")) {
                    HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(playUrl))
                } else {
                    DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(
                        MediaItem.Builder().setUri(playUrl).setMimeType("video/x-flv").build()
                    )
                }
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                miniPlayerManager.updateMediaMetadata(
                    title = liveTitle.ifBlank { "直播中" },
                    artist = uname.ifBlank { "直播" },
                    coverUrl = liveCover
                )
                CrashReporter.markLivePlaybackStage("media_source_prepared")
            } catch (e: Exception) {
                Logger.e(TAG, "Play failed", e)
                CrashReporter.markLivePlaybackStage("prepare_media_source_failed")
                CrashReporter.reportLiveError(
                    roomId = roomId,
                    errorType = "media_prepare_failed",
                    errorMessage = e.message ?: "play failed",
                    exception = e
                )
            }
            // 埋点
            AnalyticsHelper.logLivePlay(roomId, title, uname)
        }
    }
    
    // 生命周期管理
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    val isInPictureInPictureMode =
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
                            (activity?.isInPictureInPictureMode == true)
                    val isBackgroundAudioEnabled = miniPlayerManager.shouldContinueBackgroundAudio()
                    val hasRecentUserLeaveHint = miniPlayerManager.hasRecentUserLeaveHint()
                    wasPlaybackActiveBeforePause = isPlaybackActiveForLifecycle(
                        isPlaying = exoPlayer.isPlaying,
                        playWhenReady = exoPlayer.playWhenReady,
                        playbackState = exoPlayer.playbackState
                    )
                    val shouldKeepPlayingInBackground = shouldContinuePlaybackDuringPause(
                        isMiniMode = miniPlayerManager.isMiniMode,
                        isPip = isInPictureInPictureMode || isPipRequested,
                        isBackgroundAudio = isBackgroundAudioEnabled,
                        wasPlaybackActive = wasPlaybackActiveBeforePause
                    )
                    val shouldKeepBackgroundAudioByFallback =
                        isBackgroundAudioEnabled &&
                            wasPlaybackActiveBeforePause
                    val shouldPausePlayback = shouldPauseLivePlaybackOnPause(
                        isInPictureInPictureMode = isInPictureInPictureMode,
                        isPipRequested = isPipRequested,
                        shouldKeepPlayingInBackground =
                            shouldKeepPlayingInBackground || shouldKeepBackgroundAudioByFallback
                    )
                    Logger.d(
                        TAG,
                        "ON_PAUSE live policy: pip=$isInPictureInPictureMode, pipRequested=$isPipRequested, " +
                            "bgAudio=$isBackgroundAudioEnabled, leaveHint=$hasRecentUserLeaveHint, " +
                            "wasActive=$wasPlaybackActiveBeforePause, keepByPolicy=$shouldKeepPlayingInBackground, " +
                            "keepByFallback=$shouldKeepBackgroundAudioByFallback, shouldPause=$shouldPausePlayback"
                    )
                    if (shouldPausePlayback) {
                        exoPlayer.pause()
                        CrashReporter.markLivePlaybackStage("lifecycle_pause")
                    } else {
                        CrashReporter.markLivePlaybackStage("lifecycle_pause_keep_playing")
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    isPipRequested = false
                    val shouldResumePlayback = shouldResumeAfterLifecyclePause(
                        wasPlaybackActive = wasPlaybackActiveBeforePause,
                        isPlaying = exoPlayer.isPlaying,
                        playWhenReady = exoPlayer.playWhenReady,
                        playbackState = exoPlayer.playbackState
                    )
                    Logger.d(TAG, "ON_RESUME live policy: shouldResume=$shouldResumePlayback")
                    if (shouldResumePlayback) {
                        exoPlayer.play()
                    }
                    CrashReporter.markLivePlaybackStage("lifecycle_resume")
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 📺 [修改] 仅当 MiniPlayerManager 未持有该播放器时才释放
            if (!miniPlayerManager.isPlayerManaged(exoPlayer)) {
                exoPlayer.release()
                Logger.d(TAG, "📺 播放器未被小窗持有，释放")
            } else {
                Logger.d(TAG, "📺 播放器被小窗持有，保留")
            }
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    
    // 横屏时隐藏系统栏
    LaunchedEffect(isLandscape) {
        val window = activity?.window ?: return@LaunchedEffect
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        
        if (isLandscape) {
            // 隐藏状态栏和导航栏
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            // 恢复显示
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 布局结构
    val playerContent = @Composable {
        Box(
            modifier = Modifier
                .background(Color.Black)
                .hazeSource(state = hazeState)
                .then(
                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState(key = "live_cover_$roomId"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    } else Modifier
                )
        ) {
            // Video View
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Danmaku Overlay (Only render if enabled)
            val successState = uiState as? LivePlayerState.Success
            if (successState?.isDanmakuEnabled == true) {
                LiveDanmakuOverlay(
                    danmakuFlow = viewModel.danmakuFlow,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Custom Controls
            LivePlayerControls(
                isPlaying = isPlaying,
                isFullscreen = isFullscreen,
                title = title.ifEmpty { (uiState as? LivePlayerState.Success)?.roomInfo?.title ?: "" },
                onPlayPause = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                onToggleFullscreen = { toggleFullscreen() },
                onBack = { 
                    if (isFullscreen) {
                        toggleFullscreen()
                    } else {
                        // 📺 [新增] 退出时进入小窗模式
                        miniPlayerManager.enterMiniMode(forced = true)
                        onBack()
                    }
                },
                // 侧边栏开关
                isChatVisible = isChatVisible,
                onToggleChat = { isChatVisible = !isChatVisible },
                showChatToggle = isLandscape || isFullscreen,
                // 弹幕开关
                isDanmakuEnabled = (uiState as? LivePlayerState.Success)?.isDanmakuEnabled ?: true,
                onToggleDanmaku = { viewModel.toggleDanmaku() },
                // [新增] 刷新
                onRefresh = { viewModel.retry() },
                showPipButton = showLivePipButton,
                onEnterPip = { enterLivePip() }
            )
            
            // Loading/Error Indicator
            if (uiState is LivePlayerState.Loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoActivityIndicator() }
            }
            if (uiState is LivePlayerState.Error) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text((uiState as LivePlayerState.Error).message, color = Color.White)
                        Button(onClick = { viewModel.retry() }) { Text("重试") }
                    }
                }
            }
        }
    }
    
    val chatContent = @Composable {
        LiveChatSection(
            danmakuFlow = viewModel.danmakuFlow,
            onSendDanmaku = { text -> viewModel.sendDanmaku(text) },
            modifier = Modifier.fillMaxSize()
        )
    }

    // 响应式布局：全屏 / 横屏模式改为左下角透明弹幕覆盖
    if (isLandscape) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. 播放器区域 (底层全屏)
            playerContent()
            
            // 2. 左下角弹幕列表 (透明浮动)
            androidx.compose.animation.AnimatedVisibility(
                visible = isChatVisible,
                enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                LandscapeChatOverlay(
                    danmakuFlow = viewModel.danmakuFlow,
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .fillMaxHeight(0.4f)
                )
            }
        }
    } else {
        // 手机竖屏 (非全屏)
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // 1. 播放器区域 (16:9)
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f)) {
                playerContent()
            }
            
            // 2. 主播信息条
            val successState = uiState as? LivePlayerState.Success
            if (successState != null) {
                AnchorInfoBar(
                    anchorInfo = successState.anchorInfo,
                    isFollowing = successState.isFollowing,
                    online = successState.roomInfo.online,
                    onFollowClick = { viewModel.toggleFollow() },
                    onUserClick = onUserClick,
                    onQualityClick = { showQualityMenu = true },
                    currentQualityDesc = successState.qualityList.find { it.qn == successState.currentQuality }?.desc ?: "自动"
                )
            }
            
            // 3. 聊天区域
            Box(modifier = Modifier.weight(1f)) {
                chatContent()
            }
        }
    }
    
    // 画质菜单弹窗
    if (showQualityMenu) {
        val successState = uiState as? LivePlayerState.Success
        if (successState != null) {
            LiveQualityMenu(
                qualityList = successState.qualityList,
                currentQuality = successState.currentQuality,
                onQualitySelected = { qn ->
                    viewModel.changeQuality(qn)
                    showQualityMenu = false
                },
                onDismiss = { showQualityMenu = false }
            )
        }
    }
}

// 辅助组件：主播信息条
// 辅助组件：主播信息条
@Composable
private fun AnchorInfoBar(
    anchorInfo: com.android.purebilibili.feature.live.AnchorInfo,
    isFollowing: Boolean,
    online: Int,
    onFollowClick: () -> Unit,
    onUserClick: (Long) -> Unit,
    onQualityClick: () -> Unit,
    currentQualityDesc: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = anchorInfo.face,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray)
                    .clickable { onUserClick(anchorInfo.uid) }
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anchorInfo.uname, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 16.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 人气标
                    Surface(
                        color = Color(0xFFFF6699).copy(0.05f),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text(
                            text = if (online > 0) "人气 ${if (online > 10000) "%.1f万".format(online/10000f) else online}" else "人气 -",
                            color = Color(0xFFFF6699), 
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            
            // 画质按钮
            TextButton(
                onClick = onQualityClick,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(currentQualityDesc, fontSize = 13.sp, color = Color(0xFF61666D))
                Spacer(Modifier.width(2.dp))
                Icon(CupertinoIcons.Default.ChevronDown, null, modifier = Modifier.size(12.dp), tint = Color(0xFF61666D))
            }
            
            Spacer(Modifier.width(8.dp))
            
            // 关注按钮
            Button(
                onClick = onFollowClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFollowing) Color(0xFFE3E5E7) else MaterialTheme.colorScheme.primary,
                    contentColor = if (isFollowing) Color(0xFF9499A0) else Color.White
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                if (!isFollowing) {
                    Icon(CupertinoIcons.Outlined.Plus, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = if (isFollowing) "已关注" else "关注", 
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun LiveQualityMenu(
    qualityList: List<LiveQuality>,
    currentQuality: Int,
    onQualitySelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null
        ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.width(280.dp).clip(RoundedCornerShape(12.dp)),
            color = Color(0xFF2B2B2B)
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("画质选择", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                qualityList.forEach { q ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onQualitySelected(q.qn) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(q.desc, color = if (q.qn == currentQuality) MaterialTheme.colorScheme.primary else Color.White)
                        Spacer(Modifier.weight(1f))
                        if (q.qn == currentQuality) Icon(CupertinoIcons.Default.Checkmark, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
