package com.bbttvv.app.feature.video.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPanelLayoutPolicyTest {
    @Test
    fun `danmaku settings use the right sidebar layout`() {
        assertEquals(
            PlayerPanelLayout.RightSidebar,
            resolvePlayerPanelLayout(PlayerAction.Danmaku),
        )
    }

    @Test
    fun `other option panels remain floating`() {
        listOf(
            PlayerAction.Speed,
            PlayerAction.Quality,
            PlayerAction.SponsorNavigation,
            PlayerAction.Debug,
        ).forEach { action ->
            assertEquals(PlayerPanelLayout.Floating, resolvePlayerPanelLayout(action))
        }
    }
}
