package com.bbttvv.app.feature.video.viewmodel

import android.os.SystemClock
import com.bbttvv.app.data.repository.VideoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class InteractiveVideoController(
    private val scope: CoroutineScope,
    private val pausePlayback: () -> Boolean,
    private val resumePlayback: () -> Unit,
    private val currentPlayWhenReady: () -> Boolean,
    private val loadBranch: (InteractiveBranchContext) -> Unit,
    private val showStatus: (String) -> Unit,
    private val nowElapsedMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _uiState = MutableStateFlow<InteractiveVideoUiState>(InteractiveVideoUiState.Hidden)
    val uiState: StateFlow<InteractiveVideoUiState> = _uiState.asStateFlow()

    private var generation = 0L
    private var bvid = ""
    private var graphVersion = 0L
    private var currentEdgeId = 0L
    private var currentCid = 0L
    private var requestJob: Job? = null
    private var timeoutJob: Job? = null
    private var hiddenByBack = false
    private var pausedByInteraction = false
    private var resumeIntent = false
    private var pendingContext: InteractiveBranchContext? = null

    fun initialize(bvid: String, graphVersion: Long, cid: Long, mediaDurationMs: Long) {
        cancel(resume = false)
        if (bvid.isBlank() || graphVersion <= 0L || cid <= 0L) return
        this.bvid = bvid
        this.graphVersion = graphVersion
        currentCid = cid
        val token = generation
        requestJob = scope.launch {
            VideoRepository.getInteractEdgeInfo(bvid, graphVersion).fold(
                onSuccess = { edge ->
                    if (isCurrent(token, bvid, cid)) prepareEdge(edge, mediaDurationMs)
                },
                onFailure = { /* Interactive failure must not affect ordinary playback. */ },
            )
        }
    }

    fun syncPlaybackPosition(positionMs: Long) {
        val waiting = _uiState.value as? InteractiveVideoUiState.WaitingTrigger ?: return
        if (positionMs >= waiting.triggerPositionMs) show(waiting)
    }

    fun selectOption(index: Int) {
        val showing = _uiState.value as? InteractiveVideoUiState.Showing ?: return
        val option = showing.options.getOrNull(index) ?: return
        timeoutJob?.cancel()
        val context = InteractiveBranchContext(
            sessionGeneration = generation,
            sourceEdgeId = currentEdgeId,
            targetEdgeId = option.edgeId,
            targetCid = option.cid,
            playWhenReadyAfterLoad = if (pausedByInteraction) resumeIntent else currentPlayWhenReady(),
        )
        pendingContext = context
        _uiState.value = InteractiveVideoUiState.LoadingBranch(showing.question, showing.options, index)
        requestNextNode(context)
    }

    fun retry() {
        val error = _uiState.value as? InteractiveVideoUiState.RetryableError ?: return
        val option = error.options.getOrNull(error.selectedIndex) ?: return
        _uiState.value = InteractiveVideoUiState.Showing(
            question = error.question,
            options = error.options,
            defaultIndex = error.selectedIndex,
            deadlineElapsedMs = Long.MAX_VALUE,
            pauseVideo = true,
        )
        selectOption(error.selectedIndex)
    }

    fun hide() {
        if (_uiState.value is InteractiveVideoUiState.Showing) {
            hiddenByBack = true
            _uiState.value = InteractiveVideoUiState.Hidden
        }
    }

    fun validate(context: InteractiveBranchContext): Boolean =
        context == pendingContext &&
            context.sessionGeneration == generation &&
            context.sourceEdgeId == currentEdgeId &&
            context.targetEdgeId > 0L &&
            context.targetCid > 0L

    fun onBranchLoaded(context: InteractiveBranchContext, mediaDurationMs: Long) {
        if (!validate(context)) return
        pendingContext = null
        currentEdgeId = context.targetEdgeId
        currentCid = context.targetCid
        resumeIfNeeded()
        val edge = pendingEdge
        pendingEdge = null
        if (edge != null) prepareEdge(edge, mediaDurationMs) else _uiState.value = InteractiveVideoUiState.Ended
    }

    fun onBranchLoadFailed(context: InteractiveBranchContext, message: String) {
        if (!validate(context)) return
        val loading = _uiState.value as? InteractiveVideoUiState.LoadingBranch ?: return
        _uiState.value = InteractiveVideoUiState.RetryableError(
            loading.question, loading.options, loading.selectedIndex, message,
        )
    }

    fun cancel(resume: Boolean = true) {
        generation += 1
        requestJob?.cancel()
        timeoutJob?.cancel()
        requestJob = null
        timeoutJob = null
        pendingContext = null
        pendingEdge = null
        bvid = ""
        graphVersion = 0L
        currentEdgeId = 0L
        currentCid = 0L
        hiddenByBack = false
        _uiState.value = InteractiveVideoUiState.Hidden
        if (resume) resumeIfNeeded() else {
            pausedByInteraction = false
            resumeIntent = false
        }
    }

    private var pendingEdge: com.bbttvv.app.data.model.response.InteractEdgeInfoData? = null

    private fun requestNextNode(context: InteractiveBranchContext) {
        val requestBvid = bvid
        val requestGraph = graphVersion
        requestJob = scope.launch {
            VideoRepository.getInteractEdgeInfo(requestBvid, requestGraph, context.targetEdgeId).fold(
                onSuccess = { edge ->
                    if (!validate(context) || edge.edgeId != context.targetEdgeId) return@fold
                    pendingEdge = edge
                    loadBranch(context)
                },
                onFailure = { error ->
                    onBranchLoadFailed(context, error.message ?: "互动分支加载失败")
                },
            )
        }
    }

    private fun prepareEdge(
        edge: com.bbttvv.app.data.model.response.InteractEdgeInfoData,
        mediaDurationMs: Long,
    ) {
        currentEdgeId = edge.edgeId
        if (edge.isLeaf == 1) {
            _uiState.value = InteractiveVideoUiState.Ended
            resumeIfNeeded()
            return
        }
        val question = edge.firstRegularQuestion()
        val options = question?.visibleRegularOptions().orEmpty()
        if (question == null || options.isEmpty()) {
            _uiState.value = InteractiveVideoUiState.Ended
            showStatus("该互动节点暂不支持")
            resumeIfNeeded()
            return
        }
        hiddenByBack = false
        _uiState.value = InteractiveVideoUiState.WaitingTrigger(
            question = question.title.trim(),
            options = options,
            triggerPositionMs = resolveInteractiveTriggerPositionMs(mediaDurationMs, question.startTimeR),
            durationMs = resolveInteractiveDurationMs(question.duration),
            pauseVideo = question.pauseVideo == 1,
        )
    }

    private fun show(waiting: InteractiveVideoUiState.WaitingTrigger) {
        val deadline = nowElapsedMs() + waiting.durationMs
        if (waiting.pauseVideo) {
            resumeIntent = pausePlayback()
            pausedByInteraction = true
        }
        val showing = InteractiveVideoUiState.Showing(
            waiting.question,
            waiting.options,
            resolveInteractiveDefaultIndex(waiting.options),
            deadline,
            waiting.pauseVideo,
        )
        _uiState.value = showing
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(waiting.durationMs)
            if (generation <= 0L) return@launch
            if (hiddenByBack) {
                hiddenByBack = false
                _uiState.value = showing
            }
            selectOption(showing.defaultIndex)
        }
    }

    private fun resumeIfNeeded() {
        if (pausedByInteraction && resumeIntent) resumePlayback()
        pausedByInteraction = false
        resumeIntent = false
    }

    private fun isCurrent(token: Long, requestBvid: String, cid: Long): Boolean =
        generation == token && bvid == requestBvid && currentCid == cid
}
