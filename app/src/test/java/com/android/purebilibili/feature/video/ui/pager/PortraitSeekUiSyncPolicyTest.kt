package com.android.purebilibili.feature.video.ui.pager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortraitSeekUiSyncPolicyTest {

    @Test
    fun committedSeekPosition_clampsToKnownDuration() {
        assertEquals(
            120_000L,
            resolvePortraitCommittedSeekPosition(
                requestedPositionMs = 150_000L,
                durationMs = 120_000L
            )
        )
        assertEquals(
            0L,
            resolvePortraitCommittedSeekPosition(
                requestedPositionMs = -8_000L,
                durationMs = 120_000L
            )
        )
    }

    @Test
    fun committedSeekPosition_keepsPositiveRequestWhenDurationUnknown() {
        assertEquals(
            42_000L,
            resolvePortraitCommittedSeekPosition(
                requestedPositionMs = 42_000L,
                durationMs = 0L
            )
        )
    }

    @Test
    fun localSeekPosition_winsWhileSeekCommitIsStillAwaitingPlayerCatchUp() {
        assertEquals(
            25_000L,
            resolvePortraitDisplayedProgressPosition(
                playerPositionMs = 1_000L,
                localSeekPositionMs = 25_000L,
                pendingSeekPositionMs = 25_000L
            )
        )
    }

    @Test
    fun playerPosition_drivesUiWhenThereIsNoPendingSeek() {
        assertEquals(
            3_000L,
            resolvePortraitDisplayedProgressPosition(
                playerPositionMs = 3_000L,
                localSeekPositionMs = 25_000L,
                pendingSeekPositionMs = null
            )
        )
    }

    @Test
    fun pendingSeek_clearsWhenPlayerHasCaughtUpToCommittedTarget() {
        assertFalse(
            shouldHoldPortraitSeekUiPosition(
                playerPositionMs = 24_800L,
                pendingSeekPositionMs = 25_000L
            )
        )
    }

    @Test
    fun pendingSeek_staysActiveWhilePlayerStillReportsStaleOldPosition() {
        assertTrue(
            shouldHoldPortraitSeekUiPosition(
                playerPositionMs = 1_200L,
                pendingSeekPositionMs = 25_000L
            )
        )
    }
}
