package com.bbttvv.app.feature.video.usecase

import com.bbttvv.app.core.store.SettingsManager
import java.net.URI

internal object PlaybackUrlCandidates {
    fun selectPreferredPlaybackUrl(
        preference: SettingsManager.PlayerCdnPreference,
        primaryUrl: String,
        backupUrls: List<String>,
    ): String {
        return orderedPlaybackUrls(
            preference = preference,
            primaryUrl = primaryUrl,
            backupUrls = backupUrls,
        ).firstOrNull().orEmpty()
    }

    fun orderedPlaybackUrls(
        preference: SettingsManager.PlayerCdnPreference,
        primaryUrl: String,
        backupUrls: List<String>,
    ): List<String> {
        val candidates = buildList {
            primaryUrl.trim().takeIf { it.isNotBlank() }?.let(::add)
            backupUrls.map { it.trim() }.filter { it.isNotBlank() }.forEach(::add)
        }.distinct()
        if (candidates.size <= 1) return candidates

        val preferred = when (preference) {
            SettingsManager.PlayerCdnPreference.BILIVIDEO -> candidates.firstOrNull(::isBilivideoUrl)
            SettingsManager.PlayerCdnPreference.MCDN -> candidates.firstOrNull(::isMcdnUrl)
        } ?: return candidates

        return listOf(preferred) + candidates.filterNot { it == preferred }
    }

    private fun isMcdnUrl(url: String): Boolean {
        val host = hostOf(url)
        return host.contains("mcdn") && host.contains("bilivideo")
    }

    private fun isBilivideoUrl(url: String): Boolean {
        val host = hostOf(url)
        return host.contains("bilivideo") && !host.contains("mcdn")
    }

    private fun hostOf(url: String): String {
        return runCatching { URI(url).host.orEmpty().lowercase() }
            .getOrDefault("")
    }
}
