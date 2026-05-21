package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.data.model.response.DashAudio
import com.bbttvv.app.data.model.response.DashVideo
import com.bbttvv.app.feature.video.usecase.PlaybackSource

internal const val PLAYBACK_BUFFERING_STALL_RECOVERY_MS = 8_000L
internal const val PLAYBACK_BUFFERING_STALL_MIN_ADVANCE_MS = 1_500L
internal const val PLAYBACK_BUFFERING_STALL_RECOVERY_COOLDOWN_MS = 12_000L

internal data class PlaybackBufferingStallSample(
    val sessionKey: String,
    val nowMs: Long,
    val isBuffering: Boolean,
    val isLoadingPlaybackInfo: Boolean,
    val hasPlaybackError: Boolean,
    val playWhenReady: Boolean,
    val bufferedPositionMs: Long,
    val hasPlaybackSource: Boolean,
)

internal class PlaybackBufferingStallTracker(
    private val stallThresholdMs: Long = PLAYBACK_BUFFERING_STALL_RECOVERY_MS,
    private val minBufferAdvanceMs: Long = PLAYBACK_BUFFERING_STALL_MIN_ADVANCE_MS,
    private val recoveryCooldownMs: Long = PLAYBACK_BUFFERING_STALL_RECOVERY_COOLDOWN_MS,
) {
    private var activeSessionKey: String? = null
    private var bufferingStartedAtMs: Long = 0L
    private var lastBufferAdvancedAtMs: Long = 0L
    private var lastBufferedPositionMs: Long = 0L
    private var lastRecoveryAtMs: Long = Long.MIN_VALUE

    fun observe(sample: PlaybackBufferingStallSample): Boolean {
        if (!sample.isRecoverableBuffering()) {
            resetActiveBuffering()
            return false
        }

        if (activeSessionKey != sample.sessionKey) {
            startActiveBuffering(sample)
            return false
        }

        val bufferedPositionMs = sample.bufferedPositionMs.coerceAtLeast(0L)
        if (bufferedPositionMs - lastBufferedPositionMs >= minBufferAdvanceMs) {
            lastBufferedPositionMs = bufferedPositionMs
            lastBufferAdvancedAtMs = sample.nowMs
            return false
        }

        val hasBufferedLongEnough = sample.nowMs - bufferingStartedAtMs >= stallThresholdMs
        val hasStalledLongEnough = sample.nowMs - lastBufferAdvancedAtMs >= stallThresholdMs
        val isRecoveryCoolingDown = lastRecoveryAtMs != Long.MIN_VALUE &&
            sample.nowMs - lastRecoveryAtMs < recoveryCooldownMs
        if (!hasBufferedLongEnough || !hasStalledLongEnough || isRecoveryCoolingDown) {
            return false
        }

        lastRecoveryAtMs = sample.nowMs
        return true
    }

    fun reset() {
        resetActiveBuffering()
        lastRecoveryAtMs = Long.MIN_VALUE
    }

    private fun PlaybackBufferingStallSample.isRecoverableBuffering(): Boolean {
        return sessionKey.isNotBlank() &&
            isBuffering &&
            !isLoadingPlaybackInfo &&
            !hasPlaybackError &&
            playWhenReady &&
            hasPlaybackSource
    }

    private fun startActiveBuffering(sample: PlaybackBufferingStallSample) {
        activeSessionKey = sample.sessionKey
        bufferingStartedAtMs = sample.nowMs
        lastBufferAdvancedAtMs = sample.nowMs
        lastBufferedPositionMs = sample.bufferedPositionMs.coerceAtLeast(0L)
    }

    private fun resetActiveBuffering() {
        activeSessionKey = null
        bufferingStartedAtMs = 0L
        lastBufferAdvancedAtMs = 0L
        lastBufferedPositionMs = 0L
    }
}

internal fun PlaybackSource.rotateForBufferingStall(): PlaybackSource? {
    return if (segmentUrls.isNotEmpty()) {
        rotateSegmentCandidatesForBufferingStall()
    } else {
        rotateDashCandidatesForBufferingStall()
    }
}

internal fun PlaybackSource.withPrioritizedDashPlaybackCandidates(
    selectedVideoUrl: String,
    selectedAudioUrl: String?,
    videoCandidates: List<String> = videoUrlCandidates,
    audioCandidates: List<String> = audioUrlCandidates,
): PlaybackSource {
    val prioritizedVideoCandidates = prioritizePlaybackCandidate(
        selectedUrl = selectedVideoUrl,
        candidates = videoCandidates
    )
    val prioritizedAudioCandidates = prioritizePlaybackCandidate(
        selectedUrl = selectedAudioUrl,
        candidates = audioCandidates
    )
    return copy(
        videoUrl = selectedVideoUrl,
        audioUrl = selectedAudioUrl,
        videoUrlCandidates = prioritizedVideoCandidates,
        audioUrlCandidates = prioritizedAudioCandidates,
        fallbackVideoUrl = null,
        fallbackAudioUrl = null,
        selectedDashVideo = selectedDashVideo?.withSelectedUrl(
            selectedUrl = selectedVideoUrl,
            candidates = prioritizedVideoCandidates
        ),
        selectedDashAudio = selectedAudioUrl?.let { audioUrl ->
            selectedDashAudio?.withSelectedUrl(
                selectedUrl = audioUrl,
                candidates = prioritizedAudioCandidates
            )
        },
    )
}

