package com.bbttvv.app.core.player

import com.bbttvv.app.data.model.response.SponsorCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class SponsorBlockSkipPolicyTest {
    @Test
    fun `ordinary segment is kept before playback end`() {
        assertEquals(
            119_000L,
            resolveSponsorBlockSkipTargetPositionMs(
                requestedPositionMs = 121_500L,
                durationMs = 120_000L,
                category = SponsorCategory.SPONSOR,
            )
        )
    }

    @Test
    fun `outro may finish naturally`() {
        assertEquals(
            120_000L,
            resolveSponsorBlockSkipTargetPositionMs(
                requestedPositionMs = 121_500L,
                durationMs = 120_000L,
                category = SponsorCategory.OUTRO,
            )
        )
    }

    @Test
    fun `unknown duration preserves nonnegative requested position`() {
        assertEquals(42_000L, resolveSponsorBlockSkipTargetPositionMs(42_000L, 0L, SponsorCategory.SPONSOR))
        assertEquals(0L, resolveSponsorBlockSkipTargetPositionMs(-1L, 0L, SponsorCategory.SPONSOR))
    }
}
