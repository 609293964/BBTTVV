package com.bbttvv.app.data.repository

import com.bbttvv.app.data.model.response.PlayUrlData
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackAutoResumeTest {

    @Test
    fun `normalizeAutoResumePositionMs ignores too-short and near-end progress`() {
        assertEquals(0L, normalizeAutoResumePositionMs(lastPlayTimeSeconds = 2, durationMs = 120_000L))
        assertEquals(0L, normalizeAutoResumePositionMs(lastPlayTimeSeconds = 119, durationMs = 120_000L))
        assertEquals(30_000L, normalizeAutoResumePositionMs(lastPlayTimeSeconds = 30, durationMs = 120_000L))
    }

    @Test
    fun `resolveAutoResumePositionMs only resumes when cid matches current page`() {
        val sameCidData = PlayUrlData(
            timelength = 180_000L,
            lastPlayTime = 48,
            lastPlayCid = 1001L
        )
        val differentCidData = sameCidData.copy(lastPlayCid = 2002L)

        assertEquals(48_000L, resolveAutoResumePositionMs(currentCid = 1001L, playUrlData = sameCidData))
        assertEquals(0L, resolveAutoResumePositionMs(currentCid = 1001L, playUrlData = differentCidData))
    }
}
