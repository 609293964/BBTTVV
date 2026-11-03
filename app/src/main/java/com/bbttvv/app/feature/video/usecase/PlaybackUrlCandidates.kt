package com.bbttvv.app.feature.video.usecase

import com.bbttvv.app.core.store.SettingsManager
import java.net.URI

internal object PlaybackUrlCandidates {
    private val bilivideoHostSuffixes = listOf("bilivideo.com", "bilivideo.cn", "bilivideo.net")
    private val riskyHostSuffixes = listOf(
        "szbdyd.com", "mountaintoys.cn", "qrstuvwxyzab.com", "zhenidc.cn",
    )
    private val ipv4Host = Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$")

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

        // 只重排服务端提供的完整 URL，不改写路径/签名，也不删除风险线路兜底。
        // 同一优先级维持接口顺序；显式 MCDN 偏好优先于风险降权。
        return candidates.sortedBy { url ->
            val host = hostOf(url)
            when {
                preference == SettingsManager.PlayerCdnPreference.MCDN && isMcdnHost(host) -> 0
                isRiskyHost(host) -> 3
                isBilivideoHost(host) -> 1
                else -> 2
            }
        }
    }

    private fun isMcdnHost(host: String): Boolean {
        return host.contains("mcdn") && isBilivideoHost(host)
    }

    private fun isBilivideoHost(host: String): Boolean {
        return bilivideoHostSuffixes.any {
            host == it || host.endsWith(".$it")
        }
    }

    private fun isRiskyHost(host: String): Boolean {
        return host.isBlank() || host.contains("mcdn") || host.contains("pcdn") ||
            host.contains("gotcha") || ipv4Host.matches(host) ||
            host.contains(':') || // IPv6 literal
            riskyHostSuffixes.any { host == it || host.endsWith(".$it") }
    }

    private fun hostOf(url: String): String {
        return runCatching { URI(url).host.orEmpty().lowercase().trimEnd('.') }
            .getOrDefault("")
    }
}
