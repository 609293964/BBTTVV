package com.bbttvv.app.feature.live

import org.junit.Assert.*
import org.junit.Test

class LiveHdrPolicyTest {
    private fun candidate(hdr: Int = 0, eotf: Int = 0) = LiveStreamCandidate(
        key = "$hdr-$eotf", lineKey = "cdn", lineBaseLabel = "cdn", lineSubtitle = null,
        protocolName = "http_hls", protocolLabel = "HLS", formatName = "fmp4", formatLabel = "fmp4",
        codecName = "hevc", codecLabel = "HEVC", currentQn = 10000,
        host = "https://cdn.example", hostLabel = "cdn.example", url = "https://cdn.example/live.m3u8",
        hdrType = hdr, eotf = eotf, acceptedQns = listOf(150, 10000),
    )

    @Test fun `same qn has distinct SDR HDR10 and HLG keys and decodes for requests`() {
        val keys = listOf(liveQualityKey(10000), liveQualityKey(10000, 1, 1), liveQualityKey(10000, 1, 2))
        assertEquals(3, keys.toSet().size)
        keys.forEach { assertEquals(10000, liveQualityQn(it)) }
        assertEquals(1, liveQualityHdrType(keys[1]))
        assertEquals(2, liveQualityEotf(keys[2]))
    }

    @Test fun `SDR never selects HDR and missing HDR falls back only to SDR`() {
        val sdr = candidate()
        val hdr = candidate(1, 1)
        assertEquals(listOf(sdr), scopeLiveVariants(listOf(hdr, sdr), 10000))
        assertEquals(listOf(hdr), scopeLiveVariants(listOf(sdr, hdr), liveQualityKey(10000, 1, 1)))
        assertEquals(listOf(sdr), scopeLiveVariants(listOf(sdr), liveQualityKey(10000, 1, 2)))
        assertTrue(scopeLiveVariants(listOf(hdr), 10000).isEmpty())
    }

    @Test fun `HDR requires capability and supported container without private HEVC FLV`() {
        assertTrue(isPlayableLiveVariant("fmp4", "hevc", 1, 1, true))
        assertTrue(isPlayableLiveVariant("ts", "hevc", 1, 2, true))
        assertFalse(isPlayableLiveVariant("fmp4", "hevc", 1, 1, false))
        assertFalse(isPlayableLiveVariant("flv", "hevc", 1, 1, true))
        assertFalse(isPlayableLiveVariant("ts", "hevc", 1, 1, true, "http_stream"))
        assertFalse(isPlayableLiveVariant("fmp4", "hevc", 1, 0, true))
        assertTrue(isPlayableLiveVariant("flv", "avc", 0, 0, false))
    }

    @Test fun `menus use only playable carriers and selected quality reports actual variant`() {
        val sdr = candidate()
        val hdr = candidate(1, 1)
        val options = buildLiveQualityOptions(listOf(sdr, hdr, hdr), mapOf(10000 to "原画"))
        assertEquals(4, options.size)
        assertEquals(2, options.count { liveQualityQn(it.qn) == 10000 })
        assertTrue(options.any { it.label == "原画 HDR10" })
        assertEquals(liveQualityKey(10000, 1, 1), resolveEffectiveLiveQuality(hdr, 150))
        assertTrue(buildLiveQualityOptions(emptyList(), mapOf(30000 to "HDR")).isEmpty())
    }

    @Test fun `recovery never promotes SDR to HDR and exhausted HDR recovers to SDR`() {
        val sdr = candidate()
        val hdr = candidate(1, 1)
        fun recover(failed: Set<String>, currentKey: Int) = selectLiveRecoveryCandidate(
            candidates = listOf(hdr, sdr), failedCandidateKeys = failed, currentLineKey = null,
            preferHighBitrate = true,
            cdnPreference = com.bbttvv.app.core.store.SettingsManager.PlayerCdnPreference.BILIVIDEO,
            preferCompatibleCodec = false, currentQualityKey = currentKey,
        )
        assertNull(recover(setOf(sdr.key), 10000))
        assertEquals(sdr, recover(setOf(hdr.key), liveQualityKey(10000, 1, 1)))
    }

    @Test fun `HDR prefers HLS fmp4 even in high bitrate mode`() {
        val fmp4 = candidate(1, 1)
        val ts = fmp4.copy(key = "ts", formatName = "ts")
        val stream = fmp4.copy(key = "stream", protocolName = "http_stream")
        val ranked = rankLiveStreamCandidates(listOf(ts, stream, fmp4), true,
            com.bbttvv.app.core.store.SettingsManager.PlayerCdnPreference.BILIVIDEO)
        assertEquals(listOf(fmp4, ts, stream), ranked)
    }
}
