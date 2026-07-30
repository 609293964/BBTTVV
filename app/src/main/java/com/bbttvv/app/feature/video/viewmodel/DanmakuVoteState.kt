package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.feature.video.danmaku.DanmakuProto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val DANMAKU_VOTE_FALLBACK_DURATION_MS = 8_000L
private const val DANMAKU_VOTE_MIN_DURATION_MS = 2_000L
private const val DANMAKU_VOTE_MAX_DURATION_MS = 30_000L

data class DanmakuVoteOption(
    val id: Int,
    val text: String,
    val count: Int,
    val hasSelfDefined: Boolean,
)

data class DanmakuVotePrompt(
    val commandId: String,
    val voteId: Long,
    val voteType: Int,
    val question: String,
    val options: List<DanmakuVoteOption>,
    val triggerPositionMs: Long,
    val durationMs: Long,
    val selectedOptionId: Int,
)

sealed interface DanmakuVoteUiState {
    data object Hidden : DanmakuVoteUiState

    data class Showing(
        val prompt: DanmakuVotePrompt,
        val deadlineElapsedMs: Long,
    ) : DanmakuVoteUiState

    data class Submitting(
        val prompt: DanmakuVotePrompt,
        val selectedIndex: Int,
    ) : DanmakuVoteUiState

    data class RetryableError(
        val prompt: DanmakuVotePrompt,
        val selectedIndex: Int,
        val message: String,
    ) : DanmakuVoteUiState

    data class Submitted(
        val prompt: DanmakuVotePrompt,
        val selectedIndex: Int,
    ) : DanmakuVoteUiState
}

internal data class DanmakuVoteSubmitRequest(
    val aid: Long,
    val cid: Long,
    val progressMs: Long,
    val prompt: DanmakuVotePrompt,
    val option: DanmakuVoteOption,
)

private val danmakuVoteJson = Json { ignoreUnknownKeys = true }

internal fun parseDanmakuVotePrompts(
    commands: List<DanmakuProto.CommandDm>,
): List<DanmakuVotePrompt> = commands.mapNotNull { command ->
    if (command.command != "#VOTE#" || command.extra.isBlank()) return@mapNotNull null
    val payload = runCatching {
        danmakuVoteJson.decodeFromString<DanmakuVotePayload>(command.extra)
    }.getOrNull() ?: return@mapNotNull null
    val options = payload.options.mapNotNull { option ->
        if (option.id <= 0 || option.description.isBlank()) return@mapNotNull null
        DanmakuVoteOption(
            id = option.id,
            text = option.description.trim(),
            count = option.count.coerceAtLeast(0),
            hasSelfDefined = option.hasSelfDefined,
        )
    }
    val commandId = command.idStr.ifBlank { command.id.takeIf { it > 0L }?.toString().orEmpty() }
    if (payload.voteId <= 0L || commandId.isBlank() || payload.question.isBlank() || options.isEmpty()) {
        return@mapNotNull null
    }
    DanmakuVotePrompt(
        commandId = commandId,
        voteId = payload.voteId,
        voteType = payload.voteType,
        question = payload.question.trim(),
        options = options,
        triggerPositionMs = command.progress.toLong().coerceAtLeast(0L),
        durationMs = payload.duration
            .takeIf { it > 0L }
            ?.coerceIn(DANMAKU_VOTE_MIN_DURATION_MS, DANMAKU_VOTE_MAX_DURATION_MS)
            ?: DANMAKU_VOTE_FALLBACK_DURATION_MS,
        selectedOptionId = payload.myVote.coerceAtLeast(0),
    )
}.sortedWith(compareBy(DanmakuVotePrompt::triggerPositionMs, DanmakuVotePrompt::voteId))

@Serializable
private data class DanmakuVotePayload(
    @SerialName("vote_id")
    val voteId: Long = 0L,
    @SerialName("vote_type")
    val voteType: Int = 0,
    val question: String = "",
    val options: List<DanmakuVotePayloadOption> = emptyList(),
    @SerialName("my_vote")
    val myVote: Int = 0,
    val duration: Long = 0L,
)

@Serializable
private data class DanmakuVotePayloadOption(
    @SerialName("idx")
    val id: Int = 0,
    @SerialName("desc")
    val description: String = "",
    @SerialName("cnt")
    val count: Int = 0,
    @SerialName("has_self_def")
    val hasSelfDefined: Boolean = false,
)
