package com.bbttvv.app.app.startup

import com.bbttvv.app.app.BbtvApplicationRuntimeConfig
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
            taskRunner(task)
            return
        }

        resolveScope(task.thread).launch {
            if (task.delayMs > 0L) {
                delay(task.delayMs)
            }
            taskRunner(task)
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
