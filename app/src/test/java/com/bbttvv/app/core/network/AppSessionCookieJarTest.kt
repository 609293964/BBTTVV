package com.bbttvv.app.core.network

import com.bbttvv.app.core.network.policy.HomeFeedAnonymizerRuntime
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSessionCookieJarTest {

    @Test
    fun `home feed anonymizer returns anonymous buvid before token warmup`() {
        HomeFeedAnonymizerRuntime.resetStats()
        HomeFeedAnonymizerRuntime.setEnabled(true)

        try {
            val cookies = AppSessionCookieJar().loadForRequest(
                "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd?ps=20".toHttpUrl()
            )

            assertEquals(listOf("buvid3"), cookies.map { it.name })
            assertTrue(cookies.single().value.endsWith("infoc"))
            assertEquals(1L, HomeFeedAnonymizerRuntime.statsSnapshot.totalHits)
        } finally {
            HomeFeedAnonymizerRuntime.setEnabled(false)
            HomeFeedAnonymizerRuntime.resetStats()
        }
    }

    @Test
    fun `account cookies are limited to bilibili registrable domain`() {
        assertTrue(AppSessionCookieJar.isBilibiliAccountCookieHost("bilibili.com"))
        assertTrue(AppSessionCookieJar.isBilibiliAccountCookieHost("api.bilibili.com"))
        assertTrue(AppSessionCookieJar.isBilibiliAccountCookieHost("www.bilibili.com."))

        assertFalse(AppSessionCookieJar.isBilibiliAccountCookieHost("bilivideo.com"))
        assertFalse(AppSessionCookieJar.isBilibiliAccountCookieHost("cdn.bilivideo.com"))
        assertFalse(AppSessionCookieJar.isBilibiliAccountCookieHost("evilbilibili.com"))
        assertFalse(AppSessionCookieJar.isBilibiliAccountCookieHost("bilibili.com.attacker.test"))
    }
}
