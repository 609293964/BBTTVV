package com.bbttvv.app.core.store

import android.content.Context
import com.bbttvv.app.core.player.AudioBalanceLevel
import com.bbttvv.app.core.store.player.PlayerSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object PlayerSettingsCache {
    @Volatile
    private var preferredPlaybackSpeedInitialized = false

    @Volatile
    private var volumeCalibrationInitialized = false

    @Volatile
    private var audioBalanceInitialized = false

    @Volatile
    private var audioPassthroughInitialized = false

    @Volatile
    private var preferredPlaybackSpeed: Float = 1.0f

    @Volatile
    private var volumeCalibrationScale: Float = 1.0f

    @Volatile
    private var audioBalanceLevel: AudioBalanceLevel = AudioBalanceLevel.Off

    @Volatile
    private var audioPassthrough: Boolean = false

    private val _volumeCalibrationUpdateToken = MutableStateFlow(0L)
    val volumeCalibrationUpdateToken: StateFlow<Long> = _volumeCalibrationUpdateToken.asStateFlow()

    fun init(context: Context) {
        preferredPlaybackSpeed = PlayerSettingsStore.getPreferredPlaybackSpeedSync(context)
        volumeCalibrationScale = PlayerSettingsStore.getVolumeCalibrationScaleSync(context)
        audioBalanceLevel = PlayerSettingsStore.getAudioBalanceLevelSync(context)
        audioPassthrough = PlayerSettingsStore.getAudioPassthroughSync(context)
        preferredPlaybackSpeedInitialized = true
        volumeCalibrationInitialized = true
        audioBalanceInitialized = true
        audioPassthroughInitialized = true
    }

    fun refreshPreferredPlaybackSpeed(context: Context) {
        preferredPlaybackSpeed = PlayerSettingsStore.getPreferredPlaybackSpeedSync(context)
        preferredPlaybackSpeedInitialized = true
    }

    fun getPreferredPlaybackSpeed(defaultValue: Float = 1.0f): Float {
        return if (preferredPlaybackSpeedInitialized) preferredPlaybackSpeed else defaultValue
    }

    fun updateVolumeCalibrationScale(scale: Float) {
        val normalized = normalizePlayerVolumeCalibrationScale(scale)
        volumeCalibrationScale = normalized
        volumeCalibrationInitialized = true
        _volumeCalibrationUpdateToken.update { it + 1L }
    }

    fun getVolumeCalibrationScale(defaultValue: Float = 1.0f): Float {
        return if (volumeCalibrationInitialized) volumeCalibrationScale else defaultValue
    }

    fun updateAudioBalanceLevel(level: AudioBalanceLevel) {
        audioBalanceLevel = level
        audioBalanceInitialized = true
    }

    fun getAudioBalanceLevel(): AudioBalanceLevel {
        return if (audioBalanceInitialized) audioBalanceLevel else AudioBalanceLevel.Off
    }

    fun updateAudioPassthrough(enabled: Boolean) {
        audioPassthrough = enabled
        audioPassthroughInitialized = true
    }

    fun getAudioPassthrough(): Boolean {
        return if (audioPassthroughInitialized) audioPassthrough else false
    }
}
