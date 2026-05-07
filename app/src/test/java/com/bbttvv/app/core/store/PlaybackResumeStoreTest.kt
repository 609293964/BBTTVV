package com.bbttvv.app.core.store

import com.bbttvv.app.data.model.response.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackResumeStoreTest {

    @Test
    fun `normalizeStoredResumePositionMs ignores too-short and near-end positions`() {
        assertEquals(0L, normalizeStoredResumePositionMs(positionMs = 2_000L, durationMs = 120_000L))
        assertEquals(0L, normalizeStoredResumePositionMs(positionMs = 119_000L, durationMs = 120_000L))
        assertEquals(30_000L, normalizeStoredResumePositionMs(positionMs = 30_000L, durationMs = 120_000L))
    }

    @Test
    fun `resolveStoredResumeCandidate returns same-page candidate when cid matches`() {
        val candidate = resolveStoredResumeCandidate(
            currentCid = 1001L,
            pages = listOf(Page(cid = 1001L, page = 1, part = "P1")),
            record = PlaybackResumeRecord(
                bvid = "BV1",
                cid = 1001L,
                positionMs = 48_000L,
                durationMs = 180_000L,
                updatedAtMs = 1_000L,
            ),
            nowMs = 2_000L,
        )

        assertNotNull(candidate)
        val resolved = candidate!!
        assertEquals(1001L, resolved.cid)
        assertEquals(48_000L, resolved.positionMs)
        assertEquals(false, resolved.isCrossPage)
    }

    @Test
    fun `resolveStoredResumeCandidate returns cross-page candidate when cid exists in pages`() {
        val candidate = resolveStoredResumeCandidate(
            currentCid = 1001L,
            pages = listOf(
                Page(cid = 1001L, page = 1, part = "P1"),
                Page(cid = 2002L, page = 2, part = "P2"),
            ),
            record = PlaybackResumeRecord(
                bvid = "BV1",
                cid = 2002L,
                positionMs = 72_000L,
                durationMs = 180_000L,
                updatedAtMs = 1_000L,
            ),
            nowMs = 2_000L,
        )

        assertNotNull(candidate)
        val resolved = candidate!!
        assertEquals(2002L, resolved.cid)
        assertEquals(72_000L, resolved.positionMs)
        assertEquals(true, resolved.isCrossPage)
    }

    @Test
    fun `resolveStoredResumeCandidate ignores stale or invalid records`() {
        assertNull(
            resolveStoredResumeCandidate(
                currentCid = 1001L,
                pages = listOf(Page(cid = 1001L, page = 1, part = "P1")),
                record = PlaybackResumeRecord(
                    bvid = "BV1",
                    cid = 1001L,
                    positionMs = 48_000L,
                    durationMs = 180_000L,
                    updatedAtMs = 0L,
                ),
                nowMs = 31L * 24L * 60L * 60L * 1000L,
            )
        )
        assertNull(
            resolveStoredResumeCandidate(
                currentCid = 1001L,
                pages = listOf(Page(cid = 1001L, page = 1, part = "P1")),
                record = PlaybackResumeRecord(
                    bvid = "BV1",
                    cid = 3003L,
                    positionMs = 48_000L,
                    durationMs = 180_000L,
                    updatedAtMs = 1_000L,
                ),
                nowMs = 2_000L,
            )
        )
    }
}
