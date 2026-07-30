package com.bbttvv.app.core.store.player

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.bbttvv.app.data.model.VideoQuality

private const val QUALITY_PREFERENCES = "quality_settings"
private const val KEY_TV_DEFAULT_QUALITY = "tv_default_quality"
private const val KEY_LEGACY_WIFI_QUALITY = "wifi_quality"
private const val KEY_LEGACY_MOBILE_QUALITY = "mobile_quality"
private const val DEFAULT_TV_QUALITY = 80

internal object PlaybackQualityStore {
    fun getNormalPreferredQualitySync(context: Context): Int {
        val preferences = context.applicationContext.getSharedPreferences(
            QUALITY_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        val storedTvQuality = preferences.readValidQuality(KEY_TV_DEFAULT_QUALITY)
        val legacyWifiQuality = preferences.readValidQuality(KEY_LEGACY_WIFI_QUALITY)
        val resolvedQuality = resolveUnifiedTvDefaultQuality(
            storedTvQuality = storedTvQuality,
            legacyWifiQuality = legacyWifiQuality,
        )

        val hasLegacyKeys = preferences.contains(KEY_LEGACY_WIFI_QUALITY) ||
            preferences.contains(KEY_LEGACY_MOBILE_QUALITY)
        if (storedTvQuality != resolvedQuality || hasLegacyKeys) {
            preferences.edit {
                putInt(KEY_TV_DEFAULT_QUALITY, resolvedQuality)
                remove(KEY_LEGACY_WIFI_QUALITY)
                remove(KEY_LEGACY_MOBILE_QUALITY)
            }
        }
        return resolvedQuality
    }
}

internal fun resolveUnifiedTvDefaultQuality(
    storedTvQuality: Int?,
    legacyWifiQuality: Int?,
): Int {
    return storedTvQuality?.takeIf(::isKnownVideoQuality)
        ?: legacyWifiQuality?.takeIf(::isKnownVideoQuality)
        ?: DEFAULT_TV_QUALITY
}

private fun SharedPreferences.readValidQuality(key: String): Int? {
    if (!contains(key)) return null
    return runCatching { getInt(key, DEFAULT_TV_QUALITY) }
        .getOrNull()
        ?.takeIf(::isKnownVideoQuality)
}

private fun isKnownVideoQuality(quality: Int): Boolean =
    VideoQuality.fromCode(quality) != null
