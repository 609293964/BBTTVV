package com.bbttvv.app.feature.video.danmaku

import kotlin.math.abs

internal const val DANMAKU_POSITION_DISCONTINUITY_THRESHOLD_MS = 2500L
internal const val DANMAKU_HIGH_SPEED_THRESHOLD = 1.75f
internal const val DANMAKU_HIGH_SPEED_SOFT_SYNC_INTERVAL_MS = 30_000L

internal enum class DanmakuSyncReason {
    PayloadChanged,
    ConfigChanged,
    ViewAttached,
    ViewportReady,
    ViewportChanged,
    PositionDiscontinuity,
    PlayStateChanged,
    PlaybackSpeedChanged,
    HighSpeedDriftCorrection,
}

internal sealed interface DanmakuOverlaySyncDecision {
    data class WaitForAttach(val pendingReason: DanmakuSyncReason) : DanmakuOverlaySyncDecision
    data class WaitForViewport(val pendingReason: DanmakuSyncReason) : DanmakuOverlaySyncDecision
    data class HardSync(val reason: DanmakuSyncReason) : DanmakuOverlaySyncDecision
    object SoftSyncPlayState : DanmakuOverlaySyncDecision
    data class SoftSyncTimeline(val reason: DanmakuSyncReason) : DanmakuOverlaySyncDecision
    object Noop : DanmakuOverlaySyncDecision
}

