package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.data.model.response.DashAudio
import com.bbttvv.app.data.model.response.DashVideo
import com.bbttvv.app.feature.video.usecase.PlaybackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBufferingRecoveryPolicyTest {
    @Test
    fun shortBufferingDoesNotTriggerRecovery() {
        val tracker = testTracker()

        assertFalse(tracker.observe(sample(nowMs = 1_000L, bufferedPositionMs = 5_000L)))
        assertFalse(tracker.observe(sample(nowMs = 8_999L, bufferedPositionMs = 5_000L)))
    }

    @Test
    fun longBufferingWithoutBufferGrowthTriggersRecovery() {
        val tracker = testTracker()

        assertFalse(tracker.observe(sample(nowMs = 1_000L, bufferedPositionMs = 5_000L)))
        assertTrue(tracker.observe(sample(nowMs = 9_000L, bufferedPositionMs = 5_000L)))
    }

    @Test
    fun bufferGrowthDelaysRecovery() {
        val tracker = testTracker()

        assertFalse(tracker.observe(sample(nowMs = 1_000L, bufferedPositionMs = 5_000L)))
        assertFalse(tracker.observe(sample(nowMs = 6_000L, bufferedPositionMs = 7_000L)))
        assertFalse(tracker.observe(sample(nowMs = 9_000L, bufferedPositionMs = 7_000L)))
        assertTrue(tracker.observe(sample(nowMs = 14_000L, bufferedPositionMs = 7_000L)))
    }

    @Test
    fun nonRecoverableStatesDoNotTriggerRecovery() {
        val tracker = testTracker()

        assertFalse(
            tracker.observe(
                sample(
                    nowMs = 1_000L,
                    bufferedPositionMs = 5_000L,
                    isLoadingPlaybackInfo = true
                )
            )
        )
        assertFalse(tracker.observe(sample(nowMs = 9_000L, bufferedPositionMs = 5_000L)))

        assertFalse(
            tracker.observe(
                sample(
                    nowMs = 20_000L,
                    bufferedPositionMs = 5_000L,
                    playWhenReady = false
                )
            )
        )
        assertFalse(
            tracker.observe(
                sample(
                    nowMs = 30_000L,
                    bufferedPositionMs = 5_000L,
                    hasPlaybackError = true
                )
            )
        )
    }

    @Test
    fun dashRotationUpdatesSelectedTracksForManifestPlayback() {
        val source = playbackSource(
            videoUrl = VIDEO_1,
            audioUrl = AUDIO_1,
            videoUrlCandidates = listOf(VIDEO_1, VIDEO_2),
            audioUrlCandidates = listOf(AUDIO_1, AUDIO_2),
            selectedDashVideo = DashVideo(baseUrl = VIDEO_1, backupUrl = listOf(VIDEO_2)),
            selectedDashAudio = DashAudio(baseUrl = AUDIO_1, backupUrl = listOf(AUDIO_2))
        )

        val rotated = source.rotateForBufferingStall()

        assertEquals(VIDEO_2, rotated?.videoUrl)
        assertEquals(AUDIO_2, rotated?.audioUrl)
        assertEquals(listOf(VIDEO_2, VIDEO_1), rotated?.videoUrlCandidates)
        assertEquals(listOf(AUDIO_2, AUDIO_1), rotated?.audioUrlCandidates)
        assertEquals(VIDEO_2, rotated?.selectedDashVideo?.baseUrl)
        assertEquals(listOf(VIDEO_1), rotated?.selectedDashVideo?.backupUrl)
        assertEquals(AUDIO_2, rotated?.selectedDashAudio?.baseUrl)
        assertEquals(listOf(AUDIO_1), rotated?.selectedDashAudio?.backupUrl)
    }

    @Test
    fun segmentedRotationUpdatesEachSegmentCandidate() {
        val source = playbackSource(
            videoUrl = SEGMENT_1_A,
            audioUrl = null,
            segmentUrls = listOf(SEGMENT_1_A, SEGMENT_2_A),
            segmentUrlCandidates = listOf(
                listOf(SEGMENT_1_A, SEGMENT_1_B),
                listOf(SEGMENT_2_A, SEGMENT_2_B)
            )
        )

        val rotated = source.rotateForBufferingStall()

        assertEquals(listOf(SEGMENT_1_B, SEGMENT_2_B), rotated?.segmentUrls)
        assertEquals(
            listOf(
                listOf(SEGMENT_1_B, SEGMENT_1_A),
                listOf(SEGMENT_2_B, SEGMENT_2_A)
            ),
            rotated?.segmentUrlCandidates
        )
        assertEquals(SEGMENT_1_B, rotated?.videoUrl)
    }

    @Test
    fun rotationReturnsNullWhenNoAlternateCandidateExists() {
        val source = playbackSource(
            videoUrl = VIDEO_1,
            audioUrl = AUDIO_1,
            videoUrlCandidates = listOf(VIDEO_1),
            audioUrlCandidates = listOf(AUDIO_1)
        )

        assertNull(source.rotateForBufferingStall())
    }

    private fun testTracker(): PlaybackBufferingStallTracker {
        return PlaybackBufferingStallTracker(
            stallThresholdMs = 8_000L,
            minBufferAdvanceMs = 1_500L,
            recoveryCooldownMs = 12_000L
        )
    }

    private fun sample(
        nowMs: Long,
        bufferedPositionMs: Long,
        isBuffering: Boolean = true,
        isLoadingPlaybackInfo: Boolean = false,
        hasPlaybackError: Boolean = false,
        playWhenReady: Boolean = true,
        hasPlaybackSource: Boolean = true,
    ): PlaybackBufferingStallSample {
        return PlaybackBufferingStallSample(
            sessionKey = "BV1:100",
            nowMs = nowMs,
            isBuffering = isBuffering,
            isLoadingPlaybackInfo = isLoadingPlaybackInfo,
            hasPlaybackError = hasPlaybackError,
            playWhenReady = playWhenReady,
            bufferedPositionMs = bufferedPositionMs,
            hasPlaybackSource = hasPlaybackSource
        )
    }

    private fun playbackSource(
        videoUrl: String,
        audioUrl: String?,
        segmentUrls: List<String> = emptyList(),
        videoUrlCandidates: List<String> = emptyList(),
        audioUrlCandidates: List<String> = emptyList(),
        segmentUrlCandidates: List<List<String>> = emptyList(),
        selectedDashVideo: DashVideo? = null,
        selectedDashAudio: DashAudio? = null,
    ): PlaybackSource {
        return PlaybackSource(
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            segmentUrls = segmentUrls,
            videoUrlCandidates = videoUrlCandidates,
            audioUrlCandidates = audioUrlCandidates,
            segmentUrlCandidates = segmentUrlCandidates,
            actualQuality = 80,
            qualityOptions = emptyList(),
            dashVideos = selectedDashVideo?.let(::listOf).orEmpty(),
            dashAudios = selectedDashAudio?.let(::listOf).orEmpty(),
            durationMs = 120_000L,
            selectedVideoCodec = "avc1",
            selectedAudioCodec = "mp4a",
            selectedVideoCodecId = 7,
            selectedAudioQualityId = selectedDashAudio?.id ?: 30280,
            selectedAudioCodecId = 0,
            selectedVideoWidth = 1920,
            selectedVideoHeight = 1080,
            selectedVideoFrameRate = "30",
            selectedVideoBandwidth = 1_000_000,
            selectedAudioBandwidth = 128_000,
            activeDynamicRangeLabel = null,
            hasHdrTrack = false,
            hasDolbyVisionTrack = false,
            hasDolbyAudioTrack = false,
            capabilityHints = emptyList(),
            selectedDashVideo = selectedDashVideo,
            selectedDashAudio = selectedDashAudio
        )
    }

    private companion object {
        const val VIDEO_1 = "https://video.example.com/base.m4s"
        const val VIDEO_2 = "https://video-backup.example.com/base.m4s"
        const val AUDIO_1 = "https://audio.example.com/base.m4s"
        const val AUDIO_2 = "https://audio-backup.example.com/base.m4s"
        const val SEGMENT_1_A = "https://segment.example.com/1.mp4"
        const val SEGMENT_1_B = "https://segment-backup.example.com/1.mp4"
        const val SEGMENT_2_A = "https://segment.example.com/2.mp4"
        const val SEGMENT_2_B = "https://segment-backup.example.com/2.mp4"
    }
}
