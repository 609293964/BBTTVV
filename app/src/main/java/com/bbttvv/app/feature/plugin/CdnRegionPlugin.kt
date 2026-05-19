package com.bbttvv.app.feature.plugin

import android.content.Context
import com.bbttvv.app.R
import com.bbttvv.app.core.coroutines.AppScope
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.plugin.Plugin
import com.bbttvv.app.core.plugin.PluginCapability
import com.bbttvv.app.core.plugin.PluginCapabilityManifest
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.plugin.PluginStore
import com.bbttvv.app.core.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetAddress

const val CDN_REGION_PLUGIN_ID = "cdn_region"
private const val CdnRegionPluginTag = "CdnRegionPlugin"

data class PlaybackCdnRewriteResult(
    val videoUrls: List<String>,
    val audioUrls: List<String>,
    val regionLabel: String?
)

interface PlaybackCdnPlugin : Plugin {
    fun rewritePlaybackCandidates(
        videoUrls: List<String>,
        audioUrls: List<String>
    ): PlaybackCdnRewriteResult
}

class CdnRegionPlugin : PlaybackCdnPlugin {
    override val id: String = CDN_REGION_PLUGIN_ID
    override val name: String = "CDN 属地优选"
    override val description: String = "按当前 IP 属地把同地区 B 站视频 CDN 排到播放候选前面。"
    override val version: String = "1.0.0"
    override val author: String = "BBTTVV"
    override val capabilityManifest: PluginCapabilityManifest = PluginCapabilityManifest(
        pluginId = id,
        displayName = name,
        version = version,
        apiVersion = 1,
        entryClassName = "com.bbttvv.app.feature.plugin.CdnRegionPlugin",
        capabilities = setOf(
            PluginCapability.PLAYBACK_CDN,
            PluginCapability.NETWORK,
            PluginCapability.PLUGIN_STORAGE
        )
    )

    @Volatile
    private var cache: CdnRegionPluginCache = CdnRegionPluginCache()

    @Volatile
    private var catalog: Map<String, List<String>> = emptyMap()

    private val _cacheState = MutableStateFlow(cache)
    val cacheState: StateFlow<CdnRegionPluginCache> = _cacheState.asStateFlow()

    override suspend fun onEnable() {
        val context = PluginManager.getContext()
        val loaded = withContext(Dispatchers.IO) {
            val loadedCatalog = loadCdnRegionCatalog(context)
            val savedCache = CdnRegionPluginStore.read(context)
            val storedCache = normalizeCachedCdnSelection(savedCache, loadedCatalog)
            if (storedCache != savedCache) {
                CdnRegionPluginStore.write(context, storedCache)
            }
            loadedCatalog to storedCache
        }
        catalog = loaded.first
        updateCache(loaded.second)
        AppScope.ioScope.launch {
            delay(1_500L)
            refreshIpLocationIfNeeded()
        }
        Logger.d(CdnRegionPluginTag, "CDN region plugin enabled, cached=${cache.selectedRegion.ifBlank { "none" }}")
    }

    override suspend fun onDisable() {
        Logger.d(CdnRegionPluginTag, "CDN region plugin disabled")
    }

    fun refreshNow() {
        AppScope.ioScope.launch {
            refreshIpLocationIfNeeded(forceRefresh = true)
        }
    }

    override fun rewritePlaybackCandidates(
        videoUrls: List<String>,
        audioUrls: List<String>
    ): PlaybackCdnRewriteResult {
        val snapshot = cache
        val hosts = resolveCdnRegionHosts(
            region = snapshot.selectedRegion,
            cachedHosts = snapshot.selectedHosts,
            catalog = catalog,
            isp = snapshot.location.isp
        )

        if (hosts.isEmpty()) {
            return PlaybackCdnRewriteResult(
                videoUrls = videoUrls.distinct(),
                audioUrls = audioUrls.distinct(),
                regionLabel = null
            )
        }

        return PlaybackCdnRewriteResult(
            videoUrls = rewriteCdnUrlCandidates(videoUrls, hosts).urls,
            audioUrls = rewriteCdnUrlCandidates(audioUrls, hosts).urls,
            regionLabel = snapshot.selectedRegion.takeIf { it.isNotBlank() }
        )
    }

