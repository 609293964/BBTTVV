package com.bbttvv.app.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TripleActivationDurationMs = 180
private const val TripleConvergenceDurationMs = 280
private const val TripleResolutionDurationMs = 220
private const val TripleDwellDurationMs = 1_200

@Composable
internal fun DetailTripleSuccessAnimation(
    visible: Boolean,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val progress = remember { Animatable(0f) }
    val totalDurationMs = TripleActivationDurationMs +
        TripleConvergenceDurationMs +
        TripleResolutionDurationMs +
        TripleDwellDurationMs

    LaunchedEffect(visible) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = totalDurationMs, easing = FastOutSlowInEasing),
        )
        delay(120L)
        onAnimationEnd()
    }

    val totalDuration = totalDurationMs.toFloat().coerceAtLeast(1f)
    val activationEnd = TripleActivationDurationMs / totalDuration
    val convergenceEnd = (TripleActivationDurationMs + TripleConvergenceDurationMs) / totalDuration
    val resolutionEnd = (
        TripleActivationDurationMs +
            TripleConvergenceDurationMs +
            TripleResolutionDurationMs
        ) / totalDuration

    val currentProgress = progress.value
    val activationProgress = phaseProgress(currentProgress, 0f, activationEnd)
    val convergenceProgress = phaseProgress(currentProgress, activationEnd, convergenceEnd)
    val resolutionProgress = phaseProgress(currentProgress, convergenceEnd, resolutionEnd)
    val dissolveProgress = phaseProgress(currentProgress, resolutionEnd, 1f)
    val density = LocalDensity.current
    val accent = DetailAccentColor
    val white = Color.White
    val badgeScale = 0.76f + resolutionProgress * 0.38f - dissolveProgress * 0.08f
    val badgeAlpha = (resolutionProgress * (1f - dissolveProgress * 0.45f)).coerceIn(0f, 1f)
    val trailAlpha = (convergenceProgress * (1f - dissolveProgress * 0.7f)).coerceIn(0f, 1f) * 0.82f

    Box(
        modifier = modifier
            .width(320.dp)
            .height(230.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val iconStarts = with(density) {
                listOf(
                    Offset(center.x - 102.dp.toPx(), center.y + 72.dp.toPx()),
                    Offset(center.x, center.y - 90.dp.toPx()),
                    Offset(center.x + 102.dp.toPx(), center.y + 72.dp.toPx()),
                )
            }

            iconStarts.forEach { start ->
                val end = center.copy(y = center.y - 6.dp.toPx())
                drawCircle(
                    color = accent.copy(alpha = trailAlpha * 0.72f),
                    radius = 6.dp.toPx(),
                    center = Offset(
                        x = lerp(start.x, end.x, convergenceProgress),
                        y = lerp(start.y, end.y, convergenceProgress),
                    ),
                )
            }

            if (badgeAlpha > 0f) {
                val ringRadius = 48.dp.toPx()
                val burstAlpha = (resolutionProgress * (1f - dissolveProgress)).coerceIn(0f, 1f)
                for (index in 0 until 18) {
                    val angle = (index * 20f) * (Math.PI / 180f).toFloat()
                    val distance = ringRadius * (1.15f + resolutionProgress * 0.9f)
                    drawCircle(
                        color = white.copy(alpha = burstAlpha * 0.9f),
                        radius = if (index % 3 == 0) 4.dp.toPx() else 2.8.dp.toPx(),
                        center = Offset(
                            x = center.x + cos(angle) * distance,
                            y = center.y + sin(angle) * distance,
                        ),
                    )
                }
                drawCircle(
                    color = accent.copy(alpha = badgeAlpha * 0.18f),
                    radius = ringRadius * (1.55f - dissolveProgress * 0.12f),
                    center = center,
                )
                drawCircle(
                    color = accent.copy(alpha = badgeAlpha * 0.95f),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 3.5.dp.toPx()),
                )
                drawCircle(
                    color = accent.copy(alpha = badgeAlpha * 0.88f),
                    radius = ringRadius * 0.78f,
                    center = center,
                )
                drawCircle(
                    color = white.copy(alpha = badgeAlpha * 0.95f),
                    radius = ringRadius * 0.48f,
                    center = center,
                )
            }
        }

        TripleActionVectorIcon(
            image = Icons.Outlined.ThumbUp,
            tint = accent,
            baseX = with(density) { (-102).dp.toPx() },
            baseY = with(density) { 72.dp.toPx() },
            activationProgress = iconActivationProgress(activationProgress, 0),
            convergenceProgress = convergenceProgress,
            dissolveProgress = dissolveProgress,
        )
        TripleActionCoinIcon(
            tint = accent,
            baseX = 0f,
            baseY = with(density) { (-90).dp.toPx() },
            activationProgress = iconActivationProgress(activationProgress, 1),
            convergenceProgress = convergenceProgress,
            dissolveProgress = dissolveProgress,
        )
        TripleActionVectorIcon(
            image = Icons.Outlined.Star,
            tint = accent,
            baseX = with(density) { 102.dp.toPx() },
            baseY = with(density) { 72.dp.toPx() },
            activationProgress = iconActivationProgress(activationProgress, 2),
            convergenceProgress = convergenceProgress,
            dissolveProgress = dissolveProgress,
        )

        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = badgeScale
                scaleY = badgeScale
                alpha = badgeAlpha
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ThumbUp,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(30.dp),
            )
        }

        Text(
            text = "三连完成",
            color = white.copy(alpha = badgeAlpha),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-28).dp)
                .graphicsLayer {
                    alpha = badgeAlpha
                    translationY = (1f - resolutionProgress).coerceIn(0f, 1f) * 18f
                },
        )
        Text(
            text = "点赞  投币  收藏",
            color = white.copy(alpha = badgeAlpha * 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 8.dp)
                .graphicsLayer {
                    alpha = badgeAlpha
                    translationY = (1f - resolutionProgress).coerceIn(0f, 1f) * 12f
                },
        )
    }
}

