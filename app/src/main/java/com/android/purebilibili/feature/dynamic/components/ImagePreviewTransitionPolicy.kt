package com.android.purebilibili.feature.dynamic.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.pow

private const val LAYOUT_PROGRESS_MIN = -0.08f
private const val LAYOUT_PROGRESS_MAX = 1.02f
private const val FALLBACK_START_SCALE = 0.96f
private const val DISMISS_OVERSHOOT_FACTOR = 0.03f

internal data class ImagePreviewTransitionFrame(
    val layoutProgress: Float,
    val visualProgress: Float,
    val cornerRadiusDp: Float,
    val fallbackScale: Float
)

internal data class ImagePreviewVisualFrame(
    val contentAlpha: Float,
    val backdropAlpha: Float,
    val blurRadiusPx: Float
)

internal data class ImagePreviewDismissMotion(
    val overshootTarget: Float,
    val settleTarget: Float
)

internal data class ImagePreviewDismissTransform(
    val scale: Float,
    val translationXPx: Float,
    val translationYPx: Float
)

internal data class ImagePreviewDismissRectFrame(
    val rect: Rect,
    val dismissFraction: Float
)

internal data class ImagePreviewVerticalDragFrame(
    val progress: Float,
    val scale: Float,
    val backdropAlphaMultiplier: Float
)

internal enum class ImagePreviewVerticalDismissDecision {
    DISMISS,
    SNAP_BACK
}

enum class ImagePreviewTextPlacement {
    OVERLAY_BOTTOM,
    TOP_BAR
}

data class ImagePreviewTextContent(
    val headline: String = "",
    val body: String = "",
    val perImageCaptions: List<String> = emptyList(),
    val placement: ImagePreviewTextPlacement = ImagePreviewTextPlacement.OVERLAY_BOTTOM
)

internal data class ImagePreviewResolvedText(
    val headline: String,
    val body: String,
    val pageIndicator: String
)

internal data class ImagePreviewTextTransform(
    val rotationX: Float,
    val alpha: Float,
    val translateYDp: Float
)

internal data class ImagePreviewOverlayPadding(
    val start: Dp,
    val top: Dp,
    val end: Dp,
    val bottom: Dp
)

internal fun resolveImagePreviewTransitionFrame(
    rawProgress: Float,
    hasSourceRect: Boolean,
    sourceCornerRadiusDp: Float
): ImagePreviewTransitionFrame {
    val layoutProgress = rawProgress.coerceIn(LAYOUT_PROGRESS_MIN, LAYOUT_PROGRESS_MAX)
    val visualProgress = rawProgress.coerceIn(0f, 1f)
    val cornerRadiusDp = if (hasSourceRect) {
        sourceCornerRadiusDp.coerceAtLeast(0f)
    } else {
        0f
    }
    val fallbackScale = lerpFloat(FALLBACK_START_SCALE, 1f, visualProgress)
    return ImagePreviewTransitionFrame(
        layoutProgress = layoutProgress,
        visualProgress = visualProgress,
        cornerRadiusDp = cornerRadiusDp,
        fallbackScale = fallbackScale
    )
}

internal fun resolveImagePreviewVisualFrame(
    visualProgress: Float,
    transitionEnabled: Boolean,
    maxBlurRadiusPx: Float
): ImagePreviewVisualFrame {
    val progress = visualProgress.coerceIn(0f, 1f)
    if (!transitionEnabled) {
        return ImagePreviewVisualFrame(
            contentAlpha = 1f,
            backdropAlpha = progress,
            blurRadiusPx = 0f
        )
    }

    return ImagePreviewVisualFrame(
        contentAlpha = lerpFloat(0.9f, 1f, progress),
        backdropAlpha = progress,
        blurRadiusPx = maxBlurRadiusPx.coerceAtLeast(0f) * (1f - progress)
    )
}

internal fun imagePreviewDismissMotion(): ImagePreviewDismissMotion {
    return ImagePreviewDismissMotion(
        overshootTarget = -0.06f,
        settleTarget = 0f
    )
}

