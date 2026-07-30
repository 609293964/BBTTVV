package com.bbttvv.app.feature.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDebugMetricsPolicyTest {
    @Test
    fun `release build does not collect live debug metrics`() {
        val policy = resolveLiveDebugMetricsCollectionPolicy(
            isDebugBuild = false,
            isDebugOverlayVisible = false,
        )

        assertFalse(policy.collectMetrics)
        assertFalse(policy.trackRenderFps)
    }

    @Test
    fun `debug build collects metrics and tracks fps only while overlay is visible`() {
        val hiddenPolicy = resolveLiveDebugMetricsCollectionPolicy(
            isDebugBuild = true,
            isDebugOverlayVisible = false,
        )
        val visiblePolicy = resolveLiveDebugMetricsCollectionPolicy(
            isDebugBuild = true,
            isDebugOverlayVisible = true,
        )

        assertTrue(hiddenPolicy.collectMetrics)
        assertFalse(hiddenPolicy.trackRenderFps)
        assertTrue(visiblePolicy.collectMetrics)
        assertTrue(visiblePolicy.trackRenderFps)
    }
}
