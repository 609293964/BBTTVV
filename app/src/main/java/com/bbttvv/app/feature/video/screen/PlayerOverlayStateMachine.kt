package com.bbttvv.app.feature.video.screen

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bbttvv.app.feature.video.viewmodel.PlayerPlaybackState

internal enum class PlayerOverlayMode {
    Hidden,
    FullControls,
}

internal enum class PlayerFullControlsFocus {
    Progress,
    Actions,
}

internal data class SimpleSeekState(
    val targetPositionMs: Long,
)

internal enum class PlayerAction(val label: String, val symbol: String) {
    Detail("视频详细信息", "i"),
    Comments("视频评论", "评"),
    Speed("播放速度", "倍"),
    Quality("画质选择", "清"),
    Danmaku("弹幕设置", "弹"),
    Audio("音频音质", "音"),
    Codec("格式编码", "码"),
}

internal data class PanelOption(
    val key: String,
    val label: String,
    val subtitle: String? = null,
    val valueText: String? = null,
    val isSelected: Boolean = false,
    val isEnabled: Boolean = true,
    val presentation: PanelOptionPresentation = PanelOptionPresentation.Choice,
)

internal enum class PanelOptionPresentation {
    Choice,
    Setting,
}

internal fun buildPanelOptionsFocusKey(options: List<PanelOption>): String {
    return options.joinToString(separator = "|") { option ->
        "${option.key}:${option.isSelected}"
    }
}

internal data class PlayerOverlayUiState(
    val overlayMode: PlayerOverlayMode = PlayerOverlayMode.FullControls,
    val interactionToken: Int = 0,
    val simpleSeekState: SimpleSeekState? = null,
    val fullControlsFocus: PlayerFullControlsFocus = PlayerFullControlsFocus.Progress,
    val selectedActionIndex: Int = 0,
    val activePanel: PlayerAction? = null,
    val selectedPanelIndex: Int = 0,
    val showDebugOverlay: Boolean = false,
    val isScrubbing: Boolean = false,
    val scrubDirection: Int = 0,
    val scrubPreviewPositionMs: Long? = null,
    val resumePlaybackAfterScrub: Boolean = false,
    val pendingSeekKeyCode: Int = KeyEvent.KEYCODE_UNKNOWN,
    val pendingSeekDirection: Int = 0,
)

internal sealed interface PlayerOverlayEffect {
    data object ClearSeekPreview : PlayerOverlayEffect
    data object TogglePlayback : PlayerOverlayEffect
    data object ToggleDanmaku : PlayerOverlayEffect
    data object OpenComments : PlayerOverlayEffect
    data object ExitPlayer : PlayerOverlayEffect
    data class RequestSeekPreview(val targetPositionMs: Long) : PlayerOverlayEffect
    data class SeekBy(val deltaMs: Long) : PlayerOverlayEffect
    data class FinishSeekScrub(
        val targetPositionMs: Long,
        val resumePlaybackAfterScrub: Boolean,
    ) : PlayerOverlayEffect
    data class SetPlaybackSpeed(val speed: Float) : PlayerOverlayEffect
    data class ChangeQuality(val qualityId: Int) : PlayerOverlayEffect
    data class ChangeAudioQuality(val qualityId: Int) : PlayerOverlayEffect
    data class ChangeVideoCodec(val codecId: Int) : PlayerOverlayEffect
    data class ActivateDanmakuSetting(val key: String) : PlayerOverlayEffect
}

internal class PlayerOverlayStateMachine {
    var uiState by mutableStateOf(PlayerOverlayUiState())
        private set

    fun resetForNewVideo(onEffect: (PlayerOverlayEffect) -> Unit) {
        if (uiState.simpleSeekState != null) {
            onEffect(PlayerOverlayEffect.ClearSeekPreview)
        }
        uiState = PlayerOverlayUiState(interactionToken = uiState.interactionToken + 1)
    }

    fun onPlaybackPaused() {
        uiState = uiState.copy(
            overlayMode = PlayerOverlayMode.FullControls,
            fullControlsFocus = PlayerFullControlsFocus.Progress,
            interactionToken = uiState.interactionToken + 1,
        )
    }