private fun PlaybackSource.rotateDashCandidatesForBufferingStall(): PlaybackSource? {
    val nextVideo = rotatePlaybackCandidate(
        currentUrl = videoUrl,
        candidates = videoUrlCandidates
    )
    val nextAudio = audioUrl?.let { currentAudioUrl ->
        rotatePlaybackCandidate(
            currentUrl = currentAudioUrl,
            candidates = audioUrlCandidates
        )
    }

    if (nextVideo == null && nextAudio == null) return null

    return withPrioritizedDashPlaybackCandidates(
        selectedVideoUrl = nextVideo?.selectedUrl ?: videoUrl,
        selectedAudioUrl = nextAudio?.selectedUrl ?: audioUrl,
        videoCandidates = nextVideo?.candidates ?: videoUrlCandidates,
        audioCandidates = nextAudio?.candidates ?: audioUrlCandidates
    )
}

private fun PlaybackSource.rotateSegmentCandidatesForBufferingStall(): PlaybackSource? {
    var changed = false
    val nextSegmentUrls = ArrayList<String>(segmentUrls.size)
    val nextSegmentCandidates = ArrayList<List<String>>(segmentUrls.size)

    segmentUrls.forEachIndexed { index, segmentUrl ->
        val rotation = rotatePlaybackCandidate(
            currentUrl = segmentUrl,
            candidates = segmentUrlCandidates.getOrNull(index).orEmpty()
        )
        if (rotation != null) {
            changed = true
            nextSegmentUrls += rotation.selectedUrl
            nextSegmentCandidates += rotation.candidates
        } else {
            nextSegmentUrls += segmentUrl
            nextSegmentCandidates += prioritizePlaybackCandidate(
                selectedUrl = segmentUrl,
                candidates = segmentUrlCandidates.getOrNull(index).orEmpty()
            )
        }
    }

    if (!changed) return null

    return copy(
        videoUrl = nextSegmentUrls.firstOrNull().orEmpty(),
        segmentUrls = nextSegmentUrls,
        segmentUrlCandidates = nextSegmentCandidates,
        fallbackVideoUrl = null,
        fallbackAudioUrl = null,
    )
}

private data class PlaybackCandidateRotation(
    val selectedUrl: String,
    val candidates: List<String>,
)

private fun rotatePlaybackCandidate(
    currentUrl: String?,
    candidates: List<String>
): PlaybackCandidateRotation? {
    val normalizedCandidates = normalizePlaybackCandidates(
        selectedUrl = currentUrl,
        candidates = candidates
    )
    if (normalizedCandidates.size <= 1) return null

    val normalizedCurrentUrl = currentUrl.orEmpty().trim()
    val currentIndex = normalizedCandidates.indexOf(normalizedCurrentUrl)
        .takeIf { it >= 0 }
        ?: 0
    val nextUrl = normalizedCandidates[(currentIndex + 1) % normalizedCandidates.size]
    if (nextUrl == normalizedCurrentUrl) return null

    return PlaybackCandidateRotation(
        selectedUrl = nextUrl,
        candidates = prioritizePlaybackCandidate(
            selectedUrl = nextUrl,
            candidates = normalizedCandidates
        )
    )
}

private fun prioritizePlaybackCandidate(
    selectedUrl: String?,
    candidates: List<String>
): List<String> {
    return normalizePlaybackCandidates(
        selectedUrl = selectedUrl,
        candidates = candidates
    )
}

private fun normalizePlaybackCandidates(
    selectedUrl: String?,
    candidates: List<String>
): List<String> {
    return buildList {
        selectedUrl?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        candidates
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach(::add)
    }.distinct()
}

private fun DashVideo.withSelectedUrl(
    selectedUrl: String,
    candidates: List<String>
): DashVideo {
    return copy(
        baseUrl = selectedUrl,
        backupUrl = candidates.drop(1).takeIf { it.isNotEmpty() }
    )
}

private fun DashAudio.withSelectedUrl(
    selectedUrl: String,
    candidates: List<String>
): DashAudio {
    return copy(
        baseUrl = selectedUrl,
        backupUrl = candidates.drop(1).takeIf { it.isNotEmpty() }
    )
}
