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
                "https://upos-sz-mirrorcosmcdn.mcdn.bilivideo.com/video.m4s",
                "https://example.com/video.m4s",
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
}
