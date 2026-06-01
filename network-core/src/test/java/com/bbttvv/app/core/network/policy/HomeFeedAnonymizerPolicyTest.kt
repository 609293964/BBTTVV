package com.bbttvv.app.core.network.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeFeedAnonymizerPolicyTest {

    @Test
    fun `disabled plugin keeps home feed cookies`() {
        assertFalse(
            shouldClearHomeFeedCookies(
                pluginEnabled = false,
                host = "api.bilibili.com",
                encodedPath = "/x/web-interface/wbi/index/top/feed/rcmd"
            )
        )
    }

    @Test
    fun `enabled plugin clears web home feed cookies`() {
        assertTrue(
            shouldClearHomeFeedCookies(
                pluginEnabled = true,
                host = "api.bilibili.com",
                encodedPath = "/x/web-interface/wbi/index/top/feed/rcmd"
            )
        )
    }

    @Test
    fun `enabled plugin clears entire cookie header for web home feed`() {
        val cookieNames = listOf(
            "buvid3",
            "SESSDATA",
            "bili_jct",
            "DedeUserID"
        )

        assertEquals(
            emptyList(),
            filterHomeFeedCookieHeaderNames(
                pluginEnabled = true,
                host = "api.bilibili.com",
                encodedPath = "/x/web-interface/wbi/index/top/feed/rcmd",
                cookieNames = cookieNames
            )
        )
    }

    @Test
    fun `enabled plugin keeps cookies for playback dynamic and mobile feed`() {
        val requests = listOf(
            "api.bilibili.com" to "/x/player/wbi/playurl",
            "api.bilibili.com" to "/x/polymer/web-dynamic/v1/feed/all",
            "api.bilibili.com" to "/x/web-interface/nav",
            "app.bilibili.com" to "/x/v2/feed/index"
        )

        requests.forEach { (host, encodedPath) ->
            assertFalse(
                shouldClearHomeFeedCookies(
                    pluginEnabled = true,
                    host = host,
                    encodedPath = encodedPath
                )
            )
        }
    }

    @Test
    fun `enabled plugin keeps cookies for non api host and similar path`() {
        val requests = listOf(
            "www.bilibili.com" to "/x/web-interface/wbi/index/top/feed/rcmd",
            "api.bilibili.com" to "/x/web-interface/wbi/index/top/feed/rcmd_extra",
            "api.bilibili.com" to "/x/web-interface/wbi/index/top/feed"
        )

        requests.forEach { (host, encodedPath) ->
            assertFalse(
                shouldClearHomeFeedCookies(
                    pluginEnabled = true,
                    host = host,
                    encodedPath = encodedPath
                )
            )
        }
    }

    @Test
    fun `matching web home feed request records anonymizer stats`() {
        HomeFeedAnonymizerRuntime.resetStats()

        val shouldClear = resolveHomeFeedCookieAnonymizerDecision(
            pluginEnabled = true,
            host = "api.bilibili.com",
            encodedPath = "/x/web-interface/wbi/index/top/feed/rcmd",
            nowMs = 1_700_000_000_000L
        )
        val snapshot = HomeFeedAnonymizerRuntime.statsSnapshot

        assertTrue(shouldClear)
        assertEquals(1L, snapshot.totalHits)
        assertEquals(1_700_000_000_000L, snapshot.lastHitAtMs)
        assertEquals("api.bilibili.com", snapshot.lastHitHost)
        assertEquals("/x/web-interface/wbi/index/top/feed/rcmd", snapshot.lastHitEncodedPath)
    }

    @Test
    fun `non matching request does not record anonymizer stats`() {
        HomeFeedAnonymizerRuntime.resetStats()

        val shouldClear = resolveHomeFeedCookieAnonymizerDecision(
            pluginEnabled = true,
            host = "api.bilibili.com",
            encodedPath = "/x/web-interface/nav",
            nowMs = 1_700_000_000_000L
        )

        assertFalse(shouldClear)
        assertEquals(0L, HomeFeedAnonymizerRuntime.statsSnapshot.totalHits)
        assertNull(HomeFeedAnonymizerRuntime.statsSnapshot.lastHitAtMs)
    }
}
