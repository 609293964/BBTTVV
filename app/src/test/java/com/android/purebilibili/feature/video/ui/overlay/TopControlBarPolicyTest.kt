package com.android.purebilibili.feature.video.ui.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TopControlBarPolicyTest {

    @Test
    fun dislikeActionHiddenOnPhoneLandscape() {
        assertFalse(
            shouldShowDislikeInTopControlBar(
                widthDp = 780
            )
        )
    }

    @Test
    fun dislikeActionVisibleOnWideLandscape() {
        assertTrue(
            shouldShowDislikeInTopControlBar(
                widthDp = 1200
            )
        )
    }

    @Test
    fun interactiveActionsVisibleWhenSettingEnabled() {
        assertTrue(
            shouldShowInteractiveActionsInTopControlBar(
                showFullscreenActionItems = true
            )
        )
    }

    @Test
    fun interactiveActionsHiddenWhenSettingDisabled() {
        assertFalse(
            shouldShowInteractiveActionsInTopControlBar(
                showFullscreenActionItems = false
            )
        )
    }

    @Test
    fun fullscreenTopBar_doesNotConsumeStatusBarPadding() {
        assertFalse(
            shouldApplyStatusBarPaddingToTopControlBar(
                isFullscreen = true
            )
        )
    }

    @Test
    fun inlineTopBar_doesNotConsumeStatusBarPaddingEither() {
        assertFalse(
            shouldApplyStatusBarPaddingToTopControlBar(
                isFullscreen = false
            )
        )
    }

    @Test
    fun landscapeStatusInfo_showsBatteryAndTimeInExpectedOrder() {
        val result = resolveLandscapeTopStatusInfo(
            showBatteryLevel = true,
            batteryLevelPercent = 95,
            showCurrentTime = true,
            currentTimeText = "11:13"
        )

        assertEquals("95%", result.battery?.displayText)
        assertEquals("11:13", result.currentTimeText)
        assertTrue(result.isVisible)
    }

    @Test
    fun landscapeStatusInfo_hidesTimeWhenToggleDisabled() {
        val result = resolveLandscapeTopStatusInfo(
            showBatteryLevel = true,
            batteryLevelPercent = 95,
            showCurrentTime = false,
            currentTimeText = "11:13"
        )

        assertEquals("95%", result.battery?.displayText)
        assertNull(result.currentTimeText)
        assertTrue(result.isVisible)
    }

    @Test
    fun landscapeStatusInfo_hidesBatteryGroupWhenToggleDisabled() {
        val result = resolveLandscapeTopStatusInfo(
            showBatteryLevel = false,
            batteryLevelPercent = 95,
            showCurrentTime = true,
            currentTimeText = "11:13"
        )

        assertNull(result.battery)
        assertEquals("11:13", result.currentTimeText)
        assertTrue(result.isVisible)
    }

    @Test
    fun landscapeStatusInfo_hidesRowWhenBothTogglesDisabled() {
        val result = resolveLandscapeTopStatusInfo(
            showBatteryLevel = false,
            batteryLevelPercent = 95,
            showCurrentTime = false,
            currentTimeText = "11:13"
        )

        assertNull(result.battery)
        assertNull(result.currentTimeText)
        assertFalse(result.isVisible)
    }

    @Test
    fun batteryVisualState_tracksActualBatteryPercentage() {
        val result = resolveLandscapeBatteryVisualState(83)

        assertEquals("83%", result?.displayText)
        assertEquals(0.83f, result?.fillFraction ?: 0f, 0.001f)
        assertEquals(BatteryChargeTone.NORMAL, result?.chargeTone)
    }

    @Test
    fun batteryVisualState_marksCriticalBatteryClearly() {
        val result = resolveLandscapeBatteryVisualState(9)

        assertEquals("9%", result?.displayText)
        assertEquals(BatteryChargeTone.CRITICAL, result?.chargeTone)
    }

    @Test
    fun batteryMetaRow_isShownBelowTitleWhenStatusOrOnlineCountExists() {
        assertTrue(
            shouldShowLandscapeMetaRow(
                hasStatusInfo = true,
                onlineCount = ""
            )
        )
        assertTrue(
            shouldShowLandscapeMetaRow(
                hasStatusInfo = false,
                onlineCount = "23.4万在线"
            )
        )
        assertFalse(
            shouldShowLandscapeMetaRow(
                hasStatusInfo = false,
                onlineCount = ""
            )
        )
    }
}