    fun syncPanelOptions(panelOptions: List<PanelOption>) {
        val selectedIndex = panelOptions.indexOfFirst { it.isSelected }.takeIf { it >= 0 }
            ?: uiState.selectedPanelIndex.coerceIn(0, (panelOptions.lastIndex).coerceAtLeast(0))
        uiState = uiState.copy(
            fullControlsFocus = if (uiState.activePanel != null) {
                PlayerFullControlsFocus.Actions
            } else {
                uiState.fullControlsFocus
            },
            selectedPanelIndex = selectedIndex,
        )
    }

    fun showFullOverlay(
        focus: PlayerFullControlsFocus = uiState.fullControlsFocus,
        preserveSeekPreview: Boolean = false,
        onEffect: (PlayerOverlayEffect) -> Unit,
    ) {
        clearPendingSeek()
        if (!preserveSeekPreview) {
            clearSeekPreview(onEffect)
        }
        uiState = uiState.copy(
            overlayMode = PlayerOverlayMode.FullControls,
            fullControlsFocus = if (uiState.activePanel != null) PlayerFullControlsFocus.Actions else focus,
            interactionToken = uiState.interactionToken + 1,
        )
    }

    fun hideOverlay(onEffect: (PlayerOverlayEffect) -> Unit) {
        clearPendingSeek()
        clearSeekPreview(onEffect)
        uiState = uiState.copy(overlayMode = PlayerOverlayMode.Hidden)
    }

    fun dismissSeekPreview(onEffect: (PlayerOverlayEffect) -> Unit) {
        clearSeekPreview(onEffect)
    }

    fun handleBack(onEffect: (PlayerOverlayEffect) -> Unit) {
        when {
            uiState.activePanel != null -> {
                uiState = uiState.copy(activePanel = null)
                showFullOverlay(PlayerFullControlsFocus.Actions, onEffect = onEffect)
            }

            uiState.showDebugOverlay -> {
                uiState = uiState.copy(showDebugOverlay = false)
                showFullOverlay(PlayerFullControlsFocus.Actions, onEffect = onEffect)
            }

            uiState.overlayMode == PlayerOverlayMode.FullControls -> {
                hideOverlay(onEffect)
            }

            else -> onEffect(PlayerOverlayEffect.ExitPlayer)
        }
    }

