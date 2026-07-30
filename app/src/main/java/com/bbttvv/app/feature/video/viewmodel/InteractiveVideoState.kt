package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.data.model.response.InteractChoice
import com.bbttvv.app.data.model.response.InteractEdgeInfoData
import com.bbttvv.app.data.model.response.InteractQuestion

internal const val INTERACTIVE_FALLBACK_DURATION_MS = 8_000L
internal const val INTERACTIVE_FALLBACK_TRIGGER_OFFSET_MS = 300L

data class InteractiveVideoOption(
    val edgeId: Long,
    val cid: Long,
    val text: String,
    val isDefault: Boolean,
)

sealed interface InteractiveVideoUiState {
    data object Hidden : InteractiveVideoUiState
    data class WaitingTrigger(
        val question: String,
        val options: List<InteractiveVideoOption>,
        val triggerPositionMs: Long,
        val durationMs: Long,
        val pauseVideo: Boolean,
    ) : InteractiveVideoUiState
    data class Showing(
        val question: String,
        val options: List<InteractiveVideoOption>,
        val defaultIndex: Int,
        val deadlineElapsedMs: Long,
        val pauseVideo: Boolean,
    ) : InteractiveVideoUiState
    data class LoadingBranch(
        val question: String,
        val options: List<InteractiveVideoOption>,
        val selectedIndex: Int,
    ) : InteractiveVideoUiState
    data class RetryableError(
        val question: String,
        val options: List<InteractiveVideoOption>,
        val selectedIndex: Int,
        val message: String,
    ) : InteractiveVideoUiState
    data object Ended : InteractiveVideoUiState
}

internal data class InteractiveBranchContext(
    val sessionGeneration: Long,
    val sourceEdgeId: Long,
    val targetEdgeId: Long,
    val targetCid: Long,
    val playWhenReadyAfterLoad: Boolean,
)

internal fun InteractQuestion.visibleRegularOptions(): List<InteractiveVideoOption> =
    choices.mapNotNull(InteractChoice::toRegularOption)

private fun InteractChoice.toRegularOption(): InteractiveVideoOption? {
    if (id <= 0L || cid <= 0L || option.isBlank() || isHidden != 0 || condition.isNotBlank()) return null
    return InteractiveVideoOption(
        edgeId = id,
        cid = cid,
        text = option.trim(),
        isDefault = isDefault == 1,
    )
}

internal fun resolveInteractiveDefaultIndex(options: List<InteractiveVideoOption>): Int =
    options.indexOfFirst { it.isDefault }.takeIf { it >= 0 } ?: 0

internal fun resolveInteractiveDurationMs(durationSeconds: Int): Long =
    if (durationSeconds > 0) durationSeconds.coerceIn(2, 30) * 1_000L else INTERACTIVE_FALLBACK_DURATION_MS

internal fun resolveInteractiveTriggerPositionMs(
    mediaDurationMs: Long,
    startTimeR: Int,
): Long {
    if (mediaDurationMs <= 0L) return 0L
    val offset = startTimeR.toLong().takeIf { it > 0L } ?: INTERACTIVE_FALLBACK_TRIGGER_OFFSET_MS
    return (mediaDurationMs - offset).coerceIn(0L, mediaDurationMs)
}

internal fun InteractEdgeInfoData.firstRegularQuestion(): InteractQuestion? =
    edges?.questions.orEmpty().firstOrNull()
