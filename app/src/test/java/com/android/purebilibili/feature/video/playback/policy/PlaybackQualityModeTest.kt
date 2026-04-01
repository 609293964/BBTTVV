package com.android.purebilibili.feature.video.playback.policy

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackQualityModeTest {

    @Test
    fun `auto mode reports no locked quality`() {
        assertEquals(
            null,
            PlaybackQualityMode.AUTO.lockedQualityId
        )
    }

    @Test
    fun `locked mode exposes requested quality id`() {
        assertEquals(
            80,
            PlaybackQualityMode.LOCKED(80).lockedQualityId
        )
    }

    @Test
    fun `from quality id maps non-positive values to auto`() {
        assertEquals(
            PlaybackQualityMode.AUTO,
            PlaybackQualityMode.fromQualityId(-1)
        )
    }

    @Test
    fun `from quality id maps positive values to locked mode`() {
        assertEquals(
            PlaybackQualityMode.LOCKED(64),
            PlaybackQualityMode.fromQualityId(64)
        )
    }
}
