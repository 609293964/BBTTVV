package com.bbttvv.app.app.startup

import org.junit.Assert.assertEquals
import org.junit.Test

class AppStartupTaskTest {
    @Test
    fun `background warmup stays on io after first interactive`() {
        val deferredDelayMs = 800L

        val task = defaultAppStartupTasks(deferredDelayMs)
            .single { it.id == "background_warmup" }

        assertEquals(StartupPhase.AFTER_FIRST_INTERACTIVE, task.phase)
        assertEquals(StartupThread.IO, task.thread)
        assertEquals(deferredDelayMs + 1_200L, task.delayMs)
    }
}
