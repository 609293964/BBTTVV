package com.bbttvv.app.core.store.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQualityStoreTest {
    @Test
    fun `unified TV value wins over legacy WiFi value`() {
        assertEquals(
            120,
            resolveUnifiedTvDefaultQuality(
                storedTvQuality = 120,
                legacyWifiQuality = 64,
            ),
        )
    }

    @Test
    fun `legacy WiFi value is migrated when unified value is absent`() {
        assertEquals(
            116,
            resolveUnifiedTvDefaultQuality(
                storedTvQuality = null,
                legacyWifiQuality = 116,
            ),
        )
    }

    @Test
    fun `missing or invalid legacy values default to 1080P`() {
        assertEquals(
            80,
            resolveUnifiedTvDefaultQuality(
                storedTvQuality = null,
                legacyWifiQuality = 999,
            ),
        )
        assertEquals(
            80,
            resolveUnifiedTvDefaultQuality(
                storedTvQuality = null,
                legacyWifiQuality = null,
            ),
        )
    }

    @Test
    fun `legacy mobile-only configuration is ignored`() {
        // Mobile quality is intentionally not an input to the TV migration policy.
        assertEquals(
            80,
            resolveUnifiedTvDefaultQuality(
                storedTvQuality = null,
                legacyWifiQuality = null,
            ),
        )
    }
}
