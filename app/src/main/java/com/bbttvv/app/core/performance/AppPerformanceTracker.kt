package com.bbttvv.app.core.performance

import android.os.SystemClock
import android.util.Log
import com.bbttvv.app.app.startup.AppStartupTask

private const val APP_PERF_TAG = "AppPerf"
private const val NANOS_PER_MICRO = 1_000L
private const val MICROS_PER_MILLI = 1_000L
private const val NANOS_PER_MILLI = 1_000_000L

object AppPerformanceTracker {
    private val lock = Any()
    private var appCreatedAtElapsedMs: Long? = null
    private val activeSpans = mutableMapOf<String, Long>()
    private val emittedMilestones = mutableSetOf<String>()

    fun markApplicationCreated() {
        val now = SystemClock.elapsedRealtime()
        val shouldLog = synchronized(lock) {
            if (appCreatedAtElapsedMs != null) {
                false
            } else {
                appCreatedAtElapsedMs = now
                true
            }
        }
        if (shouldLog) {
            Log.i(APP_PERF_TAG, "APP_PERF milestone=application_on_create sinceApp=0ms")
        }
    }

    internal fun <T> measureStartupTask(
        task: AppStartupTask,
        block: () -> T
    ): T {
        val startNs = SystemClock.elapsedRealtimeNanos()
        return try {
            block()
        } finally {
            val durationNs = (SystemClock.elapsedRealtimeNanos() - startNs).coerceAtLeast(0L)
            val isOverBudget = task.coldStartBudgetMs?.let { budgetMs ->
                durationNs > (budgetMs * NANOS_PER_MILLI).toLong()
            } ?: false
            val message = buildStartupTaskLogMessage(
                task = task,
                durationNs = durationNs,
                isOverBudget = isOverBudget
            )
            if (isOverBudget) {
                Log.w(APP_PERF_TAG, message)
            } else {
                Log.i(APP_PERF_TAG, message)
            }
        }
    }

    fun markMilestoneOnce(
        key: String,
        extras: String? = null
    ) {
        val shouldLog = synchronized(lock) { emittedMilestones.add(key) }
        if (!shouldLog) return

        Log.i(
            APP_PERF_TAG,
            buildString {
                append("APP_PERF milestone=")
                append(key)
                append(" sinceApp=")
                append(sinceAppStartMs())
                append("ms")
                extras
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        append(' ')
                        append(it)
                    }
            }
        )
    }

    fun beginSpanOnce(key: String) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (key in emittedMilestones || key in activeSpans) return
            activeSpans[key] = now
        }
    }

    fun endSpanOnce(
        key: String,
        milestone: String,
        extras: String? = null
    ) {
        val completed = synchronized(lock) {
            val start = activeSpans.remove(key) ?: return
            if (!emittedMilestones.add(milestone)) return
            start
        }

        val durationMs = (SystemClock.elapsedRealtime() - completed).coerceAtLeast(0L)
        Log.i(
            APP_PERF_TAG,
            buildString {
                append("APP_PERF milestone=")
                append(milestone)
                append(" duration=")
                append(durationMs)
                append("ms sinceApp=")
                append(sinceAppStartMs())
                append("ms")
                extras
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        append(' ')
                        append(it)
                    }
            }
        )
    }

    private fun sinceAppStartMs(now: Long = SystemClock.elapsedRealtime()): Long {
        val appCreatedAt = synchronized(lock) { appCreatedAtElapsedMs }
        return if (appCreatedAt == null) 0L else (now - appCreatedAt).coerceAtLeast(0L)
    }

    private fun buildStartupTaskLogMessage(
        task: AppStartupTask,
        durationNs: Long,
        isOverBudget: Boolean
    ): String {
        return buildString {
            append("APP_PERF startup_task=")
            append(task.id)
            append(" phase=")
            append(task.phase)
            append(" thread=")
            append(task.thread)
            append(" duration=")
            append(formatDurationMs(durationNs))
            append("ms")
            task.coldStartBudgetMs?.let { budgetMs ->
                append(" budget=")
                append(budgetMs)
                append("ms overBudget=")
                append(isOverBudget)
            }
            append(" sinceApp=")
            append(sinceAppStartMs())
            append("ms")
        }
    }

    private fun formatDurationMs(durationNs: Long): String {
        val durationMicros = durationNs / NANOS_PER_MICRO
        val wholeMs = durationMicros / MICROS_PER_MILLI
        val fractionalMicros = (durationMicros % MICROS_PER_MILLI)
            .toString()
            .padStart(length = 3, padChar = '0')
        return "$wholeMs.$fractionalMicros"
    }
}
