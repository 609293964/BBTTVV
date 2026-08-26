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
    val gradeScore: Int? = null,
)

enum class DanmakuVoteKind { Vote, Grade }

data class DanmakuVotePrompt(
    val commandId: String,
    val voteId: Long,
    val voteType: Int,
    val kind: DanmakuVoteKind,
    val question: String,
    val participantCount: Int = 0,
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
    val kind = when (command.command.trim().uppercase()) {
        "#VOTE#", "VIDEO_VOTE_MSG" -> DanmakuVoteKind.Vote
        "#GRADE#", "GRADE_MSG", "VIDEO_GRADE_MSG" -> DanmakuVoteKind.Grade
        else -> return@mapNotNull null
    }
    if (command.extra.isBlank() && command.content.isBlank()) return@mapNotNull null
    val payload = runCatching {
        danmakuVoteJson.decodeFromString<DanmakuVotePayload>(command.extra.ifBlank { command.content })
    }.getOrNull() ?: return@mapNotNull null
    val sourceOptions = if (kind == DanmakuVoteKind.Grade && payload.options.isEmpty()) {
        (1..5).map { level -> DanmakuVotePayloadOption(id = level, description = "$level", score = level * 2) }
    } else payload.options
    val options = sourceOptions.mapNotNull { option ->
        val gradeScore = option.score?.takeIf { it in 2..10 && it % 2 == 0 }
        if (option.id <= 0 || (option.description.isBlank() && gradeScore == null)) return@mapNotNull null
        DanmakuVoteOption(
            id = option.id,
            text = option.description.trim().ifBlank { (gradeScore!! / 2).toString() },
            count = option.count.coerceAtLeast(0),
            hasSelfDefined = option.hasSelfDefined,
            gradeScore = gradeScore,
        )
    }
    val promptId = if (kind == DanmakuVoteKind.Grade) payload.gradeId else payload.voteId
    val commandId = command.idStr.ifBlank { command.id.takeIf { it > 0L }?.toString().orEmpty() }
    val title = payload.question.ifBlank { payload.title }.trim()
    if (promptId <= 0L || (kind == DanmakuVoteKind.Vote && commandId.isBlank()) || title.isBlank() || options.isEmpty()) {
        return@mapNotNull null
    }
    DanmakuVotePrompt(
        commandId = commandId,
        voteId = promptId,
        voteType = payload.voteType,
        kind = kind,
        question = title,
        participantCount = payload.count.coerceAtLeast(0),
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
    @SerialName("grade_id")
    val gradeId: Long = 0L,
    @SerialName("vote_type")
    val voteType: Int = 0,
    val question: String = "",
    val title: String = "",
    @SerialName("cnt")
    val count: Int = 0,
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
    val score: Int? = null,
)
