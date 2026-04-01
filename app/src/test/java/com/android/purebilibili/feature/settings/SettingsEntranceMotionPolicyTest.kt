package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.ui.adaptive.MotionTier
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsEntranceMotionPolicyTest {

    @Test
    fun settingsEntranceMotion_keepsBaseTierWhenHomeCardEntranceDisabled() {
        val tier = resolveSettingsEntranceMotionTier(
            baseTier = MotionTier.Enhanced
        )

        assertEquals(MotionTier.Enhanced, tier)
    }

    @Test
    fun animationSettingsCardMotion_stillReflectsDisabledHomeCardEntrance() {
        val tier = resolveAnimationSettingsCardMotionTier(
            baseTier = MotionTier.Enhanced,
            cardAnimationEnabled = false
        )

        assertEquals(MotionTier.Reduced, tier)
    }
}
