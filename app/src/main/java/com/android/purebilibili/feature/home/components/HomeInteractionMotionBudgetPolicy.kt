package com.android.purebilibili.feature.home.components

import kotlin.math.abs

internal const val HOME_HEADER_SECONDARY_BLUR_RESTORE_DELAY_MS = 120L

enum class HomeInteractionMotionBudget {
    FULL,
    REDUCED
}

internal fun resolveHomeInteractionMotionBudget(
    isPagerScrolling: Boolean,
    isProgrammaticPageSwitchInProgress: Boolean,
    isFeedScrolling: Boolean
): HomeInteractionMotionBudget {
    return if (isPagerScrolling || isProgrammaticPageSwitchInProgress || isFeedScrolling) {
        HomeInteractionMotionBudget.REDUCED
    } else {
        HomeInteractionMotionBudget.FULL
    }
}

internal fun shouldAnimateTopTabAutoScroll(
    selectedIndex: Int,
    firstVisibleIndex: Int,
    lastVisibleIndex: Int,
    budget: HomeInteractionMotionBudget
): Boolean {
    if (firstVisibleIndex > lastVisibleIndex) return true
    val isTargetOutsideViewport = selectedIndex < firstVisibleIndex || selectedIndex > lastVisibleIndex
    if (budget == HomeInteractionMotionBudget.REDUCED) {
        return isTargetOutsideViewport
    }
    return isTargetOutsideViewport
}

internal fun shouldSnapHomeTopTabSelection(
    currentPage: Int,
    targetPage: Int
): Boolean = currentPage != targetPage

internal fun resolveTopTabViewportAnchorIndex(
    selectedIndex: Int,
    pagerCurrentPage: Int?,
    pagerTargetPage: Int?,
    pagerIsScrolling: Boolean
): Int {
    if (!pagerIsScrolling) return pagerCurrentPage ?: selectedIndex
    return pagerTargetPage ?: pagerCurrentPage ?: selectedIndex
}

internal fun resolveTopTabPagerPosition(
    selectedIndex: Int,
    pagerCurrentPage: Int?,
    pagerTargetPage: Int?,
    pagerCurrentPageOffsetFraction: Float?,
    pagerIsScrolling: Boolean
): Float {
    if (!pagerIsScrolling) return (pagerCurrentPage ?: selectedIndex).toFloat()
    val currentPage = pagerCurrentPage ?: return selectedIndex.toFloat()
    val offsetFraction = pagerCurrentPageOffsetFraction ?: 0f
    val targetPage = pagerTargetPage
    if (targetPage != null && targetPage != currentPage) {
        val direction = if (targetPage > currentPage) 1f else -1f
        return currentPage + abs(offsetFraction).coerceIn(0f, 1f) * direction
    }
    return currentPage + offsetFraction
}
