package com.bbttvv.app.core.network

import org.junit.Assert.*
import org.junit.Test

class RecommendationRequestPolicyTest {
    private val path = "/x/web-interface/wbi/index/top/feed/rcmd"
    @Test fun `only the exact web recommendation endpoint receives account cookies`() {
        assertTrue(recommendationCookieHeader("api.bilibili.com", path, false, "fixture", "fixture", "device").contains("SESSDATA="))
        listOf("app.bilibili.com", "api.bilibili.com.example", "cdn.example").forEach {
            assertEquals("", recommendationCookieHeader(it, path, false, "fixture", "fixture", "device"))
        }
        assertEquals("", recommendationCookieHeader("api.bilibili.com", "/x/feed/dislike", false, "fixture", "fixture", "device"))
    }
    @Test fun `anonymous requests retain device identity without account cookies`() {
        assertEquals("buvid3=device", recommendationCookieHeader("api.bilibili.com", path, true, "fixture", "fixture", "device"))
    }
    @Test fun `invalid cookie values cannot inject headers or additional cookies`() {
        assertEquals("", recommendationCookieHeader("api.bilibili.com", path, false, "bad;injected", "bad\r\n", ""))
    }
}