    fun handleKeyEvent(
        event: KeyEvent,
        playbackState: PlayerPlaybackState,
        actions: List<PlayerAction>,
        panelOptions: List<PanelOption>,
        pauseForSeekScrub: () -> Boolean,
        onEffect: (PlayerOverlayEffect) -> Unit,
    ): Boolean {
        val isDpadLeft = event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
        val isDpadRight = event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        val isSeekLeft = isDpadLeft || event.keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
        val isSeekRight = isDpadRight || event.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        val isSeekKey = isSeekLeft || isSeekRight
        val canSeekFromFullControls = uiState.overlayMode == PlayerOverlayMode.FullControls &&
            uiState.activePanel == null &&
            uiState.fullControlsFocus == PlayerFullControlsFocus.Progress
        val shouldHandleAsSeek = uiState.overlayMode != PlayerOverlayMode.FullControls ||
            canSeekFromFullControls ||
            (!isDpadLeft && !isDpadRight)

        if (event.action == KeyEvent.ACTION_UP) {
            return when {
                isSeekKey && shouldHandleAsSeek -> {
                    if (uiState.isScrubbing) {
                        finishSeekScrub(onEffect)
                    } else if (uiState.pendingSeekDirection != 0) {
                        clearPendingSeek()
                        stepSeek(
                            deltaMs = if (isSeekLeft) -SEEK_INTERVAL_MS else SEEK_INTERVAL_MS,
                            playbackState = playbackState,
                            onEffect = onEffect,
                        )
                    }
                    true
                }

                else -> false
            }
        }

        if (event.action != KeyEvent.ACTION_DOWN) return false

        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> {
                clearPendingSeek()
                handleBack(onEffect)
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                when {
                    uiState.activePanel != null -> activatePanelOption(panelOptions, onEffect)
                    uiState.overlayMode == PlayerOverlayMode.FullControls -> {
                        when (uiState.fullControlsFocus) {
                            PlayerFullControlsFocus.Progress -> {
                                showFullOverlay(PlayerFullControlsFocus.Progress, onEffect = onEffect)
                                onEffect(PlayerOverlayEffect.TogglePlayback)
                            }

                            PlayerFullControlsFocus.Actions -> activateSelectedAction(actions, onEffect)
                        }
                    }

                    else -> {
                        showFullOverlay(PlayerFullControlsFocus.Progress, onEffect = onEffect)
                        onEffect(PlayerOverlayEffect.TogglePlayback)
                    }
                }
                true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                clearPendingSeek()
                showFullOverlay(PlayerFullControlsFocus.Progress, onEffect = onEffect)
                onEffect(PlayerOverlayEffect.TogglePlayback)
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                when {
                    uiState.activePanel != null -> {
                        uiState = uiState.copy(activePanel = null)
                        showFullOverlay(PlayerFullControlsFocus.Actions, onEffect = onEffect)
                        true
                    }
                    shouldHandleAsSeek -> {
                        if (event.repeatCount > 0) {
                            clearPendingSeek()
                            startSeekScrub(playbackState, -1, pauseForSeekScrub, onEffect)
                        } else {
                            beginPendingSeek(event.keyCode, -1)
                        }
                        true
                    }

                    uiState.overlayMode == PlayerOverlayMode.FullControls -> {
                        moveAction(-1, actions.size)
                        true
                    }

                    else -> false
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                when {
                    uiState.activePanel != null -> true
                    shouldHandleAsSeek -> {
                        if (event.repeatCount > 0) {
                            clearPendingSeek()
                            startSeekScrub(playbackState, 1, pauseForSeekScrub, onEffect)
                        } else {
                            beginPendingSeek(event.keyCode, 1)
                        }
                        true
                    }

                    uiState.overlayMode == PlayerOverlayMode.FullControls -> {
                        moveAction(1, actions.size)
                        true
                    }

                    else -> false
                }
            }

            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (event.repeatCount > 0) {
                    clearPendingSeek()
                    startSeekScrub(playbackState, -1, pauseForSeekScrub, onEffect)
                } else {
                    beginPendingSeek(event.keyCode, -1)
                }
                true
            }

            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (event.repeatCount > 0) {
                    clearPendingSeek()
                    startSeekScrub(playbackState, 1, pauseForSeekScrub, onEffect)
                } else {
                    beginPendingSeek(event.keyCode, 1)
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                when {
                    uiState.overlayMode == PlayerOverlayMode.FullControls && uiState.activePanel != null -> {
                        movePanel(1, panelOptions.size)
                    }

                    uiState.overlayMode != PlayerOverlayMode.FullControls -> {
                        showFullOverlay(PlayerFullControlsFocus.Progress, onEffect = onEffect)
                    }

                    uiState.fullControlsFocus == PlayerFullControlsFocus.Actions -> {
                        uiState = uiState.copy(
                            fullControlsFocus = PlayerFullControlsFocus.Progress,
                            interactionToken = uiState.interactionToken + 1,
                        )
                    }

                    else -> registerInteraction()
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                when {
                    uiState.overlayMode == PlayerOverlayMode.FullControls && uiState.activePanel != null -> {
                        movePanel(-1, panelOptions.size)
                    }

                    uiState.overlayMode != PlayerOverlayMode.FullControls -> {
                        showFullOverlay(PlayerFullControlsFocus.Progress, onEffect = onEffect)
                    }

                    uiState.fullControlsFocus == PlayerFullControlsFocus.Progress && actions.isNotEmpty() -> {
                        uiState = uiState.copy(
                            fullControlsFocus = PlayerFullControlsFocus.Actions,
                            interactionToken = uiState.interactionToken + 1,
                        )
                    }

                    else -> registerInteraction()
                }
                true
            }

            else -> false
        }
    }

    fun promotePendingSeekToScrub(
        playbackState: PlayerPlaybackState,
        pauseForSeekScrub: () -> Boolean,
        onEffect: (PlayerOverlayEffect) -> Unit,
    ) {
        val direction = uiState.pendingSeekDirection
        if (direction == 0 || uiState.pendingSeekKeyCode == KeyEvent.KEYCODE_UNKNOWN || uiState.isScrubbing) {
            return
        }
        startSeekScrub(playbackState, direction, pauseForSeekScrub, onEffect)
        clearPendingSeek()
    }

    fun tickScrubPreview(
        playbackState: PlayerPlaybackState,
        onEffect: (PlayerOverlayEffect) -> Unit,
    ) {
        if (!uiState.isScrubbing || uiState.scrubDirection == 0) return
        val duration = playbackState.durationMs
        if (duration <= 0L) return
        val current = (uiState.scrubPreviewPositionMs ?: playbackState.positionMs).coerceIn(0L, duration)
        val next = (current + holdScrubStepMs(duration) * uiState.scrubDirection.toLong()).coerceIn(0L, duration)
        uiState = uiState.copy(scrubPreviewPositionMs = next)
        showSeekPreview(next, onEffect)
    }

