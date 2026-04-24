package com.bbttvv.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLoadPolicyTest {

    @Test
    fun `resolveInitialStartQuality prefers stable quality bands for auto highest`() {
        assertEquals(
            120,
            resolveInitialStartQuality(
                targetQuality = 127,
                isAutoHighestQuality = true,
                isLogin = true,
                isVip = true,
                auto1080pEnabled = true
            )
        )
        assertEquals(
            80,
            resolveInitialStartQuality(
                targetQuality = 127,
                isAutoHighestQuality = true,
                isLogin = true,
                isVip = false,
                auto1080pEnabled = true
            )
        )
        assertEquals(
            64,
            resolveInitialStartQuality(
                targetQuality = 127,
                isAutoHighestQuality = true,
                isLogin = false,
                isVip = false,
                auto1080pEnabled = true
            )
        )
    }

    @Test
    fun `buildDashAttemptQualities keeps premium fallbacks ordered and deduplicated`() {
        assertEquals(listOf(80), buildDashAttemptQualities(80))
        assertEquals(listOf(120, 116, 112, 80), buildDashAttemptQualities(120))
        assertEquals(listOf(116, 112, 80), buildDashAttemptQualities(116))
    }

    @Test
    fun `shouldRetryDashTrackRecovery only retries degraded standard quality responses`() {
        assertTrue(
            shouldRetryDashTrackRecovery(
                targetQn = 80,
                returnedQuality = 64,
                acceptQualities = listOf(80, 64),
                dashVideoIds = listOf(64)
            )
        )
        assertFalse(
            shouldRetryDashTrackRecovery(
                targetQn = 120,
                returnedQuality = 80,
                acceptQualities = listOf(120, 80),
                dashVideoIds = listOf(80)
            )
        )
        assertFalse(
            shouldRetryDashTrackRecovery(
                targetQn = 80,
                returnedQuality = 80,
                acceptQualities = listOf(80, 64),
                dashVideoIds = listOf(80)
            )
        )
    }
}
