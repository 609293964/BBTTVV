package com.bbttvv.app.core.store

import android.content.Context

internal data class StartupSettingsSnapshot(
    val feedApiType: SettingsManager.FeedApiType,
    val homeRefreshCount: Int,
    val userAgent: String,
    val ipv4OnlyEnabled: Boolean,
    val autoHighestQuality: Boolean,
    val playerCdnPreference: SettingsManager.PlayerCdnPreference,
    val playerAutoResumeEnabled: Boolean,
    val videoDetailCommentsEnabled: Boolean,
    val watchLaterInTopTabsEnabled: Boolean
)

internal object StartupSettingsCache {
    @Volatile
    private var snapshot: StartupSettingsSnapshot? = null

    fun warmup(context: Context): StartupSettingsSnapshot {
        val appContext = context.applicationContext
        return StartupSettingsSnapshot(
            feedApiType = SettingsManager.getFeedApiTypeSync(appContext),
            homeRefreshCount = SettingsManager.getHomeRefreshCountSync(appContext),
            userAgent = SettingsManager.getUserAgentSync(appContext),
            ipv4OnlyEnabled = SettingsManager.getIpv4OnlyEnabledSync(appContext),
            autoHighestQuality = SettingsManager.getAutoHighestQualitySync(appContext),
            playerCdnPreference = SettingsManager.getPlayerCdnPreferenceSync(appContext),
            playerAutoResumeEnabled = SettingsManager.getPlayerAutoResumeEnabledSync(appContext),
            videoDetailCommentsEnabled = SettingsManager.getVideoDetailCommentsEnabledSync(appContext),
            watchLaterInTopTabsEnabled = SettingsManager.getWatchLaterInTopTabsEnabledSync(appContext)
        ).also { snapshot = it }
    }

    fun currentSnapshot(): StartupSettingsSnapshot? = snapshot
}
