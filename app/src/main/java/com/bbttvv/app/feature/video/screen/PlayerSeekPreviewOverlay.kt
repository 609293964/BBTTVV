package com.bbttvv.app.feature.video.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bbttvv.app.feature.video.viewmodel.PlayerPlaybackState
import com.bbttvv.app.feature.video.videoshot.VideoShotFrame
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun SimpleSeekOverlay(
    state: SimpleSeekState,
    previewFrame: VideoShotFrame?,
    playbackState: State<PlayerPlaybackState>,
    modifier: Modifier = Modifier,
) {
    val currentPlaybackState = playbackState.value
    if (previewFrame == null || currentPlaybackState.durationMs <= 0L) return

    val progress = when {
        currentPlaybackState.durationMs <= 0L -> 0f
        else -> (state.targetPositionMs.toFloat() / currentPlaybackState.durationMs.toFloat()).coerceIn(0f, 1f)
    }
    val previewWidth = 165.dp

    BoxWithConstraints(
        modifier = modifier.height(96.dp),
    ) {
        val maxOffset = (maxWidth - previewWidth).coerceAtLeast(0.dp)
        val previewOffset = ((maxWidth * progress) - (previewWidth / 2)).coerceIn(0.dp, maxOffset)

        VideoShotPreviewImage(
            frame = previewFrame,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = previewOffset, y = (-6).dp)
                .width(previewWidth),
        )
    }
}

@Composable
private fun VideoShotPreviewImage(
    frame: VideoShotFrame,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = remember(frame.spriteSheet) { frame.spriteSheet.asImageBitmap() }
    Canvas(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
    ) {
        val crop = calculateCenteredCrop(
            left = frame.srcRect.left,
            top = frame.srcRect.top,
            width = frame.srcRect.width(),
            height = frame.srcRect.height(),
            targetAspect = 16f / 9f,
        )
        drawImage(
            image = imageBitmap,
            srcOffset = IntOffset(crop.left, crop.top),
            srcSize = IntSize(crop.width, crop.height),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}

private data class CropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

private fun calculateCenteredCrop(
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    targetAspect: Float,
): CropRect {
    val sourceWidth = width.coerceAtLeast(1)
    val sourceHeight = height.coerceAtLeast(1)
    val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
    if (abs(sourceAspect - targetAspect) < 0.001f) {
        return CropRect(left = left, top = top, width = sourceWidth, height = sourceHeight)
    }
    return if (sourceAspect > targetAspect) {
        val croppedWidth = (sourceHeight * targetAspect).roundToInt().coerceIn(1, sourceWidth)
        val inset = ((sourceWidth - croppedWidth) / 2).coerceAtLeast(0)
        CropRect(left = left + inset, top = top, width = croppedWidth, height = sourceHeight)
    } else {
        val croppedHeight = (sourceWidth / targetAspect).roundToInt().coerceIn(1, sourceHeight)
        val inset = ((sourceHeight - croppedHeight) / 2).coerceAtLeast(0)
        CropRect(left = left, top = top + inset, width = sourceWidth, height = croppedHeight)
    }
}

