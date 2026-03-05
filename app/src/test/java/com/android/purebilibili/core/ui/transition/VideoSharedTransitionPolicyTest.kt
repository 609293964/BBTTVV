package com.android.purebilibili.core.ui.transition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoSharedTransitionPolicyTest {

    @Test
    fun coverSharedTransition_enabled_whenTransitionAndScopesAreReady() {
        assertTrue(
            shouldEnableVideoCoverSharedTransition(
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
        assertFalse(
            shouldEnableVideoCoverSharedTransition(
                transitionEnabled = true,
                hasSharedTransitionScope = false,
                hasAnimatedVisibilityScope = true
            )
        )
    }

    @Test
    fun metadataSharedTransition_enabled_byDefault_forUnifiedVideoReturn() {
        assertEquals(VideoSharedTransitionProfile.COVER_AND_METADATA, resolveVideoSharedTransitionProfile())
        assertTrue(
            shouldEnableVideoMetadataSharedTransition(
                coverSharedEnabled = true,
                isQuickReturnLimited = false
            )
        )
    }

    @Test
    fun metadataSharedTransition_staysEnabled_evenWhenQuickReturnLimited() {
        assertTrue(
            shouldEnableVideoMetadataSharedTransition(
                coverSharedEnabled = true,
                isQuickReturnLimited = true
            )
        )
    }

    @Test
    fun sharedCoverAspectRatio_defaultsToHomeCardSixteenByTen() {
        assertEquals(1.6f, VIDEO_SHARED_COVER_ASPECT_RATIO, 0.0001f)
    }
}
