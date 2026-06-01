package com.bbttvv.app.core.network

import com.bbttvv.app.core.network.policy.HomeFeedAnonymizerRuntime
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSessionCookieJarTest {

    @Test
    fun `home feed anonymizer returns empty cookies before token warmup`() {
        HomeFeedAnonymizerRuntime.resetStats()
        HomeFeedAnonymizerRuntime.setEnabled(true)

        try {
            val cookies = AppSessionCookieJar().loadForRequest(
                "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd?ps=20".toHttpUrl()
            )

            assertTrue(cookies.isEmpty())
            assertEquals(1L, HomeFeedAnonymizerRuntime.statsSnapshot.totalHits)
        } finally {
            HomeFeedAnonymizerRuntime.setEnabled(false)
            HomeFeedAnonymizerRuntime.resetStats()
        }
    }
}
