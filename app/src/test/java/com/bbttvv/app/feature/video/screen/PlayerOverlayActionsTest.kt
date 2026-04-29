package com.bbttvv.app.feature.video.screen

import com.bbttvv.app.feature.video.viewmodel.PlayerOption
import com.bbttvv.app.feature.video.viewmodel.PlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerOverlayActionsTest {
    @Test
    fun `audio and codec actions are hidden when source has no switch choices`() {
        val actions = buildPlayerActions(
            uiState = PlayerUiState(),
            isDebugBuild = false,
        )

        assertEquals(baseActions, actions)
    }

    @Test
    fun `audio and codec actions are shown only when source has multiple choices`() {
        val actions = buildPlayerActions(
            uiState = PlayerUiState(
                audioOptions = listOf(
                    playerOption("30216", selected = true),
                    playerOption("30280"),
                ),
                videoCodecOptions = listOf(
                    playerOption("7", selected = true),
                    playerOption("12", enabled = false),
                ),
            ),
            isDebugBuild = false,
        )

        assertEquals(baseActions + listOf(PlayerAction.Audio, PlayerAction.Codec), actions)
    }

    @Test
    fun `debug action is shown only when debug build is enabled`() {
        assertFalse(
            PlayerAction.Debug in buildPlayerActions(
                uiState = PlayerUiState(),
                isDebugBuild = false,
            )
        )

        val debugActions = buildPlayerActions(
            uiState = PlayerUiState(),
            isDebugBuild = true,
        )

        assertEquals(baseActions + listOf(PlayerAction.Debug), debugActions)
    }

    @Test
    fun `debug action stays last after dynamic audio and codec actions`() {
        val actions = buildPlayerActions(
            uiState = PlayerUiState(
                audioOptions = listOf(
                    playerOption("30216", selected = true),
                    playerOption("30280"),
                ),
                videoCodecOptions = listOf(
                    playerOption("7", selected = true),
                    playerOption("12"),
                ),
            ),
            isDebugBuild = true,
        )

        assertEquals(
            baseActions + listOf(PlayerAction.Audio, PlayerAction.Codec, PlayerAction.Debug),
            actions,
        )
    }

    @Test
    fun `single audio or codec choice does not add switch action`() {
        val actions = buildPlayerActions(
            uiState = PlayerUiState(
                audioOptions = listOf(playerOption("30216", selected = true)),
                videoCodecOptions = listOf(playerOption("7", selected = true)),
            ),
            isDebugBuild = false,
        )

        assertFalse(PlayerAction.Audio in actions)
        assertFalse(PlayerAction.Codec in actions)
    }

    @Test
    fun `detail action emits open detail effect instead of debug toggle`() {
        val stateMachine = PlayerOverlayStateMachine()
        val effects = mutableListOf<PlayerOverlayEffect>()

        stateMachine.activateAction(PlayerAction.Detail, effects::add)

        assertEquals(listOf(PlayerOverlayEffect.OpenDetail), effects)
        assertFalse(stateMachine.uiState.showDebugOverlay)
    }

    @Test
    fun `debug action toggles debug overlay without opening detail`() {
        val stateMachine = PlayerOverlayStateMachine()
        val effects = mutableListOf<PlayerOverlayEffect>()

        stateMachine.activateAction(PlayerAction.Debug, effects::add)

        assertTrue(stateMachine.uiState.showDebugOverlay)
        assertFalse(PlayerOverlayEffect.OpenDetail in effects)
    }

    @Test
    fun `action sync closes panel and clamps focus when dynamic action disappears`() {
        val state = PlayerOverlayUiState(
            fullControlsFocus = PlayerFullControlsFocus.Actions,
            selectedActionIndex = 6,
            activePanel = PlayerAction.Codec,
            selectedPanelIndex = 1,
        )

        val next = state.withSyncedActions(baseActions)

        assertEquals(baseActions.lastIndex, next.selectedActionIndex)
        assertNull(next.activePanel)
        assertEquals(0, next.selectedPanelIndex)
        assertEquals(PlayerFullControlsFocus.Actions, next.fullControlsFocus)
    }

    @Test
    fun `action sync preserves available active panel`() {
        val state = PlayerOverlayUiState(
            fullControlsFocus = PlayerFullControlsFocus.Actions,
            selectedActionIndex = 2,
            activePanel = PlayerAction.Speed,
            selectedPanelIndex = 3,
        )

        val next = state.withSyncedActions(baseActions)

        assertEquals(PlayerAction.Speed, next.activePanel)
        assertEquals(3, next.selectedPanelIndex)
        assertTrue(next.selectedActionIndex in baseActions.indices)
    }

    private fun playerOption(
        key: String,
        selected: Boolean = false,
        enabled: Boolean = true,
    ): PlayerOption {
        return PlayerOption(
            key = key,
            label = key,
            isSelected = selected,
            isEnabled = enabled,
        )
    }

    private companion object {
        val baseActions = listOf(
            PlayerAction.Detail,
            PlayerAction.Comments,
            PlayerAction.Speed,
            PlayerAction.Quality,
            PlayerAction.Danmaku,
        )
    }
}
