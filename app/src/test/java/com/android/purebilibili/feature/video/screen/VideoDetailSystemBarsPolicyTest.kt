package com.android.purebilibili.feature.video.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoDetailSystemBarsPolicyTest {

    @Test
    fun restorePolicy_defaultsToFallbackValuesWhenSnapshotMissing() {
        val snapshot = resolveVideoDetailSystemBarsSnapshot(
            statusBarColor = null,
            navigationBarColor = null,
            lightStatusBars = null,
            lightNavigationBars = null,
            systemBarsBehavior = null,
            fallbackColor = 0x00000000,
            fallbackLightBars = true,
            fallbackSystemBarsBehavior = 1
        )

        assertEquals(0x00000000, snapshot.statusBarColor)
        assertEquals(0x00000000, snapshot.navigationBarColor)
        assertEquals(true, snapshot.lightStatusBars)
        assertEquals(true, snapshot.lightNavigationBars)
        assertEquals(1, snapshot.systemBarsBehavior)
    }

    @Test
    fun restorePolicy_usesCapturedSystemBarsSnapshotWhenAvailable() {
        val snapshot = resolveVideoDetailSystemBarsSnapshot(
            statusBarColor = 0x11223344,
            navigationBarColor = 0x55667788,
            lightStatusBars = false,
            lightNavigationBars = false,
            systemBarsBehavior = 2,
            fallbackColor = 0x00000000,
            fallbackLightBars = true,
            fallbackSystemBarsBehavior = 1
        )

        assertEquals(0x11223344, snapshot.statusBarColor)
        assertEquals(0x55667788, snapshot.navigationBarColor)
        assertEquals(false, snapshot.lightStatusBars)
        assertEquals(false, snapshot.lightNavigationBars)
        assertEquals(2, snapshot.systemBarsBehavior)
    }

    @Test
    fun restorePolicy_alwaysShowsSystemBarsOnExit() {
        assertTrue(shouldShowSystemBarsOnVideoDetailExit())
    }
}

