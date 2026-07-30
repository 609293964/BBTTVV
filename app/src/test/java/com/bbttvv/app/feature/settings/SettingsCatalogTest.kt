package com.bbttvv.app.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {
    @Test
    fun `interactive option row stays in playback settings before playback end`() {
        val keys = SettingsCatalog.keysByCategory.getValue(SettingsCategory.PLAYBACK)
        assertTrue(keys.indexOf("settings_interactive_video") < keys.indexOf("settings_playback_end"))
    }

    @Test
    fun `PGC quality row stays next to the inherited normal quality policy`() {
        val keys = SettingsCatalog.keysByCategory.getValue(SettingsCategory.PLAYBACK)
        assertEquals(
            keys.indexOf("settings_auto_highest_quality") + 1,
            keys.indexOf("settings_pgc_preferred_quality"),
        )
    }
    @Test
    fun everyCategoryHasUniqueStableKeys() {
        assertEquals(SettingsCategory.entries.toSet(), SettingsCatalog.keysByCategory.keys)
        SettingsCatalog.keysByCategory.forEach { (_, keys) ->
            assertTrue(keys.size > 1)
            assertEquals(keys.size, keys.distinct().size)
        }
    }

    @Test
    fun uiCategoryKeepsHomeTopTabFocusSettingInFocusOrder() {
        val keys = SettingsCatalog.keysByCategory.getValue(SettingsCategory.UI_UX)

        assertEquals(
            keys.indexOf("settings_home_top_tab_select_on_focus"),
            SettingsCatalog.itemIndex(
                SettingsCategory.UI_UX,
                "settings_home_top_tab_select_on_focus",
            ),
        )
    }

    @Test
    fun danmakuCategoryUsesDanmakuCatalogOrder() {
        assertEquals(
            DanmakuSettingsCatalog.keys,
            SettingsCatalog.keysByCategory.getValue(SettingsCategory.DANMAKU),
        )
    }
}
