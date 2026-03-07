package com.android.purebilibili.feature.video.ui.overlay

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPlayerOverlayCastPolicyTest {

    @Test
    fun releasesCastBindingWhenDialogCloses() {
        assertTrue(
            shouldReleaseCastBindingAfterDialogVisibilityChange(
                previousVisible = true,
                currentVisible = false
            )
        )
    }

    @Test
    fun keepsCastBindingForAllOtherVisibilityTransitions() {
        assertFalse(
            shouldReleaseCastBindingAfterDialogVisibilityChange(
                previousVisible = false,
                currentVisible = false
            )
        )
        assertFalse(
            shouldReleaseCastBindingAfterDialogVisibilityChange(
                previousVisible = false,
                currentVisible = true
            )
        )
        assertFalse(
            shouldReleaseCastBindingAfterDialogVisibilityChange(
                previousVisible = true,
                currentVisible = true
            )
        )
    }
}
