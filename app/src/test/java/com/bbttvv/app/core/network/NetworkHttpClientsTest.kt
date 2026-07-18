package com.bbttvv.app.core.network

import java.io.IOException
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkHttpClientsTest {
    @Test
    fun spaceWbiRequestKeepsSpaceHeaders() {
        val url = "https://api.bilibili.com/x/space/wbi/arc/search?mid=123&pn=1".toHttpUrl()

        assertTrue(NetworkHttpClients.shouldAttachBilibiliReferer(url))
        assertEquals("https://space.bilibili.com/123", NetworkHttpClients.resolveBilibiliReferer(url))
        assertEquals("https://space.bilibili.com", NetworkHttpClients.resolveBilibiliOrigin(url))
    }

    @Test
    fun nonSpaceWbiRequestStillOmitsReferer() {
        val url = "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd?ps=20".toHttpUrl()

        assertFalse(NetworkHttpClients.shouldAttachBilibiliReferer(url))
        assertEquals("https://www.bilibili.com", NetworkHttpClients.resolveBilibiliOrigin(url))
    }

    @Test
    fun plainPlayerInfoFallbackKeepsVideoReferer() {
        val url = "https://api.bilibili.com/x/player/v2?bvid=BV1xx411c7mD&cid=123".toHttpUrl()

        assertTrue(NetworkHttpClients.shouldAttachBilibiliReferer(url))
        assertEquals(
            "https://www.bilibili.com/video/BV1xx411c7mD",
            NetworkHttpClients.resolveBilibiliReferer(url)
        )
        assertEquals("https://www.bilibili.com", NetworkHttpClients.resolveBilibiliOrigin(url))
    }

    @Test
    fun canceledRequestsAreNotReportedAsApiErrors() {
        assertFalse(NetworkHttpClients.shouldReportApiException(IOException("Canceled")))
        assertFalse(NetworkHttpClients.shouldReportApiException(IOException("canceled")))
    }

    @Test
    fun nonCancellationExceptionsAreStillReportedAsApiErrors() {
        assertTrue(NetworkHttpClients.shouldReportApiException(IOException("timeout")))
    }

    @Test
    fun imageClientDropsApiInterceptorCookiesAndHttpCache() {
        val apiCookieJar = object : CookieJar {
            override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
        }
        val apiClient = OkHttpClient.Builder()
            .cookieJar(apiCookieJar)
            .addInterceptor { chain -> chain.proceed(chain.request()) }
            .build()

        val imageClient = NetworkHttpClients.buildImageOkHttpClient(apiClient) { null }

        assertNull(imageClient.cache)
        assertSame(CookieJar.NO_COOKIES, imageClient.cookieJar)
        assertEquals(1, imageClient.interceptors.size)
        assertNotSame(apiClient.interceptors.single(), imageClient.interceptors.single())
    }
}
