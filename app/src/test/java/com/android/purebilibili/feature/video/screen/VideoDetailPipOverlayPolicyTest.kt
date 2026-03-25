package com.android.purebilibili.feature.video.screen

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoDetailPipOverlayPolicyTest {

    @Test
    fun `entering pip dismisses visible comment thread detail`() {
        assertTrue(
            shouldDismissCommentThreadDetailForPip(
                wasInPipMode = false,
                isInPipMode = true,
                subReplyVisible = true
            )
        )
    }

    @Test
    fun `entering pip does not dismiss when thread detail is already hidden`() {
        assertFalse(
            shouldDismissCommentThreadDetailForPip(
                wasInPipMode = false,
                isInPipMode = true,
                subReplyVisible = false
            )
        )
    }

    @Test
    fun `staying outside pip does not dismiss thread detail`() {
        assertFalse(
            shouldDismissCommentThreadDetailForPip(
                wasInPipMode = false,
                isInPipMode = false,
                subReplyVisible = true
            )
        )
    }

    @Test
    fun `staying in pip does not repeatedly dismiss thread detail`() {
        assertFalse(
            shouldDismissCommentThreadDetailForPip(
                wasInPipMode = true,
                isInPipMode = true,
                subReplyVisible = true
            )
        )
    }
}
