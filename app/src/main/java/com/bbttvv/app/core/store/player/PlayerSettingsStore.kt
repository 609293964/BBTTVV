package com.bbttvv.app.core.store.player

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bbttvv.app.core.player.AudioBalanceLevel
import com.bbttvv.app.core.store.normalizePlayerVolumeCalibrationScale
import com.bbttvv.app.core.store.normalizePlaybackSpeed
import com.bbttvv.app.core.store.resolvePreferredPlaybackSpeed
import com.bbttvv.app.core.store.settingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

object PlayerSettingsStore {
    private val keyDefaultPlaybackSpeed = floatPreferencesKey("default_playback_speed")
    private val keyRememberLastPlaybackSpeed = booleanPreferencesKey("remember_last_playback_speed")
    private val keyLastPlaybackSpeed = floatPreferencesKey("last_playback_speed")
    private val keyVolumeCalibrationScale = floatPreferencesKey("volume_calibration_scale")
    private val keyAudioBalanceLevel = stringPreferencesKey("audio_balance_level")
    private val keyAudioPassthrough = booleanPreferencesKey("audio_passthrough")

    private const val playbackSpeedCachePrefs = "playback_speed_cache"
    private const val cacheKeyDefaultPlaybackSpeed = "default_speed"
    private const val cacheKeyRememberLastSpeed = "remember_last_speed"
    private const val cacheKeyLastPlaybackSpeed = "last_speed"
    private const val cacheKeyVolumeCalibrationScale = "volume_calibration_scale"
    private const val cacheKeyAudioBalanceLevel = "audio_balance_level"
    private const val cacheKeyAudioPassthrough = "audio_passthrough"

    fun getDefaultPlaybackSpeed(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences -> normalizePlaybackSpeed(preferences[keyDefaultPlaybackSpeed] ?: 1.0f) }

    suspend fun setDefaultPlaybackSpeed(context: Context, speed: Float) {
        val normalized = normalizePlaybackSpeed(speed)
        context.settingsDataStore.edit { preferences ->
            preferences[keyDefaultPlaybackSpeed] = normalized
        }
        context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putFloat(cacheKeyDefaultPlaybackSpeed, normalized)
            .apply()
    }

    fun getRememberLastPlaybackSpeed(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[keyRememberLastPlaybackSpeed] ?: false }

    suspend fun setRememberLastPlaybackSpeed(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyRememberLastPlaybackSpeed] = enabled
        }
        context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(cacheKeyRememberLastSpeed, enabled)
            .apply()
    }

    fun getLastPlaybackSpeed(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences -> normalizePlaybackSpeed(preferences[keyLastPlaybackSpeed] ?: 1.0f) }

    suspend fun setLastPlaybackSpeed(context: Context, speed: Float) {
        val normalized = normalizePlaybackSpeed(speed)
        context.settingsDataStore.edit { preferences ->
            preferences[keyLastPlaybackSpeed] = normalized
        }
        context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putFloat(cacheKeyLastPlaybackSpeed, normalized)
            .apply()
    }

    fun getPreferredPlaybackSpeed(context: Context): Flow<Float> = combine(
        getDefaultPlaybackSpeed(context),
        getRememberLastPlaybackSpeed(context),
        getLastPlaybackSpeed(context)
    ) { defaultSpeed, rememberLast, lastSpeed ->
        resolvePreferredPlaybackSpeed(defaultSpeed, rememberLast, lastSpeed)
    }

    fun getPreferredPlaybackSpeedSync(context: Context): Float {
        val prefs = context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
        val defaultSpeed = normalizePlaybackSpeed(prefs.getFloat(cacheKeyDefaultPlaybackSpeed, 1.0f))
        val rememberLast = prefs.getBoolean(cacheKeyRememberLastSpeed, false)
        val lastSpeed = normalizePlaybackSpeed(prefs.getFloat(cacheKeyLastPlaybackSpeed, 1.0f))
        return resolvePreferredPlaybackSpeed(defaultSpeed, rememberLast, lastSpeed)
    }

    fun getVolumeCalibrationScale(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            normalizePlayerVolumeCalibrationScale(preferences[keyVolumeCalibrationScale] ?: 1.0f)
        }

    suspend fun setVolumeCalibrationScale(context: Context, scale: Float) {
        val normalized = normalizePlayerVolumeCalibrationScale(scale)
        context.settingsDataStore.edit { preferences ->
            preferences[keyVolumeCalibrationScale] = normalized
        }
        context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putFloat(cacheKeyVolumeCalibrationScale, normalized)
            .apply()
    }

    fun getVolumeCalibrationScaleSync(context: Context): Float {
        val prefs = context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
        return normalizePlayerVolumeCalibrationScale(
            prefs.getFloat(cacheKeyVolumeCalibrationScale, 1.0f)
        )
    }

    fun getAudioBalanceLevel(context: Context): Flow<AudioBalanceLevel> = context.settingsDataStore.data
        .map { preferences ->
            AudioBalanceLevel.fromPrefValue(preferences[keyAudioBalanceLevel] ?: AudioBalanceLevel.Off.prefValue)
        }

    suspend fun setAudioBalanceLevel(context: Context, level: AudioBalanceLevel) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyAudioBalanceLevel] = level.prefValue
        }
        context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putString(cacheKeyAudioBalanceLevel, level.prefValue)
            .apply()
    }

    fun getAudioBalanceLevelSync(context: Context): AudioBalanceLevel {
        val prefs = context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
        return AudioBalanceLevel.fromPrefValue(
            prefs.getString(cacheKeyAudioBalanceLevel, AudioBalanceLevel.Off.prefValue) ?: AudioBalanceLevel.Off.prefValue
        )
    }

    fun getAudioPassthrough(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[keyAudioPassthrough] ?: false }

    suspend fun setAudioPassthrough(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyAudioPassthrough] = enabled
        }
        context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(cacheKeyAudioPassthrough, enabled)
            .apply()
    }

    fun getAudioPassthroughSync(context: Context): Boolean {
        val prefs = context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
        return prefs.getBoolean(cacheKeyAudioPassthrough, false)
    }
}
