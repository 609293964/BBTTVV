package com.bbttvv.app.core.network

import android.content.Context
import com.bbttvv.app.core.network.policy.resolveHardcodedDnsFallback
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.util.CrashReporter
import com.bbttvv.app.core.util.Logger
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol

internal object NetworkHttpClients {
    fun resolveSharedNetworkProtocols(): List<Protocol> {
        return listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
    }

    fun resolveApiHttpCacheBudgetBytes(): Long {
        return 32L * 1024 * 1024
    }

    fun buildApiOkHttpClient(
        appContext: () -> Context?,
        appSessionCookieJar: AppSessionCookieJar,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .protocols(resolveSharedNetworkProtocols())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .cache(
                Cache(
                    directory = File(appContext()?.cacheDir ?: File("/tmp"), "okhttp_cache"),
                    maxSize = resolveApiHttpCacheBudgetBytes()
                )
            )
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = 10,
                    keepAliveDuration = 5,
                    timeUnit = TimeUnit.MINUTES
                )
            )
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)

        return builder
            .dns(buildApiDns(appContext))
            .cookieJar(appSessionCookieJar)
            .addInterceptor { chain ->
                TokenManager.awaitWarmupBlocking(appContext())
                val original = chain.request()
                val url = original.url
                val referer = resolveBilibiliReferer(url)
                val origin = resolveBilibiliOrigin(url)
                val shouldAttachReferer = shouldAttachBilibiliReferer(url)

                val requestBuilder = original.newBuilder()
                    .header("User-Agent", resolveAppUserAgent(appContext()))
                    .header("Origin", origin)

                if (shouldAttachReferer) {
                    requestBuilder.header("Referer", referer)
                }

                Logger.d(
                    "ApiClient",
                    " Sending request to ${original.url}, Referer: " +
                        "${if (shouldAttachReferer) referer else "OMITTED (WBI)"}, " +
                        "hasSess=${!TokenManager.sessDataCache.isNullOrEmpty()}, " +
                        "hasCsrf=${!TokenManager.csrfCache.isNullOrEmpty()}"
                )

                proceedWithApiErrorReporting(
                    request = requestBuilder.build(),
                    proceed = chain::proceed,
                    endpointPrefix = "",
                )
            }
            .build()
    }

    fun buildImageOkHttpClient(
        sharedClient: OkHttpClient,
        appContext: () -> Context?,
    ): OkHttpClient {
        val builder = sharedClient.newBuilder()
            // Coil owns the image disk cache. Keeping the API HTTP cache here would
            // store the same image bytes twice and let image traffic evict API data.
            .cache(null)
            .cookieJar(CookieJar.NO_COOKIES)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        // The shared client reports API failures and waits for account warmup. Image
        // requests do not need either behavior, especially while a TV is offline.
        builder.interceptors().clear()
        builder.networkInterceptors().clear()

        return builder
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url
                val requestBuilder = original.newBuilder()
                    .header("User-Agent", resolveAppUserAgent(appContext()))
                    .header("Origin", resolveBilibiliOrigin(url))

                if (shouldAttachBilibiliReferer(url)) {
                    requestBuilder.header("Referer", resolveBilibiliReferer(url))
                }

                chain.proceed(requestBuilder.build())
            }
            .build()
    }

    fun buildPlaybackOkHttpClient(sharedClient: OkHttpClient): OkHttpClient {
        return sharedClient.newBuilder()
            .proxy(Proxy.NO_PROXY)
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = 2,
                    keepAliveDuration = 15,
                    timeUnit = TimeUnit.SECONDS
                )
            )
            .addInterceptor { chain ->
                val original = chain.request()
                val request = if (isPlaybackMediaHost(original.url.host)) {
                    original.newBuilder()
                        .removeHeader("Origin")
                        .build()
                } else {
                    original
                }
                chain.proceed(request)
            }
            .build()
    }

    fun buildGuestOkHttpClient(appContext: () -> Context?): OkHttpClient {
        return OkHttpClient.Builder()
            .protocols(resolveSharedNetworkProtocols())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .dns(
                object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        val ipv4OnlyEnabled = isIpv4OnlyEnabled(appContext())
                        val addresses = Dns.SYSTEM.lookup(hostname)
                        return filterPreferredInetAddresses(addresses, ipv4OnlyEnabled)
                    }
                }
            )
            .cookieJar(GuestCookieJar())
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url
                val referer = resolveBilibiliReferer(url)
                val origin = resolveBilibiliOrigin(url)
                val shouldAttachReferer = shouldAttachBilibiliReferer(url)
                val requestBuilder = original.newBuilder()
                    .header("User-Agent", resolveAppUserAgent(appContext()))
                    .header("Origin", origin)

                if (shouldAttachReferer) {
                    requestBuilder.header("Referer", referer)
                }

                proceedWithApiErrorReporting(
                    request = requestBuilder.build(),
                    proceed = chain::proceed,
                    endpointPrefix = "guest ",
                )
            }
            .build()
    }

    internal fun resolveBilibiliReferer(url: HttpUrl): String {
        var referer = "https://www.bilibili.com"

        val bvid = url.queryParameter("bvid")
        if (!bvid.isNullOrEmpty()) {
            referer = "https://www.bilibili.com/video/$bvid"
        }

        val mid = url.queryParameter("mid") ?: url.queryParameter("vmid")
        if (isSpaceEndpoint(url) && !mid.isNullOrEmpty()) {
            referer = "https://space.bilibili.com/$mid"
        }

        if (url.host == "api.live.bilibili.com") {
            val roomId = url.queryParameter("room_id") ?: url.queryParameter("id")
            referer = if (!roomId.isNullOrEmpty()) {
                "https://live.bilibili.com/$roomId"
            } else {
                "https://live.bilibili.com"
            }
        }

        if (url.encodedPath.contains("/dm/list.so") || url.encodedPath.contains("/x/v1/dm/")) {
            referer = "https://www.bilibili.com/video/"
        }

        if (isDynamicEndpoint(url)) {
            referer = "https://t.bilibili.com/"
        }

        return referer
    }

    internal fun resolveBilibiliOrigin(url: HttpUrl): String {
        return when {
            url.host == "api.live.bilibili.com" -> "https://live.bilibili.com"
            isDynamicEndpoint(url) -> "https://t.bilibili.com"
            isSpaceEndpoint(url) -> "https://space.bilibili.com"
            else -> "https://www.bilibili.com"
        }
    }

    internal fun shouldAttachBilibiliReferer(url: HttpUrl): Boolean {
        val isWbiEndpoint = url.encodedPath.contains("/wbi/")
        return !isWbiEndpoint || isSpaceEndpoint(url)
    }

    private fun buildApiDns(appContext: () -> Context?): Dns {
        return object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val ipv4OnlyEnabled = isIpv4OnlyEnabled(appContext())
                return try {
                    val addresses = InetAddress.getAllByName(hostname).toList()
                    val filtered = filterPreferredInetAddresses(addresses, ipv4OnlyEnabled)
                    Logger.d("ApiClient", "DNS resolved: $hostname -> $filtered (ipv4Only=$ipv4OnlyEnabled)")
                    filtered
                } catch (error: Exception) {
                    Logger.e("ApiClient", "DNS failed for $hostname: ${error.message}")
                    val fallback = resolveHardcodedDnsFallback(
                        hostname = hostname,
                        allowHardcodedIpFallback = false
                    )
                    if (fallback != null) {
                        val fallbackAddress = InetAddress.getByName(fallback.ipAddress)
                        val filteredFallback = filterPreferredInetAddresses(
                            listOf(fallbackAddress),
                            ipv4OnlyEnabled
                        )
                        Logger.w(
                            "ApiClient",
                            "⚠️ Using Hardcoded IP for ${fallback.description}: ${fallback.ipAddress}"
                        )
                        filteredFallback
                    } else {
                        throw error
                    }
                }
            }
        }
    }

    internal fun shouldReportApiException(error: Exception): Boolean {
        return !isNormalRequestCancellation(error)
    }

    private fun proceedWithApiErrorReporting(
        request: okhttp3.Request,
        proceed: (okhttp3.Request) -> okhttp3.Response,
        endpointPrefix: String,
    ): okhttp3.Response {
        return try {
            val response = proceed(request)
            if (response.code >= 500 || response.code == 429 || response.code == 412) {
                CrashReporter.reportApiError(
                    endpoint = "$endpointPrefix${request.method} ${request.url.encodedPath}",
                    httpCode = response.code,
                    errorMessage = "HTTP ${response.code}"
                )
            }
            response
        } catch (error: Exception) {
            if (shouldReportApiException(error)) {
                CrashReporter.reportApiError(
                    endpoint = "$endpointPrefix${request.method} ${request.url.encodedPath}",
                    httpCode = -1,
                    errorMessage = error.message ?: error.javaClass.simpleName
                )
            }
            throw error
        }
    }

    private fun isNormalRequestCancellation(error: Exception): Boolean {
        if (error is IOException && error.message.equals("Canceled", ignoreCase = true)) {
            return true
        }
        return error.cause?.let { cause ->
            cause is IOException && cause.message.equals("Canceled", ignoreCase = true)
        } == true
    }

    private fun isPlaybackMediaHost(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized.endsWith("hdslb.com") ||
            normalized.contains("bilivideo.com") ||
            normalized.contains("bilivideo.cn") ||
            normalized.contains("mcdn.bilivideo")
    }

    private fun isSpaceEndpoint(url: HttpUrl): Boolean {
        return url.encodedPath.contains("/x/space/")
    }

    private fun isDynamicEndpoint(url: HttpUrl): Boolean {
        return url.encodedPath.contains("/x/polymer/web-dynamic/") ||
            url.encodedPath.contains("/x/dynamic/")
    }
}
