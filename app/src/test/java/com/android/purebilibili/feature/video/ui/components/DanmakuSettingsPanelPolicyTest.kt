package com.android.purebilibili.feature.video.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DanmakuSettingsPanelPolicyTest {

    @Test
    fun portraitPanelAnchorsToBottomAndUsesWiderSheetWidth() {
        val policy = resolveDanmakuSettingsPanelLayoutPolicy(
            isFullscreen = false,
            screenWidthDp = 411,
            screenHeightDp = 915
        )

        assertEquals(
            DanmakuSettingsPanelPresentation.BottomSheet,
            policy.presentation
        )
        assertEquals(16, policy.horizontalPaddingDp)
        assertEquals(20, policy.bottomPaddingDp)
        assertTrue(policy.maxWidthDp >= 520)
    }

    @Test
    fun fullscreenPanelKeepsCenteredDialogPresentation() {
        val policy = resolveDanmakuSettingsPanelLayoutPolicy(
            isFullscreen = true,
            screenWidthDp = 915,
            screenHeightDp = 411
        )

        assertEquals(
            DanmakuSettingsPanelPresentation.CenteredDialog,
            policy.presentation
        )
        assertEquals(0, policy.bottomPaddingDp)
        assertEquals(380, policy.maxWidthDp)
        assertEquals(480, policy.maxHeightDp)
    }
}