    private fun clearPendingSeek() {
        uiState = uiState.copy(
            pendingSeekKeyCode = KeyEvent.KEYCODE_UNKNOWN,
            pendingSeekDirection = 0,
        )
    }

    private fun clearSeekPreview(onEffect: (PlayerOverlayEffect) -> Unit) {
        if (uiState.simpleSeekState != null) {
            onEffect(PlayerOverlayEffect.ClearSeekPreview)
            uiState = uiState.copy(simpleSeekState = null)
        }
    }

    private fun showSeekPreview(
        targetPosition: Long,
        onEffect: (PlayerOverlayEffect) -> Unit,
    ) {
        clearPendingSeek()
        uiState = uiState.copy(
            activePanel = null,
            overlayMode = PlayerOverlayMode.FullControls,
            fullControlsFocus = PlayerFullControlsFocus.Progress,
            simpleSeekState = SimpleSeekState(targetPositionMs = targetPosition),
            interactionToken = uiState.interactionToken + 1,
        )
        onEffect(PlayerOverlayEffect.RequestSeekPreview(targetPosition))
    }

    private fun registerInteraction() {
        uiState = uiState.copy(interactionToken = uiState.interactionToken + 1)
    }

    private fun beginPendingSeek(keyCode: Int, direction: Int) {
        uiState = uiState.copy(
            pendingSeekKeyCode = keyCode,
            pendingSeekDirection = direction,
            interactionToken = uiState.interactionToken + 1,
        )
    }

    private fun stepSeek(
        deltaMs: Long,
        playbackState: PlayerPlaybackState,
        onEffect: (PlayerOverlayEffect) -> Unit,
    ) {
        val duration = playbackState.durationMs.coerceAtLeast(0L)
        val targetPosition = if (duration > 0L) {
            (playbackState.positionMs + deltaMs).coerceIn(0L, duration)
        } else {
            (playbackState.positionMs + deltaMs).coerceAtLeast(0L)
        }
        showSeekPreview(targetPosition, onEffect)
        onEffect(PlayerOverlayEffect.SeekBy(deltaMs))
    }

    private fun startSeekScrub(
        playbackState: PlayerPlaybackState,
        direction: Int,
        pauseForSeekScrub: () -> Boolean,
        onEffect: (PlayerOverlayEffect) -> Unit,
    ) {
        val duration = playbackState.durationMs
        if (duration <= 0L) return
        var resumeAfterScrub = uiState.resumePlaybackAfterScrub
        var previewPosition = uiState.scrubPreviewPositionMs
        if (!uiState.isScrubbing) {
            resumeAfterScrub = pauseForSeekScrub()
            previewPosition = playbackState.positionMs.coerceIn(0L, duration)
            uiState = uiState.copy(
                resumePlaybackAfterScrub = resumeAfterScrub,
                scrubPreviewPositionMs = previewPosition,
            )
            showSeekPreview(previewPosition, onEffect)
        }
        uiState = uiState.copy(
            scrubDirection = direction,
            isScrubbing = true,
            scrubPreviewPositionMs = previewPosition,
            resumePlaybackAfterScrub = resumeAfterScrub,
        )
    }

    private fun finishSeekScrub(onEffect: (PlayerOverlayEffect) -> Unit) {
        val targetPosition = uiState.scrubPreviewPositionMs
        val wasScrubbing = uiState.isScrubbing
        val resumeAfterScrub = uiState.resumePlaybackAfterScrub
        uiState = uiState.copy(
            isScrubbing = false,
            scrubDirection = 0,
            scrubPreviewPositionMs = null,
            resumePlaybackAfterScrub = false,
        )
        if (wasScrubbing && targetPosition != null) {
            onEffect(
                PlayerOverlayEffect.FinishSeekScrub(
                    targetPositionMs = targetPosition,
                    resumePlaybackAfterScrub = resumeAfterScrub,
                )
            )
            showSeekPreview(targetPosition, onEffect)
        }
    }