internal fun resolveImagePreviewDismissTransform(
    transitionProgress: Float,
    sourceRect: Rect?,
    displayedImageRect: Rect?
): ImagePreviewDismissTransform {
    if (sourceRect == null || displayedImageRect == null) {
        return ImagePreviewDismissTransform(
            scale = 1f,
            translationXPx = 0f,
            translationYPx = 0f
        )
    }

    val clampedProgress = transitionProgress.coerceIn(LAYOUT_PROGRESS_MIN, 1f)
    val baseDismiss = (1f - clampedProgress.coerceIn(0f, 1f)).pow(1.6f)
    val overshoot = if (clampedProgress < 0f) {
        ((-clampedProgress) / (-LAYOUT_PROGRESS_MIN)) * DISMISS_OVERSHOOT_FACTOR
    } else {
        0f
    }
    val dismissFraction = baseDismiss + overshoot
    val targetScale = min(
        sourceRect.width / displayedImageRect.width,
        sourceRect.height / displayedImageRect.height
    ).coerceIn(0f, 1f)
    val containerCenterX = (displayedImageRect.left + displayedImageRect.right) / 2f
    val containerCenterY = (displayedImageRect.top + displayedImageRect.bottom) / 2f
    val sourceCenterX = (sourceRect.left + sourceRect.right) / 2f
    val sourceCenterY = (sourceRect.top + sourceRect.bottom) / 2f
    val targetTranslationX = sourceCenterX - containerCenterX
    val targetTranslationY = sourceCenterY - containerCenterY

    return ImagePreviewDismissTransform(
        scale = lerpFloat(1f, targetScale, dismissFraction).coerceAtLeast(0.01f),
        translationXPx = lerpFloat(0f, targetTranslationX, dismissFraction),
        translationYPx = lerpFloat(0f, targetTranslationY, dismissFraction)
    )
}

internal fun resolveImagePreviewDismissRectFrame(
    transitionProgress: Float,
    sourceRect: Rect?,
    displayedImageRect: Rect?
): ImagePreviewDismissRectFrame? {
    if (sourceRect == null || displayedImageRect == null) return null

    val dismissFraction = resolveImagePreviewDismissFraction(transitionProgress)
    val displayedCenterX = (displayedImageRect.left + displayedImageRect.right) / 2f
    val displayedCenterY = (displayedImageRect.top + displayedImageRect.bottom) / 2f
    val sourceCenterX = (sourceRect.left + sourceRect.right) / 2f
    val sourceCenterY = (sourceRect.top + sourceRect.bottom) / 2f
    val width = lerpFloat(displayedImageRect.width, sourceRect.width, dismissFraction)
    val height = lerpFloat(displayedImageRect.height, sourceRect.height, dismissFraction)
    val centerX = lerpFloat(displayedCenterX, sourceCenterX, dismissFraction)
    val centerY = lerpFloat(displayedCenterY, sourceCenterY, dismissFraction)

    return ImagePreviewDismissRectFrame(
        rect = Rect(
            left = centerX - width / 2f,
            top = centerY - height / 2f,
            right = centerX + width / 2f,
            bottom = centerY + height / 2f
        ),
        dismissFraction = dismissFraction
    )
}

internal fun resolveImagePreviewDismissStartRect(
    previewSurfaceRect: Rect?,
    displayedImageRect: Rect?,
    preferPreviewSurface: Boolean
): Rect? {
    return if (preferPreviewSurface) {
        previewSurfaceRect ?: displayedImageRect
    } else {
        displayedImageRect ?: previewSurfaceRect
    }
}

internal fun resolveImagePreviewOverlayPadding(
    safeInsetStart: Dp,
    safeInsetTop: Dp,
    safeInsetEnd: Dp,
    safeInsetBottom: Dp,
    extraHorizontal: Dp = 16.dp,
    extraVertical: Dp = 16.dp
): ImagePreviewOverlayPadding {
    val resolvedHorizontal = extraHorizontal.coerceAtLeast(0.dp)
    val resolvedVertical = extraVertical.coerceAtLeast(0.dp)
    return ImagePreviewOverlayPadding(
        start = safeInsetStart.coerceAtLeast(0.dp) + resolvedHorizontal,
        top = safeInsetTop.coerceAtLeast(0.dp) + resolvedVertical,
        end = safeInsetEnd.coerceAtLeast(0.dp) + resolvedHorizontal,
        bottom = safeInsetBottom.coerceAtLeast(0.dp) + resolvedVertical
    )
}

