package com.bbttvv.app.feature.live

import org.json.JSONArray
import org.json.JSONObject

internal data class ParsedLiveDanmakuMessage(
    val text: String,
    val color: Int,
    val userId: String,
    val danmakuId: Long?,
)

internal data class LiveSuperChat(
    val id: Long,
    val userName: String,
    val price: Long,
    val message: String,
    val endTimeSeconds: Long?,
    val durationSeconds: Long?,
)

internal sealed interface ParsedLiveSuperChatEvent {
    data class Message(val item: LiveSuperChat) : ParsedLiveSuperChatEvent
    data class Delete(val ids: Set<Long>) : ParsedLiveSuperChatEvent
}

internal fun parseLiveSuperChatEvent(body: ByteArray): ParsedLiveSuperChatEvent? {
    val root = parseLiveMessageRoot(body) ?: return null
    return when (root.optString("cmd").orEmpty().substringBefore(':')) {
        "SUPER_CHAT_MESSAGE", "SUPER_CHAT_MESSAGE_JPN" -> {
            val data = root.optJSONObject("data") ?: return null
            buildLiveSuperChat(
                id = data.opt("id"),
                userName = data.optJSONObject("user_info")?.optString("uname"),
                price = data.opt("price"),
                message = data.optString("message"),
                fallbackMessage = data.optString("message_jpn"),
                endTimeSeconds = data.opt("end_time"),
                durationSeconds = data.opt("time"),
            )?.let(ParsedLiveSuperChatEvent::Message)
        }

        "SUPER_CHAT_MESSAGE_DELETE" -> {
            val ids = root.optJSONObject("data")
                ?.optJSONArray("ids")
                ?.let { values ->
                    buildSet {
                        for (index in 0 until values.length()) {
                            values.optLongCompat(index)?.takeIf { it > 0L }?.let(::add)
                        }
                    }
                }
                .orEmpty()
            ids.takeIf { it.isNotEmpty() }?.let(ParsedLiveSuperChatEvent::Delete)
        }

        else -> null
    }
}

internal fun buildLiveSuperChat(
    id: Any?,
    userName: String?,
    price: Any?,
    message: String?,
    fallbackMessage: String?,
    endTimeSeconds: Any?,
    durationSeconds: Any?,
): LiveSuperChat? {
    val normalizedMessage = message.orEmpty()
        .ifBlank { fallbackMessage.orEmpty() }
        .trim()
    if (normalizedMessage.isBlank()) return null
    return LiveSuperChat(
        id = id.toLongCompat() ?: 0L,
        userName = userName.orEmpty().trim(),
        price = (price.toLongCompat() ?: 0L).coerceAtLeast(0L),
        message = normalizedMessage,
        endTimeSeconds = endTimeSeconds.toLongCompat()?.takeIf { it > 0L },
        durationSeconds = durationSeconds.toLongCompat()?.takeIf { it > 0L },
    )
}

internal fun parseLiveDanmakuMessage(body: ByteArray): ParsedLiveDanmakuMessage? {
    val root = parseLiveMessageRoot(body) ?: return null
    return runCatching {
        val cmd = root.optString("cmd").substringBefore(':')
        if (cmd != "DANMU_MSG") return null

        val info = root.optJSONArray("info") ?: return null
        val text = info.opt(1)?.toString()?.trim().orEmpty()
        if (text.isBlank()) return null

        val meta = info.optJSONArray(0)
        val user = info.optJSONArray(2)
        ParsedLiveDanmakuMessage(
            text = text,
            color = resolveLiveDanmakuColor(meta),
            userId = user?.opt(0)?.toString().orEmpty(),
            danmakuId = (
                meta.optLongCompat(5)
                    ?: meta.optLongCompat(6)
                    ?: meta.optLongCompat(7)
                )?.takeIf { it > 0L },
        )
    }.getOrNull()
}

private fun parseLiveMessageRoot(body: ByteArray): JSONObject? {
    val bodyText = body.toString(Charsets.UTF_8)
        .replace("\u0000", "")
        .trim()
    if (bodyText.isBlank()) return null
    return runCatching {
        when {
            bodyText.startsWith("{") -> JSONObject(bodyText)
            bodyText.startsWith("[") -> JSONArray(bodyText).optJSONObject(0)
            else -> null
        }
    }.getOrNull()
}

private fun resolveLiveDanmakuColor(meta: JSONArray?): Int {
    if (meta == null) return 0x00FFFFFF
    listOf(3, 2, 4).forEach { index ->
        val value = meta.optIntCompat(index)
        if (value != null && value in 1..0x00FFFFFF) return value
    }
    return 0x00FFFFFF
}

private fun JSONArray?.optIntCompat(index: Int): Int? {
    val value = this?.opt(index) ?: return null
    return when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
}

private fun JSONArray?.optLongCompat(index: Int): Long? {
    val value = this?.opt(index) ?: return null
    return when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
}

private fun Any?.toLongCompat(): Long? {
    return when (this) {
        is Number -> toLong()
        is String -> trim().toLongOrNull()
        else -> null
    }
}
