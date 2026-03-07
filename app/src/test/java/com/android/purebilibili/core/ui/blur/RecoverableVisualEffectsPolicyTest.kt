package com.android.purebilibili.core.ui.blur

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecoverableVisualEffectsPolicyTest {

    @Test
    fun enablesHeavyVisualEffectsOnlyWhenForegroundAndUserEnabled() {
        assertTrue(
            shouldEnableRecoverableHeavyVisualEffects(
                userEnabled = true,
                isAppInBackground = false
            )
        )
        assertFalse(
            shouldEnableRecoverableHeavyVisualEffects(
                userEnabled = true,
                isAppInBackground = true
            )
        )
        assertFalse(
            shouldEnableRecoverableHeavyVisualEffects(
                userEnabled = false,
                isAppInBackground = false
            )
        )
    }
}
