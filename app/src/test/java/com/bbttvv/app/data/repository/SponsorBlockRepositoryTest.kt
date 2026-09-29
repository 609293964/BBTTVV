package com.bbttvv.app.data.repository

import com.bbttvv.app.data.model.response.SponsorActionType
import com.bbttvv.app.data.model.response.SponsorCategory
import com.bbttvv.app.data.model.response.SponsorSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorBlockRepositoryTest {
    @Test
    fun `segments url binds bvid cid categories and action types`() {
        val url = buildSponsorBlockSegmentsUrl(
            bvid = " BV1TEST ",
            cid = 123L,
            categories = listOf(SponsorCategory.SPONSOR, SponsorCategory.POI_HIGHLIGHT),
            actionTypes = listOf(SponsorActionType.SKIP, SponsorActionType.POI),
        )

        assertEquals("BV1TEST", url.queryParameter("videoID"))
        assertEquals("123", url.queryParameter("cid"))
        assertEquals(
            listOf(SponsorCategory.SPONSOR, SponsorCategory.POI_HIGHLIGHT),
            url.queryParameterValues("category"),
        )
        assertEquals(
            listOf(SponsorActionType.SKIP, SponsorActionType.POI),
            url.queryParameterValues("actionType"),
        )
    }

    @Test
    fun `default skip segments request omits unsupported chapter filters`() {
        val url = buildSponsorBlockSegmentsUrl(
            bvid = "BV14741127BN",
            cid = 168885122L,
            categories = SponsorCategory.PLAYBACK_CATEGORIES,
            actionTypes = SponsorActionType.PLAYBACK_ACTION_TYPES,
        )

        assertFalse(url.queryParameterValues("category").contains(SponsorCategory.CHAPTER))
        assertFalse(url.queryParameterValues("actionType").contains(SponsorActionType.CHAPTER))
    }

    @Test
    fun `cid filter keeps current and legacy unscoped segments`() {
        val current = segment(uuid = "current", cid = 123L)
        val other = segment(uuid = "other", cid = 456L)
        val legacy = segment(uuid = "legacy", cid = 0L)

        val filtered = filterSponsorSegmentsForCid(listOf(current, other, legacy), cid = 123L)

        assertEquals(listOf("current", "legacy"), filtered.map { it.UUID })
        assertTrue(filterSponsorSegmentsForCid(listOf(other), cid = 0L).isNotEmpty())
    }

    private fun segment(uuid: String, cid: Long) = SponsorSegment(
        segment = listOf(1f, 2f),
        UUID = uuid,
        category = SponsorCategory.SPONSOR,
        actionType = SponsorActionType.SKIP,
        cid = cid,
    )
}
