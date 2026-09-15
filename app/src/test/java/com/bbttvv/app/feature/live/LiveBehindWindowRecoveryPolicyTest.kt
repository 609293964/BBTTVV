package com.bbttvv.app.feature.live

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveBehindWindowRecoveryPolicyTest {
    @Test
    fun `recovery escalates from seek to source rebuild`() {
        assertEquals(
            LiveBehindWindowRecoveryAction.SEEK_DEFAULT,
            resolveLiveBehindWindowRecoveryAction(emptyList(), nowMs = 20_000L)
        )
        assertEquals(
            LiveBehindWindowRecoveryAction.REBUILD_SOURCE,
            resolveLiveBehindWindowRecoveryAction(listOf(18_000L), nowMs = 20_000L)
        )
    }

    @Test
    fun `recovery is bounded and rate limited`() {
        assertEquals(
            LiveBehindWindowRecoveryAction.IGNORE_DUPLICATE,
            resolveLiveBehindWindowRecoveryAction(listOf(19_500L), nowMs = 20_000L)
        )
        assertEquals(
            LiveBehindWindowRecoveryAction.NONE,
            resolveLiveBehindWindowRecoveryAction(listOf(16_000L, 18_000L), nowMs = 20_000L)
        )
    }

    @Test
    fun `attempts outside the window do not consume recovery budget`() {
        assertEquals(
            LiveBehindWindowRecoveryAction.SEEK_DEFAULT,
            resolveLiveBehindWindowRecoveryAction(listOf(1_000L, 2_000L), nowMs = 20_000L)
        )
    }
}
