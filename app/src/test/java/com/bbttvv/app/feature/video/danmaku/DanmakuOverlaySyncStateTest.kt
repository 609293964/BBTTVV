package com.bbttvv.app.feature.video.danmaku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuOverlaySyncStateTest {
    @Test
    fun `payload waits for attach and viewport before hard sync`() {
        val state = DanmakuOverlaySyncState()

        val waitForAttach = state.decide(
            viewAttached = false,
            viewportWidth = 0,
            viewportHeight = 0,
            attachToken = 0,
            dataToken = 10L,
            configToken = 20L,
            isPlaying = true,
            playbackPositionMs = 0L,
        )

        assertTrue(waitForAttach is DanmakuOverlaySyncDecision.WaitForAttach)
        assertTrue(state.waitingForAttachment)

        val waitForViewport = state.decide(
            viewAttached = true,
            viewportWidth = 0,
            viewportHeight = 0,
            attachToken = 1,
            dataToken = 10L,
            configToken = 20L,
            isPlaying = true,
            playbackPositionMs = 0L,
        )

        assertTrue(waitForViewport is DanmakuOverlaySyncDecision.WaitForViewport)
        assertTrue(state.waitingForViewport)

        val hardSync = state.decide(
            viewAttached = true,
            viewportWidth = 1920,
            viewportHeight = 1080,
            attachToken = 1,
            dataToken = 10L,
            configToken = 20L,
            isPlaying = true,
            playbackPositionMs = 0L,
        )

        assertEquals(
            DanmakuOverlaySyncDecision.HardSync(DanmakuSyncReason.PayloadChanged),
            hardSync,
        )
    }

    @Test
    fun `position discontinuity triggers hard sync after data is applied`() {
        val state = DanmakuOverlaySyncState()
        state.notifyHardSynced(
            dataToken = 10L,
            configToken = 20L,
            attachToken = 1,
            viewportWidth = 1920,
            viewportHeight = 1080,
            positionMs = 0L,
            isPlaying = true,
        )

        val noop = state.decide(
            viewAttached = true,
            viewportWidth = 1920,
            viewportHeight = 1080,
            attachToken = 1,
            dataToken = 10L,
            configToken = 20L,
            isPlaying = true,
            playbackPositionMs = 500L,
        )
        assertEquals(DanmakuOverlaySyncDecision.Noop, noop)

        val discontinuity = state.decide(
            viewAttached = true,
            viewportWidth = 1920,
            viewportHeight = 1080,
            attachToken = 1,
            dataToken = 10L,
            configToken = 20L,
            isPlaying = true,
            playbackPositionMs = DANMAKU_POSITION_DISCONTINUITY_THRESHOLD_MS + 1L,
        )

        assertEquals(
            DanmakuOverlaySyncDecision.HardSync(DanmakuSyncReason.PositionDiscontinuity),
            discontinuity,
        )
    }

    @Test
    fun `pause after applied data uses soft sync`() {
        val state = DanmakuOverlaySyncState()
        state.notifyHardSynced(
            dataToken = 10L,
            configToken = 20L,
            attachToken = 1,
            viewportWidth = 1920,
            viewportHeight = 1080,
            positionMs = 0L,
            isPlaying = true,
        )

        val pauseDecision = state.decide(
            viewAttached = true,
            viewportWidth = 1920,
            viewportHeight = 1080,
            attachToken = 1,
            dataToken = 10L,
            configToken = 20L,
            isPlaying = false,
            playbackPositionMs = 500L,
        )

        assertEquals(DanmakuOverlaySyncDecision.SoftSyncPlayState, pauseDecision)
    }

    @Test
    fun `playback speed change triggers one hard timeline sync`() {
        val state = DanmakuOverlaySyncState()
        state.notifyHardSynced(
            dataToken = 10L,
            configToken = 20L,
            attachToken = 1,
            viewportWidth = 1920,
            viewportHeight = 1080,
            positionMs = 1_000L,
            isPlaying = true,
            playbackSpeed = 1f,
            elapsedRealtimeMs = 1_000L,
        )

        val decision = state.decide(
            viewAttached = true,
            viewportWidth = 1920,
            viewportHeight = 1080,
            attachToken = 1,
            dataToken = 10L,
            configToken = 20L,
            isPlaying = true,
            playbackPositionMs = 1_250L,
            playbackSpeed = 2f,
            elapsedRealtimeMs = 1_250L,
        )

        assertEquals(
            DanmakuOverlaySyncDecision.HardSync(DanmakuSyncReason.PlaybackSpeedChanged),
            decision,
        )
    }

    @Test
    fun `high speed playback uses low frequency soft correction`() {
        val state = DanmakuOverlaySyncState()
        state.notifyHardSynced(
            dataToken = 10L,
            configToken = 20L,
            attachToken = 1,
            viewportWidth = 1920,
            viewportHeight = 1080,
            positionMs = 0L,
            isPlaying = true,
            playbackSpeed = 2f,
            elapsedRealtimeMs = 1_000L,
        )
        state.notifyPositionObserved(59_750L)

        val decision = state.decide(
            viewAttached = true,
            viewportWidth = 1920,
            viewportHeight = 1080,
            attachToken = 1,
            dataToken = 10L,
            configToken = 20L,
            isPlaying = true,
            playbackPositionMs = 60_000L,
            playbackSpeed = 2f,
            elapsedRealtimeMs = 31_000L,
        )

        assertEquals(
            DanmakuOverlaySyncDecision.SoftSyncTimeline(DanmakuSyncReason.HighSpeedDriftCorrection),
            decision,
        )
    }
}
