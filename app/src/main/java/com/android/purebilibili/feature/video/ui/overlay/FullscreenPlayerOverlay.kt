// 文件路径: feature/video/FullscreenPlayerOverlay.kt
package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.feature.video.danmaku.FaceOcclusionDanmakuContainer
import com.android.purebilibili.feature.video.danmaku.FaceOcclusionMaskStabilizer
import com.android.purebilibili.feature.video.danmaku.FaceOcclusionModuleState
import com.android.purebilibili.feature.video.danmaku.FaceOcclusionVisualMask
import com.android.purebilibili.feature.video.danmaku.checkFaceOcclusionModuleState
import com.android.purebilibili.feature.video.danmaku.createFaceOcclusionDetector
import com.android.purebilibili.feature.video.danmaku.detectFaceOcclusionRegions
import com.android.purebilibili.feature.video.danmaku.installFaceOcclusionModule
import com.android.purebilibili.feature.video.danmaku.rememberDanmakuManager
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.ui.section.resolveHorizontalSeekDeltaMs
import com.android.purebilibili.feature.video.ui.section.shouldCommitGestureSeek
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import com.android.purebilibili.feature.video.usecase.togglePlayerPlaybackFromUserAction

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
// 🌈 Material Icons Extended - 亮度图标
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.purebilibili.core.store.DanmakuSettings
import com.android.purebilibili.core.store.FullscreenAspectRatio
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.unifiedBlur
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.video.ui.gesture.GestureMode
import com.android.purebilibili.feature.video.ui.gesture.GestureIndicator
import com.android.purebilibili.feature.video.ui.gesture.rememberPlayerGestureState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.runtime.collectAsState
import com.android.purebilibili.feature.video.ui.components.DanmakuSettingsPanel
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.components.PlaybackSpeed
import com.android.purebilibili.feature.video.ui.components.toFullscreenAspectRatio
import com.android.purebilibili.feature.video.ui.components.toVideoAspectRatio
import com.android.purebilibili.core.ui.common.copyOnLongPress
import androidx.lifecycle.compose.currentStateAsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.withTimeoutOrNull
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private const val AUTO_HIDE_DELAY = 4000L

// Keep for backward compatibility, maps to new GestureMode
enum class FullscreenGestureMode { None, Brightness, Volume, Seek }

