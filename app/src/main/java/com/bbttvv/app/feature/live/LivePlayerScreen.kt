package com.bbttvv.app.feature.live

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bbttvv.app.core.player.createConfiguredPlayer
import com.bbttvv.app.core.store.player.DanmakuSettings
import com.bbttvv.app.core.store.player.DanmakuSettingsStore
import com.bbttvv.app.core.store.player.toEngineConfig
import com.bbttvv.app.core.util.ScreenUtils
import com.bbttvv.app.feature.video.danmaku.DanmakuOverlay
import com.bbttvv.app.feature.video.screen.buildPanelOptionsFocusKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

private const val LIVE_OVERLAY_AUTO_HIDE_MS = 3500L

private data class LiveOverlayAutoHideState(
    val isPlaying: Boolean,
    val showOverlay: Boolean,
    val hasActivePanel: Boolean,
    val isLoading: Boolean,
    val hasError: Boolean,
) {
    val shouldAutoHide: Boolean
        get() = isPlaying && showOverlay && !hasActivePanel && !isLoading && !hasError
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun LivePlayerScreen(
    roomId: Long,
    onBack: () -> Unit,
    viewModel: LivePlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val danmakuPayload by viewModel.danmakuPayload.collectAsStateWithLifecycle()
    val isDanmakuEnabled by viewModel.isDanmakuEnabled.collectAsStateWithLifecycle()
    val storedDanmakuSettings by DanmakuSettingsStore.getSettings(context)
        .collectAsStateWithLifecycle(initialValue = DanmakuSettings())
    val danmakuConfig = remember(storedDanmakuSettings) { storedDanmakuSettings.toEngineConfig() }
    val exoPlayer = remember(context) { createConfiguredPlayer(context) }
    val keyCaptureRequester = remember { FocusRequester() }
    val actions = remember { LiveOverlayAction.entries }
    var showOverlay by rememberSaveable(roomId) { mutableStateOf(true) }
    var selectedActionIndex by rememberSaveable(roomId) { mutableIntStateOf(0) }
    var activePanelKey by rememberSaveable(roomId) { mutableStateOf<String?>(null) }
    var selectedPanelIndex by rememberSaveable(roomId) { mutableIntStateOf(0) }
    var showDebugOverlay by rememberSaveable(roomId) { mutableStateOf(false) }
    val debugMetrics = rememberLivePlaybackDebugMetrics(
        player = exoPlayer,
        roomId = roomId,
        streamUrl = uiState.streamUrl,
        isDebugOverlayVisible = showDebugOverlay,
    )
    val activePanel = activePanelKey?.let(LiveOverlayAction::valueOf)
    val panelOptions = remember(activePanel, uiState) {
        buildLivePanelOptions(activePanel = activePanel, uiState = uiState)
    }
    val panelOptionsFocusKey = buildPanelOptionsFocusKey(panelOptions)
    val latestUiState = rememberUpdatedState(uiState)
    val latestPlaybackState = rememberUpdatedState(playbackState)
    val exitLivePlayer = {
        viewModel.resetTransientPlaybackTuning()
        viewModel.finishSession(reason = "back_pressed")
        onBack()
    }
    val handlePlayerKeyDown: (Int) -> Boolean = { keyCode ->
        when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> {
                when {
                    activePanel != null -> activePanelKey = null
                    showDebugOverlay -> showDebugOverlay = false
                    else -> exitLivePlayer()
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                showOverlay = true
                if (activePanel != null) {
                    activePanelKey = null
                } else {
                    selectedActionIndex = (selectedActionIndex - 1).floorMod(actions.size)
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                showOverlay = true
                if (activePanel == null) {
                    selectedActionIndex = (selectedActionIndex + 1).floorMod(actions.size)
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                if (activePanel != null && panelOptions.isNotEmpty()) {
                    selectedPanelIndex = (selectedPanelIndex - 1).floorMod(panelOptions.size)
                } else {
                    showOverlay = true
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (activePanel != null && panelOptions.isNotEmpty()) {
                    selectedPanelIndex = (selectedPanelIndex + 1).floorMod(panelOptions.size)
                } else {
                    showOverlay = true
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                val wasOverlayVisible = showOverlay
                showOverlay = true
                when {
                    !uiState.errorMessage.isNullOrBlank() -> viewModel.retry()
                    activePanel != null -> {
                        panelOptions.getOrNull(selectedPanelIndex)?.let { option ->
                            activePanelKey = null
                            when (activePanel) {
                                LiveOverlayAction.Quality -> viewModel.changeQuality(option.key.toIntOrNull() ?: return@let)
                                LiveOverlayAction.Line -> viewModel.changeLine(option.key)
                                LiveOverlayAction.Bitrate -> viewModel.changeBitrateMode(option.key)
                                LiveOverlayAction.AudioBalance -> viewModel.changeAudioBalance(option.key)
                                LiveOverlayAction.PlayerCore -> viewModel.changePlayerCore(option.key)
                                else -> Unit
                            }
                        }
                    }
                    !wasOverlayVisible -> viewModel.togglePlayback()
                    else -> when (actions[selectedActionIndex]) {
                        LiveOverlayAction.Danmaku -> viewModel.toggleDanmaku()
                        LiveOverlayAction.Quality,
                        LiveOverlayAction.Line,
                        LiveOverlayAction.Bitrate,
                        LiveOverlayAction.AudioBalance,
                        LiveOverlayAction.PlayerCore -> {
                            activePanelKey = actions[selectedActionIndex].name
                        }
                        LiveOverlayAction.Debug -> showDebugOverlay = !showDebugOverlay
                    }
                }
                true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                showOverlay = true
                viewModel.togglePlayback()
                true
            }

            KeyEvent.KEYCODE_MENU -> {
                showOverlay = true
                true
            }

            else -> false
        }
    }

    BackHandler {
        when {
            activePanel != null -> activePanelKey = null
            showDebugOverlay -> showDebugOverlay = false
            else -> exitLivePlayer()
        }
    }

    DisposableEffect(exoPlayer) {
        viewModel.attachPlayer(exoPlayer)
        onDispose {
            ScreenUtils.setPlaybackKeepScreenOn(context = context, keepScreenOn = false)
            viewModel.resetTransientPlaybackTuning()
            viewModel.finishSession(reason = "screen_dispose")
            exoPlayer.release()
        }
    }

    DisposableEffect(hostView, handlePlayerKeyDown) {
        hostView.isFocusable = true
        hostView.isFocusableInTouchMode = true
        hostView.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener false
            }
            handlePlayerKeyDown(keyCode)
        }
        hostView.requestFocus()
        onDispose {
            hostView.setOnKeyListener(null)
        }
    }

    LaunchedEffect(roomId) {
        showOverlay = true
        selectedActionIndex = 0
        activePanelKey = null
        selectedPanelIndex = 0
        showDebugOverlay = false
        viewModel.loadLive(roomId = roomId, force = true)
    }

    LaunchedEffect(panelOptionsFocusKey, activePanel) {
        selectedPanelIndex = panelOptions.indexOfFirst { it.isSelected }
            .takeIf { it >= 0 }
            ?: selectedPanelIndex.coerceIn(0, panelOptions.lastIndex.coerceAtLeast(0))
    }

    LaunchedEffect(roomId, showOverlay, activePanel, showDebugOverlay, uiState.isLoading, uiState.errorMessage) {
        runCatching { keyCaptureRequester.requestFocus() }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { latestUiState.value.statusMessage.orEmpty() }
            .distinctUntilChanged()
            .collectLatest { statusMessage ->
                if (statusMessage.isBlank()) return@collectLatest
                showOverlay = true
                delay(1800L)
                viewModel.clearStatusMessage()
            }
    }

    LaunchedEffect(Unit) {
        snapshotFlow {
            val currentUiState = latestUiState.value
            LiveOverlayAutoHideState(
                isPlaying = latestPlaybackState.value.isPlaying,
                showOverlay = showOverlay,
                hasActivePanel = activePanelKey != null,
                isLoading = currentUiState.isLoading,
                hasError = !currentUiState.errorMessage.isNullOrBlank()
            )
        }
            .distinctUntilChanged()
            .collectLatest { state ->
                if (!state.shouldAutoHide) return@collectLatest
                delay(LIVE_OVERLAY_AUTO_HIDE_MS)
                val currentUiState = latestUiState.value
                if (
                    latestPlaybackState.value.isPlaying &&
                    showOverlay &&
                    activePanelKey == null &&
                    !currentUiState.isLoading &&
                    currentUiState.errorMessage.isNullOrBlank()
                ) {
                    showOverlay = false
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(keyCaptureRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    handlePlayerKeyDown(keyEvent.nativeKeyEvent.keyCode)
                } else {
                    false
                }
            }
    ) {
        AndroidView(
            factory = { androidContext ->
                PlayerView(androidContext).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setShutterBackgroundColor(Color.Black.toArgb())
                }
            },
            update = { playerView ->
                if (playerView.player !== exoPlayer) {
                    playerView.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        DanmakuOverlay(
            payload = danmakuPayload,
            isEnabled = isDanmakuEnabled,
            isPlaying = playbackState.isPlaying,
            playbackPositionMs = playbackState.positionMs,
            config = danmakuConfig,
            modifier = Modifier.fillMaxSize()
        )

        if (uiState.isLoading) {
            Text(
                text = "正在加载直播...",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        uiState.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                color = Color(0xFFFFB5C2),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            )
        }

        if (showOverlay) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                uiState.statusMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.64f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
                LiveActionBar(
                    actions = actions,
                    selectedIndex = selectedActionIndex,
                    hasActivePanel = activePanel != null
                )
                activePanel?.let { panel ->
                    LiveOptionsPanel(
                        title = panelTitleFor(panel),
                        options = panelOptions,
                        selectedIndex = selectedPanelIndex
                    )
                }
            }
        }

        if (showDebugOverlay) {
            val debugSnapshot = remember(uiState, playbackState, debugMetrics) {
                buildLiveDebugSnapshot(
                    player = exoPlayer,
                    uiState = uiState,
                    playbackState = playbackState,
                    metrics = debugMetrics,
                )
            }
            LiveDebugOverlay(
                snapshot = debugSnapshot,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 28.dp, end = 28.dp)
            )
        }
    }
}

private fun Int.floorMod(size: Int): Int {
    if (size <= 0) return 0
    val mod = this % size
    return if (mod < 0) mod + size else mod
}