    private fun moveAction(delta: Int, actionCount: Int) {
        if (actionCount <= 0) return
        uiState = uiState.copy(
            fullControlsFocus = PlayerFullControlsFocus.Actions,
            selectedActionIndex = (uiState.selectedActionIndex + delta + actionCount) % actionCount,
            interactionToken = uiState.interactionToken + 1,
        )
    }

    private fun movePanel(delta: Int, panelCount: Int) {
        if (panelCount <= 0) return
        uiState = uiState.copy(
            selectedPanelIndex = (uiState.selectedPanelIndex + delta + panelCount) % panelCount,
            interactionToken = uiState.interactionToken + 1,
        )
    }

    private fun activateSelectedAction(
        actions: List<PlayerAction>,
        onEffect: (PlayerOverlayEffect) -> Unit,
    ) {
        when (actions.getOrNull(uiState.selectedActionIndex) ?: return) {
            PlayerAction.Detail -> {
                uiState = uiState.copy(
                    activePanel = null,
                    showDebugOverlay = !uiState.showDebugOverlay,
                )
                showFullOverlay(PlayerFullControlsFocus.Actions, onEffect = onEffect)
            }

            PlayerAction.Comments -> {
                uiState = uiState.copy(activePanel = null)
                onEffect(PlayerOverlayEffect.OpenComments)
                showFullOverlay(PlayerFullControlsFocus.Actions, onEffect = onEffect)
            }

            PlayerAction.Danmaku -> {
                uiState = uiState.copy(
                    activePanel = actions[uiState.selectedActionIndex],
                    fullControlsFocus = PlayerFullControlsFocus.Actions,
                    selectedPanelIndex = 0,
                    interactionToken = uiState.interactionToken + 1,
                )
            }

            PlayerAction.Speed,
            PlayerAction.Quality,
            PlayerAction.Audio,
            PlayerAction.Codec -> {
                uiState = uiState.copy(
                    activePanel = actions[uiState.selectedActionIndex],
                    fullControlsFocus = PlayerFullControlsFocus.Actions,
                    selectedPanelIndex = 0,
                    interactionToken = uiState.interactionToken + 1,
                )
            }
        }
    }

    private fun activatePanelOption(
        panelOptions: List<PanelOption>,
        onEffect: (PlayerOverlayEffect) -> Unit,
    ) {
        val action = uiState.activePanel ?: return
        val option = panelOptions.getOrNull(uiState.selectedPanelIndex) ?: return
        when (action) {
            PlayerAction.Speed -> option.key.toFloatOrNull()?.let {
                onEffect(PlayerOverlayEffect.SetPlaybackSpeed(it))
            }

            PlayerAction.Quality -> option.key.toIntOrNull()?.let {
                onEffect(PlayerOverlayEffect.ChangeQuality(it))
            }

            PlayerAction.Audio -> option.key.toIntOrNull()?.let {
                onEffect(PlayerOverlayEffect.ChangeAudioQuality(it))
            }

            PlayerAction.Codec -> option.key.toIntOrNull()?.let {
                onEffect(PlayerOverlayEffect.ChangeVideoCodec(it))
            }

            PlayerAction.Danmaku -> onEffect(PlayerOverlayEffect.ActivateDanmakuSetting(option.key))
            PlayerAction.Detail,
            PlayerAction.Comments -> Unit
        }
        if (action != PlayerAction.Danmaku) {
            uiState = uiState.copy(activePanel = null)
        }
        showFullOverlay(PlayerFullControlsFocus.Actions, onEffect = onEffect)
    }
}

private const val SEEK_INTERVAL_MS = 10_000L
internal const val HOLD_SCRUB_TICK_MS = 120L
private const val HOLD_SCRUB_TRAVERSE_MS = 10_000L
private const val HOLD_SCRUB_SHORT_VIDEO_THRESHOLD_MS = 40_000L
private const val HOLD_SCRUB_SHORT_SPEED_MS_PER_S = 4_000L

private fun holdScrubStepMs(durationMs: Long): Long {
    val duration = durationMs.coerceAtLeast(0L)
    if (duration <= 0L) return SEEK_INTERVAL_MS
    val step = if (duration < HOLD_SCRUB_SHORT_VIDEO_THRESHOLD_MS) {
        HOLD_SCRUB_SHORT_SPEED_MS_PER_S * HOLD_SCRUB_TICK_MS / 1000L
    } else {
        duration * HOLD_SCRUB_TICK_MS / HOLD_SCRUB_TRAVERSE_MS
    }
    return step.coerceAtLeast(1L)
}
