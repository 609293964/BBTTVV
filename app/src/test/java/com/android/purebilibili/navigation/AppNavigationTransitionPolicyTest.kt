package com.android.purebilibili.navigation

import com.android.purebilibili.core.ui.transition.VideoSharedTransitionProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigationTransitionPolicyTest {

    @Test
    fun tabletBackToHomeFromVideo_usesSeamlessTransition() {
        assertTrue(
            shouldUseTabletSeamlessBackTransition(
                isTabletLayout = true,
                cardTransitionEnabled = true,
                fromRoute = VideoRoute.route,
                toRoute = ScreenRoutes.Home.route
            )
        )
    }

    @Test
    fun tabletBackToHistoryFromVideo_usesSeamlessTransition() {
        assertTrue(
            shouldUseTabletSeamlessBackTransition(
                isTabletLayout = true,
                cardTransitionEnabled = true,
                fromRoute = VideoRoute.route,
                toRoute = ScreenRoutes.History.route
            )
        )
    }

    @Test
    fun phoneBackToHomeFromVideo_keepsDefaultTransition() {
        assertFalse(
            shouldUseTabletSeamlessBackTransition(
                isTabletLayout = false,
                cardTransitionEnabled = true,
                fromRoute = VideoRoute.route,
                toRoute = ScreenRoutes.Home.route
            )
        )
    }

    @Test
    fun tabletBackToHomeWithoutCardTransition_keepsDefaultTransition() {
        assertFalse(
            shouldUseTabletSeamlessBackTransition(
                isTabletLayout = true,
                cardTransitionEnabled = false,
                fromRoute = VideoRoute.route,
                toRoute = ScreenRoutes.Home.route
            )
        )
    }

    @Test
    fun tabletBackToHomeFromNonVideoRoute_keepsDefaultTransition() {
        assertFalse(
            shouldUseTabletSeamlessBackTransition(
                isTabletLayout = true,
                cardTransitionEnabled = true,
                fromRoute = ScreenRoutes.Search.route,
                toRoute = ScreenRoutes.Home.route
            )
        )
    }

    @Test
    fun leavingVideoToHome_shouldStopPlaybackEagerly() {
        assertTrue(
            shouldStopPlaybackEagerlyOnVideoRouteExit(
                fromRoute = VideoRoute.route,
                toRoute = ScreenRoutes.Home.route
            )
        )
    }

    @Test
    fun leavingVideoToAudioMode_shouldNotStopPlaybackEagerly() {
        assertFalse(
            shouldStopPlaybackEagerlyOnVideoRouteExit(
                fromRoute = VideoRoute.route,
                toRoute = ScreenRoutes.AudioMode.route
            )
        )
    }

    @Test
    fun switchingBetweenVideoRoutes_shouldNotStopPlaybackEagerly() {
        assertFalse(
            shouldStopPlaybackEagerlyOnVideoRouteExit(
                fromRoute = VideoRoute.route,
                toRoute = VideoRoute.route
            )
        )
    }

    @Test
    fun nonSharedReturnToHome_leftCard_slidesRightToLeft() {
        assertEquals(
            VideoPopExitDirection.LEFT,
            resolveVideoPopExitDirection(
                targetRoute = ScreenRoutes.Home.route,
                isSingleColumnCard = false,
                lastClickedCardCenterX = 0.2f
            )
        )
    }

    @Test
    fun nonSharedReturnToHome_rightCard_slidesLeftToRight() {
        assertEquals(
            VideoPopExitDirection.RIGHT,
            resolveVideoPopExitDirection(
                targetRoute = ScreenRoutes.Home.route,
                isSingleColumnCard = false,
                lastClickedCardCenterX = 0.8f
            )
        )
    }

    @Test
    fun nonSharedReturnToNonCardRoute_singleColumn_slidesDown() {
        assertEquals(
            VideoPopExitDirection.DOWN,
            resolveVideoPopExitDirection(
                targetRoute = ScreenRoutes.Settings.route,
                isSingleColumnCard = true,
                lastClickedCardCenterX = 0.2f
            )
        )
    }

    @Test
    fun nonSharedReturnToCardList_singleColumn_keepsHorizontalDirectionLikeHome() {
        assertEquals(
            VideoPopExitDirection.LEFT,
            resolveVideoPopExitDirection(
                targetRoute = ScreenRoutes.History.route,
                isSingleColumnCard = true,
                lastClickedCardCenterX = 0.2f
            )
        )
    }

    @Test
    fun cardReturnTargetPolicy_matchesExpectedRoutes() {
        assertTrue(isVideoCardReturnTargetRoute(ScreenRoutes.Home.route))
        assertTrue(isVideoCardReturnTargetRoute(ScreenRoutes.History.route))
        assertTrue(isVideoCardReturnTargetRoute(ScreenRoutes.Favorite.route))
        assertTrue(isVideoCardReturnTargetRoute(ScreenRoutes.WatchLater.route))
        assertTrue(isVideoCardReturnTargetRoute(ScreenRoutes.Search.route))
        assertTrue(isVideoCardReturnTargetRoute(ScreenRoutes.Dynamic.route))
        assertTrue(isVideoCardReturnTargetRoute(ScreenRoutes.Partition.route))
        assertTrue(isVideoCardReturnTargetRoute(ScreenRoutes.Space.route))
        assertTrue(isVideoCardReturnTargetRoute(ScreenRoutes.Category.route))
        assertFalse(isVideoCardReturnTargetRoute(ScreenRoutes.Settings.route))
    }

    @Test
    fun videoToVideoRouteTransition_usesNoOpWhenCardTransitionEnabled() {
        assertTrue(
            shouldUseNoOpRouteTransitionBetweenVideoDetails(
                cardTransitionEnabled = true,
                fromRoute = VideoRoute.route,
                toRoute = VideoRoute.route
            )
        )
    }

    @Test
    fun videoToVideoRouteTransition_disabledWhenCardTransitionDisabledOrRouteMismatch() {
        assertFalse(
            shouldUseNoOpRouteTransitionBetweenVideoDetails(
                cardTransitionEnabled = false,
                fromRoute = VideoRoute.route,
                toRoute = VideoRoute.route
            )
        )
        assertFalse(
            shouldUseNoOpRouteTransitionBetweenVideoDetails(
                cardTransitionEnabled = true,
                fromRoute = VideoRoute.route,
                toRoute = ScreenRoutes.Home.route
            )
        )
    }

    @Test
    fun quickReturn_nonHomeCardRoute_canUseNoOpToAvoidRouteLayerInterference() {
        assertTrue(
            shouldUseNoOpQuickReturnForNonHomeCardRoute(
                targetRoute = ScreenRoutes.History.route,
                cardTransitionEnabled = true,
                isQuickReturnFromDetail = true,
                sharedTransitionReady = true,
                profile = VideoSharedTransitionProfile.COVER_ONLY
            )
        )
    }

    @Test
    fun quickReturn_homeRoute_doesNotForceNoOpViaNonHomePolicy() {
        assertFalse(
            shouldUseNoOpQuickReturnForNonHomeCardRoute(
                targetRoute = ScreenRoutes.Home.route,
                cardTransitionEnabled = true,
                isQuickReturnFromDetail = true,
                sharedTransitionReady = true,
                profile = VideoSharedTransitionProfile.COVER_ONLY
            )
        )
    }

    @Test
    fun quickReturn_coverOnlyProfile_usesNoOpWhenSharedTransitionReady() {
        assertTrue(
            shouldUseNoOpRouteTransitionOnQuickReturn(
                cardTransitionEnabled = true,
                isQuickReturnFromDetail = true,
                sharedTransitionReady = true,
                profile = VideoSharedTransitionProfile.COVER_ONLY
            )
        )
    }

    @Test
    fun quickReturn_coverOnlyProfile_usesFallbackWhenSharedTransitionNotReady() {
        assertFalse(
            shouldUseNoOpRouteTransitionOnQuickReturn(
                cardTransitionEnabled = true,
                isQuickReturnFromDetail = true,
                sharedTransitionReady = false,
                profile = VideoSharedTransitionProfile.COVER_ONLY
            )
        )
    }

    @Test
    fun quickReturn_coverAndMetadataProfile_allowsNoOpRouteTransition() {
        assertTrue(
            shouldUseNoOpRouteTransitionOnQuickReturn(
                cardTransitionEnabled = true,
                isQuickReturnFromDetail = true,
                sharedTransitionReady = false,
                profile = VideoSharedTransitionProfile.COVER_AND_METADATA
            )
        )
    }

    @Test
    fun predictiveBack_enabled_withCardTransitionAndSharedReady_disablesOneTakeReturnForStability() {
        assertFalse(
            shouldPreferOneTakeVideoToHomeReturn(
                predictiveBackAnimationEnabled = true,
                cardTransitionEnabled = true,
                sharedTransitionReady = true
            )
        )
    }

    @Test
    fun predictiveBack_disabled_neverPrefersOneTakeReturn() {
        assertFalse(
            shouldPreferOneTakeVideoToHomeReturn(
                predictiveBackAnimationEnabled = false,
                cardTransitionEnabled = true,
                sharedTransitionReady = true
            )
        )
    }

    @Test
    fun predictiveBack_disabled_withCardTransition_usesClassicBackRouteMotion() {
        assertTrue(
            shouldUseClassicBackRouteMotion(
                predictiveBackAnimationEnabled = false,
                cardTransitionEnabled = true
            )
        )
        assertFalse(
            shouldUseClassicBackRouteMotion(
                predictiveBackAnimationEnabled = true,
                cardTransitionEnabled = true
            )
        )
    }

    @Test
    fun predictiveBack_enabled_withCardTransition_usesPredictiveStableBackRouteMotion() {
        assertTrue(
            shouldUsePredictiveStableBackRouteMotion(
                predictiveBackAnimationEnabled = true,
                cardTransitionEnabled = true
            )
        )
    }

    @Test
    fun predictiveBack_disabled_orCardTransitionDisabled_doesNotUsePredictiveStableBackRouteMotion() {
        assertFalse(
            shouldUsePredictiveStableBackRouteMotion(
                predictiveBackAnimationEnabled = false,
                cardTransitionEnabled = true
            )
        )
        assertFalse(
            shouldUsePredictiveStableBackRouteMotion(
                predictiveBackAnimationEnabled = true,
                cardTransitionEnabled = false
            )
        )
    }

    @Test
    fun predictiveBack_enabled_withCardTransition_usesLinkedSettingsBackMotion() {
        assertTrue(
            shouldUseLinkedSettingsBackMotion(
                predictiveBackAnimationEnabled = true,
                cardTransitionEnabled = true
            )
        )
    }

    @Test
    fun predictiveBack_disabled_forSettingsBackMotion_fallsBackToClassic() {
        assertFalse(
            shouldUseLinkedSettingsBackMotion(
                predictiveBackAnimationEnabled = false,
                cardTransitionEnabled = true
            )
        )
    }

    @Test
    fun cardTransition_disabled_forSettingsBackMotion_staysClassic() {
        assertFalse(
            shouldUseLinkedSettingsBackMotion(
                predictiveBackAnimationEnabled = true,
                cardTransitionEnabled = false
            )
        )
    }

    @Test
    fun returningFromDetailToHome_shouldNotDeferBottomBarReveal() {
        assertFalse(
            shouldDeferBottomBarRevealOnVideoReturn(
                isReturningFromDetail = true,
                currentRoute = ScreenRoutes.Home.route
            )
        )
    }

    @Test
    fun notReturningFromDetail_shouldNotDeferBottomBarReveal() {
        assertFalse(
            shouldDeferBottomBarRevealOnVideoReturn(
                isReturningFromDetail = false,
                currentRoute = ScreenRoutes.Home.route
            )
        )
    }

    @Test
    fun returningFromDetailOnNonHomeRoute_shouldNotDeferBottomBarReveal() {
        assertFalse(
            shouldDeferBottomBarRevealOnVideoReturn(
                isReturningFromDetail = true,
                currentRoute = ScreenRoutes.History.route
            )
        )
    }
}
