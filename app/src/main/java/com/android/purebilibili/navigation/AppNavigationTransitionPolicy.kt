package com.android.purebilibili.navigation

import com.android.purebilibili.core.ui.transition.VideoSharedTransitionProfile
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionProfile

internal enum class VideoPopExitDirection {
    LEFT,
    RIGHT,
    DOWN
}

internal fun shouldUseNoOpRouteTransitionOnQuickReturn(
    cardTransitionEnabled: Boolean,
    isQuickReturnFromDetail: Boolean,
    sharedTransitionReady: Boolean,
    profile: VideoSharedTransitionProfile = resolveVideoSharedTransitionProfile()
): Boolean {
    if (!cardTransitionEnabled || !isQuickReturnFromDetail) return false
    return when (profile) {
        VideoSharedTransitionProfile.COVER_ONLY -> sharedTransitionReady
        VideoSharedTransitionProfile.COVER_AND_METADATA -> true
    }
}

internal fun shouldUseNoOpRouteTransitionBetweenVideoDetails(
    cardTransitionEnabled: Boolean,
    fromRoute: String?,
    toRoute: String?
): Boolean {
    return cardTransitionEnabled &&
        isVideoDetailRoute(fromRoute) &&
        isVideoDetailRoute(toRoute)
}

internal fun shouldUseNoOpQuickReturnForNonHomeCardRoute(
    targetRoute: String?,
    cardTransitionEnabled: Boolean,
    isQuickReturnFromDetail: Boolean,
    sharedTransitionReady: Boolean,
    profile: VideoSharedTransitionProfile = resolveVideoSharedTransitionProfile()
): Boolean {
    if (targetRoute == ScreenRoutes.Home.route) return false
    if (!isVideoCardReturnTargetRoute(targetRoute)) return false
    return shouldUseNoOpRouteTransitionOnQuickReturn(
        cardTransitionEnabled = cardTransitionEnabled,
        isQuickReturnFromDetail = isQuickReturnFromDetail,
        sharedTransitionReady = sharedTransitionReady,
        profile = profile
    )
}

internal fun shouldPreferOneTakeVideoToHomeReturn(
    predictiveBackAnimationEnabled: Boolean,
    cardTransitionEnabled: Boolean,
    sharedTransitionReady: Boolean
): Boolean {
    if (!predictiveBackAnimationEnabled) return false
    if (!cardTransitionEnabled) return false
    if (!sharedTransitionReady) return false
    // Phase 1 stability fallback:
    // predictive back enabled 时先禁用视频<->首页的一镜到底 route no-op，
    // 避免 Surface/overlay 链路抖动导致黑屏与长时间滞留。
    return false
}

internal fun shouldUseClassicBackRouteMotion(
    predictiveBackAnimationEnabled: Boolean,
    cardTransitionEnabled: Boolean
): Boolean {
    return !predictiveBackAnimationEnabled && cardTransitionEnabled
}

internal fun shouldUsePredictiveStableBackRouteMotion(
    predictiveBackAnimationEnabled: Boolean,
    cardTransitionEnabled: Boolean
): Boolean {
    return predictiveBackAnimationEnabled && cardTransitionEnabled
}

internal fun shouldUseLinkedSettingsBackMotion(
    predictiveBackAnimationEnabled: Boolean,
    cardTransitionEnabled: Boolean
): Boolean {
    return predictiveBackAnimationEnabled && cardTransitionEnabled
}

internal fun shouldDeferBottomBarRevealOnVideoReturn(
    isReturningFromDetail: Boolean,
    currentRoute: String?
): Boolean {
    if (!isReturningFromDetail || currentRoute != ScreenRoutes.Home.route) return false
    // 返回首页时不再延迟底栏显示，避免“先隐藏再出现”造成的闪烁感。
    return false
}

internal fun shouldUseTabletSeamlessBackTransition(
    isTabletLayout: Boolean,
    cardTransitionEnabled: Boolean,
    fromRoute: String?,
    toRoute: String?
): Boolean {
    return isTabletLayout &&
        cardTransitionEnabled &&
        isVideoDetailRoute(fromRoute) &&
        isVideoCardReturnTargetRoute(toRoute)
}

internal fun shouldStopPlaybackEagerlyOnVideoRouteExit(
    fromRoute: String?,
    toRoute: String?
): Boolean {
    return isVideoDetailRoute(fromRoute) &&
        !isVideoDetailRoute(toRoute) &&
        toRoute != ScreenRoutes.AudioMode.route
}

internal fun resolveVideoPopExitDirection(
    targetRoute: String?,
    isSingleColumnCard: Boolean,
    lastClickedCardCenterX: Float?
): VideoPopExitDirection {
    val isCardOnLeft = (lastClickedCardCenterX ?: 0.5f) < 0.5f
    if (isVideoCardReturnTargetRoute(targetRoute)) {
        return if (isCardOnLeft) VideoPopExitDirection.LEFT else VideoPopExitDirection.RIGHT
    }
    if (isSingleColumnCard) return VideoPopExitDirection.DOWN
    return if (isCardOnLeft) VideoPopExitDirection.LEFT else VideoPopExitDirection.RIGHT
}

internal fun isVideoCardReturnTargetRoute(route: String?): Boolean {
    val routeBase = route?.substringBefore("?") ?: return false
    return routeBase == ScreenRoutes.Home.route ||
        routeBase == ScreenRoutes.History.route ||
        routeBase == ScreenRoutes.Favorite.route ||
        routeBase == ScreenRoutes.WatchLater.route ||
        routeBase == ScreenRoutes.Search.route ||
        routeBase == ScreenRoutes.Dynamic.route ||
        routeBase == ScreenRoutes.Partition.route ||
        routeBase.startsWith("category/") ||
        routeBase.startsWith("space/")
}

private fun isVideoDetailRoute(route: String?): Boolean {
    return route?.startsWith("${VideoRoute.base}/") == true
}