internal fun resolveImagePreviewDraggedDisplayRect(
    displayedImageRect: Rect?,
    translationYPx: Float,
    scale: Float
): Rect? {
    if (displayedImageRect == null) return null
    val safeScale = scale.coerceAtLeast(0.01f)
    val width = displayedImageRect.width * safeScale
    val height = displayedImageRect.height * safeScale
    val centerX = (displayedImageRect.left + displayedImageRect.right) / 2f
    val centerY = (displayedImageRect.top + displayedImageRect.bottom) / 2f + translationYPx
    return Rect(
        left = centerX - width / 2f,
        top = centerY - height / 2f,
        right = centerX + width / 2f,
        bottom = centerY + height / 2f
    )
}

internal fun shouldEnableImagePreviewVerticalDismiss(zoomScale: Float): Boolean {
    return zoomScale <= 1.01f
}

internal fun resolveImagePreviewVerticalDragFrame(
    dragOffsetYPx: Float,
    containerHeightPx: Float
): ImagePreviewVerticalDragFrame {
    val denominator = (containerHeightPx.coerceAtLeast(1f) * 0.28f).coerceAtLeast(120f)
    val progress = (kotlin.math.abs(dragOffsetYPx) / denominator).coerceIn(0f, 1f)
    return ImagePreviewVerticalDragFrame(
        progress = progress,
        scale = lerpFloat(1f, 0.9f, progress),
        backdropAlphaMultiplier = lerpFloat(1f, 0.18f, progress)
    )
}

internal fun resolveImagePreviewVerticalDismissDecision(
    dragOffsetYPx: Float,
    containerHeightPx: Float
): ImagePreviewVerticalDismissDecision {
    val threshold = maxOf(120f, containerHeightPx.coerceAtLeast(1f) * 0.14f)
    return if (kotlin.math.abs(dragOffsetYPx) >= threshold) {
        ImagePreviewVerticalDismissDecision.DISMISS
    } else {
        ImagePreviewVerticalDismissDecision.SNAP_BACK
    }
}

internal fun resolveImagePreviewDismissBackdropAlpha(
    visualProgress: Float
): Float {
    return visualProgress.coerceIn(0f, 1f).pow(0.45f)
}

internal fun resolvePredictiveBackAnimationProgress(backGestureProgress: Float): Float {
    val clamped = backGestureProgress.coerceIn(0f, 1f)
    return 1f - clamped
}

internal fun resolveImagePreviewText(
    textContent: ImagePreviewTextContent?,
    currentPage: Int,
    totalPages: Int
): ImagePreviewResolvedText? {
    if (textContent == null) return null
    val headline = textContent.headline.trim().take(48)
    val pageCaption = textContent.perImageCaptions
        .getOrNull(currentPage)
        ?.trim()
        .orEmpty()
    val body = if (pageCaption.isNotEmpty()) {
        pageCaption.take(150)
    } else {
        textContent.body.trim().take(150)
    }
    val indicator = if (totalPages > 1 && currentPage >= 0) {
        "${currentPage + 1} / $totalPages"
    } else {
        ""
    }
    if (headline.isEmpty() && body.isEmpty() && indicator.isEmpty()) return null
    return ImagePreviewResolvedText(
        headline = headline,
        body = body,
        pageIndicator = indicator
    )
}

internal fun resolveImagePreviewTextTransform(pageOffsetFraction: Float): ImagePreviewTextTransform {
    val clampedOffset = pageOffsetFraction.coerceIn(-1f, 1f)
    val absOffset = kotlin.math.abs(clampedOffset)
    return ImagePreviewTextTransform(
        rotationX = absOffset * 22f,
        alpha = (1f - absOffset * 0.38f).coerceIn(0.62f, 1f),
        translateYDp = absOffset * 8f
    )
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

private fun resolveImagePreviewDismissFraction(transitionProgress: Float): Float {
    val clampedProgress = transitionProgress.coerceIn(LAYOUT_PROGRESS_MIN, 1f)
    val baseDismiss = (1f - clampedProgress.coerceIn(0f, 1f)).pow(1.6f)
    val overshoot = if (clampedProgress < 0f) {
        ((-clampedProgress) / (-LAYOUT_PROGRESS_MIN)) * DISMISS_OVERSHOOT_FACTOR
    } else {
        0f
    }
    return baseDismiss + overshoot
}
