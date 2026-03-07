package com.android.purebilibili

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainActivityCrashLogPromptPolicyTest {

    @Test
    fun showsPromptOnlyWhenPendingCrashSnapshotExists() {
        assertTrue(
            shouldShowPendingCrashLogPrompt(
                hasPendingCrashSnapshot = true,
                hasPromptBeenHandled = false
            )
        )
        assertFalse(
            shouldShowPendingCrashLogPrompt(
                hasPendingCrashSnapshot = false,
                hasPromptBeenHandled = false
            )
        )
        assertFalse(
            shouldShowPendingCrashLogPrompt(
                hasPendingCrashSnapshot = true,
                hasPromptBeenHandled = true
            )
        )
    }

    @Test
    fun handledActionsClearPendingPromptState() {
        assertTrue(shouldClearPendingCrashLogAfterAction(CrashLogPromptAction.SHARE))
        assertTrue(shouldClearPendingCrashLogAfterAction(CrashLogPromptAction.DISMISS))
        assertFalse(shouldClearPendingCrashLogAfterAction(CrashLogPromptAction.IGNORE))
    }
}
