package com.bbttvv.app.core.player

import android.os.SystemClock
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

private const val SPEED_SAMPLE_WINDOW_MS = 1_000L
private const val SPEED_STALE_AFTER_MS = 2_000L

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class BufferingSpeedMeter : TransferListener {
    private val lock = Any()
    private var windowStartedAtMs = SystemClock.elapsedRealtime()
    private var windowBytes = 0L
    private var lastBytesAtMs = 0L
    private var lastBytesPerSecond = 0L

    override fun onTransferInitializing(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean
    ) = Unit

    override fun onTransferStart(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean
    ) {
        if (!isNetwork) return
        synchronized(lock) {
            if (lastBytesAtMs == 0L || isStaleLocked(SystemClock.elapsedRealtime())) {
                resetLocked()
            }
        }
    }

    override fun onBytesTransferred(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
        bytesTransferred: Int
    ) {
        if (!isNetwork || bytesTransferred <= 0) return
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            windowBytes += bytesTransferred.toLong()
            lastBytesAtMs = now

            val elapsedMs = now - windowStartedAtMs
            if (elapsedMs >= SPEED_SAMPLE_WINDOW_MS) {
                lastBytesPerSecond = calculateBytesPerSecond(windowBytes, elapsedMs)
                windowBytes = 0L
                windowStartedAtMs = now
            }
        }
    }

    override fun onTransferEnd(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean
    ) = Unit

    fun bytesPerSecond(): Long {
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            if (lastBytesAtMs == 0L || isStaleLocked(now)) {
                return 0L
            }

            val elapsedMs = now - windowStartedAtMs
            val currentBytesPerSecond = if (elapsedMs > 0L && windowBytes > 0L) {
                calculateBytesPerSecond(windowBytes, elapsedMs)
            } else {
                0L
            }
            return maxOf(lastBytesPerSecond, currentBytesPerSecond)
        }
    }

    fun reset() {
        synchronized(lock) {
            resetLocked()
        }
    }

    private fun resetLocked() {
        windowStartedAtMs = SystemClock.elapsedRealtime()
        windowBytes = 0L
        lastBytesAtMs = 0L
        lastBytesPerSecond = 0L
    }

    private fun isStaleLocked(now: Long): Boolean {
        return now - lastBytesAtMs > SPEED_STALE_AFTER_MS
    }

    private fun calculateBytesPerSecond(bytes: Long, elapsedMs: Long): Long {
        return if (elapsedMs <= 0L) {
            0L
        } else {
            (bytes * 1_000L / elapsedMs).coerceAtLeast(0L)
        }
    }
}
