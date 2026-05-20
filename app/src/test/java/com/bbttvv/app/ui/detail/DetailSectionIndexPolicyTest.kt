package com.bbttvv.app.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailSectionIndexPolicyTest {
    @Test
    fun `pages section follows hero section`() {
        assertEquals(0, DetailHeroSectionIndex)
        assertEquals(1, DetailPagesSectionIndex)
    }

    @Test
    fun `related videos section follows hero when pages section is hidden`() {
        assertEquals(1, detailRelatedVideosSectionIndex(hasPagesSection = false))
    }

    @Test
    fun `related videos section follows pages when pages section is visible`() {
        assertEquals(2, detailRelatedVideosSectionIndex(hasPagesSection = true))
    }
}
