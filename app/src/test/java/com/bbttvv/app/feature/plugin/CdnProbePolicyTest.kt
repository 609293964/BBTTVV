package com.bbttvv.app.feature.plugin

import org.junit.Assert.assertEquals
import org.junit.Test
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class CdnProbePolicyTest {
    @Test
    fun `successful low latency hosts lead while unknown order stays stable`() {
        val urls = listOf(
            "https://unknown.example/video?token=secret",
            "https://slow.example/video?token=secret",
            "https://fast.example/video?token=secret",
        )
        val ranked = rankCdnUrlsByProbeResult(
            urls = urls,
            results = listOf(
                CdnProbeResult("slow.example", true, 80L, 1024),
                CdnProbeResult("fast.example", true, 20L, 1024),
            )
        )

        assertEquals(listOf("fast.example", "slow.example", "unknown.example"), ranked.map { it.toHttpUrlOrNull()!!.host })
    }

    @Test
    fun `failed result does not outrank an unmeasured candidate`() {
        val urls = listOf("https://unknown.example/v", "https://failed.example/v")
        val ranked = rankCdnUrlsByProbeResult(
            urls,
            listOf(CdnProbeResult("failed.example", false, 10L, 0))
        )

        assertEquals(urls, ranked)
    }
}
