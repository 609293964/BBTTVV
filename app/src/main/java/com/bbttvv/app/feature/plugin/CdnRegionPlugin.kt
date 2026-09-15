package com.bbttvv.app.feature.plugin

import android.content.Context
import android.os.SystemClock
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
import java.util.concurrent.TimeUnit
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

const val CDN_REGION_PLUGIN_ID = "cdn_region"
private const val CdnRegionPluginTag = "CdnRegionPlugin"
private const val CDN_PROBE_SAMPLE_BYTES = 32 * 1024
private const val CDN_PROBE_MAX_CANDIDATES = 5
private const val CDN_PROBE_TIMEOUT_SECONDS = 4L

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
    override val description: String = "按当前 IP 属地和当前会话的小流量检测结果排列 B 站视频 CDN。"
    override val version: String = "1.1.0"
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

    @Volatile
    private var activeProbeUrls: List<String> = emptyList()
    private val _probeState = MutableStateFlow(CdnProbeState())
    val probeState: StateFlow<CdnProbeState> = _probeState.asStateFlow()
    private val probeClient by lazy {
        NetworkModule.okHttpClient.newBuilder()
            .callTimeout(CDN_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

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
        activeProbeUrls = emptyList()
        _probeState.value = CdnProbeState()
        Logger.d(CdnRegionPluginTag, "CDN region plugin disabled")
    }

    fun refreshNow() {
        AppScope.ioScope.launch {
            refreshIpLocationIfNeeded(forceRefresh = true)
        }
    }

    fun probeCurrentSessionNow() {
        val candidates = activeProbeUrls.take(CDN_PROBE_MAX_CANDIDATES)
        if (candidates.isEmpty() || _probeState.value.isProbing) return
        AppScope.ioScope.launch {
            _probeState.value = _probeState.value.copy(isProbing = true, lastError = null)
            val results = candidates.mapNotNull(::probeCdnCandidate)
            _probeState.value = CdnProbeState(
                isProbing = false,
                results = results,
                measuredAtMs = System.currentTimeMillis(),
                lastError = if (results.isEmpty()) "当前播放会话没有可检测的 CDN 候选" else null,
            )
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
            captureActiveProbeCandidates(videoUrls + audioUrls)
            val diagnostics = _probeState.value.results
            return PlaybackCdnRewriteResult(
                videoUrls = rankCdnUrlsByProbeResult(videoUrls, diagnostics),
                audioUrls = rankCdnUrlsByProbeResult(audioUrls, diagnostics),
                regionLabel = null
            )
        }

        val rewrittenVideoUrls = rewriteCdnUrlCandidates(videoUrls, hosts).urls
        val rewrittenAudioUrls = rewriteCdnUrlCandidates(audioUrls, hosts).urls
        captureActiveProbeCandidates(rewrittenVideoUrls + rewrittenAudioUrls)
        val diagnostics = _probeState.value.results
        return PlaybackCdnRewriteResult(
            videoUrls = rankCdnUrlsByProbeResult(rewrittenVideoUrls, diagnostics),
            audioUrls = rankCdnUrlsByProbeResult(rewrittenAudioUrls, diagnostics),
            regionLabel = snapshot.selectedRegion.takeIf { it.isNotBlank() }
        )
    }

    private fun captureActiveProbeCandidates(urls: List<String>) {
        activeProbeUrls = urls
            .filter { it.toHttpUrlOrNull()?.isHttps == true }
            .distinctBy { it.toHttpUrlOrNull()?.host }
            .take(CDN_PROBE_MAX_CANDIDATES)
    }

    private fun probeCdnCandidate(url: String): CdnProbeResult? {
        val parsed = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
        val request = Request.Builder()
            .url(parsed)
            .header("Range", "bytes=0-${CDN_PROBE_SAMPLE_BYTES - 1}")
            .get()
            .build()
        val startedAtMs = SystemClock.elapsedRealtime()
        return runCatching {
            probeClient.newCall(request)
                .execute()
                .use { response ->
                    var sampledBytes = 0
                    val stream = response.body.byteStream()
                    val buffer = ByteArray(4 * 1024)
                    while (sampledBytes < CDN_PROBE_SAMPLE_BYTES) {
                        val read = stream.read(
                            buffer,
                            0,
                            minOf(buffer.size, CDN_PROBE_SAMPLE_BYTES - sampledBytes)
                        )
                        if (read <= 0) break
                        sampledBytes += read
                    }
                    CdnProbeResult(
                        host = parsed.host,
                        success = response.isSuccessful && sampledBytes > 0,
                        latencyMs = SystemClock.elapsedRealtime() - startedAtMs,
                        sampledBytes = sampledBytes,
                    )
                }
        }.getOrElse {
            CdnProbeResult(
                host = parsed.host,
                success = false,
                latencyMs = SystemClock.elapsedRealtime() - startedAtMs,
                sampledBytes = 0,
            )
        }
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
                catalog = loadedCatalog
            )
            val verifiedHosts = filterResolvableCdnHosts(selection.hosts)
            val next = CdnRegionPluginCache(
                location = location,
                selectedRegion = selection.region.takeIf { verifiedHosts.isNotEmpty() }.orEmpty(),
                selectedHosts = verifiedHosts,
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

data class CdnProbeResult(
    val host: String,
    val success: Boolean,
    val latencyMs: Long,
    val sampledBytes: Int,
)

data class CdnProbeState(
    val isProbing: Boolean = false,
    val results: List<CdnProbeResult> = emptyList(),
    val measuredAtMs: Long = 0L,
    val lastError: String? = null,
)

internal fun rankCdnUrlsByProbeResult(
    urls: List<String>,
    results: List<CdnProbeResult>,
): List<String> {
    val scores = results.associateBy { it.host.lowercase() }
    return urls.distinct().withIndex().sortedWith(
        compareBy<IndexedValue<String>>(
            { entry ->
                when (scores[entry.value.toHttpUrlOrNull()?.host?.lowercase()]?.success) {
                    true -> 0
                    null -> 1
                    false -> 2
                }
            },
            { entry -> scores[entry.value.toHttpUrlOrNull()?.host?.lowercase()]?.latencyMs ?: Long.MAX_VALUE },
            { entry -> entry.index },
        )
    ).map { it.value }
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
