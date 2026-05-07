package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.data.repository.SubtitleAndAuxRepository
import com.bbttvv.app.feature.video.videoshot.VideoShot
import com.bbttvv.app.feature.video.videoshot.VideoShotFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class VideoShotController(
    private val scope: CoroutineScope,
    private val isCurrentPlayback: (bvid: String, cid: Long) -> Boolean,
    private val currentSessionKey: () -> String,
) {
    private var loadJob: Job? = null
    private var frameJob: Job? = null
    private var videoShot: VideoShot? = null
    private var videoShotSessionKey: String = ""

    private val _seekPreviewFrame = MutableStateFlow<VideoShotFrame?>(null)
    val seekPreviewFrame: StateFlow<VideoShotFrame?> = _seekPreviewFrame.asStateFlow()

    fun load(bvid: String, cid: Long) {
        clear()
        val requestBvid = bvid.trim()
        if (requestBvid.isBlank() || cid <= 0L) return

        loadJob = scope.launch {
            val data = SubtitleAndAuxRepository.getVideoshot(
                bvid = requestBvid,
                cid = cid,
            ) ?: return@launch
            if (!isCurrentPlayback(requestBvid, cid)) {
                return@launch
            }

            val nextVideoShot = VideoShot.fromData(data) ?: return@launch
            if (!isCurrentPlayback(requestBvid, cid)) {
                nextVideoShot.clear()
                return@launch
            }

            videoShot?.clear()
            videoShot = nextVideoShot
            videoShotSessionKey = "$requestBvid:$cid"
        }
    }

    fun requestFrame(positionMs: Long) {
        val shot = videoShot ?: return
        val sessionKey = videoShotSessionKey
        frameJob?.cancel()
        frameJob = scope.launch {
            delay(SEEK_PREVIEW_DEBOUNCE_MS)
            val frame = runCatching {
                shot.getFrame(positionMs)
            }.getOrNull()
            if (currentSessionKey() == sessionKey && videoShot === shot) {
                _seekPreviewFrame.value = frame
            }
        }
    }

    fun clearPreview() {
        frameJob?.cancel()
        frameJob = null
        _seekPreviewFrame.value = null
    }

    fun clear() {
        loadJob?.cancel()
        frameJob?.cancel()
        loadJob = null
        frameJob = null
        videoShot?.clear()
        videoShot = null
        videoShotSessionKey = ""
        _seekPreviewFrame.value = null
    }

    private companion object {
        const val SEEK_PREVIEW_DEBOUNCE_MS = 80L
    }
}
