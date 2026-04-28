package com.bbttvv.app.core.util

enum class ClosestTargetFallback {
    NEAREST_HIGHER,
    HIGHEST_AVAILABLE,
    LOWEST_AVAILABLE
}

fun List<Int>.findClosestTarget(
    target: Int,
    fallback: ClosestTargetFallback = ClosestTargetFallback.NEAREST_HIGHER
): Int? {
    if (isEmpty()) return null

    val lowerOrEqual = filter { it <= target }
    if (lowerOrEqual.isNotEmpty()) {
        return lowerOrEqual.maxOrNull()
    }

    return when (fallback) {
        ClosestTargetFallback.NEAREST_HIGHER -> filter { it > target }.minOrNull() ?: maxOrNull()
        ClosestTargetFallback.HIGHEST_AVAILABLE -> maxOrNull()
        ClosestTargetFallback.LOWEST_AVAILABLE -> minOrNull()
    }
}