/**
 *  全屏播放器覆盖层
 * 
 * 从小窗展开时直接显示全屏播放器
 * 包含：亮度调节、音量调节、进度滑动等完整功能
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun FullscreenPlayerOverlay(
    miniPlayerManager: MiniPlayerManager,
    onDismiss: () -> Unit,
    onNavigateToDetail: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val player = miniPlayerManager.player
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val hostLifecycleStarted = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    
    var showControls by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    //  [新增] 弹幕设置面板状态
    var showDanmakuSettings by remember { mutableStateOf(false) }
    
    //  播放速度状态
    var playbackSpeed by remember(player) { mutableFloatStateOf(player?.playbackParameters?.speed ?: 1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    
    //  视频比例状态
    val fixedFullscreenAspectRatio by SettingsManager
        .getFullscreenAspectRatio(context)
        .collectAsState(initial = FullscreenAspectRatio.FIT)
    var aspectRatio by remember { mutableStateOf(fixedFullscreenAspectRatio.toVideoAspectRatio()) }
    var showRatioMenu by remember { mutableStateOf(false) }
    
    //  画质选择菜单状态
    var showQualityMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var faceVisualMasks by remember { mutableStateOf(emptyList<FaceOcclusionVisualMask>()) }
    val faceMaskStabilizer = remember { FaceOcclusionMaskStabilizer() }
    var smartOcclusionModuleState by remember { mutableStateOf(FaceOcclusionModuleState.Checking) }
    var smartOcclusionDownloadProgress by remember { mutableStateOf<Int?>(null) }
    //  共享弹幕管理器（横竖屏切换保持状态，同时可用于手势 seek 同步）
    val danmakuManager = rememberDanmakuManager()
    
    // 手势状态
    var gestureMode by remember { mutableStateOf(FullscreenGestureMode.None) }
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var seekPreviewPosition by remember { mutableLongStateOf(0L) }
    var gestureSeekStartPosition by remember { mutableLongStateOf(0L) }
    val fullscreenSwipeSeekSeconds by produceState<Int?>(initialValue = null, context) {
        SettingsManager.getFullscreenSwipeSeekSeconds(context)
            .collectLatest { value = it }
    }
    val doubleTapSeekEnabled by SettingsManager
        .getDoubleTapSeekEnabled(context)
        .collectAsState(initial = true)
    val seekForwardSeconds by SettingsManager
        .getSeekForwardSeconds(context)
        .collectAsState(initial = 10)
    val seekBackwardSeconds by SettingsManager
        .getSeekBackwardSeconds(context)
        .collectAsState(initial = 10)
    
    // 亮度状态
    var currentBrightness by remember { 
        mutableFloatStateOf(
            try {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            } catch (e: Exception) { 0.5f }
        )
    }
    
    // 播放器状态
    var isPlaying by remember { mutableStateOf(player?.isPlaying ?: false) }
    var currentProgress by remember { mutableFloatStateOf(0f) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val currentClockText by produceState(initialValue = formatCurrentClock(), hostLifecycleStarted) {
        if (!hostLifecycleStarted) {
            value = formatCurrentClock()
            return@produceState
        }
        while (true) {
            value = formatCurrentClock()
            val now = System.currentTimeMillis()
            val nextMinuteDelay = (60_000L - (now % 60_000L)).coerceAtLeast(1_000L)
            delay(nextMinuteDelay)
        }
    }

    DisposableEffect(player) {
        val exoPlayer = player
        if (exoPlayer == null) {
            onDispose { }
        } else {
            playbackSpeed = exoPlayer.playbackParameters.speed
            val speedListener = object : Player.Listener {
                override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                    playbackSpeed = playbackParameters.speed
                }
            }
            exoPlayer.addListener(speedListener)
            onDispose {
                exoPlayer.removeListener(speedListener)
            }
        }
    }

    LaunchedEffect(fixedFullscreenAspectRatio) {
        aspectRatio = fixedFullscreenAspectRatio.toVideoAspectRatio()
    }
    
    // 进入全屏时设置横屏和沉浸式
    DisposableEffect(Unit) {
        val activity = (context as? Activity) ?: return@DisposableEffect onDispose {}
        val window = activity.window
        val originalOrientation = activity.requestedOrientation
        
        //  [重构] 定义设置沉浸式模式的函数（可复用）
        val applyImmersiveMode = {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
        
        // 设置横屏
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        
        //  首次进入时应用沉浸式
        applyImmersiveMode()
        
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        //  [关键修复] 生命周期观察器：返回前台时重新应用沉浸式模式
        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                applyImmersiveMode()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        
        onDispose {
            //  移除生命周期观察器
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            
            // 恢复竖屏
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            
            // 恢复系统栏
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            
            // 取消屏幕常亮
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    // 监听播放器状态
    LaunchedEffect(player, showControls, gestureMode, hostLifecycleStarted) {
        if (!shouldPollFullscreenPlayerProgress(
                playerExists = player != null,
                hostLifecycleStarted = hostLifecycleStarted
            )
        ) {
            return@LaunchedEffect
        }
        while (isActive) {
            player?.let {
                isPlaying = it.isPlaying
                duration = it.duration.coerceAtLeast(1L)
                currentPosition = it.currentPosition
                if (gestureMode != FullscreenGestureMode.Seek) {
                    currentProgress = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                }
            }
            val pollInterval = resolveFullscreenPlayerPollingIntervalMs(
                isPlaying = isPlaying,
                showControls = showControls,
                isSeekingGesture = gestureMode == FullscreenGestureMode.Seek
            )
            delay(pollInterval)
        }
    }
    
    // 自动隐藏控制按钮
    LaunchedEffect(showControls, lastInteractionTime) {
        if (showControls && gestureMode == FullscreenGestureMode.None) {
            delay(AUTO_HIDE_DELAY)
            if (System.currentTimeMillis() - lastInteractionTime >= AUTO_HIDE_DELAY) {
                showControls = false
            }
        }
    }
    
    // 返回键处理
    BackHandler { onNavigateToDetail() }
    
    // [问题6修复] 弹幕设置面板打开时禁用手势
    val gesturesEnabled = !showDanmakuSettings && !showSpeedMenu && !showRatioMenu && !showQualityMenu
    
    // [问题8修复] 状态栏排除区域高度（像素）
    val statusBarExclusionZonePx = with(density) { 40.dp.toPx() }
    val overlayHazeState = rememberRecoverableHazeState()
    val displayedProgressState = remember(
        currentPosition,
        duration,
        seekPreviewPosition,
        gestureMode,
        player?.bufferedPosition
    ) {
        resolveDisplayedPlayerProgress(
            progress = PlayerProgress(
                current = currentPosition,
                duration = duration,
                buffered = player?.bufferedPosition ?: 0L
            ),
            previewPositionMs = seekPreviewPosition,
            previewActive = gestureMode == FullscreenGestureMode.Seek
        )
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .hazeSource(overlayHazeState)
            .pointerInput(
                gesturesEnabled,
                doubleTapSeekEnabled,
                seekForwardSeconds,
                seekBackwardSeconds
            ) {
                if (!gesturesEnabled) return@pointerInput
                
                val screenWidth = size.width.toFloat()
                
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                        if (showControls) lastInteractionTime = System.currentTimeMillis()
                    },
                    onDoubleTap = { offset ->
                        // 分区双击策略可由设置控制：关闭跳转时全屏双击仅暂停/播放
                        val relativeX = offset.x / screenWidth
                        player?.let { p ->
                            when (resolveFullscreenDoubleTapAction(relativeX, doubleTapSeekEnabled)) {
                                FullscreenDoubleTapAction.SeekBackward -> {
                                    val seekMs = seekBackwardSeconds * 1000L
                                    val newPos = (p.currentPosition - seekMs).coerceAtLeast(0L)
                                    p.seekTo(newPos)
                                    danmakuManager.seekTo(newPos)
                                }
                                FullscreenDoubleTapAction.SeekForward -> {
                                    val seekMs = seekForwardSeconds * 1000L
                                    val durationLimit = p.duration.coerceAtLeast(0L)
                                    val target = p.currentPosition + seekMs
                                    val newPos = if (durationLimit > 0L) {
                                        target.coerceAtMost(durationLimit)
                                    } else {
                                        target
                                    }
                                    p.seekTo(newPos)
                                    danmakuManager.seekTo(newPos)
                                }
                                FullscreenDoubleTapAction.TogglePlayPause -> {
                                    togglePlayerPlaybackFromUserAction(p)
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(gesturesEnabled, fullscreenSwipeSeekSeconds) {
                if (!gesturesEnabled) return@pointerInput
                
                val screenWidth = size.width.toFloat()
                val screenHeight = size.height.toFloat()
                
                detectDragGestures(
                    onDragStart = { offset ->
                        // [问题8修复] 排除状态栏区域的手势触发
                        if (offset.y < statusBarExclusionZonePx) {
                            gestureMode = FullscreenGestureMode.None
                            return@detectDragGestures
                        }
                        
                        showControls = true
                        lastInteractionTime = System.currentTimeMillis()
                        dragDelta = 0f
                        
                        // 根据起始位置决定手势类型
                        gestureMode = when {
                            offset.x < screenWidth * 0.3f -> {
                                gestureValue = currentBrightness
                                FullscreenGestureMode.Brightness
                            }
                            offset.x > screenWidth * 0.7f -> {
                                gestureValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                                FullscreenGestureMode.Volume
                            }
                            else -> {
                                seekPreviewPosition = currentPosition
                                gestureSeekStartPosition = currentPosition
                                FullscreenGestureMode.Seek
                            }
                        }
                    },
                    onDragEnd = {
                        if (
                            gestureMode == FullscreenGestureMode.Seek &&
                            shouldCommitGestureSeek(
                                currentPositionMs = gestureSeekStartPosition,
                                targetPositionMs = seekPreviewPosition
                            )
                        ) {
                            player?.let {
                                it.seekTo(seekPreviewPosition)
                                danmakuManager.seekTo(seekPreviewPosition)
                            }
                        }
                        gestureMode = FullscreenGestureMode.None
                    },
                    onDragCancel = { gestureMode = FullscreenGestureMode.None },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        when (gestureMode) {
                            FullscreenGestureMode.Brightness -> {
                                gestureValue = (gestureValue - dragAmount.y / screenHeight).coerceIn(0f, 1f)
                                currentBrightness = gestureValue
                                (context as? Activity)?.window?.let { window ->
                                    val params = window.attributes
                                    params.screenBrightness = gestureValue
                                    window.attributes = params
                                }
                            }
                            FullscreenGestureMode.Volume -> {
                                gestureValue = (gestureValue - dragAmount.y / screenHeight).coerceIn(0f, 1f)
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    (gestureValue * maxVolume).toInt(),
                                    0
                                )
                            }
                            FullscreenGestureMode.Seek -> {
                                dragDelta += dragAmount.x
                                val seekDelta = resolveHorizontalSeekDeltaMs(
                                    isFullscreen = true,
                                    fullscreenSwipeSeekEnabled = true,
                                    totalDragDistanceX = dragDelta,
                                    containerWidthPx = screenWidth,
                                    fullscreenSwipeSeekSeconds = fullscreenSwipeSeekSeconds,
                                    gestureSensitivity = 1f
                                )
                                if (seekDelta != null) {
                                    seekPreviewPosition = (gestureSeekStartPosition + seekDelta).coerceIn(0L, duration)
                                    currentProgress = (seekPreviewPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                                }
                            }
                            else -> {}
                        }
                    }
                )
            }
    ) {
        val danmakuScope = com.android.purebilibili.core.store.DanmakuSettingsScope.LANDSCAPE
        val danmakuSettings by SettingsManager
            .getDanmakuSettings(context, danmakuScope)
            .collectAsState(initial = DanmakuSettings())
        val danmakuEnabled = danmakuSettings.enabled
        val danmakuOpacity = danmakuSettings.opacity
        val danmakuFontScale = danmakuSettings.fontScale
        val danmakuSpeed = danmakuSettings.speed
        val danmakuDisplayArea = danmakuSettings.displayArea
        val danmakuMergeDuplicates = danmakuSettings.mergeDuplicates
        val danmakuAllowScroll = danmakuSettings.allowScroll
        val danmakuAllowTop = danmakuSettings.allowTop
        val danmakuAllowBottom = danmakuSettings.allowBottom
        val danmakuAllowColorful = danmakuSettings.allowColorful
        val danmakuAllowSpecial = danmakuSettings.allowSpecial
        val danmakuSmartOcclusion = danmakuSettings.smartOcclusion
        val danmakuBlockRulesRaw = danmakuSettings.blockRulesRaw
        val danmakuBlockRules = danmakuSettings.blockRules
        val faceDetector = remember { createFaceOcclusionDetector() }
        DisposableEffect(faceDetector) {
            onDispose {
                faceDetector.close()
            }
        }

        LaunchedEffect(faceDetector) {
            smartOcclusionModuleState = FaceOcclusionModuleState.Checking
            smartOcclusionDownloadProgress = null
            smartOcclusionModuleState = checkFaceOcclusionModuleState(context, faceDetector)
        }
        
        //  获取当前 cid 并加载弹幕
        val currentCid = miniPlayerManager.currentCid
        LaunchedEffect(currentCid, danmakuEnabled, player) {
            if (currentCid > 0 && danmakuEnabled) {
                danmakuManager.isEnabled = true
                
                // 等待播放器 duration 可用后再加载弹幕，启用 Protobuf API
                var durationMs = player?.duration ?: 0L
                var retries = 0
                while (durationMs <= 0 && retries < 50) {
                    delay(100)
                    durationMs = player?.duration ?: 0L
                    retries++
                }
                
                danmakuManager.loadDanmaku(currentCid, miniPlayerManager.currentAid, durationMs)
            } else {
                danmakuManager.isEnabled = false
            }
        }
        
        //  弹幕设置变化时实时应用
        LaunchedEffect(
            danmakuOpacity,
            danmakuFontScale,
            danmakuSpeed,
            danmakuDisplayArea,
            danmakuMergeDuplicates,
            danmakuAllowScroll,
            danmakuAllowTop,
            danmakuAllowBottom,
            danmakuAllowColorful,
            danmakuAllowSpecial,
            danmakuBlockRules,
            danmakuSmartOcclusion
        ) {
            danmakuManager.updateSettings(
                opacity = danmakuOpacity,
                fontScale = danmakuFontScale,
                speed = danmakuSpeed,
                displayArea = danmakuDisplayArea,
                mergeDuplicates = danmakuMergeDuplicates,
                allowScroll = danmakuAllowScroll,
                allowTop = danmakuAllowTop,
                allowBottom = danmakuAllowBottom,
                allowColorful = danmakuAllowColorful,
                allowSpecial = danmakuAllowSpecial,
                blockedRules = danmakuBlockRules,
                // Mask-only mode: keep lane layout fixed, do not move danmaku tracks.
                smartOcclusion = false
            )
        }

        LaunchedEffect(
            playerViewRef,
            player,
            faceDetector,
            danmakuEnabled,
            danmakuSmartOcclusion,
            smartOcclusionModuleState
        ) {
            if (
                !danmakuEnabled ||
                !danmakuSmartOcclusion ||
                smartOcclusionModuleState != FaceOcclusionModuleState.Ready
            ) {
                faceMaskStabilizer.reset()
                faceVisualMasks = emptyList()
                return@LaunchedEffect
            }
            faceMaskStabilizer.reset()
            while (isActive) {
                val view = playerViewRef
                val exoPlayer = player
                if (view == null || exoPlayer == null || !exoPlayer.isPlaying || view.width <= 0 || view.height <= 0) {
                    delay(1200L)
                    continue
                }

                val videoWidth = exoPlayer.videoSize.width
                val videoHeight = exoPlayer.videoSize.height
                val sampleWidth = 480
                val sampleHeight = when {
                    videoWidth > 0 && videoHeight > 0 -> (sampleWidth * videoHeight / videoWidth).coerceIn(270, 960)
                    else -> 270
                }

                val detection = withTimeoutOrNull(1_500L) {
                    detectFaceOcclusionRegions(
                        playerView = view,
                        sampleWidth = sampleWidth,
                        sampleHeight = sampleHeight,
                        detector = faceDetector
                    )
                } ?: com.android.purebilibili.feature.video.danmaku.FaceOcclusionDetectionResult(
                    verticalRegions = emptyList(),
                    maskRects = emptyList(),
                    visualMasks = emptyList()
                )
                faceVisualMasks = faceMaskStabilizer.step(detection.visualMasks)
                delay(if (detection.visualMasks.isEmpty()) 1300L else 900L)
            }
        }
        
        //  绑定 Player（不在 onDispose 中释放，单例会保持状态）
        //  [修复] 移除 detachView 调用，避免横竖屏切换时弹幕消失
        // attachView 会自动暂停旧视图，不需要手动 detach
        DisposableEffect(player) {
            player?.let { danmakuManager.attachPlayer(it) }
            onDispose {
                //  不再调用 detachView()
                // 单例模式下，视图引用会在下次 attachView 时自动更新
            }
        }
        
        //  [修复] 使用 LifecycleOwner 监听真正的 Activity 生命周期
        // DisposableEffect(Unit) 会在重组时触发，导致 player 引用被清除
        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) {
                    android.util.Log.d("FullscreenPlayer", " ON_DESTROY: Clearing danmaku references")
                    danmakuManager.clearViewReference()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        
        // 视频播放器
        player?.let { exoPlayer ->
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val viewportLayout = remember(maxWidth, maxHeight, aspectRatio) {
                    with(density) {
                        com.android.purebilibili.feature.video.ui.components.resolveVideoViewportLayout(
                            containerWidth = maxWidth.roundToPx(),
                            containerHeight = maxHeight.roundToPx(),
                            aspectRatio = aspectRatio
                        )
                    }
                }
                val viewportModifier = with(density) {
                    Modifier
                        .size(
                            width = viewportLayout.width.toDp(),
                            height = viewportLayout.height.toDp()
                        )
                }

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = exoPlayer
                            useController = false
                            keepScreenOn = true
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            resizeMode = aspectRatio.playerResizeMode
                            playerViewRef = this
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                        playerView.resizeMode = aspectRatio.playerResizeMode
                        playerViewRef = playerView
                    },
                    modifier = viewportModifier
                )

                if (danmakuEnabled) {
                    AndroidView(
                        factory = { ctx ->
                            FaceOcclusionDanmakuContainer(ctx).apply {
                                setMasks(faceVisualMasks)
                                setVideoViewport(
                                    videoWidth = exoPlayer.videoSize.width,
                                    videoHeight = exoPlayer.videoSize.height,
                                    resizeMode = aspectRatio.playerResizeMode
                                )
                                danmakuManager.attachView(danmakuView())
                                com.android.purebilibili.core.util.Logger.d("FullscreenDanmaku", " DanmakuView (RenderEngine) created for fullscreen")
                            }
                        },
                        update = { container ->
                            container.setMasks(faceVisualMasks)
                            container.setVideoViewport(
                                videoWidth = exoPlayer.videoSize.width,
                                videoHeight = exoPlayer.videoSize.height,
                                resizeMode = aspectRatio.playerResizeMode
                            )
                            val view = container.danmakuView()
                            if (view.width > 0 && view.height > 0) {
                                val sizeTag = "${view.width}x${view.height}"
                                if (view.tag != sizeTag) {
                                    view.tag = sizeTag
                                    danmakuManager.attachView(view)
                                    com.android.purebilibili.core.util.Logger.d("FullscreenDanmaku", " DanmakuView update: size=${view.width}x${view.height}")
                                }
                            }
                        },
                        modifier = viewportModifier
                    )
                }
            }
        }
        
        // 手势指示器
        if (gestureMode != FullscreenGestureMode.None) {
            GestureIndicator(
                mode = gestureMode,
                value = when (gestureMode) {
                    FullscreenGestureMode.Brightness -> currentBrightness
                    FullscreenGestureMode.Volume -> gestureValue
                    FullscreenGestureMode.Seek -> currentProgress
                    else -> 0f
                },
                seekTime = if (gestureMode == FullscreenGestureMode.Seek) seekPreviewPosition else null,
                duration = duration,
                hazeState = overlayHazeState,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // 控制层
        AnimatedVisibility(
            visible = showControls && gestureMode == FullscreenGestureMode.None,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶部渐变 + 返回按钮和标题
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        IconButton(onClick = onNavigateToDetail) {
                            Icon(CupertinoIcons.Default.ChevronBackward, "返回详情页", tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = miniPlayerManager.currentTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .copyOnLongPress(miniPlayerManager.currentTitle, "视频标题")
                        )

                        Text(
                            text = currentClockText,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        
                        //  [新增] 弹幕开关按钮
                        val danmakuToggleInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val danmakuActiveColor = MaterialTheme.colorScheme.primary
                        val danmakuInactiveColor = Color.White.copy(alpha = 0.74f)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (danmakuEnabled) {
                                        danmakuActiveColor.copy(alpha = 0.22f)
                                    } else {
                                        danmakuInactiveColor.copy(alpha = 0.16f)
                                    }
                                )
                                .clickable(
                                    interactionSource = danmakuToggleInteraction,
                                    indication = null,
                                    onClick = {
                                        val newValue = !danmakuEnabled
                                        danmakuManager.isEnabled = newValue
                                        scope.launch { SettingsManager.setDanmakuEnabled(context, newValue, danmakuScope) }
                                        com.android.purebilibili.core.util.Logger.d("FullscreenDanmaku", " Danmaku toggle: $newValue")
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (danmakuEnabled) CupertinoIcons.Filled.TextBubble else CupertinoIcons.Outlined.TextBubble,
                                contentDescription = if (danmakuEnabled) "关闭弹幕" else "开启弹幕",
                                tint = if (danmakuEnabled) danmakuActiveColor else danmakuInactiveColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (danmakuEnabled) "开" else "关",
                                color = if (danmakuEnabled) danmakuActiveColor else danmakuInactiveColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        
                        //  [新增] 弹幕设置按钮
                        IconButton(onClick = { showDanmakuSettings = true }) {
                            Icon(CupertinoIcons.Default.Gear, "弹幕设置", tint = Color.White)
                        }
                    }
                }
                
                //  [修改] 移除中间大按钮，改为在底部控制栏左侧显示
                
                // 底部进度条和控制按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).align(Alignment.Center)
                    ) {
                        // 进度条行
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            //  [新增] 左下角播放/暂停按钮
                            Surface(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    player?.let { togglePlayerPlaybackFromUserAction(it) }
                                },
                                shape = CircleShape,
                                color = Color.Transparent
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) CupertinoIcons.Default.Pause else CupertinoIcons.Default.Play,
                                    contentDescription = if (isPlaying) "暂停" else "播放",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                FormatUtils.formatDuration((displayedProgressState.current / 1000).toInt()),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            
                            var isDragging by remember { mutableStateOf(false) }
                            var dragProgress by remember { mutableFloatStateOf(0f) }
                            
                            Slider(
                                value = if (isDragging) {
                                    dragProgress
                                } else if (displayedProgressState.duration > 0L) {
                                    (displayedProgressState.current.toFloat() / displayedProgressState.duration.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    currentProgress
                                },
                                onValueChange = { newValue ->
                                    if (!isDragging) {
                                        danmakuManager.clear()  //  拖动开始时清除弹幕
                                    }
                                    isDragging = true
                                    dragProgress = newValue
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                onValueChangeFinished = {
                                    isDragging = false
                                    val newPosition = (dragProgress * duration).toLong()
                                    player?.let {
                                        seekPlayerFromUserAction(it, newPosition)
                                        danmakuManager.seekTo(newPosition)
                                    }
                                    currentProgress = dragProgress
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                            
                            Text(FormatUtils.formatDuration((duration / 1000).toInt()), color = Color.White, fontSize = 12.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        //  底部控制按钮行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 倍速按钮
                            FullscreenControlButton(
                                text = PlaybackSpeed.formatSpeed(playbackSpeed),
                                isHighlighted = playbackSpeed != 1.0f,
                                onClick = { showSpeedMenu = true }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            // 比例按钮
                            FullscreenControlButton(
                                text = aspectRatio.displayName,
                                isHighlighted = aspectRatio != VideoAspectRatio.FIT,
                                onClick = { showRatioMenu = true }
                            )
                        }
                    }
                }
            }
        }
        
        //  [新增] 弹幕设置面板
        if (showDanmakuSettings) {
            //  使用本地状态确保滑动条可以更新
            var localOpacity by remember(danmakuOpacity) { mutableFloatStateOf(danmakuOpacity) }
            var localFontScale by remember(danmakuFontScale) { mutableFloatStateOf(danmakuFontScale) }
            var localSpeed by remember(danmakuSpeed) { mutableFloatStateOf(danmakuSpeed) }
            var localDisplayArea by remember(danmakuDisplayArea) { mutableFloatStateOf(danmakuDisplayArea) }
            var localMergeDuplicates by remember(danmakuMergeDuplicates) { mutableStateOf(danmakuMergeDuplicates) }
            var localAllowScroll by remember(danmakuAllowScroll) { mutableStateOf(danmakuAllowScroll) }
            var localAllowTop by remember(danmakuAllowTop) { mutableStateOf(danmakuAllowTop) }
            var localAllowBottom by remember(danmakuAllowBottom) { mutableStateOf(danmakuAllowBottom) }
            var localAllowColorful by remember(danmakuAllowColorful) { mutableStateOf(danmakuAllowColorful) }
            var localAllowSpecial by remember(danmakuAllowSpecial) { mutableStateOf(danmakuAllowSpecial) }
            var localSmartOcclusion by remember(danmakuSmartOcclusion) { mutableStateOf(danmakuSmartOcclusion) }
            var localBlockRulesRaw by remember(danmakuBlockRulesRaw) { mutableStateOf(danmakuBlockRulesRaw) }
            var localFullscreenPanelWidthMode by remember(danmakuSettings.fullscreenPanelWidthMode) {
                mutableStateOf(danmakuSettings.fullscreenPanelWidthMode)
            }
            
            DanmakuSettingsPanel(
                isFullscreen = true,
                settingsScope = danmakuScope,
                opacity = localOpacity,
                fontScale = localFontScale,
                speed = localSpeed,
                displayArea = localDisplayArea,
                mergeDuplicates = localMergeDuplicates,
                allowScroll = localAllowScroll,
                allowTop = localAllowTop,
                allowBottom = localAllowBottom,
                allowColorful = localAllowColorful,
                allowSpecial = localAllowSpecial,
                showBlockRuleEditor = true,
                blockRulesRaw = localBlockRulesRaw,
                smartOcclusion = localSmartOcclusion,
                fullscreenWidthMode = localFullscreenPanelWidthMode,
                smartOcclusionModuleState = smartOcclusionModuleState,
                smartOcclusionDownloadProgress = smartOcclusionDownloadProgress,
                onOpacityChange = { 
                    localOpacity = it
                    danmakuManager.opacity = it
                    scope.launch { SettingsManager.setDanmakuOpacity(context, it, danmakuScope) }
                },
                onFontScaleChange = { 
                    localFontScale = it
                    danmakuManager.fontScale = it
                    scope.launch { SettingsManager.setDanmakuFontScale(context, it, danmakuScope) }
                },
                onSpeedChange = { 
                    localSpeed = it
                    danmakuManager.speedFactor = it
                    scope.launch { SettingsManager.setDanmakuSpeed(context, it, danmakuScope) }
                },
                onDisplayAreaChange = {
                    localDisplayArea = it
                    danmakuManager.displayArea = it
                    scope.launch { SettingsManager.setDanmakuArea(context, it, danmakuScope) }
                },
                onMergeDuplicatesChange = {
                    localMergeDuplicates = it
                    // 需要在 Manager 中添加临时变量或直接持久化
                    // 对于 Switch 这种立即生效的 Prefernce，直接存就行
                    scope.launch { SettingsManager.setDanmakuMergeDuplicates(context, it, danmakuScope) }
                },
                onAllowScrollChange = {
                    localAllowScroll = it
                    scope.launch { SettingsManager.setDanmakuAllowScroll(context, it, danmakuScope) }
                },
                onAllowTopChange = {
                    localAllowTop = it
                    scope.launch { SettingsManager.setDanmakuAllowTop(context, it, danmakuScope) }
                },
                onAllowBottomChange = {
                    localAllowBottom = it
                    scope.launch { SettingsManager.setDanmakuAllowBottom(context, it, danmakuScope) }
                },
                onAllowColorfulChange = {
                    localAllowColorful = it
                    scope.launch { SettingsManager.setDanmakuAllowColorful(context, it, danmakuScope) }
                },
                onAllowSpecialChange = {
                    localAllowSpecial = it
                    scope.launch { SettingsManager.setDanmakuAllowSpecial(context, it, danmakuScope) }
                },
                onSmartOcclusionChange = {
                    localSmartOcclusion = it
                    scope.launch { SettingsManager.setDanmakuSmartOcclusion(context, it, danmakuScope) }
                },
                onBlockRulesRawChange = {
                    localBlockRulesRaw = it
                    scope.launch { SettingsManager.setDanmakuBlockRulesRaw(context, it, danmakuScope) }
                },
                onFullscreenWidthModeChange = {
                    localFullscreenPanelWidthMode = it
                    scope.launch { SettingsManager.setDanmakuFullscreenPanelWidthMode(context, it) }
                },
                onSmartOcclusionDownloadClick = {
                    if (smartOcclusionModuleState != FaceOcclusionModuleState.Downloading) {
                        scope.launch {
                            smartOcclusionModuleState = FaceOcclusionModuleState.Downloading
                            smartOcclusionDownloadProgress = 0
                            smartOcclusionModuleState = installFaceOcclusionModule(
                                context = context,
                                detector = faceDetector,
                                onProgress = { progress ->
                                    smartOcclusionDownloadProgress = progress
                                }
                            )
                            if (smartOcclusionModuleState != FaceOcclusionModuleState.Downloading) {
                                smartOcclusionDownloadProgress = null
                            }
                        }
                    }
                },
                onDismiss = { showDanmakuSettings = false }
            )
        }
        
        //  播放速度选择菜单
        if (showSpeedMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) {
                        detectTapGestures { showSpeedMenu = false }
                    },
                contentAlignment = Alignment.Center
            ) {
                com.android.purebilibili.feature.video.ui.components.SpeedSelectionMenu(
                    currentSpeed = playbackSpeed,
                    onSpeedSelected = { speed ->
                        playbackSpeed = speed
                        player?.setPlaybackSpeed(speed)
                        scope.launch {
                            SettingsManager.setLastPlaybackSpeed(context, speed)
                        }
                        showSpeedMenu = false
                        lastInteractionTime = System.currentTimeMillis()
                    },
                    onDismiss = { showSpeedMenu = false }
                )
            }
        }
        
        //  视频比例选择菜单
        if (showRatioMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) {
                        detectTapGestures { showRatioMenu = false }
                    },
                contentAlignment = Alignment.Center
            ) {
                com.android.purebilibili.feature.video.ui.components.AspectRatioMenu(
                    currentRatio = aspectRatio,
                    onRatioSelected = { ratio ->
                        aspectRatio = ratio
                        scope.launch {
                            SettingsManager.setFullscreenAspectRatio(
                                context,
                                ratio.toFullscreenAspectRatio()
                            )
                        }
                        showRatioMenu = false
                        lastInteractionTime = System.currentTimeMillis()
                    },
                    onDismiss = { showRatioMenu = false }
                )
            }
        }
    }
}

@Composable
private fun GestureIndicator(
    mode: FullscreenGestureMode,
    value: Float,
    seekTime: Long?,
    duration: Long,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val renderProgress by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 120),
        label = "fullscreen-gesture-progress"
    )
    val accentColor = when (mode) {
        FullscreenGestureMode.Brightness -> Color(0xFFFFD54F)
        FullscreenGestureMode.Volume -> Color(0xFF80DEEA)
        else -> Color.White
    }
    val overlayShape = RoundedCornerShape(18.dp)
    if (mode == FullscreenGestureMode.Seek) {
        Surface(
            modifier = modifier.then(
                if (hazeState != null) {
                    Modifier.unifiedBlur(
                        hazeState = hazeState,
                        shape = overlayShape,
                        surfaceType = BlurSurfaceType.OVERLAY
                    )
                } else {
                    Modifier
                }
            ),
            shape = overlayShape,
            color = Color.Black.copy(alpha = 0.74f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.58f))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(min = 128.dp, max = 190.dp)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(
                    "${FormatUtils.formatDuration(((seekTime ?: 0) / 1000).toInt())} / ${FormatUtils.formatDuration((duration / 1000).toInt())}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .widthIn(min = 128.dp, max = 190.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            when (mode) {
                FullscreenGestureMode.Brightness -> {
                    Icon(CupertinoIcons.Default.SunMax, null, tint = accentColor, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("亮度", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("${(value * 100).toInt()}%", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color.White.copy(alpha = 0.20f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(renderProgress)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(accentColor.copy(alpha = 0.66f), accentColor)
                                    )
                                )
                        )
                    }
                }
                FullscreenGestureMode.Volume -> {
                    val volumeIcon = when {
                        value < 0.01f -> CupertinoIcons.Default.SpeakerSlash
                        value < 0.5f -> CupertinoIcons.Default.Speaker
                        else -> CupertinoIcons.Default.SpeakerWave2
                    }
                    Icon(volumeIcon, null, tint = accentColor, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("音量", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("${(value * 100).toInt()}%", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color.White.copy(alpha = 0.20f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(renderProgress)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(accentColor.copy(alpha = 0.66f), accentColor)
                                    )
                                )
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

/**
 *  全屏底部控制按钮
 */
@Composable
private fun FullscreenControlButton(
    text: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        Text(
            text = text,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary else Color.White,
            fontSize = 12.sp,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private fun formatCurrentClock(): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date())
}
