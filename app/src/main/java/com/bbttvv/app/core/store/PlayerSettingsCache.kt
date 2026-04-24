package com.bbttvv.app.core.store

import android.content.Context
import com.bbttvv.app.core.store.player.PlayerSettingsStore

object PlayerSettingsCache {
    @Volatile
    private var initialized = false

    @Volatile
    private var preferredPlaybackSpeed: Float = 1.0f

    fun init(context: Context) {
        preferredPlaybackSpeed = PlayerSettingsStore.getPreferredPlaybackSpeedSync(context)
        initialized = true
    }

    fun updatePreferredPlaybackSpeed(speed: Float) {
        preferredPlaybackSpeed = speed
        initialized = true
    }

    fun getPreferredPlaybackSpeed(defaultValue: Float = 1.0f): Float {
        return if (initialized) preferredPlaybackSpeed else defaultValue
    }
}
