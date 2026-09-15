package com.bbttvv.app.app.startup

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStartupTaskTest {
    @Test
    fun `plugin context is ready before deferred registration`() {
        val tasks = defaultAppStartupTasks(deferredDelayMs = 800L)
        val contextTask = tasks.single { it.id == "plugin_manager_context_init" }
        val registrationTask = tasks.single { it.id == "plugin_registration_init" }

        assertEquals(StartupPhase.BEFORE_FIRST_INTERACTIVE, contextTask.phase)
        assertEquals(StartupThread.MAIN, contextTask.thread)
        assertEquals(StartupPhase.AFTER_FIRST_INTERACTIVE, registrationTask.phase)
        assertEquals(StartupThread.IO, registrationTask.thread)
        assertTrue(tasks.indexOf(contextTask) < tasks.indexOf(registrationTask))
    }

    @Test
    fun `background warmup stays on io after first interactive`() {
        val deferredDelayMs = 800L
        val task = defaultAppStartupTasks(deferredDelayMs)
            .single { it.id == "background_warmup" }

        assertEquals(StartupPhase.AFTER_FIRST_INTERACTIVE, task.phase)
        assertEquals(StartupThread.IO, task.thread)
        assertEquals(deferredDelayMs + 1_200L, task.delayMs)
    }

    @Test
    fun `deferred startup failure is isolated`() {
        val task = AppStartupTask(
            id = "optional",
            phase = StartupPhase.AFTER_FIRST_INTERACTIVE,
            thread = StartupThread.IO
        )
        var reported: Throwable? = null

        runStartupTaskWithFailurePolicy(
            task = task,
            taskRunner = { error("boom") },
            onNonCriticalFailure = { _, error -> reported = error }
        )

        assertEquals("boom", reported?.message)
    }

    @Test
    fun `critical startup failure remains fail fast`() {
        val task = AppStartupTask(
            id = "critical",
            phase = StartupPhase.BEFORE_FIRST_INTERACTIVE,
            thread = StartupThread.MAIN
        )

        assertThrows(IllegalStateException::class.java) {
            runStartupTaskWithFailurePolicy(
                task = task,
                taskRunner = { error("critical failure") },
                onNonCriticalFailure = { _, _ -> }
            )
        }
    }

    @Test
    fun `startup cancellation always propagates`() {
        val task = AppStartupTask(
            id = "optional",
            phase = StartupPhase.AFTER_FIRST_INTERACTIVE,
            thread = StartupThread.IO
        )

        assertThrows(CancellationException::class.java) {
            runStartupTaskWithFailurePolicy(
                task = task,
                taskRunner = { throw CancellationException("cancel") },
                onNonCriticalFailure = { _, _ -> }
            )
        }
    }
}
