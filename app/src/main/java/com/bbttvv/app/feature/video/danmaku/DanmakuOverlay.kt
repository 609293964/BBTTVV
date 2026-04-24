package com.bbttvv.app.feature.video.danmaku

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.bbttvv.app.core.util.Logger
import com.bytedance.danmaku.render.engine.DanmakuView
import com.bytedance.danmaku.render.engine.control.DanmakuController
import com.bytedance.danmaku.render.engine.data.DanmakuData
import com.bytedance.danmaku.render.engine.render.draw.text.TextData
import kotlin.math.abs

private const val DANMAKU_POSITION_DISCONTINUITY_THRESHOLD_MS = 2500L
private const val DANMAKU_OVERLAY_TAG = "DanmakuOverlay"
private const val DEFAULT_DANMAKU_TEXT_SIZE = 25f

private enum class DanmakuSyncReason {
    PayloadChanged,
    ConfigChanged,
    ViewAttached,
    ViewportReady,
    ViewportChanged,
    PositionDiscontinuity,
    PlayStateChanged,
}

private class DanmakuOverlayRuntime {
    var lastLoadedToken: Long = Long.MIN_VALUE
    var lastAppliedConfigToken: Long = Long.MIN_VALUE
    var lastObservedPosition: Long = 0L
    var lastObservedPlayState: Boolean = false
    var lastAppliedAttachToken: Int = Int.MIN_VALUE
    var lastAppliedViewportWidth: Int = 0
    var lastAppliedViewportHeight: Int = 0
    var waitingForAttachment: Boolean = false
    var waitingForViewport: Boolean = false

    fun resetLoadedState() {
        lastLoadedToken = Long.MIN_VALUE
        lastAppliedAttachToken = Int.MIN_VALUE
        lastAppliedViewportWidth = 0
        lastAppliedViewportHeight = 0
        lastAppliedConfigToken = Long.MIN_VALUE
        waitingForAttachment = false
        waitingForViewport = false
        lastObservedPlayState = false
    }
}

private fun DanmakuRenderPayload.renderToken(): Long {
    var result = sourceLabel.hashCode()
    result = 31 * result + totalCount
    result = 31 * result + System.identityHashCode(standardList)
    result = 31 * result + System.identityHashCode(advancedList)
    result = 31 * result + (standardList.firstOrNull()?.showAtTime?.hashCode() ?: 0)
    result = 31 * result + (standardList.lastOrNull()?.showAtTime?.hashCode() ?: 0)
    return result.toLong()
}

private fun DanmakuConfig.renderToken(): Long {
    var result = opacity.toRawBits()
    result = 31 * result + fontScale.toRawBits()
    result = 31 * result + itemTextSize.toRawBits()
    result = 31 * result + fontWeight
    result = 31 * result + speedFactor.toRawBits()
    result = 31 * result + scrollDurationSeconds.toRawBits()
    result = 31 * result + displayAreaRatio.toRawBits()
    result = 31 * result + lineHeight.toRawBits()
    result = 31 * result + strokeEnabled.hashCode()
    result = 31 * result + strokeWidth.toRawBits()
    result = 31 * result + staticDurationSeconds.toRawBits()
    return result.toLong()
}

private fun buildRenderStandardList(
    source: List<DanmakuData>,
    config: DanmakuConfig
): List<DanmakuData> {
    val targetTextSize = config.itemTextSize
    if (!targetTextSize.isFinite() || targetTextSize <= 0f) {
        return source
    }
    return source.map { item ->
        when (item) {
            is WeightedTextData -> item.copyWithResolvedTextSize(targetTextSize)
            is TextData -> item.copyWithResolvedTextSize(targetTextSize)
            else -> item
        }
    }
}

private fun WeightedTextData.copyWithResolvedTextSize(targetTextSize: Float): WeightedTextData {
    return WeightedTextData().also { copy ->
        copy.danmakuId = danmakuId
        copy.userHash = userHash
        copy.weight = weight
        copy.pool = pool
        copy.copyTextDataFrom(this, targetTextSize)
    }
}

private fun TextData.copyWithResolvedTextSize(targetTextSize: Float): TextData {
    return TextData().also { copy ->
        copy.copyTextDataFrom(this, targetTextSize)
    }
}

