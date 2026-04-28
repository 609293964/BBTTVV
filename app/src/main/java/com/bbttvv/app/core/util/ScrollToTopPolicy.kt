package com.bbttvv.app.core.util

data class ScrollToTopPlan(
    val preJumpIndex: Int?,
    val animateTargetIndex: Int = 0
)

/**
 * 长列表回顶策略：
 * - 近距离直接平滑到顶部
 * - 远距离先瞬移到较近位置，再平滑到顶部，减少一次性测量造成的卡顿
 */
fun resolveScrollToTopPlan(firstVisibleItemIndex: Int): ScrollToTopPlan {
    val index = firstVisibleItemIndex.coerceAtLeast(0)
    val preJump = when {
        index > 180 -> 28
        index > 96 -> 20
        index > 36 -> 12
        index > 14 -> 6
        else -> null
    }
    return ScrollToTopPlan(preJumpIndex = preJump)
}

