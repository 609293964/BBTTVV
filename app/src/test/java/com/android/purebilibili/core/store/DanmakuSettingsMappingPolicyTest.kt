package com.android.purebilibili.core.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DanmakuSettingsMappingPolicyTest {

    private val portraitScope = DanmakuSettingsScope.PORTRAIT
    private val landscapeScope = DanmakuSettingsScope.LANDSCAPE

    @Test
    fun arbitraryDisplayAreaValues_snapToNearestSupportedOption() {
        assertEquals(0.25f, normalizeDanmakuDisplayArea(0.33f))
        assertEquals(0.5f, normalizeDanmakuDisplayArea(0.6f))
        assertEquals(0.75f, normalizeDanmakuDisplayArea(0.63f))
        assertEquals(1.0f, normalizeDanmakuDisplayArea(0.99f))
    }

    @Test
    fun emptyPreferences_useExpectedDanmakuDefaults() {
        val prefs = mutablePreferencesOf()

        val result = mapDanmakuSettingsFromPreferences(prefs, portraitScope)

        assertTrue(result.enabled)
        assertEquals(0.85f, result.opacity)
        assertEquals(1.0f, result.fontScale)
        assertEquals(1.0f, result.speed)
        assertEquals(0.5f, result.displayArea)
        assertTrue(result.mergeDuplicates)
        assertTrue(result.allowScroll)
        assertTrue(result.allowTop)
        assertTrue(result.allowBottom)
        assertTrue(result.allowColorful)
        assertTrue(result.allowSpecial)
        assertFalse(result.smartOcclusion)
        assertEquals(DanmakuPanelWidthMode.THIRD, result.fullscreenPanelWidthMode)
        assertEquals("", result.blockRulesRaw)
        assertEquals(emptyList(), result.blockRules)
    }

    @Test
    fun populatedPreferences_normalizeFullscreenPanelWidthToThird() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("danmaku_enabled") to false,
            floatPreferencesKey("danmaku_opacity") to 0.3f,
            floatPreferencesKey("danmaku_font_scale") to 1.3f,
            floatPreferencesKey("danmaku_speed") to 1.6f,
            floatPreferencesKey("danmaku_area") to 0.75f,
            booleanPreferencesKey("danmaku_merge_duplicates") to false,
            booleanPreferencesKey("danmaku_allow_scroll") to false,
            booleanPreferencesKey("danmaku_allow_top") to false,
            booleanPreferencesKey("danmaku_allow_bottom") to false,
            booleanPreferencesKey("danmaku_allow_colorful") to false,
            booleanPreferencesKey("danmaku_allow_special") to false,
            booleanPreferencesKey("danmaku_smart_occlusion") to true,
            intPreferencesKey("danmaku_fullscreen_panel_width_mode") to DanmakuPanelWidthMode.HALF.value,
            stringPreferencesKey("danmaku_block_rules") to "剧透\n广告\n  \n测试"
        )

        val result = mapDanmakuSettingsFromPreferences(prefs, portraitScope)

        assertFalse(result.enabled)
        assertEquals(0.3f, result.opacity)
        assertEquals(1.3f, result.fontScale)
        assertEquals(1.6f, result.speed)
        assertEquals(0.75f, result.displayArea)
        assertFalse(result.mergeDuplicates)
        assertFalse(result.allowScroll)
        assertFalse(result.allowTop)
        assertFalse(result.allowBottom)
        assertFalse(result.allowColorful)
        assertFalse(result.allowSpecial)
        assertTrue(result.smartOcclusion)
        assertEquals(DanmakuPanelWidthMode.THIRD, result.fullscreenPanelWidthMode)
        assertEquals("剧透\n广告\n  \n测试", result.blockRulesRaw)
        assertEquals(listOf("剧透", "广告", "测试"), result.blockRules)
    }

    @Test
    fun persistedFontScale_supportsThirtyPercentMinimum() {
        val prefs = mutablePreferencesOf(
            floatPreferencesKey("danmaku_font_scale") to 0.2f
        )

        val result = mapDanmakuSettingsFromPreferences(prefs, portraitScope)

        assertEquals(0.3f, result.fontScale)
    }

    @Test
    fun persistedDisplayArea_isNormalizedBackToDiscreteOption() {
        val prefs = mutablePreferencesOf(
            floatPreferencesKey("danmaku_area") to 0.33f
        )

        val result = mapDanmakuSettingsFromPreferences(prefs, portraitScope)

        assertEquals(0.25f, result.displayArea)
    }

    @Test
    fun invalidFullscreenPanelWidthMode_fallsBackToThirdWidth() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("danmaku_fullscreen_panel_width_mode") to 99
        )

        val result = mapDanmakuSettingsFromPreferences(prefs, portraitScope)

        assertEquals(DanmakuPanelWidthMode.THIRD, result.fullscreenPanelWidthMode)
    }

    @Test
    fun portraitScope_fallsBackToLegacyPreferences_whenScopedKeysAreMissing() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("danmaku_enabled") to false,
            floatPreferencesKey("danmaku_opacity") to 0.42f,
            floatPreferencesKey("danmaku_font_scale") to 1.4f,
            floatPreferencesKey("danmaku_speed") to 1.6f,
            floatPreferencesKey("danmaku_area") to 0.75f,
            booleanPreferencesKey("danmaku_merge_duplicates") to false,
            booleanPreferencesKey("danmaku_allow_scroll") to false,
            booleanPreferencesKey("danmaku_allow_top") to false,
            booleanPreferencesKey("danmaku_allow_bottom") to false,
            booleanPreferencesKey("danmaku_allow_colorful") to false,
            booleanPreferencesKey("danmaku_allow_special") to false,
            booleanPreferencesKey("danmaku_smart_occlusion") to true,
            stringPreferencesKey("danmaku_block_rules") to "剧透\n测试"
        )

        val result = mapDanmakuSettingsFromPreferences(prefs, portraitScope)

        assertFalse(result.enabled)
        assertEquals(0.42f, result.opacity)
        assertEquals(1.4f, result.fontScale)
        assertEquals(1.6f, result.speed)
        assertEquals(0.75f, result.displayArea)
        assertFalse(result.mergeDuplicates)
        assertFalse(result.allowScroll)
        assertFalse(result.allowTop)
        assertFalse(result.allowBottom)
        assertFalse(result.allowColorful)
        assertFalse(result.allowSpecial)
        assertTrue(result.smartOcclusion)
        assertEquals(listOf("剧透", "测试"), result.blockRules)
    }

    @Test
    fun landscapeScope_fallsBackToLegacyPreferences_whenScopedKeysAreMissing() {
        val prefs = mutablePreferencesOf(
            floatPreferencesKey("danmaku_opacity") to 0.61f,
            floatPreferencesKey("danmaku_font_scale") to 1.8f,
            floatPreferencesKey("danmaku_speed") to 0.8f,
            floatPreferencesKey("danmaku_area") to 1.0f
        )

        val result = mapDanmakuSettingsFromPreferences(prefs, landscapeScope)

        assertEquals(0.61f, result.opacity)
        assertEquals(1.8f, result.fontScale)
        assertEquals(0.8f, result.speed)
        assertEquals(1.0f, result.displayArea)
    }

    @Test
    fun scopedPreferences_overrideLegacyValues_perOrientation() {
        val prefs = mutablePreferencesOf(
            floatPreferencesKey("danmaku_opacity") to 0.4f,
            floatPreferencesKey("danmaku_portrait_opacity") to 0.55f,
            floatPreferencesKey("danmaku_landscape_opacity") to 0.72f,
            floatPreferencesKey("danmaku_portrait_font_scale") to 0.9f,
            floatPreferencesKey("danmaku_landscape_font_scale") to 1.6f,
            stringPreferencesKey("danmaku_portrait_block_rules") to "竖屏",
            stringPreferencesKey("danmaku_landscape_block_rules") to "横屏"
        )

        val portrait = mapDanmakuSettingsFromPreferences(prefs, portraitScope)
        val landscape = mapDanmakuSettingsFromPreferences(prefs, landscapeScope)

        assertEquals(0.55f, portrait.opacity)
        assertEquals(0.72f, landscape.opacity)
        assertEquals(0.9f, portrait.fontScale)
        assertEquals(1.6f, landscape.fontScale)
        assertEquals(listOf("竖屏"), portrait.blockRules)
        assertEquals(listOf("横屏"), landscape.blockRules)
    }
}
