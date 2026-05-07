package com.bbttvv.app.feature.video.viewmodel

import android.os.SystemClock
import androidx.annotation.MainThread
import com.bbttvv.app.core.cache.PlayUrlCache
import com.bbttvv.app.core.coroutines.AppScope
import com.bbttvv.app.core.history.PlaybackHistorySyncBus
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.store.PlaybackResumeStore
import com.bbttvv.app.core.store.TodayWatchProfileStore
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.repository.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val HISTORY_HEARTBEAT_INITIAL_DELAY_MS = 3_000L
private const val HISTORY_HEARTBEAT_INTERVAL_MS = 15_000L

internal data class PlaybackHistoryPlaybackSnapshot(
    val bvid: String,
    val cid: Long,
    val currentPositionMs: Long,
    val currentDurationMs: Long,
    val fallbackPositionMs: Long,
    val fallbackDurationMs: Long,
    val isPlaying: Boolean,
)

private data class PlaybackHeartbeatSnapshot(
    val playedTimeSec: Long,
    val realPlayedTimeSec: Long,
    val durationMs: Long,
)

private data class PlaybackHeartbeatReport(
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val startTsSec: Long,
    val snapshot: PlaybackHeartbeatSnapshot,
    val creatorMid: Long,
    val creatorName: String,
    val deltaWatchSec: Long,
)

private data class HeartbeatRuntimeState(
    val aid: Long = 0L,
    val bvid: String = "",
    val cid: Long = 0L,
    val startTsSec: Long = 0L,
    val creatorMid: Long = 0L,
    val creatorName: String = "",
    val accumulatedPlayMs: Long = 0L,
    val activePlayStartElapsedMs: Long? = null,
    val lastReportedSnapshot: PlaybackHeartbeatSnapshot? = null,
) {
    val hasSession: Boolean
        get() = bvid.isNotBlank() && cid > 0L
}

