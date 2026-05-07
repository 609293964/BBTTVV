package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.data.model.response.PbpHeatmapResponse
import com.bbttvv.app.data.repository.PbpHeatmapRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun normalizePbpHeatmapPoints(
    response: PbpHeatmapResponse,
    durationMs: Long,
): List<ProgressHeatmapPoint> {
    val rawPoints = response.events.default
    val stepSec = response.stepSec
    if (durationMs <= 0L || stepSec <= 0.0 || !stepSec.isFinite() || rawPoints.isEmpty()) {
        return emptyList()
    }

    val finitePoints = rawPoints.map { value ->
        if (value.isFinite()) value else 0.0
    }
    val maxValue = finitePoints.maxOrNull()?.takeIf { it > 0.0 } ?: return emptyList()

    return finitePoints.mapIndexed { index, value ->
        val timeMs = index * stepSec * 1000.0
        ProgressHeatmapPoint(
            fraction = (timeMs / durationMs.toDouble()).toFloat().coerceIn(0f, 1f),
            intensity = (value / maxValue).toFloat().coerceIn(0f, 1f),
        )
    }
}

internal class ProgressHeatmapController(
    private val scope: CoroutineScope,
    private val isCurrentPlayback: (bvid: String, cid: Long) -> Boolean,
    private val updateUiState: (((PlayerUiState) -> PlayerUiState) -> Unit),
) {
    private var loadJob: Job? = null

    fun load(
        bvid: String,
        aid: Long,
        cid: Long,
        durationMs: Long,
    ) {
        loadJob?.cancel()
        val requestBvid = bvid.trim()
        if (requestBvid.isBlank() || cid <= 0L || durationMs <= 0L) {
            updateUiState { it.copy(heatmapPoints = emptyList()) }
            return
        }

        loadJob = scope.launch {
            val points = PbpHeatmapRepository.getHeatmap(
                cid = cid,
                aid = aid,
                bvid = requestBvid,
            ).getOrNull()?.let { response ->
                normalizePbpHeatmapPoints(
                    response = response,
                    durationMs = durationMs,
                )
            }.orEmpty()

            if (!isCurrentPlayback(requestBvid, cid)) {
                return@launch
            }
            updateUiState { it.copy(heatmapPoints = points) }
        }
    }

    fun clear() {
        loadJob?.cancel()
        loadJob = null
        updateUiState { it.copy(heatmapPoints = emptyList()) }
    }
}
