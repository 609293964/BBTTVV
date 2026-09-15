package com.bbttvv.app.feature.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSuperChatDisplayPolicyTest {
    @Test
    fun followsDanmakuSwitch() {
        assertTrue(shouldShowLiveSuperChat(isDanmakuEnabled = true, flashEnabled = true))
        assertFalse(shouldShowLiveSuperChat(isDanmakuEnabled = false, flashEnabled = true))
    }

    @Test
    fun respectsIndependentSetting() {
        assertFalse(shouldShowLiveSuperChat(isDanmakuEnabled = true, flashEnabled = false))
    }
}