@Composable
internal fun DetailActionFeedbackHost(
    message: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.96f),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.66f))
                .widthIn(min = 160.dp, max = 360.dp)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message.orEmpty(),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BoxScope.TripleActionVectorIcon(
    image: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    baseX: Float,
    baseY: Float,
    activationProgress: Float,
    convergenceProgress: Float,
    dissolveProgress: Float,
) {
    val alpha = (activationProgress * (1f - dissolveProgress * 0.85f)).coerceIn(0f, 1f)
    val scale = 0.78f + activationProgress * 0.24f - convergenceProgress * 0.06f
    Icon(
        imageVector = image,
        contentDescription = null,
        tint = tint.copy(alpha = alpha),
        modifier = Modifier
            .align(Alignment.Center)
            .offset {
                IntOffset(
                    x = lerp(baseX, 0f, convergenceProgress).roundToInt(),
                    y = lerp(baseY, 0f, convergenceProgress).roundToInt(),
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .size(34.dp),
    )
}

@Composable
private fun BoxScope.TripleActionCoinIcon(
    tint: Color,
    baseX: Float,
    baseY: Float,
    activationProgress: Float,
    convergenceProgress: Float,
    dissolveProgress: Float,
) {
    val alpha = (activationProgress * (1f - dissolveProgress * 0.85f)).coerceIn(0f, 1f)
    val scale = 0.78f + activationProgress * 0.24f - convergenceProgress * 0.06f
    CoinMetricIcon(
        tint = tint.copy(alpha = alpha),
        modifier = Modifier
            .align(Alignment.Center)
            .offset {
                IntOffset(
                    x = lerp(baseX, 0f, convergenceProgress).roundToInt(),
                    y = lerp(baseY, 0f, convergenceProgress).roundToInt(),
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .size(34.dp),
    )
}

private fun phaseProgress(progress: Float, start: Float, end: Float): Float {
    if (end <= start) return 1f
    return ((progress - start) / (end - start)).coerceIn(0f, 1f)
}

private fun iconActivationProgress(activationProgress: Float, index: Int): Float {
    val start = index * 0.22f
    return phaseProgress(activationProgress, start, start + 0.56f)
}

private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}
