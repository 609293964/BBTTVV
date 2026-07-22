package com.bbttvv.app.feature.video.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackHistoryReporterTest {
    @Test
    fun forceFlushDoesNotReportSessionThatNeverPlayed() {
        assertFalse(
            shouldSendHeartbeatReport(
                forceFlush = true,
                isPlaying = false,
                playedTimeSec = 0L,
                realPlayedTimeSec = 0L,
                lastReportedSnapshot = null
            )
        )
    }

    @Test
    fun forceFlushReportsAfterPlaybackProgress() {
        assertTrue(
            shouldSendHeartbeatReport(
                forceFlush = true,
                isPlaying = false,
                playedTimeSec = 12L,
                realPlayedTimeSec = 8L,
                lastReportedSnapshot = null
            )
        )
    }

    @Test
    fun intervalReportStillRequiresPlaybackProgress() {
        val previous = PlaybackHeartbeatSnapshot(
            playedTimeSec = 12L,
            realPlayedTimeSec = 8L,
            durationMs = 60_000L
        )

        assertFalse(
            shouldSendHeartbeatReport(
                forceFlush = false,
                isPlaying = true,
                playedTimeSec = 12L,
                realPlayedTimeSec = 8L,
                lastReportedSnapshot = previous
            )
        )
        assertTrue(
            shouldSendHeartbeatReport(
                forceFlush = false,
                isPlaying = true,
                playedTimeSec = 13L,
                realPlayedTimeSec = 8L,
                lastReportedSnapshot = previous
            )
        )
    }
}
