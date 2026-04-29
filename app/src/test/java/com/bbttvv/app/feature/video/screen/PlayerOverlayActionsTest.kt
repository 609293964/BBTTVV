package com.bbttvv.app.feature.video.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerOverlayActionsTest {
    @Test
    fun `player actions only include progress bar controls`() {
        val actions = buildPlayerActions(
            isDebugBuild = false,
        )

        assertEquals(baseActions, actions)
    }

    @Test
    fun `debug action is shown only when debug build is enabled`() {
        assertFalse(
            PlayerAction.Debug in buildPlayerActions(
                isDebugBuild = false,
            )
        )

        val debugActions = buildPlayerActions(
            isDebugBuild = true,
        )

        assertEquals(baseActions + listOf(PlayerAction.Debug), debugActions)
    }

    @Test
    fun `debug action toggles debug overlay without side effects`() {
        val stateMachine = PlayerOverlayStateMachine()
        val effects = mutableListOf<PlayerOverlayEffect>()

        stateMachine.activateAction(PlayerAction.Debug, effects::add)

        assertTrue(stateMachine.uiState.showDebugOverlay)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `action sync closes panel and clamps focus when dynamic action disappears`() {
        val state = PlayerOverlayUiState(
            fullControlsFocus = PlayerFullControlsFocus.Actions,
            selectedActionIndex = 4,
            activePanel = PlayerAction.Debug,
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

    private companion object {
        val baseActions = listOf(
            PlayerAction.Comments,
            PlayerAction.Speed,
            PlayerAction.Quality,
            PlayerAction.Danmaku,
        )
    }
}
