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
    private var initialized = false

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
        initialized = true
    }

    fun updatePreferredPlaybackSpeed(speed: Float) {
        preferredPlaybackSpeed = speed
        initialized = true
    }

    fun getPreferredPlaybackSpeed(defaultValue: Float = 1.0f): Float {
        return if (initialized) preferredPlaybackSpeed else defaultValue
    }

    fun updateVolumeCalibrationScale(scale: Float) {
        val normalized = normalizePlayerVolumeCalibrationScale(scale)
        volumeCalibrationScale = normalized
        initialized = true
        _volumeCalibrationUpdateToken.update { it + 1L }
    }

    fun getVolumeCalibrationScale(defaultValue: Float = 1.0f): Float {
        return if (initialized) volumeCalibrationScale else defaultValue
    }

    fun updateAudioBalanceLevel(level: AudioBalanceLevel) {
        audioBalanceLevel = level
        initialized = true
    }

    fun getAudioBalanceLevel(): AudioBalanceLevel {
        return if (initialized) audioBalanceLevel else AudioBalanceLevel.Off
    }

    fun updateAudioPassthrough(enabled: Boolean) {
        audioPassthrough = enabled
        initialized = true
    }

    fun getAudioPassthrough(): Boolean {
        return if (initialized) audioPassthrough else false
    }
}
