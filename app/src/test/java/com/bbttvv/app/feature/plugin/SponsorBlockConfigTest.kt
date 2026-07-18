package com.bbttvv.app.feature.plugin

import com.bbttvv.app.data.model.response.SponsorCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorBlockConfigTest {
    @Test
    fun `unknown categories fail closed`() {
        val config = SponsorBlockConfig()

        assertFalse(config.isCategoryEnabled("future_category"))
        assertEquals(SponsorCategoryMode.DISABLED, config.playbackMode("future_category"))
    }

    @Test
    fun `music mode is independent from global auto skip`() {
        val config = SponsorBlockConfig(
            autoSkip = true,
            musicOfftopicModeRaw = SponsorCategoryMode.MANUAL.name,
        )

        assertTrue(config.isCategoryEnabled(SponsorCategory.MUSIC_OFFTOPIC))
        assertEquals(
            SponsorCategoryMode.MANUAL,
            config.playbackMode(SponsorCategory.MUSIC_OFFTOPIC),
        )
    }

    @Test
    fun `invalid persisted music mode normalizes to disabled`() {
        val normalized = SponsorBlockConfig(musicOfftopicModeRaw = "future_mode").normalized()

        assertEquals(SponsorCategoryMode.DISABLED.name, normalized.musicOfftopicModeRaw)
    }
}
