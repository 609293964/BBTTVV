package com.android.purebilibili.core.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeSettingsMappingPolicyTest {

    @Test
    fun emptyPreferences_useExpectedRuntimeDefaults() {
        val prefs = mutablePreferencesOf()

        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(0, result.displayMode)
        assertTrue(result.isBottomBarFloating)
        assertEquals(0, result.bottomBarLabelMode)
        assertEquals(SettingsManager.TopTabLabelMode.TEXT_ONLY, result.topTabLabelMode)
        assertTrue(result.isHeaderBlurEnabled)
        assertTrue(result.isBottomBarBlurEnabled)
        assertTrue(result.isLiquidGlassEnabled)
        assertEquals(LiquidGlassStyle.CLASSIC, result.liquidGlassStyle)
        assertFalse(result.cardAnimationEnabled)
        assertTrue(result.cardTransitionEnabled)
        assertTrue(result.predictiveBackAnimationEnabled)
        assertFalse(result.smartVisualGuardEnabled)
        assertTrue(result.compactVideoStatsOnCover)
        assertFalse(result.crashTrackingConsentShown)
    }

    @Test
    fun populatedPreferences_mapToHomeSettingsCorrectly() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("display_mode") to 1,
            booleanPreferencesKey("bottom_bar_floating") to false,
            intPreferencesKey("bottom_bar_label_mode") to 2,
            intPreferencesKey("top_tab_label_mode") to 1,
            booleanPreferencesKey("header_blur_enabled") to false,
            booleanPreferencesKey("header_collapse_enabled") to false,
            booleanPreferencesKey("bottom_bar_blur_enabled") to false,
            booleanPreferencesKey("liquid_glass_enabled") to false,
            intPreferencesKey("liquid_glass_style") to LiquidGlassStyle.IOS26.value,
            intPreferencesKey("grid_column_count") to 4,
            booleanPreferencesKey("card_animation_enabled") to true,
            booleanPreferencesKey("card_transition_enabled") to false,
            booleanPreferencesKey("predictive_back_animation_enabled") to false,
            booleanPreferencesKey("smart_visual_guard_enabled") to false,
            booleanPreferencesKey("compact_video_stats_on_cover") to false,
            booleanPreferencesKey("crash_tracking_consent_shown") to true
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(1, result.displayMode)
        assertFalse(result.isBottomBarFloating)
        assertEquals(2, result.bottomBarLabelMode)
        assertEquals(1, result.topTabLabelMode)
        assertFalse(result.isHeaderBlurEnabled)
        assertFalse(result.isHeaderCollapseEnabled)
        assertFalse(result.isBottomBarBlurEnabled)
        assertFalse(result.isLiquidGlassEnabled)
        assertEquals(LiquidGlassStyle.IOS26, result.liquidGlassStyle)
        assertEquals(4, result.gridColumnCount)
        assertTrue(result.cardAnimationEnabled)
        assertFalse(result.cardTransitionEnabled)
        assertFalse(result.predictiveBackAnimationEnabled)
        assertFalse(result.smartVisualGuardEnabled)
        assertFalse(result.compactVideoStatsOnCover)
        assertTrue(result.crashTrackingConsentShown)
    }
}
