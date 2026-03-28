package com.android.purebilibili.core.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginManagerPolicyTest {

    @Test
    fun pendingEnabledOverride_winsDuringRegistrationAndIsConsumed() {
        val pendingOverrides = mutableMapOf(
            "sponsor_block" to true
        )

        val resolved = consumePendingPluginEnabledState(
            pluginId = "sponsor_block",
            storedEnabled = false,
            pendingEnabledOverrides = pendingOverrides
        )

        assertTrue(resolved)
        assertFalse(pendingOverrides.containsKey("sponsor_block"))
    }

    @Test
    fun storedEnabledState_usedWhenNoPendingOverrideExists() {
        val pendingOverrides = mutableMapOf<String, Boolean>()

        val resolved = consumePendingPluginEnabledState(
            pluginId = "sponsor_block",
            storedEnabled = false,
            pendingEnabledOverrides = pendingOverrides
        )

        assertEquals(false, resolved)
    }
}
