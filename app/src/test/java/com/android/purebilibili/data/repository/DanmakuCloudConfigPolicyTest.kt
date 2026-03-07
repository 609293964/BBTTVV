package com.android.purebilibili.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DanmakuCloudConfigPolicyTest {

    @Test
    fun buildDanmakuCloudConfigPayload_mapsAllowFlagsAndRanges() {
        val payload = buildDanmakuCloudConfigPayload(
            DanmakuCloudSyncSettings(
                enabled = false,
                allowScroll = false,
                allowTop = true,
                allowBottom = false,
                allowColorful = true,
                allowSpecial = false,
                opacity = 1.2f,
                displayAreaRatio = 0.74f,
                speed = 2.2f,
                fontScale = 0.2f
            )
        )

        assertEquals("false", payload.dmSwitch)
        assertEquals("false", payload.blockScroll)
        assertEquals("true", payload.blockTop)
        assertEquals("false", payload.blockBottom)
        assertEquals("true", payload.blockColor)
        assertEquals("false", payload.blockSpecial)
        assertEquals(1.0f, payload.opacity)
        assertEquals(75, payload.dmArea)
        assertEquals(1.6f, payload.speedPlus)
        assertEquals(0.4f, payload.fontSize)
    }

    @Test
    fun mapDanmakuDisplayAreaRatioToCloudValue_mapsDiscreteBuckets() {
        assertEquals(0, mapDanmakuDisplayAreaRatioToCloudValue(0f))
        assertEquals(25, mapDanmakuDisplayAreaRatioToCloudValue(0.26f))
        assertEquals(50, mapDanmakuDisplayAreaRatioToCloudValue(0.51f))
        assertEquals(75, mapDanmakuDisplayAreaRatioToCloudValue(0.75f))
        assertEquals(100, mapDanmakuDisplayAreaRatioToCloudValue(1.0f))
    }

    @Test
    fun mapDanmakuDisplayAreaRatioToCloudValue_snapsArbitraryValuesToSupportedBuckets() {
        assertEquals(25, mapDanmakuDisplayAreaRatioToCloudValue(0.33f))
        assertEquals(50, mapDanmakuDisplayAreaRatioToCloudValue(0.62f))
        assertEquals(75, mapDanmakuDisplayAreaRatioToCloudValue(0.63f))
    }

    @Test
    fun isDanmakuCloudSyncSuccessful_acceptsNoChangeCode() {
        assertTrue(isDanmakuCloudSyncSuccessful(0))
        assertTrue(isDanmakuCloudSyncSuccessful(23004))
    }
}
