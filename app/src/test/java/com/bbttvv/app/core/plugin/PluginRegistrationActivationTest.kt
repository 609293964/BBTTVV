package com.bbttvv.app.core.plugin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRegistrationActivationTest {
    @Test
    fun `successful requested activation publishes enabled`() {
        val activation = resolvePluginRegistrationActivation(
            requestedEnabled = true,
            enableSucceeded = true
        )

        assertTrue(activation.publishedEnabled)
        assertFalse(activation.persistDisabled)
    }

    @Test
    fun `failed requested activation stays disabled and repairs persistence`() {
        val activation = resolvePluginRegistrationActivation(
            requestedEnabled = true,
            enableSucceeded = false
        )

        assertFalse(activation.publishedEnabled)
        assertTrue(activation.persistDisabled)
    }

    @Test
    fun `explicitly disabled plugin stays disabled without persistence repair`() {
        val activation = resolvePluginRegistrationActivation(
            requestedEnabled = false,
            enableSucceeded = true
        )

        assertFalse(activation.publishedEnabled)
        assertFalse(activation.persistDisabled)
    }
}
