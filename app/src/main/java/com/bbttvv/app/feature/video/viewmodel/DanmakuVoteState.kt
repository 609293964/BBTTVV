package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.feature.video.danmaku.DanmakuProto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
) {
    // Vote and grade IDs belong to separate server namespaces.
    val interactionKey: String = "${kind.name}:$voteId"
}

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
        "#VOTE#", "VOTE_MSG", "VIDEO_VOTE", "VIDEO_VOTE_MSG" -> DanmakuVoteKind.Vote
        "#GRADE#", "GRADE_MSG", "VIDEO_GRADE_MSG" -> DanmakuVoteKind.Grade
        else -> return@mapNotNull null
    }
    if (command.extra.isBlank() && command.content.isBlank()) return@mapNotNull null
    if (kind == DanmakuVoteKind.Grade) return@mapNotNull parseDanmakuGradePrompt(command)
    val payload = parseDanmakuVotePayload(command.extra.ifBlank { command.content }) ?: return@mapNotNull null
    val options = payload.options
    val promptId = payload.voteId
    val commandId = command.idStr.ifBlank { command.id.takeIf { it > 0L }?.toString().orEmpty() }
    val title = payload.question.trim()
    if (promptId <= 0L || commandId.isBlank() || title.isBlank() || options.isEmpty()) {
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

private fun parseDanmakuGradePrompt(command: DanmakuProto.CommandDm): DanmakuVotePrompt? {
    val payload = runCatching {
        danmakuVoteJson.decodeFromString<DanmakuGradePayload>(command.extra.ifBlank { command.content })
    }.getOrNull() ?: return null
    val title = payload.msg.ifBlank { payload.title }.ifBlank { payload.question }.trim()
    if (payload.gradeId <= 0L || title.isBlank()) return null
    return DanmakuVotePrompt(
        commandId = command.idStr.ifBlank { command.id.toString() },
        voteId = payload.gradeId,
        voteType = 0,
        kind = DanmakuVoteKind.Grade,
        question = title,
        participantCount = (payload.count ?: payload.legacyCount).coerceAtLeast(0),
        // The grade command defines five stars, not arbitrary vote options.
        options = (1..5).map { level ->
            DanmakuVoteOption(level, level.toString(), 0, false, gradeScore = level * 2)
        },
        triggerPositionMs = command.progress.toLong().coerceAtLeast(0L),
        durationMs = payload.duration.takeIf { it > 0L }
            ?.coerceIn(DANMAKU_VOTE_MIN_DURATION_MS, DANMAKU_VOTE_MAX_DURATION_MS)
            ?: DANMAKU_VOTE_FALLBACK_DURATION_MS,
        selectedOptionId = payload.midScore.takeIf { it in 2..10 && it % 2 == 0 }?.div(2) ?: 0,
    )
}

private data class ParsedDanmakuVotePayload(
    val voteId: Long,
    val voteType: Int,
    val question: String,
    val count: Int,
    val options: List<DanmakuVoteOption>,
    val myVote: Int,
    val duration: Long,
)

private fun parseDanmakuVotePayload(raw: String): ParsedDanmakuVotePayload? {
    val root = runCatching { danmakuVoteJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    fun string(vararg keys: String): String =
        keys.firstNotNullOfOrNull { (root[it] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }.orEmpty()
    fun long(vararg keys: String): Long =
        keys.firstNotNullOfOrNull { (root[it] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() } ?: 0L
    fun int(vararg keys: String): Int = long(*keys).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    val rawOptions = root["options"] ?: root["choices"]
    val options = buildList {
        when (rawOptions) {
            is JsonArray -> rawOptions.forEachIndexed { index, element ->
                when (element) {
                    is JsonObject -> {
                        val id = element.number("id", "idx", "value") ?: (index + 1)
                        val label = element.text("description", "desc", "title", "name", "text", "label", "score")
                        if (label.isNotBlank()) add(
                            DanmakuVoteOption(
                                id = id,
                                text = label,
                                count = element.number("count", "cnt")?.coerceAtLeast(0) ?: 0,
                                hasSelfDefined = element.boolean("has_self_def", "hasSelfDefined"),
                            )
                        )
                    }
                    is JsonPrimitive -> element.contentOrNull?.takeIf(String::isNotBlank)?.let {
                        add(DanmakuVoteOption(index + 1, it, 0, false))
                    }
                    else -> Unit
                }
            }
            is JsonObject -> rawOptions.entries.forEach { (key, value) ->
                val label = (value as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
                val id = key.toIntOrNull() ?: return@forEach
                if (label.isNotBlank()) add(DanmakuVoteOption(id, label, 0, false))
            }
            else -> Unit
        }
    }.distinctBy { it.id }
    return ParsedDanmakuVotePayload(
        voteId = long("vote_id", "id"),
        voteType = int("vote_type", "type"),
        question = string("question", "title", "msg"),
        count = int("cnt", "count").coerceAtLeast(0),
        options = options,
        myVote = int("my_vote", "myVote"),
        duration = long("duration"),
    )
}

private fun JsonObject.number(vararg keys: String): Int? =
    keys.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }

private fun JsonObject.text(vararg keys: String): String =
    keys.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }.orEmpty()

private fun JsonObject.boolean(vararg keys: String): Boolean =
    keys.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() } ?: false

@Serializable
private data class DanmakuGradePayload(
    @SerialName("grade_id") val gradeId: Long = 0L,
    val msg: String = "",
    val title: String = "",
    val question: String = "",
    val count: Int? = null,
    @SerialName("cnt") val legacyCount: Int = 0,
    @SerialName("mid_score") val midScore: Int = 0,
    val duration: Long = 0L,
)

@Serializable
private data class DanmakuVotePayload(
    @SerialName("vote_id")
    val voteId: Long = 0L,
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
)
