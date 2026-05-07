package com.bbttvv.app.feature.video.screen

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlin.concurrent.thread

internal class PlayerExitTrace(
    private val session: PlayerScreenSession,
) {
    @Volatile
    private var startMs: Long = 0L

    @Volatile
    private var lastMarkMs: Long = 0L

    @Volatile
    private var completed: Boolean = false

    @Volatile
    private var stallLogged: Boolean = false

    private val mainThread: Thread = Looper.getMainLooper().thread

    fun start(trigger: String) {
        if (startMs != 0L) {
            mark("start:reuse", "trigger=$trigger")
            return
        }
        val now = SystemClock.uptimeMillis()
        startMs = now
        lastMarkMs = now
        completed = false
        log(stage = "start", extra = "trigger=$trigger", nowMs = now)
        watchForExitStall(startedAtMs = now)
    }

    fun mark(stage: String, extra: String = "") {
        if (startMs == 0L) {
            start(trigger = "implicit:$stage")
            return
        }
        log(stage = stage, extra = extra, nowMs = SystemClock.uptimeMillis())
    }

    fun <T> measure(stage: String, block: () -> T): T {
        val start = SystemClock.uptimeMillis()
        return try {
            block()
        } finally {
            val costMs = SystemClock.uptimeMillis() - start
            mark(stage, "costMs=$costMs")
        }
    }

    fun complete(reason: String) {
        if (startMs == 0L) {
            start(trigger = "complete:$reason")
        }
        completed = true
        mark("complete", "reason=$reason")
    }

    private fun watchForExitStall(startedAtMs: Long) {
        thread(
            start = true,
            isDaemon = true,
            name = "PlayerExitTrace",
        ) {
            SystemClock.sleep(ExitStallThresholdMs)
            if (completed || startMs != startedAtMs || stallLogged) return@thread
            stallLogged = true
            Log.w(
                Tag,
                buildMessage(
                    stage = "main_thread_stall",
                    nowMs = SystemClock.uptimeMillis(),
                    extra = "thresholdMs=$ExitStallThresholdMs stack=${mainStackTop()}",
                )
            )
        }
    }

    private fun log(stage: String, extra: String, nowMs: Long) {
        Log.i(Tag, buildMessage(stage = stage, nowMs = nowMs, extra = extra))
    }

    private fun buildMessage(stage: String, nowMs: Long, extra: String): String {
        val startedAt = startMs.takeIf { it != 0L } ?: nowMs
        val previous = lastMarkMs.takeIf { it != 0L } ?: nowMs
        val totalMs = nowMs - startedAt
        val deltaMs = nowMs - previous
        lastMarkMs = nowMs
        return buildString {
            append("PLAYER_EXIT_TRACE")
            append(" stage=").append(stage)
            append(" totalMs=").append(totalMs)
            append(" deltaMs=").append(deltaMs)
            append(" bvidTail=").append(session.bvid.takeLast(8))
            append(" aid=").append(session.aid)
            append(" cid=").append(session.cid)
            if (extra.isNotBlank()) {
                append(' ').append(extra)
            }
        }
    }

    private fun mainStackTop(): String {
        return mainThread.stackTrace
            .asSequence()
            .filterNot { frame ->
                frame.className.startsWith("dalvik.") ||
                    frame.className.startsWith("java.lang.Thread")
            }
            .take(6)
            .joinToString(separator = " <- ") { frame ->
                "${frame.className.substringAfterLast('.')}.${frame.methodName}:${frame.lineNumber}"
            }
            .ifBlank { "empty" }
    }

    private companion object {
        private const val Tag = "PlayerExitTrace"
        private const val ExitStallThresholdMs = 600L
    }
}
