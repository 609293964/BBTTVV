package com.bbttvv.app.data.repository

import com.bbttvv.app.data.model.CommentFraudStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentFraudDetectionPolicyTest {
    @Test
    fun startPolicyRequiresEnabledPositiveRpid() {
        assertEquals(true, shouldStartCommentFraudDetection(enabled = true, rpid = 123L))
        assertEquals(false, shouldStartCommentFraudDetection(enabled = false, rpid = 123L))
        assertEquals(false, shouldStartCommentFraudDetection(enabled = true, rpid = 0L))
    }

    @Test
    fun normalResultUsesLightMessageOnly() {
        assertEquals(false, shouldShowCommentFraudResultDialog(CommentFraudStatus.NORMAL))
        assertEquals("评论已正常显示", resolveCommentFraudLightMessage(CommentFraudStatus.NORMAL))
        assertEquals(true, shouldShowCommentFraudResultDialog(CommentFraudStatus.SHADOW_BANNED))
        assertEquals(null, resolveCommentFraudLightMessage(CommentFraudStatus.SHADOW_BANNED))
    }

    @Test
    fun rootTimelineStatusCanDetectShadowBanAndReview() {
        assertEquals(
            CommentFraudStatus.SHADOW_BANNED,
            resolveRootFraudStatusFromTimeline(
                guestTimelineProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
                authReplyPageProbe = CommentReplyPageProbe(requestSucceeded = true, visible = true),
                guestReplyPageProbe = CommentReplyPageProbe(requestSucceeded = true, visible = false, deletedHint = true),
                confirmedDeletedAfterRetry = false
            )
        )
        assertEquals(
            CommentFraudStatus.UNDER_REVIEW,
            resolveRootFraudStatusFromTimeline(
                guestTimelineProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
                authReplyPageProbe = CommentReplyPageProbe(requestSucceeded = true, visible = true),
                guestReplyPageProbe = CommentReplyPageProbe(requestSucceeded = true, visible = true),
                confirmedDeletedAfterRetry = false
            )
        )
    }

    @Test
    fun rootTimelineStatusDeletesOnlyAfterRetryConfirmation() {
        assertEquals(
            CommentFraudStatus.UNKNOWN,
            resolveRootFraudStatusFromTimeline(
                guestTimelineProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
                authReplyPageProbe = CommentReplyPageProbe(requestSucceeded = true, visible = false, deletedHint = true),
                guestReplyPageProbe = null,
                confirmedDeletedAfterRetry = false
            )
        )
        assertEquals(
            CommentFraudStatus.DELETED,
            resolveRootFraudStatusFromTimeline(
                guestTimelineProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
                authReplyPageProbe = CommentReplyPageProbe(requestSucceeded = true, visible = false, deletedHint = true),
                guestReplyPageProbe = null,
                confirmedDeletedAfterRetry = true
            )
        )
    }
}
