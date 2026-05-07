package com.bbttvv.app.feature.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CdnRegionPolicyTest {
    @Test
    fun `city match selects local region`() {
        val selection = selectCdnRegionForLocation(
            location = IpLocationSnapshot(country = "中国", province = "广东省", city = "广州市"),
            catalog = mapOf(
                "广州" to listOf("gz.bilivideo.com"),
                "上海" to listOf("sh.bilivideo.com")
            ),
            fallbackRegion = { "上海" }
        )

        assertEquals("广州", selection.region)
        assertEquals(listOf("gz.bilivideo.com"), selection.hosts)
        assertFalse(selection.fallbackUsed)
    }

    @Test
    fun `overseas country selects overseas hosts`() {
        val selection = selectCdnRegionForLocation(
            location = IpLocationSnapshot(country = "美国", province = "", city = ""),
            catalog = mapOf(
                "广州" to listOf("gz.bilivideo.com"),
                "海外" to listOf("upos-hz-mirrorakam.akamaized.net")
            ),
            fallbackRegion = { "广州" }
        )

        assertEquals("海外", selection.region)
        assertEquals(listOf("upos-hz-mirrorakam.akamaized.net"), selection.hosts)
        assertFalse(selection.fallbackUsed)
    }

    @Test
    fun `rewrite keeps original urls as fallback`() {
        val original = listOf(
            "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/video.m4s?deadline=1",
            "https://example.com/fallback.m4s"
        )
        val result = rewriteCdnUrlCandidates(
            originalUrls = original,
            preferredHosts = listOf("cn-gdgz-fx-01-01.bilivideo.com")
        )

        assertEquals(
            "https://cn-gdgz-fx-01-01.bilivideo.com/upgcxcode/video.m4s?deadline=1",
            result.urls.first()
        )
        assertTrue(result.urls.containsAll(original))
        assertEquals(1, result.rewrittenCount)
    }

    @Test
    fun `empty verified cache does not fall back to raw catalog hosts`() {
        val hosts = resolveCdnRegionHosts(
            region = "广州",
            cachedHosts = emptyList(),
            catalog = mapOf("广州" to listOf("cn-gdgz-cm-01-02.bilivideo.com"))
        )

        assertTrue(hosts.isEmpty())
    }

    @Test
    fun `host verification drops unresolved candidates before playback rewrite`() {
        val hosts = filterResolvableCdnHosts(
            hosts = listOf(
                "cn-gdgz-cm-01-02.bilivideo.com",
                "cn-gdgz-fx-01-01.bilivideo.com",
                "cn-gdgz-fx-01-01.bilivideo.com"
            ),
            resolver = { it == "cn-gdgz-fx-01-01.bilivideo.com" }
        )

        assertEquals(listOf("cn-gdgz-fx-01-01.bilivideo.com"), hosts)
    }

    @Test
    fun `refresh policy honors disabled state and ttl`() {
        assertFalse(
            shouldRefreshCdnIpLocation(
                enabled = false,
                nowMs = 2_000L,
                lastRefreshMs = 0L,
                hasSelection = false
            )
        )
        assertTrue(
            shouldRefreshCdnIpLocation(
                enabled = true,
                nowMs = CDN_REGION_LOCATION_TTL_MS + 10L,
                lastRefreshMs = 1L,
                hasSelection = true
            )
        )
    }
}
