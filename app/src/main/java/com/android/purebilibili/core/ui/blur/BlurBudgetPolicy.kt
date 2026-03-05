package com.android.purebilibili.core.ui.blur

import com.android.purebilibili.core.ui.adaptive.MotionTier

enum class BlurSurfaceType {
    HEADER,
    BOTTOM_BAR,
    DRAWER_OR_SHEET,
    OVERLAY,
    GENERIC
}

data class BlurBudget(
    val maxBlurLevel: Int,
    val backgroundAlphaMultiplier: Float,
    val allowRealtime: Boolean
)

private val blurLevelOrder = listOf(
    BlurIntensity.THIN,
    BlurIntensity.APPLE_DOCK,
    BlurIntensity.THICK
)

internal fun resolveBlurBudget(
    surfaceType: BlurSurfaceType,
    motionTier: MotionTier,
    isScrolling: Boolean,
    isTransitionRunning: Boolean,
    forceLowBudget: Boolean = false
): BlurBudget {
    var maxBlurLevel = when (surfaceType) {
        BlurSurfaceType.HEADER -> 2
        BlurSurfaceType.DRAWER_OR_SHEET -> 2
        BlurSurfaceType.BOTTOM_BAR -> 1
        BlurSurfaceType.OVERLAY -> 1
        BlurSurfaceType.GENERIC -> 1
    }
    var backgroundAlphaMultiplier = when (surfaceType) {
        BlurSurfaceType.HEADER -> 1.0f
        BlurSurfaceType.DRAWER_OR_SHEET -> 1.0f
        BlurSurfaceType.BOTTOM_BAR -> 0.95f
        BlurSurfaceType.OVERLAY -> 0.92f
        BlurSurfaceType.GENERIC -> 0.95f
    }
    var allowRealtime = true

    when (motionTier) {
        MotionTier.Reduced -> {
            maxBlurLevel = 0
            backgroundAlphaMultiplier *= 0.9f
            allowRealtime = false
        }

        MotionTier.Normal -> Unit
        MotionTier.Enhanced -> Unit
    }

    if (isScrolling) {
        if (surfaceType != BlurSurfaceType.HEADER) {
            maxBlurLevel = minOf(maxBlurLevel, 0)
            backgroundAlphaMultiplier *= 0.92f
        }
        allowRealtime = false
    }

    if (isTransitionRunning) {
        if (surfaceType != BlurSurfaceType.HEADER) {
            maxBlurLevel = minOf(maxBlurLevel, 0)
            backgroundAlphaMultiplier *= 0.92f
        }
        allowRealtime = false
    }

    if (forceLowBudget) {
        maxBlurLevel = 0
        backgroundAlphaMultiplier *= 0.9f
        allowRealtime = false
    }

    return BlurBudget(
        maxBlurLevel = maxBlurLevel.coerceIn(0, 2),
        backgroundAlphaMultiplier = backgroundAlphaMultiplier.coerceIn(0.70f, 1.10f),
        allowRealtime = allowRealtime
    )
}

internal fun resolveBudgetedBlurIntensity(
    preferred: BlurIntensity,
    budget: BlurBudget
): BlurIntensity {
    val preferredLevel = blurLevelOrder.indexOf(preferred).coerceAtLeast(0)
    val cappedLevel = minOf(preferredLevel, budget.maxBlurLevel)
    return blurLevelOrder[cappedLevel]
}
