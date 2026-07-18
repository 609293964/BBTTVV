package com.bbttvv.app.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {
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
