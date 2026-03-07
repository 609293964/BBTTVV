package com.android.purebilibili.feature.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileTopBarSystemUiPolicyTest {

    @Test
    fun mobileProfile_keepsTopBarPinnedWhileScrolling() {
        assertTrue(
            shouldPinProfileTopBarOnScroll(
                useSplitLayout = false
            )
        )
    }

    @Test
    fun splitLayoutProfile_keepsTopBarPinnedWhileScrolling() {
        assertTrue(
            shouldPinProfileTopBarOnScroll(
                useSplitLayout = true
            )
        )
    }

    @Test
    fun immersiveMobileProfile_keepsTopScrimTransparentAtRest() {
        assertEquals(
            0f,
            resolveProfileTopBarScrimAlpha(
                isImmersive = true,
                collapsedFraction = 0f
            ),
            0.001f
        )
    }

    @Test
    fun immersiveMobileProfile_delaysTopScrimUntilMeaningfulScroll() {
        assertEquals(
            0f,
            resolveProfileTopBarScrimAlpha(
                isImmersive = true,
                collapsedFraction = 0.05f
            ),
            0.001f
        )
        assertEquals(
            0f,
            resolveProfileTopBarScrimAlpha(
                isImmersive = true,
                collapsedFraction = 0.45f
            ),
            0.001f
        )
    }

    @Test
    fun immersiveMobileProfile_keepsTopScrimTransparentWhenFullyCollapsed() {
        assertEquals(
            0f,
            resolveProfileTopBarScrimAlpha(
                isImmersive = true,
                collapsedFraction = 1f
            ),
            0.001f
        )
    }

    @Test
    fun immersiveMobileProfile_usesLightStatusBarIcons() {
        assertFalse(
            resolveProfileLightStatusBars(
                isImmersive = true,
                useSplitLayout = false,
                isDarkTheme = false
            )
        )
    }

    @Test
    fun nonImmersiveProfile_followsThemeForStatusBarIcons() {
        assertTrue(
            resolveProfileLightStatusBars(
                isImmersive = false,
                useSplitLayout = false,
                isDarkTheme = false
            )
        )
        assertFalse(
            resolveProfileLightStatusBars(
                isImmersive = false,
                useSplitLayout = false,
                isDarkTheme = true
            )
        )
    }
}
