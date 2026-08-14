package com.bbttvv.app.data.repository

import com.bbttvv.app.data.model.CommentFraudStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentFraudDetectionPolicyTest {
    @Test
    fun rootStatusDistinguishesReviewAndShadowBan() {
        val guestMissing = CommentPresenceProbe(requestSucceeded = true, found = false)
        val authVisible = CommentPresenceProbe(requestSucceeded = true, found = true)

        assertEquals(
            CommentFraudStatus.UNDER_REVIEW,
            resolveRootFraudStatus(
                guestSeekProbe = guestMissing,
                authSeekProbe = authVisible,
                guestReplyPageVisible = true,
                confirmedNotFoundAfterRetry = false
            )
        )
        assertEquals(
            CommentFraudStatus.SHADOW_BANNED,
            resolveRootFraudStatus(
                guestSeekProbe = guestMissing,
                authSeekProbe = authVisible,
                guestReplyPageVisible = false,
                confirmedNotFoundAfterRetry = false
            )
        )
    }

    @Test
    fun rootStatusDeletesOnlyAfterRetryConfirmation() {
        val missing = CommentPresenceProbe(requestSucceeded = true, found = false)

        assertEquals(
            CommentFraudStatus.UNKNOWN,
            resolveRootFraudStatus(missing, missing, null, confirmedNotFoundAfterRetry = false)
        )
        assertEquals(
            CommentFraudStatus.DELETED,
            resolveRootFraudStatus(missing, missing, null, confirmedNotFoundAfterRetry = true)
        )
    }
}
