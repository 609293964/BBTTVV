package com.bbttvv.app.core.util

import com.bbttvv.app.data.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQualityPolicyTest {
    @Test
    fun `auto highest still requests 8K`() {
        assertEquals(
            VideoQuality.SUPER_8K.code,
            resolvePlaybackDefaultQualityId(
                storedQuality = VideoQuality.HIGH_1080.code,
                autoHighestEnabled = true,
                isLoggedIn = true,
                isVip = true,
            ),
        )
    }

    @Test
    fun `existing account access clamps remain unchanged`() {
        assertEquals(
            VideoQuality.HIGH_720.code,
            resolvePlaybackDefaultQualityId(
                storedQuality = VideoQuality.SUPER_4K.code,
                autoHighestEnabled = false,
                isLoggedIn = false,
                isVip = false,
            ),
        )
        assertEquals(
            VideoQuality.HIGH_1080.code,
            resolvePlaybackDefaultQualityId(
                storedQuality = VideoQuality.SUPER_4K.code,
                autoHighestEnabled = false,
                isLoggedIn = true,
                isVip = false,
            ),
        )
        assertEquals(
            VideoQuality.SUPER_4K.code,
            resolvePlaybackDefaultQualityId(
                storedQuality = VideoQuality.SUPER_4K.code,
                autoHighestEnabled = false,
                isLoggedIn = true,
                isVip = true,
            ),
        )
    }
}
