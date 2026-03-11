package com.android.purebilibili.core.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.android.purebilibili.core.store.home.HomeSettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeSettingsStoreParityTest {

    @Test
    fun `home store maps defaults the same way as settings manager policy`() {
        val prefs = mutablePreferencesOf()

        assertEquals(
            mapHomeSettingsFromPreferences(prefs),
            HomeSettingsStore.mapFromPreferences(prefs)
        )
    }

    @Test
    fun `home store maps populated preferences the same way as settings manager policy`() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("display_mode") to 1,
            booleanPreferencesKey("bottom_bar_floating") to false,
            intPreferencesKey("bottom_bar_label_mode") to 2
        )

        assertEquals(
            mapHomeSettingsFromPreferences(prefs),
            HomeSettingsStore.mapFromPreferences(prefs)
        )
    }

    @Test
    fun `home settings defaults keep new glass visibility groups enabled`() {
        val result = mapHomeSettingsFromPreferences(mutablePreferencesOf())

        assertTrue(result.showHomeCoverGlassBadges)
        assertTrue(result.showHomeInfoGlassBadges)
    }

    @Test
    fun `home settings map new glass visibility groups from preferences`() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("home_cover_glass_badges_visible") to false,
            booleanPreferencesKey("home_info_glass_badges_visible") to false
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertEquals(false, result.showHomeCoverGlassBadges)
        assertEquals(false, result.showHomeInfoGlassBadges)
    }
}