    private suspend fun refreshIpLocationIfNeeded(forceRefresh: Boolean = false) {
        val context = PluginManager.getContext()
        val enabled = PluginStore.isEnabled(context, id)
        val loadedCatalog = catalog.ifEmpty {
            loadCdnRegionCatalog(context).also { catalog = it }
        }
        if (loadedCatalog.isEmpty()) return

        var current = CdnRegionPluginStore.read(context)
        val verifiedCurrent = normalizeCachedCdnSelection(current, loadedCatalog)
        if (verifiedCurrent != current) {
            current = verifiedCurrent
            CdnRegionPluginStore.write(context, current)
        }
        updateCache(current)
        val hasSelection = hasUsableCdnRegionSelection(
            region = current.selectedRegion,
            cachedHosts = current.selectedHosts,
            catalog = loadedCatalog
        )

        if (!forceRefresh && !shouldRefreshCdnIpLocation(
                enabled = enabled,
                nowMs = System.currentTimeMillis(),
                lastRefreshMs = current.refreshedAtMs,
                hasSelection = hasSelection
            )
        ) {
            val correctedHosts = filterResolvableCdnHosts(
                resolveCdnRegionHosts(
                    region = current.selectedRegion,
                    cachedHosts = current.selectedHosts,
                    catalog = loadedCatalog,
                    isp = current.location.isp
                )
            )
            if (current.selectedHosts != correctedHosts) {
                val corrected = current.copy(selectedHosts = correctedHosts)
                updateCache(corrected)
                CdnRegionPluginStore.write(context, corrected)
            }
            return
        }

        try {
            val response = NetworkModule.api.getIpZone()
            val data = response.data
            if (response.code != 0 || data == null) {
                error(response.message.ifBlank { "IP zone api code=${response.code}" })
            }

            val location = IpLocationSnapshot(
                addr = data.addr,
                country = data.country,
                province = data.province,
                city = data.city,
                isp = data.isp
            )
            val selection = selectCdnRegionForLocation(
                location = location,
                catalog = loadedCatalog,
                fallbackRegion = {
                    current.fallbackRegion.takeIf { it in loadedCatalog }
                        ?: loadedCatalog.keys.first()
                }
            )
            val verifiedHosts = filterResolvableCdnHosts(selection.hosts)
            val next = CdnRegionPluginCache(
                location = location,
                selectedRegion = selection.region.takeIf { verifiedHosts.isNotEmpty() }.orEmpty(),
                selectedHosts = verifiedHosts,
                fallbackRegion = if (selection.fallbackUsed && verifiedHosts.isNotEmpty()) {
                    selection.region
                } else {
                    current.fallbackRegion
                },
                fallbackUsed = selection.fallbackUsed,
                refreshedAtMs = System.currentTimeMillis(),
                lastError = if (verifiedHosts.isEmpty()) {
                    "CDN ${selection.region.ifBlank { "候选" }} 当前不可解析，已保留原始线路。"
                } else {
                    null
                }
            )
            updateCache(next)
            CdnRegionPluginStore.write(context, next)
            Logger.d(
                CdnRegionPluginTag,
                "CDN region refreshed: ${location.country}/${location.province}/${location.city} -> " +
                    "${selection.region}, isp=${location.isp.ifBlank { "unknown" }}, " +
                    "ip=${maskIpAddressForLog(location.addr)}, verifiedHosts=${verifiedHosts.size}/${selection.hosts.size}"
            )
        } catch (error: Exception) {
            val preserved = current.copy(lastError = error.message ?: error.javaClass.simpleName)
            updateCache(preserved)
            CdnRegionPluginStore.write(context, preserved)
            Logger.w(CdnRegionPluginTag, "CDN region refresh failed, preserving cache", error)
        }
    }

    private fun updateCache(next: CdnRegionPluginCache) {
        cache = next
        _cacheState.value = next
    }
}

private fun normalizeCachedCdnSelection(
    cache: CdnRegionPluginCache,
    catalog: Map<String, List<String>>
): CdnRegionPluginCache {
    if (cache.selectedRegion.isBlank() || cache.selectedHosts.isEmpty()) return cache
    val resolvedHosts = resolveCdnRegionHosts(
        region = cache.selectedRegion,
        cachedHosts = cache.selectedHosts,
        catalog = catalog,
        isp = cache.location.isp
    )
    val verifiedHosts = filterResolvableCdnHosts(resolvedHosts)
    if (verifiedHosts == cache.selectedHosts) return cache
    return cache.copy(
        selectedRegion = cache.selectedRegion.takeIf { verifiedHosts.isNotEmpty() }.orEmpty(),
        selectedHosts = verifiedHosts,
        lastError = if (verifiedHosts.isEmpty()) {
            "已移除不可解析的 CDN 候选，暂时保留原始线路。"
        } else {
            null
        }
    )
}

internal fun filterResolvableCdnHosts(
    hosts: List<String>,
    resolver: (String) -> Boolean = ::isCdnHostResolvable
): List<String> {
    return hosts
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .filter(resolver)
}

private fun isCdnHostResolvable(host: String): Boolean {
    return runCatching { InetAddress.getAllByName(host).isNotEmpty() }
        .getOrDefault(false)
}

@Serializable
data class CdnRegionPluginCache(
    val location: IpLocationSnapshot = IpLocationSnapshot(),
    val selectedRegion: String = "",
    val selectedHosts: List<String> = emptyList(),
    val fallbackRegion: String = "",
    val fallbackUsed: Boolean = false,
    val refreshedAtMs: Long = 0L,
    val lastError: String? = null
)

internal object CdnRegionPluginStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun read(context: Context): CdnRegionPluginCache {
        val raw = PluginStore.getConfigJson(context, CDN_REGION_PLUGIN_ID) ?: return CdnRegionPluginCache()
        return runCatching { json.decodeFromString<CdnRegionPluginCache>(raw) }
            .getOrDefault(CdnRegionPluginCache())
    }

    suspend fun write(context: Context, cache: CdnRegionPluginCache) {
        PluginStore.setConfigJson(
            context = context,
            pluginId = CDN_REGION_PLUGIN_ID,
            configJson = json.encodeToString(cache)
        )
    }
}

internal fun loadCdnRegionCatalog(context: Context): Map<String, List<String>> {
    return runCatching {
        context.resources.openRawResource(R.raw.cdn_region_catalog).bufferedReader().use { reader ->
            Json.decodeFromString<Map<String, List<String>>>(reader.readText())
        }
            .filterValues { hosts -> hosts.any { it.isNotBlank() } }
            .mapValues { (_, hosts) -> hosts.filter { it.isNotBlank() }.distinct() }
    }.getOrElse { error ->
        Logger.w(CdnRegionPluginTag, "Failed to read CDN region catalog", error)
        emptyMap()
    }
}
