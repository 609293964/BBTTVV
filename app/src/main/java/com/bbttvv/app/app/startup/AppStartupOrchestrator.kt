package com.bbttvv.app.app.startup

import com.bbttvv.app.app.BbtvApplicationRuntimeConfig
import com.bbttvv.app.core.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class AppStartupOrchestrator(
    private val deferredDelayMs: Long = BbtvApplicationRuntimeConfig.deferredNonCriticalStartupDelayMs(),
    private val mainScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val defaultScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    fun startupTasks(): List<AppStartupTask> {
        return defaultAppStartupTasks(deferredDelayMs = deferredDelayMs)
    }

    fun runImmediate(taskRunner: (AppStartupTask) -> Unit) {
        dispatch(
            phase = StartupPhase.BEFORE_FIRST_INTERACTIVE,
            taskRunner = taskRunner
        )
    }

    fun scheduleDeferred(taskRunner: (AppStartupTask) -> Unit) {
        dispatch(
            phase = StartupPhase.AFTER_FIRST_INTERACTIVE,
            taskRunner = taskRunner
        )
    }

    private fun dispatch(
        phase: StartupPhase,
        taskRunner: (AppStartupTask) -> Unit
    ) {
        startupTasks()
            .filter { it.phase == phase }
            .forEach { task -> dispatchTask(task, taskRunner) }
    }

    private fun dispatchTask(
        task: AppStartupTask,
        taskRunner: (AppStartupTask) -> Unit
    ) {
        if (task.thread == StartupThread.MAIN && task.delayMs <= 0L) {
            runStartupTaskWithFailurePolicy(task, taskRunner)
            return
        }

        resolveScope(task.thread).launch {
            if (task.delayMs > 0L) delay(task.delayMs)
            runStartupTaskWithFailurePolicy(task, taskRunner)
        }
    }

    private fun resolveScope(thread: StartupThread): CoroutineScope {
        return when (thread) {
            StartupThread.MAIN -> mainScope
            StartupThread.IO -> ioScope
            StartupThread.DEFAULT -> defaultScope
        }
    }
}

/**
 * BEFORE_FIRST_INTERACTIVE tasks are critical and remain fail-fast.
 * AFTER_FIRST_INTERACTIVE tasks are explicitly best-effort: cancellation propagates,
 * ordinary failures are logged and isolated instead of escaping a root launch.
 */
internal fun runStartupTaskWithFailurePolicy(
    task: AppStartupTask,
    taskRunner: (AppStartupTask) -> Unit,
    onNonCriticalFailure: (AppStartupTask, Throwable) -> Unit = { failedTask, error ->
        Logger.e(
            "AppStartup",
            "non-critical startup task failed: ${failedTask.id}",
            error
        )
    }
) {
    try {
        taskRunner(task)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        if (task.phase == StartupPhase.BEFORE_FIRST_INTERACTIVE) throw error
        onNonCriticalFailure(task, error)
    }
}
