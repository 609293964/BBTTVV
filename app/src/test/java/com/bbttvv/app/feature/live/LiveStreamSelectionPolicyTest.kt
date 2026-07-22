package com.bbttvv.app.feature.live

import com.bbttvv.app.core.store.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveStreamSelectionPolicyTest {
    @Test
    fun `standard mode selects highest scoring compatible preferred cdn candidate`() {
        val preferred = candidate(
            key = "preferred",
            lineKey = "bilivideo",
            protocol = "http_hls",
            format = "ts",
            codec = "avc",
            host = "cn-gotcha.bilivideo.com",
        )
        val aggressive = candidate(
            key = "aggressive",
            lineKey = "mcdn",
            protocol = "http_stream",
            format = "fmp4",
            codec = "hevc",
            host = "live.mcdn.example",
        )

        val selected = selectLiveStreamCandidate(
            candidates = listOf(aggressive, preferred),
            preferredLineKey = null,
            preferHighBitrate = false,
            cdnPreference = SettingsManager.PlayerCdnPreference.BILIVIDEO,
        )

        assertEquals("preferred", selected?.key)
    }

    @Test
    fun `higher current quality wins before route tie breakers`() {
        val low = candidate(key = "low", qn = 150, host = "cn-low.bilivideo.com")
        val high = candidate(key = "high", qn = 10_000, host = "other.example")

        val selected = selectLiveStreamCandidate(
            candidates = listOf(low, high),
            preferredLineKey = null,
            preferHighBitrate = false,
            cdnPreference = SettingsManager.PlayerCdnPreference.BILIVIDEO,
        )

        assertEquals("high", selected?.key)
    }

    @Test
    fun `recovery stays on current line before leaving it`() {
        val failed = candidate(key = "failed", lineKey = "line-a")
        val sameLine = candidate(key = "same", lineKey = "line-a", format = "fmp4")
        val otherLine = candidate(key = "other", lineKey = "line-b", format = "ts")

        val selected = selectLiveRecoveryCandidate(
            candidates = listOf(failed, otherLine, sameLine),
            failedCandidateKeys = setOf("failed"),
            currentLineKey = "line-a",
            preferHighBitrate = false,
            cdnPreference = SettingsManager.PlayerCdnPreference.BILIVIDEO,
            preferCompatibleCodec = false,
        )

        assertEquals("same", selected?.key)
    }

    @Test
    fun `decoder recovery prefers avc and never retries failed keys`() {
        val failed = candidate(key = "failed", codec = "hevc")
        val hevc = candidate(key = "hevc", codec = "hevc")
        val avc = candidate(key = "avc", codec = "avc")

        val selected = selectLiveRecoveryCandidate(
            candidates = listOf(failed, hevc, avc),
            failedCandidateKeys = setOf("failed"),
            currentLineKey = null,
            preferHighBitrate = true,
            cdnPreference = SettingsManager.PlayerCdnPreference.MCDN,
            preferCompatibleCodec = true,
        )

        assertEquals("avc", selected?.key)
        assertNull(
            selectLiveRecoveryCandidate(
                candidates = listOf(failed),
                failedCandidateKeys = setOf("failed"),
                currentLineKey = null,
                preferHighBitrate = false,
                cdnPreference = SettingsManager.PlayerCdnPreference.BILIVIDEO,
                preferCompatibleCodec = false,
            )
        )
    }

    @Test
    fun `selected candidate quality is the displayed quality`() {
        assertEquals(
            150,
            resolveEffectiveLiveQuality(candidate(key = "low", qn = 150), responseQuality = 10_000),
        )
    }

    private fun candidate(
        key: String,
        lineKey: String = "line",
        protocol: String = "http_hls",
        format: String = "ts",
        codec: String = "avc",
        host: String = "cn-live.bilivideo.com",
        qn: Int = 10_000,
    ): LiveStreamCandidate {
        return LiveStreamCandidate(
            key = key,
            lineKey = lineKey,
            lineBaseLabel = lineKey,
            lineSubtitle = host,
            protocolName = protocol,
            protocolLabel = protocol,
            formatName = format,
            formatLabel = format,
            codecName = codec,
            codecLabel = codec,
            currentQn = qn,
            host = "https://$host",
            hostLabel = host,
            url = "https://$host/$key",
        )
    }
}
