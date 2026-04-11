package com.android.purebilibili.navigation

import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.theme.AndroidNativeVariant
import com.android.purebilibili.core.theme.UiPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigationAppearancePolicyTest {

    @Test
    fun mapsBottomBarAndTransitionFlagsFromHomeSettings() {
        val appearance = resolveAppNavigationAppearance(
            HomeSettings(
                isBottomBarFloating = false,
                bottomBarLabelMode = 2,
                isBottomBarBlurEnabled = false,
                cardTransitionEnabled = false,
                predictiveBackAnimationEnabled = false
            )
        )

        assertFalse(appearance.cardTransitionEnabled)
        assertFalse(appearance.predictiveBackAnimationEnabled)
        assertFalse(appearance.bottomBarBlurEnabled)
        assertEquals(2, appearance.bottomBarLabelMode)
        assertFalse(appearance.bottomBarFloating)
    }

    @Test
    fun keepsDefaultsWhenHomeSettingsUseDefaults() {
        val appearance = resolveAppNavigationAppearance(HomeSettings())

        assertTrue(appearance.cardTransitionEnabled)
        assertTrue(appearance.predictiveBackAnimationEnabled)
        assertTrue(appearance.bottomBarBlurEnabled)
        assertEquals(0, appearance.bottomBarLabelMode)
        assertTrue(appearance.bottomBarFloating)
    }

    @Test
    fun md3Preset_keepsFloatingBottomBarWhenShellSettingsAreStillDefault() {
        val appearance = resolveAppNavigationAppearance(
            homeSettings = HomeSettings(),
            uiPreset = UiPreset.MD3
        )

        assertTrue(appearance.bottomBarFloating)
        assertTrue(appearance.bottomBarBlurEnabled)
        assertEquals(0, appearance.bottomBarLabelMode)
    }

    @Test
    fun md3Preset_preservesExplicitBottomBarShellCustomization() {
        val appearance = resolveAppNavigationAppearance(
            homeSettings = HomeSettings(
                isBottomBarFloating = true,
                bottomBarLabelMode = 1,
                isBottomBarBlurEnabled = false
            ),
            uiPreset = UiPreset.MD3
        )

        assertTrue(appearance.bottomBarFloating)
        assertFalse(appearance.bottomBarBlurEnabled)
        assertEquals(1, appearance.bottomBarLabelMode)
    }

    @Test
    fun md3MiuixPreset_keepsFloatingBottomBarWhenShellSettingsAreDefault() {
        val appearance = resolveAppNavigationAppearance(
            homeSettings = HomeSettings(),
            uiPreset = UiPreset.MD3,
            androidNativeVariant = AndroidNativeVariant.MIUIX
        )

        assertTrue(appearance.bottomBarFloating)
        assertTrue(appearance.bottomBarBlurEnabled)
        assertEquals(0, appearance.bottomBarLabelMode)
    }
}