internal class DanmakuOverlaySyncState(
    private val discontinuityThresholdMs: Long = DANMAKU_POSITION_DISCONTINUITY_THRESHOLD_MS,
) {
    private var lastLoadedToken: Long = Long.MIN_VALUE
    private var lastAppliedConfigToken: Long = Long.MIN_VALUE
    private var lastObservedPosition: Long = 0L
    private var lastObservedPlayState: Boolean = false
    private var lastAppliedPlaybackSpeed: Float = 1f
    private var lastTimelineSyncElapsedMs: Long = Long.MIN_VALUE
    private var lastAppliedAttachToken: Int = Int.MIN_VALUE
    private var lastAppliedViewportWidth: Int = 0
    private var lastAppliedViewportHeight: Int = 0
    private var pendingHardSyncReason: DanmakuSyncReason? = null

    var waitingForAttachment: Boolean = false
        private set
    var waitingForViewport: Boolean = false
        private set

    val hasLoadedData: Boolean
        get() = lastLoadedToken != Long.MIN_VALUE

    fun onDisabled() {
        waitingForAttachment = false
        waitingForViewport = false
        pendingHardSyncReason = null
        lastObservedPlayState = false
        lastAppliedPlaybackSpeed = 1f
        lastTimelineSyncElapsedMs = Long.MIN_VALUE
        lastAppliedConfigToken = Long.MIN_VALUE
    }

    fun resetLoadedState() {
        lastLoadedToken = Long.MIN_VALUE
        lastAppliedAttachToken = Int.MIN_VALUE
        lastAppliedViewportWidth = 0
        lastAppliedViewportHeight = 0
        lastAppliedConfigToken = Long.MIN_VALUE
        pendingHardSyncReason = null
        waitingForAttachment = false
        waitingForViewport = false
        lastObservedPlayState = false
    }

    fun decide(
        viewAttached: Boolean,
        viewportWidth: Int,
        viewportHeight: Int,
        attachToken: Int,
        dataToken: Long,
        configToken: Long,
        isPlaying: Boolean,
        playbackPositionMs: Long,
        playbackSpeed: Float = 1f,
        elapsedRealtimeMs: Long = 0L,
    ): DanmakuOverlaySyncDecision {
        val normalizedPlaybackSpeed = normalizeDanmakuPlaybackSpeed(playbackSpeed)
        val viewportReady = viewportWidth > 0 && viewportHeight > 0
        val reason = pendingHardSyncReason ?: nextHardSyncReason(
            attachToken = attachToken,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            dataToken = dataToken,
            configToken = configToken,
            isPlaying = isPlaying,
            playbackPositionMs = playbackPositionMs,
            playbackSpeed = normalizedPlaybackSpeed,
        )

        if (!viewAttached) {
            val pendingReason = reason ?: DanmakuSyncReason.ViewAttached
            pendingHardSyncReason = pendingReason
            waitingForAttachment = true
            lastAppliedAttachToken = Int.MIN_VALUE
            return DanmakuOverlaySyncDecision.WaitForAttach(pendingReason)
        }

        waitingForAttachment = false

        if (!viewportReady) {
            val pendingReason = reason ?: DanmakuSyncReason.ViewportReady
            pendingHardSyncReason = pendingReason
            waitingForViewport = true
            lastAppliedViewportWidth = 0
            lastAppliedViewportHeight = 0
            return DanmakuOverlaySyncDecision.WaitForViewport(pendingReason)
        }

        waitingForViewport = false

        if (reason != null) {
            pendingHardSyncReason = null
            return DanmakuOverlaySyncDecision.HardSync(reason)
        }

        return if (shouldSoftSyncHighSpeedTimeline(normalizedPlaybackSpeed, isPlaying, elapsedRealtimeMs)) {
            DanmakuOverlaySyncDecision.SoftSyncTimeline(DanmakuSyncReason.HighSpeedDriftCorrection)
        } else if (isPlaying != lastObservedPlayState) {
            DanmakuOverlaySyncDecision.SoftSyncPlayState
        } else {
            DanmakuOverlaySyncDecision.Noop
        }
    }

    fun notifyHardSynced(
        dataToken: Long,
        configToken: Long,
        attachToken: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        positionMs: Long,
        isPlaying: Boolean,
        playbackSpeed: Float = 1f,
        elapsedRealtimeMs: Long = 0L,
    ) {
        lastLoadedToken = dataToken
        lastAppliedConfigToken = configToken
        lastAppliedAttachToken = attachToken
        lastAppliedViewportWidth = viewportWidth
        lastAppliedViewportHeight = viewportHeight
        lastObservedPosition = positionMs
        lastObservedPlayState = isPlaying
        lastAppliedPlaybackSpeed = normalizeDanmakuPlaybackSpeed(playbackSpeed)
        lastTimelineSyncElapsedMs = elapsedRealtimeMs
        pendingHardSyncReason = null
        waitingForAttachment = false
        waitingForViewport = false
    }

    fun notifySoftPlayStateObserved(isPlaying: Boolean) {
        lastObservedPlayState = isPlaying
    }

    fun notifyPositionObserved(positionMs: Long) {
        lastObservedPosition = positionMs
    }

    fun notifySoftTimelineSynced(
        positionMs: Long,
        isPlaying: Boolean,
        playbackSpeed: Float,
        elapsedRealtimeMs: Long,
    ) {
        lastObservedPosition = positionMs
        lastObservedPlayState = isPlaying
        lastAppliedPlaybackSpeed = normalizeDanmakuPlaybackSpeed(playbackSpeed)
        lastTimelineSyncElapsedMs = elapsedRealtimeMs
    }

    private fun nextHardSyncReason(
        attachToken: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        dataToken: Long,
        configToken: Long,
        isPlaying: Boolean,
        playbackPositionMs: Long,
        playbackSpeed: Float,
    ): DanmakuSyncReason? {
        return when {
            dataToken != lastLoadedToken -> DanmakuSyncReason.PayloadChanged
            configToken != lastAppliedConfigToken -> DanmakuSyncReason.ConfigChanged
            attachToken != lastAppliedAttachToken -> DanmakuSyncReason.ViewAttached
            lastAppliedViewportWidth <= 0 || lastAppliedViewportHeight <= 0 -> DanmakuSyncReason.ViewportReady
            viewportWidth != lastAppliedViewportWidth || viewportHeight != lastAppliedViewportHeight ->
                DanmakuSyncReason.ViewportChanged
            abs(playbackSpeed - lastAppliedPlaybackSpeed) > 0.01f ->
                DanmakuSyncReason.PlaybackSpeedChanged
            isPlaying && isPlaying != lastObservedPlayState -> DanmakuSyncReason.PlayStateChanged
            abs(playbackPositionMs - lastObservedPosition) > discontinuityThresholdMs ->
                DanmakuSyncReason.PositionDiscontinuity
            else -> null
        }
    }


    private fun shouldSoftSyncHighSpeedTimeline(
        playbackSpeed: Float,
        isPlaying: Boolean,
        elapsedRealtimeMs: Long,
    ): Boolean {
        if (!isPlaying || playbackSpeed < DANMAKU_HIGH_SPEED_THRESHOLD) return false
        if (lastTimelineSyncElapsedMs == Long.MIN_VALUE) return false
        return elapsedRealtimeMs - lastTimelineSyncElapsedMs >= DANMAKU_HIGH_SPEED_SOFT_SYNC_INTERVAL_MS
    }
}

internal fun normalizeDanmakuPlaybackSpeed(speed: Float): Float {
    return speed.takeIf { it.isFinite() }?.coerceIn(0.1f, 4f) ?: 1f
}
