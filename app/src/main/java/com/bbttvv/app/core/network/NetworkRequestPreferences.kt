package com.bbttvv.app.core.network

import android.content.Context
import com.bbttvv.app.core.store.DEFAULT_APP_USER_AGENT
import com.bbttvv.app.core.store.SettingsManager

internal fun resolveAppUserAgent(context: Context?): String {
    return context?.let(SettingsManager::getUserAgentSync) ?: DEFAULT_APP_USER_AGENT
}

internal fun isIpv4OnlyEnabled(context: Context?): Boolean {
    return context?.let(SettingsManager::getIpv4OnlyEnabledSync) ?: false
}
