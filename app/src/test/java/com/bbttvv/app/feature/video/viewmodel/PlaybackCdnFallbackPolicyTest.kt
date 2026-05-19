package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.data.model.response.DashAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCdnFallbackPolicyTest {
    @Test
    fun cdnRewriteKeepsOriginalPlaybackPairAsFallback() {
        val state = buildPlaybackCdnFallbackState(
            selectedVideoUrl = "https://cn-sh-ct-01-01.bilivideo.com/video.m4s",
            selectedAudioUrl = "https://cn-sh-ct-01-01.bilivideo.com/audio.m4s",
            originalVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            originalAudioUrl = "https://upos-sz-mirrorali.bilivideo.com/audio.m4s",
            regionLabel = "上海"
        )

        assertTrue(state.usesCdnRewrite)
        assertEquals("https://upos-sz-mirrorali.bilivideo.com/video.m4s", state.fallbackVideoUrl)
        assertEquals("https://upos-sz-mirrorali.bilivideo.com/audio.m4s", state.fallbackAudioUrl)
        assertTrue(shouldFallbackFromCdnRewrite(state, playbackReady = false))
    }

    @Test
    fun unchangedPlaybackUrlDoesNotArmFallback() {
        val state = buildPlaybackCdnFallbackState(
            selectedVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            selectedAudioUrl = "https://upos-sz-mirrorali.bilivideo.com/audio.m4s",
            originalVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            originalAudioUrl = "https://upos-sz-mirrorali.bilivideo.com/audio.m4s",
            regionLabel = null
        )

        assertFalse(state.usesCdnRewrite)
        assertFalse(shouldFallbackFromCdnRewrite(state, playbackReady = false))
    }

    @Test
    fun audioCandidatesUseBackupUrlsFromSelectedAudioTrack() {
        val candidates = buildPlaybackAudioUrlCandidates(
            audioUrl = "https://audio.example.com/30280-base.m4s",
            cachedDashAudios = listOf(
                DashAudio(
                    id = 30232,
                    baseUrl = "https://audio.example.com/30232-base.m4s",
                    backupUrl = listOf("https://audio.example.com/30232-backup.m4s")
                ),
                DashAudio(
                    id = 30280,
                    baseUrl = "https://audio.example.com/30280-base.m4s",
                    backupUrl = listOf("https://audio.example.com/30280-backup.m4s")
                )
            )
        )

        assertEquals(
            listOf(
                "https://audio.example.com/30280-base.m4s",
                "https://audio.example.com/30280-backup.m4s"
            ),
            candidates
        )
    }

    @Test
    fun sameVideoUrlCanArmAudioFallbackWhenSelectedAudioHasBackup() {
        val state = buildPlaybackCdnFallbackState(
            selectedVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            selectedAudioUrl = "https://audio.example.com/30280-base.m4s",
            originalVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            originalAudioUrl = "https://audio.example.com/30280-base.m4s",
            regionLabel = null,
            audioFallbackUrl = "https://audio.example.com/30280-backup.m4s"
        )

        assertTrue(state.usesCdnRewrite)
        assertEquals("https://audio.example.com/30280-backup.m4s", state.fallbackAudioUrl)
        assertTrue(
            shouldFallbackFromCdnRewrite(
                state = state,
                playbackReady = true,
                expectedAudioTrack = true,
                hasSelectedAudioTrack = false,
                audioRendererError = false
            )
        )
    }

    @Test
    fun cdnFallbackCanOnlyFireOnce() {
        val state = buildPlaybackCdnFallbackState(
            selectedVideoUrl = "https://cn-sh-ct-01-01.bilivideo.com/video.m4s",
            selectedAudioUrl = null,
            originalVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            originalAudioUrl = null,
            regionLabel = "上海"
        )

        assertFalse(shouldFallbackFromCdnRewrite(state.markFallbackConsumed(), playbackReady = false))
    }

    @Test
    fun readyPlaybackStillFallsBackWhenRewrittenAudioTrackIsMissing() {
        val state = buildPlaybackCdnFallbackState(
            selectedVideoUrl = "https://cn-sh-ct-01-01.bilivideo.com/video.m4s",
            selectedAudioUrl = "https://cn-sh-ct-01-01.bilivideo.com/audio.m4s",
            originalVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            originalAudioUrl = "https://upos-sz-mirrorali.bilivideo.com/audio.m4s",
            regionLabel = "上海"
        )

        assertTrue(
            shouldFallbackFromCdnRewrite(
                state = state,
                playbackReady = true,
                expectedAudioTrack = true,
                hasSelectedAudioTrack = false,
                audioRendererError = false
            )
        )
    }

    @Test
    fun readyPlaybackFallsBackImmediatelyAfterAudioRendererError() {
        val state = buildPlaybackCdnFallbackState(
            selectedVideoUrl = "https://cn-sh-ct-01-01.bilivideo.com/video.m4s",
            selectedAudioUrl = "https://cn-sh-ct-01-01.bilivideo.com/audio.m4s",
            originalVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            originalAudioUrl = "https://upos-sz-mirrorali.bilivideo.com/audio.m4s",
            regionLabel = "上海"
        )

        assertTrue(
            shouldFallbackFromCdnRewrite(
                state = state,
                playbackReady = true,
                expectedAudioTrack = true,
                hasSelectedAudioTrack = true,
                audioRendererError = true
            )
        )
    }
}
