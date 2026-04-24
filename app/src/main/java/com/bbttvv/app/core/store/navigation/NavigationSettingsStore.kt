package com.bbttvv.app.core.store.navigation

import android.content.Context
import com.bbttvv.app.core.store.AppNavigationSettings
import com.bbttvv.app.core.store.SettingsManager
import kotlinx.coroutines.flow.Flow

object NavigationSettingsStore {
    fun observe(context: Context): Flow<AppNavigationSettings> {
        return SettingsManager.observeAppNavigationSettings(context)
    }
}
