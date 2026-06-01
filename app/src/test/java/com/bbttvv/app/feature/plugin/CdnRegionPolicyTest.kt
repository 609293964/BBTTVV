package com.bbttvv.app.feature.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class CdnRegionPolicyTest {
    @Test
    fun `city match selects local region`() {
        val selection = selectCdnRegionForLocation(
            location = IpLocationSnapshot(country = "中国", province = "广东省", city = "广州市"),
            catalog = mapOf(
                "广州" to listOf("gz.bilivideo.com"),
                "上海" to listOf("sh.bilivideo.com")
            )
        )

        assertEquals("广州", selection.region)
        assertEquals(listOf("gz.bilivideo.com"), selection.hosts)
        assertFalse(selection.fallbackUsed)
    }

    @Test
    fun `ip snapshot preserves public address and isp for internal cdn decisions`() {
        val snapshot = IpLocationSnapshot(
            addr = "36.40.120.145",
            country = "中国",
            province = "陕西",
            city = "渭南",
            isp = "电信"
        )

        assertEquals("36.40.120.145", snapshot.addr)
        assertEquals("电信", snapshot.isp)
    }

    @Test
    fun `city match wins before province alias`() {
        val selection = selectCdnRegionForLocation(
            location = IpLocationSnapshot(country = "中国", province = "广东省", city = "深圳市"),
            catalog = mapOf(
                "广州" to listOf("gz.bilivideo.com"),
                "深圳" to listOf("sz.bilivideo.com")
            )
        )

        assertEquals("深圳", selection.region)
        assertFalse(selection.fallbackUsed)
    }

    @Test
    fun `province aliases map to nearest catalog region`() {
        val catalog = mapOf(
            "西安" to listOf("xa.bilivideo.com"),
            "武汉" to listOf("wh.bilivideo.com"),
            "南京" to listOf("nj.bilivideo.com"),
            "哈市" to listOf("heb.bilivideo.com")
        )

        assertEquals(
            "西安",
            selectCdnRegionForLocation(
                location = IpLocationSnapshot(country = "中国", province = "陕西", city = "渭南"),
                catalog = catalog
            ).region
        )
        assertEquals(
            "武汉",
            selectCdnRegionForLocation(
                location = IpLocationSnapshot(country = "中国", province = "湖北省", city = ""),
                catalog = catalog
            ).region
        )
        assertEquals(
            "南京",
            selectCdnRegionForLocation(
                location = IpLocationSnapshot(country = "中国", province = "江苏", city = "苏州"),
                catalog = catalog
            ).region
        )
    }

    @Test
    fun `does not choose random fallback region when location has no catalog match`() {
        val selection = selectCdnRegionForLocation(
            location = IpLocationSnapshot(country = "中国", province = "青海", city = "西宁"),
            catalog = mapOf("广州" to listOf("gz.bilivideo.com"))
        )

        assertEquals("", selection.region)
        assertEquals(emptyList<String>(), selection.hosts)
        assertTrue(selection.fallbackUsed)
    }

    @Test
    fun `sorts selected region hosts by isp carrier before rewriting`() {
        val selection = selectCdnRegionForLocation(
            location = IpLocationSnapshot(country = "中国", province = "上海", city = "上海", isp = "中国移动"),
            catalog = mapOf(
                "上海" to listOf(
                    "cn-sh-ct-01-01.bilivideo.com",
                    "cn-sh-cu-01-01.bilivideo.com",
                    "cn-sh-cm-01-01.bilivideo.com",
                    "cn-sh-fx-01-01.bilivideo.com"
                )
            )
        )

        assertEquals("cn-sh-cm-01-01.bilivideo.com", selection.hosts.first())
    }

    @Test
    fun `overseas country selects overseas hosts`() {
        val selection = selectCdnRegionForLocation(
            location = IpLocationSnapshot(country = "美国", province = "", city = ""),
            catalog = mapOf(
                "广州" to listOf("gz.bilivideo.com"),
                "海外" to listOf("upos-hz-mirrorakam.akamaized.net")
            )
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
    fun `stale cached hosts fall back to current catalog hosts`() {
        val currentCatalogHosts = listOf(
            "upos-hz-mirrorakam.akamaized.net",
            "upos-sz-mirroraliov.bilivideo.com"
        )

        assertEquals(
            currentCatalogHosts,
            resolveCdnRegionHosts(
                region = "海外",
                cachedHosts = listOf("d1--ov-gotcha01.bilivideo.com"),
                catalog = mapOf("海外" to currentCatalogHosts)
            )
        )
        assertFalse(
            hasUsableCdnRegionSelection(
                region = "海外",
                cachedHosts = listOf("d1--ov-gotcha01.bilivideo.com"),
                catalog = mapOf("海外" to currentCatalogHosts)
            )
        )
    }

    @Test
    fun `bundled catalog uses provided overseas upos cdn hosts`() {
        val catalogFile = listOf(
            File("src/main/res/raw/cdn_region_catalog.json"),
            File("app/src/main/res/raw/cdn_region_catalog.json")
        ).first { it.exists() }
        val catalog = Json.decodeFromString<Map<String, List<String>>>(catalogFile.readText())

        assertEquals(
            listOf(
                "upos-hz-mirrorakam.akamaized.net",
                "upos-sz-mirroraliov.bilivideo.com",
                "upos-sz-mirrorcosov.bilivideo.com"
            ),
            catalog["海外"]
        )
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
