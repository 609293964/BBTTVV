package com.bbttvv.app.feature.video.viewmodel

import android.os.SystemClock
import com.bbttvv.app.data.repository.DanmakuRepository
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.feature.video.danmaku.DanmakuProto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class DanmakuVoteController(
    private val scope: CoroutineScope,
    private val submitVote: suspend (DanmakuVoteSubmitRequest) -> Result<Unit> = { request ->
        if (request.prompt.kind == DanmakuVoteKind.Grade) {
            val score = request.option.gradeScore
            if (score == null) {
                Result.failure(IllegalArgumentException("无效评分"))
            } else {
                DanmakuRepository.submitDanmakuGrade(
                    aid = request.aid, cid = request.cid, progressMs = request.progressMs,
                    gradeId = request.prompt.voteId, gradeScore = score,
                )
            }
        } else DanmakuRepository.submitDanmakuVote(
            aid = request.aid,
            cid = request.cid,
            progressMs = request.progressMs,
            voteId = request.prompt.voteId,
            voteType = request.prompt.voteType,
            commandId = request.prompt.commandId,
            optionId = request.option.id,
            hasSelfDefined = request.option.hasSelfDefined,
        )
    },
    private val nowElapsedMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _uiState = MutableStateFlow<DanmakuVoteUiState>(DanmakuVoteUiState.Hidden)
    val uiState: StateFlow<DanmakuVoteUiState> = _uiState.asStateFlow()

    private var generation = 0L
    private var aid = 0L
    private var cid = 0L
    private var lastPositionMs = 0L
    private var prompts = emptyList<DanmakuVotePrompt>()
    private val handledVoteIds = mutableSetOf<String>()
    private var timeoutJob: Job? = null
    private var submitJob: Job? = null
    private var overlaySuppressed = false
    private var suspendedRemainingMs: Long? = null

    fun initialize(aid: Long, cid: Long, commands: List<DanmakuProto.CommandDm>) {
        cancel()
        if (aid <= 0L || cid <= 0L) return
        val parsed = parseDanmakuVotePrompts(commands)
        if (parsed.isEmpty()) {
            Logger.d("DanmakuVote", "No supported vote commands cid=$cid commands=${commands.size}")
            return
        }
        this.aid = aid
        this.cid = cid
        prompts = parsed
        Logger.d(
            "DanmakuVote",
            "Vote prompts ready cid=$cid count=${parsed.size} firstAtMs=${parsed.first().triggerPositionMs}"
        )
    }

    fun syncPlaybackPosition(positionMs: Long) {
        lastPositionMs = positionMs.coerceAtLeast(0L)
        when (_uiState.value) {
            is DanmakuVoteUiState.Showing,
            is DanmakuVoteUiState.Submitting,
            is DanmakuVoteUiState.RetryableError,
            is DanmakuVoteUiState.Submitted,
            -> return

            DanmakuVoteUiState.Hidden -> Unit
        }
        prompts.forEach { prompt ->
            if (prompt.interactionKey in handledVoteIds) return@forEach
            val endPositionMs = prompt.triggerPositionMs + prompt.durationMs
            if (lastPositionMs > endPositionMs) {
                return@forEach
            }
            if (lastPositionMs >= prompt.triggerPositionMs) {
                show(prompt, remainingMs = endPositionMs - lastPositionMs)
                return
            }
        }
    }

    fun setOverlaySuppressed(suppressed: Boolean) {
        if (overlaySuppressed == suppressed) return
        overlaySuppressed = suppressed
        val showing = _uiState.value as? DanmakuVoteUiState.Showing ?: return
        if (suppressed) {
            suspendedRemainingMs = (showing.deadlineElapsedMs - nowElapsedMs()).coerceAtLeast(1L)
            timeoutJob?.cancel()
            timeoutJob = null
        } else {
            val remaining = suspendedRemainingMs ?: return
            suspendedRemainingMs = null
            startTimeout(showing.prompt, remaining)
        }
    }

    fun selectOption(index: Int) {
        val showing = _uiState.value as? DanmakuVoteUiState.Showing ?: return
        if (showing.prompt.selectedOptionId > 0) return
        submit(showing.prompt, index)
    }

    fun retry() {
        val error = _uiState.value as? DanmakuVoteUiState.RetryableError ?: return
        submit(error.prompt, error.selectedIndex)
    }

    fun hide() {
        val prompt = when (val state = _uiState.value) {
            is DanmakuVoteUiState.Showing -> state.prompt
            is DanmakuVoteUiState.Submitting -> state.prompt
            is DanmakuVoteUiState.RetryableError -> state.prompt
            is DanmakuVoteUiState.Submitted -> state.prompt
            else -> return
        }
        generation += 1
        handledVoteIds += prompt.interactionKey
        timeoutJob?.cancel()
        suspendedRemainingMs = null
        submitJob?.cancel()
        timeoutJob = null
        submitJob = null
        _uiState.value = DanmakuVoteUiState.Hidden
    }

    fun cancel() {
        generation += 1
        timeoutJob?.cancel()
        submitJob?.cancel()
        timeoutJob = null
        submitJob = null
        aid = 0L
        cid = 0L
        lastPositionMs = 0L
        prompts = emptyList()
        handledVoteIds.clear()
        _uiState.value = DanmakuVoteUiState.Hidden
    }

    private fun show(prompt: DanmakuVotePrompt, remainingMs: Long) {
        val safeRemainingMs = remainingMs.coerceAtLeast(1L)
        Logger.d(
            "DanmakuVote",
            "Showing vote cid=$cid voteId=${prompt.voteId} positionMs=$lastPositionMs remainingMs=$safeRemainingMs"
        )
        _uiState.value = DanmakuVoteUiState.Showing(
            prompt = prompt,
            deadlineElapsedMs = nowElapsedMs() + safeRemainingMs,
        )
        if (!overlaySuppressed) startTimeout(prompt, safeRemainingMs)
    }

    private fun startTimeout(prompt: DanmakuVotePrompt, remainingMs: Long) {
        timeoutJob?.cancel()
        val token = generation
        timeoutJob = scope.launch {
            delay(remainingMs)
            if (generation != token) return@launch
            handledVoteIds += prompt.interactionKey
            _uiState.value = DanmakuVoteUiState.Hidden
        }
    }

    private fun submit(prompt: DanmakuVotePrompt, index: Int) {
        val option = prompt.options.getOrNull(index) ?: return
        timeoutJob?.cancel()
        _uiState.value = DanmakuVoteUiState.Submitting(prompt, index)
        val request = DanmakuVoteSubmitRequest(
            aid = aid,
            cid = cid,
            progressMs = lastPositionMs,
            prompt = prompt,
            option = option,
        )
        val token = generation
        submitJob?.cancel()
        submitJob = scope.launch {
            val result = try {
                submitVote(request)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = {
                    if (generation != token) return@fold
                    handledVoteIds += prompt.interactionKey
                    val updatedPrompt = prompt.copy(
                        options = prompt.options.map { current ->
                            if (prompt.kind == DanmakuVoteKind.Vote && current.id == option.id) {
                                current.copy(count = current.count + 1)
                            } else current
                        },
                        participantCount = if (prompt.kind == DanmakuVoteKind.Grade) {
                            (prompt.participantCount.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        } else prompt.participantCount,
                        selectedOptionId = option.id,
                    )
                    _uiState.value = DanmakuVoteUiState.Submitted(updatedPrompt, index)
                    delay(1_500L)
                    if (generation == token) _uiState.value = DanmakuVoteUiState.Hidden
                },
                onFailure = { error ->
                    if (generation != token) return@fold
                    _uiState.value = DanmakuVoteUiState.RetryableError(
                        prompt = prompt,
                        selectedIndex = index,
                        message = error.message?.takeIf { it.isNotBlank() } ?: if (prompt.kind == DanmakuVoteKind.Grade) {
                            "打分失败，请重试"
                        } else "投票失败，请重试",
                    )
                },
            )
        }
    }
}
