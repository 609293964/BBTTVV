package com.bbttvv.app.core.network

import com.bbttvv.app.core.util.Logger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class NetworkWarmupResult(
    val dnsResolvedCount: Int,
    val httpPreconnectCount: Int,
    val imagePreconnectCount: Int
)

internal object NetworkWarmup {
    private const val TAG = "NetworkWarmup"
    private const val CALL_TIMEOUT_MS = 900L

    private val dnsWarmupHosts = listOf(
        "api.bilibili.com",
        "app.bilibili.com",
        "www.bilibili.com",
        "api.live.bilibili.com",
        "i0.hdslb.com",
        "i1.hdslb.com",
        "i2.hdslb.com"
    )

    private val httpPreconnectUrls = listOf(
        "https://www.bilibili.com/"
    )

    private val imagePreconnectUrls = listOf(
        "https://i0.hdslb.com/"
    )

    suspend fun warmupConnections(): NetworkWarmupResult = withContext(Dispatchers.IO) {
        val client = NetworkModule.okHttpClient
        val dnsResolved = dnsWarmupHosts.count { host -> resolveDns(client, host) }
        val httpConnected = httpPreconnectUrls.count { url -> preconnect(client, url) }
        val imageConnected = imagePreconnectUrls.count { url -> preconnect(client, url) }
        NetworkWarmupResult(
            dnsResolvedCount = dnsResolved,
            httpPreconnectCount = httpConnected,
            imagePreconnectCount = imageConnected
        )
    }

    private fun resolveDns(client: OkHttpClient, host: String): Boolean {
        return runCatching {
            val addresses = client.dns.lookup(host)
            Logger.d(TAG, "dns warmup host=$host count=${addresses.size}")
            addresses.isNotEmpty()
        }.getOrElse { error ->
            Logger.w(TAG, "dns warmup failed host=$host error=${error.message}")
            false
        }
    }

    private fun preconnect(client: OkHttpClient, url: String): Boolean {
        val request = Request.Builder()
            .url(url)
            .head()
            .build()
        return runCatching {
            val call = client.newCall(request)
            call.timeout().timeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            call.execute().use { response ->
                Logger.d(TAG, "preconnect url=$url code=${response.code}")
                true
            }
        }.getOrElse { error ->
            Logger.w(TAG, "preconnect failed url=$url error=${error.message}")
            false
        }
    }
}
