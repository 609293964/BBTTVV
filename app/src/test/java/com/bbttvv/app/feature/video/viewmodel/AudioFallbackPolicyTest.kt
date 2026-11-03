package com.bbttvv.app.feature.video.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioFallbackPolicyTest {
    private val tracks = listOf(
        AudioFallbackTrack(1, "ec-3", 900, true),
        AudioFallbackTrack(2, "mp4a.40.2", 300, true),
        AudioFallbackTrack(3, "mp4a.40.2", 100, true),
        AudioFallbackTrack(4, "mp4a.40.2", 500, false),
    )

    @Test fun `falls back only to available AAC and terminates after exhaustion`() {
        val policy = AudioFallbackPolicy()
        assertEquals(2, policy.next(1, tracks))
        assertEquals(3, policy.next(2, tracks))
        assertNull(policy.next(3, tracks))
        assertNull(policy.next(1, tracks))
    }

    @Test fun `a new media session can use tracks rejected by the previous session`() {
        val policy = AudioFallbackPolicy()
        policy.next(1, tracks)
        policy.next(2, tracks)
        policy.reset()
        assertEquals(2, policy.next(1, tracks))
    }

    @Test fun `AAC failure may downgrade but never retries the failing track`() {
        val policy = AudioFallbackPolicy()
        assertEquals(3, policy.next(2, tracks))
        assertNull(policy.next(3, tracks))
    }

    @Test fun `missing tracks and unsupported audio terminate immediately`() {
        assertNull(AudioFallbackPolicy().next(1, emptyList()))
        assertNull(AudioFallbackPolicy().next(2, tracks.filter { !it.codecs.startsWith("mp4a") }))
        assertNull(AudioFallbackPolicy().next(3, tracks))
    }
}