internal class PlaybackHistoryReporter(
    private val scope: CoroutineScope,
    private val ensureMainThread: (String) -> Unit,
    private val playbackSnapshot: () -> PlaybackHistoryPlaybackSnapshot,
) {
    private var heartbeatJob: Job? = null
    private var heartbeatRuntime = HeartbeatRuntimeState()

    val hasSession: Boolean
        get() = heartbeatRuntime.hasSession

    @MainThread
    fun beginSession(
        aid: Long,
        bvid: String,
        cid: Long,
        creatorMid: Long,
        creatorName: String,
        nowEpochSec: Long = System.currentTimeMillis() / 1000L,
    ) {
        ensureMainThread("PlaybackHistoryReporter.beginSession")
        heartbeatJob?.cancel()
        heartbeatRuntime = HeartbeatRuntimeState(
            aid = aid,
            bvid = bvid,
            cid = cid,
            startTsSec = nowEpochSec,
            creatorMid = creatorMid,
            creatorName = creatorName,
        )

        if (bvid.isBlank() || cid <= 0L) {
            return
        }

        heartbeatJob = scope.launch {
            delay(HISTORY_HEARTBEAT_INITIAL_DELAY_MS)
            while (isActive && heartbeatRuntime.bvid == bvid && heartbeatRuntime.cid == cid) {
                reportPlaybackHeartbeat(forceFlush = false, reason = "interval")
                delay(HISTORY_HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    @MainThread
    fun finishSession(reason: String, closeSession: Boolean = true) {
        ensureMainThread("PlaybackHistoryReporter.finishSession")
        val report = captureHeartbeatReport(forceFlush = true)
        if (closeSession) {
            clear()
        }
        if (report == null) return
        AppScope.ioScope.launch {
            sendHeartbeatReport(
                report = report,
                reason = reason,
                updateSessionState = false,
            )
        }
    }

    @MainThread
    fun syncPlaybackTracking(
        isActivelyPlaying: Boolean,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ) {
        ensureMainThread("PlaybackHistoryReporter.syncPlaybackTracking")
        val session = heartbeatRuntime
        if (!session.hasSession) return
        if (isActivelyPlaying) {
            if (session.activePlayStartElapsedMs == null) {
                heartbeatRuntime = session.copy(activePlayStartElapsedMs = nowElapsedMs)
            }
            return
        }

        val activePlayStartElapsedMs = session.activePlayStartElapsedMs ?: return
        heartbeatRuntime = session.copy(
            accumulatedPlayMs = session.accumulatedPlayMs + (nowElapsedMs - activePlayStartElapsedMs).coerceAtLeast(0L),
            activePlayStartElapsedMs = null,
        )
    }

    @MainThread
    fun clear() {
        ensureMainThread("PlaybackHistoryReporter.clear")
        heartbeatJob?.cancel()
        heartbeatJob = null
        heartbeatRuntime = HeartbeatRuntimeState()
    }

    @MainThread
    private fun buildHeartbeatSnapshot(
        currentPositionMs: Long = currentHeartbeatPositionMs(),
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ): PlaybackHeartbeatSnapshot {
        ensureMainThread("PlaybackHistoryReporter.buildHeartbeatSnapshot")
        val session = heartbeatRuntime
        val playback = playbackSnapshot()
        val activePlayMs = session.activePlayStartElapsedMs
            ?.let { (nowElapsedMs - it).coerceAtLeast(0L) }
            ?: 0L
        val currentDurationMs = if (
            session.bvid.isNotBlank() &&
            session.bvid == playback.bvid &&
            session.cid == playback.cid
        ) {
            playback.currentDurationMs
        } else {
            playback.fallbackDurationMs
        }.coerceAtLeast(0L)
        return PlaybackHeartbeatSnapshot(
            playedTimeSec = currentPositionMs.coerceAtLeast(0L) / 1000L,
            realPlayedTimeSec = (session.accumulatedPlayMs + activePlayMs).coerceAtLeast(0L) / 1000L,
            durationMs = currentDurationMs,
        )
    }

    @MainThread
    private fun currentHeartbeatPositionMs(): Long {
        ensureMainThread("PlaybackHistoryReporter.currentHeartbeatPositionMs")
        val session = heartbeatRuntime
        val playback = playbackSnapshot()
        return if (
            session.bvid.isNotBlank() &&
            session.bvid == playback.bvid &&
            session.cid == playback.cid
        ) {
            playback.currentPositionMs
        } else {
            playback.fallbackPositionMs
        }.coerceAtLeast(0L)
    }

    @MainThread
    private fun captureHeartbeatReport(forceFlush: Boolean): PlaybackHeartbeatReport? {
        ensureMainThread("PlaybackHistoryReporter.captureHeartbeatReport")
        var session = heartbeatRuntime
        if ((session.aid <= 0L && session.bvid.isBlank()) || session.cid <= 0L) return null

        val nowElapsedMs = SystemClock.elapsedRealtime()
        val playback = playbackSnapshot()
        syncPlaybackTracking(
            isActivelyPlaying = playback.isPlaying,
            nowElapsedMs = nowElapsedMs,
        )
        session = heartbeatRuntime
        val snapshot = buildHeartbeatSnapshot(nowElapsedMs = nowElapsedMs)
        val shouldSend = if (forceFlush) {
            snapshot.playedTimeSec > 0L ||
                snapshot.realPlayedTimeSec > 0L ||
                session.lastReportedSnapshot == null
        } else {
            playback.isPlaying &&
                (
                    session.lastReportedSnapshot == null ||
                        snapshot.playedTimeSec > session.lastReportedSnapshot.playedTimeSec ||
                        snapshot.realPlayedTimeSec > session.lastReportedSnapshot.realPlayedTimeSec
                    )
        }
        if (!shouldSend) return null

        if (session.startTsSec <= 0L) {
            session = session.copy(startTsSec = System.currentTimeMillis() / 1000L)
            heartbeatRuntime = session
        }
        val deltaWatchSec = (
            snapshot.realPlayedTimeSec - session.lastReportedSnapshot?.realPlayedTimeSec.orZero()
            ).coerceAtLeast(0L)
        return PlaybackHeartbeatReport(
            aid = session.aid,
            bvid = session.bvid,
            cid = session.cid,
            startTsSec = session.startTsSec,
            snapshot = snapshot,
            creatorMid = session.creatorMid,
            creatorName = session.creatorName,
            deltaWatchSec = deltaWatchSec,
        )
    }

    private suspend fun reportPlaybackHeartbeat(
        forceFlush: Boolean,
        reason: String,
    ): Boolean {
        val report = captureHeartbeatReport(forceFlush = forceFlush) ?: return false
        return sendHeartbeatReport(report = report, reason = reason, updateSessionState = true)
    }

    private suspend fun sendHeartbeatReport(
        report: PlaybackHeartbeatReport,
        reason: String,
        updateSessionState: Boolean,
    ): Boolean {
        val appContext = NetworkModule.appContext
        if (appContext != null) {
            PlaybackResumeStore.save(
                context = appContext,
                bvid = report.bvid,
                cid = report.cid,
                positionMs = report.snapshot.playedTimeSec * 1000L,
                durationMs = report.snapshot.durationMs,
            )
        }
        PlayUrlCache.invalidate(
            bvid = report.bvid,
            cid = report.cid,
        )
        val historyReported = if (report.aid > 0L) {
            PlaybackRepository.reportPlayHistoryProgress(
                aid = report.aid,
                cid = report.cid,
                progressSec = report.snapshot.playedTimeSec,
            )
        } else {
            false
        }
        val heartbeatReported = PlaybackRepository.reportPlayHeartbeat(
            bvid = report.bvid,
            cid = report.cid,
            playedTime = report.snapshot.playedTimeSec,
            realPlayedTime = report.snapshot.realPlayedTimeSec,
            startTsSec = report.startTsSec,
        )
        val reported = historyReported || heartbeatReported
        if (reported && updateSessionState &&
            heartbeatRuntime.bvid == report.bvid &&
            heartbeatRuntime.cid == report.cid
        ) {
            heartbeatRuntime = heartbeatRuntime.copy(lastReportedSnapshot = report.snapshot)
        }
        if (reported) {
            PlaybackHistorySyncBus.publish(
                mid = TokenManager.midCache ?: 0L,
                bvid = report.bvid,
                cid = report.cid,
            )
            if (appContext != null && report.deltaWatchSec > 0L) {
                TodayWatchProfileStore.recordWatchProgress(
                    context = appContext,
                    mid = report.creatorMid,
                    creatorName = report.creatorName,
                    deltaWatchSec = report.deltaWatchSec,
                    watchedAtSec = System.currentTimeMillis() / 1000L,
                )
            }
        }
        if (!historyReported) {
            Logger.w(
                TAG,
                "Remote history report failed; server-side resume state may stay stale " +
                    "even if heartbeat succeeds. reason=$reason aid=${report.aid} bvid=${report.bvid} cid=${report.cid}",
            )
        }
        Logger.d(
            TAG,
            "history sync result=$reported history=$historyReported heartbeat=$heartbeatReported " +
                "reason=$reason aid=${report.aid} bvid=${report.bvid} cid=${report.cid} " +
                "played=${report.snapshot.playedTimeSec} real=${report.snapshot.realPlayedTimeSec} " +
                "delta=${report.deltaWatchSec} startTs=${report.startTsSec}",
        )
        return reported
    }

    private fun Long?.orZero(): Long = this ?: 0L

    private companion object {
        const val TAG = "PlayerVM"
    }
}
