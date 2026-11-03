package com.bbttvv.app.feature.video.usecase

import com.bbttvv.app.core.store.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackUrlCandidatesTest {
    @Test
    fun bilivideoPreferenceKeepsNonMcdnBilivideoFirst() {
        val ordered = PlaybackUrlCandidates.orderedPlaybackUrls(
            preference = SettingsManager.PlayerCdnPreference.BILIVIDEO,
            primaryUrl = "https://upos-sz-mirrorcos.bilivideo.com/video.m4s",
            backupUrls = listOf(
                "https://upos-sz-mirrorcosmcdn.mcdn.bilivideo.com/video.m4s",
                "https://example.com/video.m4s",
            ),
        )

        assertEquals(
            listOf(
                "https://upos-sz-mirrorcos.bilivideo.com/video.m4s",
                "https://example.com/video.m4s",
                "https://upos-sz-mirrorcosmcdn.mcdn.bilivideo.com/video.m4s",
            ),
            ordered,
        )
    }

    @Test
    fun mcdnPreferenceMovesMcdnCandidateFirstWithoutDroppingFallbacks() {
        val ordered = PlaybackUrlCandidates.orderedPlaybackUrls(
            preference = SettingsManager.PlayerCdnPreference.MCDN,
            primaryUrl = "https://upos-sz-mirrorcos.bilivideo.com/video.m4s",
            backupUrls = listOf(
                "https://upos-sz-mirrorcosmcdn.mcdn.bilivideo.com/video.m4s",
                "https://example.com/video.m4s",
            ),
        )

        assertEquals(
            listOf(
                "https://upos-sz-mirrorcosmcdn.mcdn.bilivideo.com/video.m4s",
                "https://upos-sz-mirrorcos.bilivideo.com/video.m4s",
                "https://example.com/video.m4s",
            ),
            ordered,
        )
    }

    @Test
    fun orderedPlaybackUrlsDeduplicatesAndDropsBlankValues() {
        val ordered = PlaybackUrlCandidates.orderedPlaybackUrls(
            preference = SettingsManager.PlayerCdnPreference.BILIVIDEO,
            primaryUrl = " https://bad.example/kept-because-not-blank ",
            backupUrls = listOf(
                "",
                "https://example.com/fallback.m4s",
                "https://example.com/fallback.m4s",
            ),
        )

        assertEquals(
            listOf(
                "https://bad.example/kept-because-not-blank",
                "https://example.com/fallback.m4s",
            ),
            ordered,
        )
    }

    @Test
    fun riskyCandidatesRemainAsFallbacksWithPathsAndSignaturesIntact() {
        val risky = listOf(
            "https://edge.szbdyd.com/v.m4s?sign=test%2Bvalue&range=1",
            "https://mountaintoys.cn/v.m4s",
            "https://edge.qrstuvwxyzab.com/v.m4s",
            "https://edge.zhenidc.cn/v.m4s",
            "https://edge.pcdn.example/v.m4s",
            "https://gotcha.example/v.m4s",
            "http://192.0.2.1:8080/v.m4s",
            "https://[2001:db8::1]/v.m4s",
        )
        val safe = "https://cdn.example/v.m4s?sign=other%2Fvalue"
        assertEquals(
            listOf(safe) + risky,
            PlaybackUrlCandidates.orderedPlaybackUrls(
                SettingsManager.PlayerCdnPreference.BILIVIDEO,
                risky.first(), risky.drop(1) + safe,
            ),
        )
    }

    @Test
    fun mcdnPreferenceStillDeprioritizesOtherRiskyFallbacks() {
        val risky = "https://edge.szbdyd.com/v.m4s"
        val safe = "https://cdn.example/v.m4s"
        val mcdn = "https://edge.mcdn.bilivideo.cn/v.m4s"
        assertEquals(
            listOf(mcdn, safe, risky),
            PlaybackUrlCandidates.orderedPlaybackUrls(
                SettingsManager.PlayerCdnPreference.MCDN, risky, listOf(safe, mcdn),
            ),
        )
    }

    @Test
    fun domainMatchingUsesHostBoundariesAndNormalizesCase() {
        val unrelated = "https://notbilivideo.com/v.m4s"
        val normal = "https://CDN.BILIVIDEO.COM./v.m4s"
        val suffixLookalike = "https://notszbdyd.com/v.m4s"
        assertEquals(
            listOf(normal, unrelated, suffixLookalike),
            PlaybackUrlCandidates.orderedPlaybackUrls(
                SettingsManager.PlayerCdnPreference.BILIVIDEO,
                unrelated, listOf(suffixLookalike, normal),
            ),
        )
    }

    @Test
    fun allRiskyCandidatesAndSingleCandidateStayUsable() {
        val risky = "https://edge.szbdyd.com/v.m4s"
        val fallback = "https://192.0.2.1/v.m4s"
        assertEquals(
            listOf(risky, fallback),
            PlaybackUrlCandidates.orderedPlaybackUrls(
                SettingsManager.PlayerCdnPreference.BILIVIDEO, risky, listOf(fallback),
            ),
        )
        assertEquals(
            risky,
            PlaybackUrlCandidates.selectPreferredPlaybackUrl(
                SettingsManager.PlayerCdnPreference.BILIVIDEO, risky, emptyList(),
            ),
        )
    }
}