private fun TextData.copyTextDataFrom(source: TextData, targetTextSize: Float) {
    text = source.text
    showAtTime = source.showAtTime
    layerType = source.layerType
    textColor = source.textColor
    val rawSize = source.textSize
    val sourceTextSize = if (rawSize != null && rawSize.isFinite() && rawSize > 0f) {
        rawSize
    } else {
        DEFAULT_DANMAKU_TEXT_SIZE
    }
    val sourceSizeRatio = (sourceTextSize / DEFAULT_DANMAKU_TEXT_SIZE).coerceIn(0.5f, 2.5f)
    textSize = targetTextSize.coerceIn(10f, 120f) * sourceSizeRatio
}

@Composable
fun DanmakuOverlay(
    payload: DanmakuRenderPayload?,
    isEnabled: Boolean,
    isPlaying: Boolean,
    playbackPositionMs: Long,
    config: DanmakuConfig,
    modifier: Modifier = Modifier
) {
    var controller by remember { mutableStateOf<DanmakuController?>(null) }
    var danmakuView by remember { mutableStateOf<DanmakuView?>(null) }
    var attachChangeToken by remember { mutableIntStateOf(0) }
    var viewportChangeToken by remember { mutableIntStateOf(0) }
    val runtime = remember { DanmakuOverlayRuntime() }
    val dataToken = remember(payload) { payload?.renderToken() ?: Long.MIN_VALUE }
    val configToken = config.renderToken()
    val renderStandardList = remember(payload, configToken) {
        payload?.let { buildRenderStandardList(it.standardList, config) }.orEmpty()
    }

    fun hardSync(
        reason: DanmakuSyncReason,
        targetController: DanmakuController,
        targetPayload: DanmakuRenderPayload,
        targetRenderList: List<DanmakuData>,
        dataToken: Long,
        positionMs: Long,
        play: Boolean,
        attachToken: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        config: DanmakuConfig,
        configToken: Long
    ) {
        Logger.w(
            DANMAKU_OVERLAY_TAG,
            "hardSync reason=$reason size=${viewportWidth}x$viewportHeight position=$positionMs play=$play total=${targetPayload.totalCount}"
        )
        Logger.d(DANMAKU_OVERLAY_TAG) {
            "hardSync reason=$reason size=${viewportWidth}x$viewportHeight position=$positionMs play=$play total=${targetPayload.totalCount}"
        }
        targetController.pause()
        config.applyTo(targetController.config, viewportWidth, viewportHeight)
        targetController.setData(targetRenderList, 0L)
        targetController.start(positionMs.coerceAtLeast(0L))
        targetController.invalidateView()
        if (!play) {
            targetController.pause()
        }
        runtime.lastLoadedToken = dataToken
        runtime.lastAppliedConfigToken = configToken
        runtime.lastAppliedAttachToken = attachToken
        runtime.lastAppliedViewportWidth = viewportWidth
        runtime.lastAppliedViewportHeight = viewportHeight
        runtime.lastObservedPosition = positionMs
        runtime.lastObservedPlayState = play
    }

    @Suppress("UNUSED_VARIABLE")
    val observedAttachChangeToken = attachChangeToken
    @Suppress("UNUSED_VARIABLE")
    val observedViewportChangeToken = viewportChangeToken
    AndroidView(
        factory = { context ->
            DanmakuView(context).apply {
                alpha = if (isEnabled) 1f else 0f
                visibility = if (isEnabled) View.VISIBLE else View.INVISIBLE
                isClickable = false
                isFocusable = false
            }.also { view ->
                danmakuView = view
            }
        },
        update = { view ->
            if (danmakuView !== view) {
                danmakuView = view
            }
            val targetController = view.controller
            if (controller !== targetController) {
                controller = targetController
            }
            val viewAttached = view.isAttachedToWindow
            val viewportWidth = view.width.coerceAtLeast(0)
            val viewportHeight = view.height.coerceAtLeast(0)
            val viewportReady = viewportWidth > 0 && viewportHeight > 0

            val targetAlpha = if (isEnabled) 1f else 0f
            if (view.alpha != targetAlpha) {
                view.alpha = targetAlpha
            }
            val targetVisibility = if (isEnabled) View.VISIBLE else View.INVISIBLE
            if (view.visibility != targetVisibility) {
                view.visibility = targetVisibility
            }

            if (!isEnabled) {
                if (runtime.lastObservedPlayState) {
                    targetController.pause()
                }
                runtime.waitingForAttachment = false
                runtime.waitingForViewport = false
                runtime.lastObservedPlayState = false
                runtime.lastAppliedConfigToken = Long.MIN_VALUE
                return@AndroidView
            }

            if (payload == null || payload.standardList.isEmpty()) {
                if (runtime.lastLoadedToken != Long.MIN_VALUE) {
                    targetController.clear(0)
                }
                runtime.resetLoadedState()
                return@AndroidView
            }

            if (!viewAttached) {
                if (!runtime.waitingForAttachment) {
                    Logger.d(DANMAKU_OVERLAY_TAG) {
                        "awaiting attach for payload total=${payload.totalCount}"
                    }
                }
                runtime.waitingForAttachment = true
                runtime.lastAppliedAttachToken = Int.MIN_VALUE
                return@AndroidView
            }

            runtime.waitingForAttachment = false

            if (!viewportReady) {
                if (!runtime.waitingForViewport) {
                    Logger.d(DANMAKU_OVERLAY_TAG) {
                        "awaiting viewport for payload total=${payload.totalCount}"
                    }
                }
                runtime.waitingForViewport = true
                runtime.lastAppliedViewportWidth = 0
                runtime.lastAppliedViewportHeight = 0
                return@AndroidView
            }

            runtime.waitingForViewport = false

            val syncReason = when {
                attachChangeToken != runtime.lastAppliedAttachToken -> DanmakuSyncReason.ViewAttached
                runtime.lastAppliedViewportWidth <= 0 || runtime.lastAppliedViewportHeight <= 0 -> DanmakuSyncReason.ViewportReady
                viewportWidth != runtime.lastAppliedViewportWidth || viewportHeight != runtime.lastAppliedViewportHeight ->
                    DanmakuSyncReason.ViewportChanged
                dataToken != runtime.lastLoadedToken -> DanmakuSyncReason.PayloadChanged
                configToken != runtime.lastAppliedConfigToken -> DanmakuSyncReason.ConfigChanged
                isPlaying && isPlaying != runtime.lastObservedPlayState -> DanmakuSyncReason.PlayStateChanged
                abs(playbackPositionMs - runtime.lastObservedPosition) > DANMAKU_POSITION_DISCONTINUITY_THRESHOLD_MS ->
                    DanmakuSyncReason.PositionDiscontinuity
                else -> null
            }

            if (syncReason != null) {
                hardSync(
                    reason = syncReason,
                    targetController = targetController,
                    targetPayload = payload,
                    targetRenderList = renderStandardList,
                    dataToken = dataToken,
                    positionMs = playbackPositionMs,
                    play = isPlaying,
                    attachToken = attachChangeToken,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    config = config,
                    configToken = configToken
                )
                return@AndroidView
            }

            if (isPlaying != runtime.lastObservedPlayState) {
                Logger.d(DANMAKU_OVERLAY_TAG) {
                    "softSync reason=${DanmakuSyncReason.PlayStateChanged} size=${viewportWidth}x$viewportHeight position=$playbackPositionMs total=${payload.totalCount}"
                }
                if (!isPlaying) {
                    targetController.pause()
                } else {
                    targetController.invalidateView()
                }
                runtime.lastObservedPlayState = isPlaying
            }

            runtime.lastObservedPosition = playbackPositionMs
        },
        modifier = modifier
    )

    DisposableEffect(danmakuView) {
        val view = danmakuView
        if (view == null) {
            onDispose { }
        } else {
            val listener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val width = right - left
                val height = bottom - top
                val oldWidth = oldRight - oldLeft
                val oldHeight = oldBottom - oldTop
                if (width != oldWidth || height != oldHeight) {
                    viewportChangeToken += 1
                }
            }
            val attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    attachChangeToken += 1
                }

                override fun onViewDetachedFromWindow(v: View) {
                    attachChangeToken += 1
                }
            }
            view.addOnLayoutChangeListener(listener)
            view.addOnAttachStateChangeListener(attachListener)
            onDispose {
                view.removeOnLayoutChangeListener(listener)
                view.removeOnAttachStateChangeListener(attachListener)
            }
        }
    }

    DisposableEffect(controller) {
        onDispose {
            controller?.stop()
        }
    }

    LaunchedEffect(isEnabled, payload?.totalCount) {
        if (!isEnabled) {
            controller?.pause()
        }
    }
}
