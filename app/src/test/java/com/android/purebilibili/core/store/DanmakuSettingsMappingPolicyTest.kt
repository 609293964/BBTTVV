package com.android.purebilibili.core.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DanmakuSettingsMappingPolicyTest {

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

        val result = mapDanmakuSettingsFromPreferences(prefs)

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
        assertEquals("", result.blockRulesRaw)
        assertEquals(emptyList(), result.blockRules)
    }

    @Test
    fun populatedPreferences_mapToDanmakuSettingsCorrectly() {
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
            stringPreferencesKey("danmaku_block_rules") to "剧透\n广告\n  \n测试"
        )

        val result = mapDanmakuSettingsFromPreferences(prefs)

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
        assertEquals("剧透\n广告\n  \n测试", result.blockRulesRaw)
        assertEquals(listOf("剧透", "广告", "测试"), result.blockRules)
    }

    @Test
    fun persistedDisplayArea_isNormalizedBackToDiscreteOption() {
        val prefs = mutablePreferencesOf(
            floatPreferencesKey("danmaku_area") to 0.33f
        )

        val result = mapDanmakuSettingsFromPreferences(prefs)

        assertEquals(0.25f, result.displayArea)
    }
}
