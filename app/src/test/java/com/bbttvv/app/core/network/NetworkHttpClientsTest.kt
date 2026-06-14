package com.bbttvv.app.core.network

import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun canceledRequestsAreNotReportedAsApiErrors() {
        assertFalse(NetworkHttpClients.shouldReportApiException(IOException("Canceled")))
        assertFalse(NetworkHttpClients.shouldReportApiException(IOException("canceled")))
    }

    @Test
    fun nonCancellationExceptionsAreStillReportedAsApiErrors() {
        assertTrue(NetworkHttpClients.shouldReportApiException(IOException("timeout")))
    }
}
