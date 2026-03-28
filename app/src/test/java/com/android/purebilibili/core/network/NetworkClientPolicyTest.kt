package com.android.purebilibili.core.network

import okhttp3.Protocol
import okhttp3.OkHttpClient
import java.net.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkClientPolicyTest {

    @Test
    fun sharedClient_allowsHttpTwoForMultiplexing() {
        assertTrue(
            NetworkModule.okHttpClient.protocols.contains(Protocol.HTTP_2),
            "Expected shared client to support HTTP/2, actual=${NetworkModule.okHttpClient.protocols}"
        )
    }

    @Test
    fun sharedClient_expandsHttpCacheBudget() {
        val cache = NetworkModule.okHttpClient.cache
        val expectedBudget = 32L * 1024 * 1024

        requireNotNull(cache) { "Expected shared client to expose an HTTP cache" }
        assertEquals(expectedBudget, cache.maxSize())
    }

    @Test
    fun playbackClient_bypassesSystemProxyButKeepsProtocols() {
        val sharedClient = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()

        val playbackClient = NetworkModule.buildPlaybackOkHttpClient(sharedClient)

        assertEquals(Proxy.NO_PROXY, playbackClient.proxy)
        assertEquals(sharedClient.protocols, playbackClient.protocols)
    }
}
