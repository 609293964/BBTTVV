package com.bbttvv.app.feature.video.viewmodel

import androidx.annotation.MainThread
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.util.MediaUtils
import com.bbttvv.app.feature.video.usecase.PlaybackSource
import com.bbttvv.app.feature.video.usecase.VideoPlaybackUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class PlaybackQualityController(
    private val scope: CoroutineScope,
    private val playbackUseCase: VideoPlaybackUseCase,
    private val ensureMainThread: (String) -> Unit,
    private val currentUiState: () -> PlayerUiState,
    private val currentSource: () -> PlaybackSource?,
    private val resolveCdnPreference: () -> SettingsManager.PlayerCdnPreference,
    private val isCurrentPlayback: (bvid: String, cid: Long) -> Boolean,
    private val updateUiState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val switchPlaybackSource: (source: PlaybackSource, statusMessage: String) -> Unit,
) {
    private var qualityChangeJob: Job? = null
    private var qualityChangeGeneration: Long = 0L

    @MainThread
    fun changeQuality(qualityId: Int) {
        ensureMainThread("PlaybackQualityController.changeQuality")
        val state = currentUiState()
        val info = state.info ?: return
        state.qualityOptions.firstOrNull { it.id == qualityId && !it.isSupported }?.let { option ->
            updateUiState {
                it.copy(
                    statusMessage = option.unsupportedReason ?: "当前设备暂不支持该画质",
                )
            }
            return
        }

        qualityChangeJob?.cancel()
        val qualityGeneration = ++qualityChangeGeneration
        val qualityBvid = info.bvid
        val qualityCid = info.cid
        qualityChangeJob = scope.launch {
            val appContext = NetworkModule.appContext
            val hdrSupported = MediaUtils.isHdrSupported(appContext)
            val dolbyVisionSupported = MediaUtils.isDolbyVisionSupported(appContext)
            val hevcSupported = MediaUtils.isHevcSupported()
            val av1Supported = MediaUtils.isAv1Supported()
            val previousSource = currentSource()
            val cdnPreference = resolveCdnPreference()

            if (!isCurrentQualityChange(qualityGeneration, qualityBvid, qualityCid)) {
                return@launch
            }

            updateUiState {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = "正在切换画质...",
                )
            }

            val loadedSource = playbackUseCase.changeQuality(
                bvid = qualityBvid,
                cid = qualityCid,
                qualityId = qualityId,
                cdnPreference = cdnPreference,
                isHevcSupported = hevcSupported,
                isAv1Supported = av1Supported,
                isHdrSupported = hdrSupported,
                isDolbyVisionSupported = dolbyVisionSupported,
            )
            if (!isCurrentQualityChange(qualityGeneration, qualityBvid, qualityCid)) {
                return@launch
            }
            if (loadedSource == null) {
                updateUiState {
                    it.copy(
                        isLoading = false,
                        errorMessage = "无法切换到该画质。",
                    )
                }
                return@launch
            }

            val nextSource = playbackUseCase.selectDashTracks(
                source = loadedSource,
                qualityId = loadedSource.actualQuality,
                videoCodecId = previousSource?.selectedVideoCodecId ?: loadedSource.selectedVideoCodecId,
                audioQualityId = previousSource?.selectedAudioQualityId ?: loadedSource.selectedAudioQualityId,
                cdnPreference = cdnPreference,
                isHevcSupported = hevcSupported,
                isAv1Supported = av1Supported,
                isHdrSupported = hdrSupported,
                isDolbyVisionSupported = dolbyVisionSupported,
            ) ?: loadedSource

            if (!isCurrentQualityChange(qualityGeneration, qualityBvid, qualityCid)) {
                return@launch
            }

            switchPlaybackSource(
                nextSource,
                "画质：${nextSource.qualityOptions.firstOrNull { it.id == nextSource.actualQuality }?.label ?: nextSource.actualQuality}",
            )
        }
    }

    @MainThread
    fun cancelPending() {
        ensureMainThread("PlaybackQualityController.cancelPending")
        qualityChangeJob?.cancel()
        qualityChangeJob = null
        qualityChangeGeneration += 1
    }

    private fun isCurrentQualityChange(
        generation: Long,
        bvid: String,
        cid: Long,
    ): Boolean {
        return generation == qualityChangeGeneration && isCurrentPlayback(bvid, cid)
    }
}
