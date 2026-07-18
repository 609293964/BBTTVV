package com.bbttvv.app.data.repository

import com.bbttvv.app.data.model.response.SponsorActionType
import com.bbttvv.app.data.model.response.SponsorCategory
import com.bbttvv.app.data.model.response.SponsorSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorBlockRepositoryTest {
    @Test
    fun `segments url binds bvid cid categories and action types`() {
        val url = buildSponsorBlockSegmentsUrl(
            bvid = " BV1TEST ",
            cid = 123L,
            categories = listOf(SponsorCategory.SPONSOR, SponsorCategory.CHAPTER),
            actionTypes = listOf(SponsorActionType.SKIP, SponsorActionType.CHAPTER),
        )

        assertEquals("BV1TEST", url.queryParameter("videoID"))
        assertEquals("123", url.queryParameter("cid"))
        assertEquals(
            listOf(SponsorCategory.SPONSOR, SponsorCategory.CHAPTER),
            url.queryParameterValues("category"),
        )
        assertEquals(
            listOf(SponsorActionType.SKIP, SponsorActionType.CHAPTER),
            url.queryParameterValues("actionType"),
        )
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
