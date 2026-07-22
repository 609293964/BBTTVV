package com.bbttvv.app.feature.live

import org.json.JSONArray
import org.json.JSONObject

internal data class ParsedLiveDanmakuMessage(
    val text: String,
    val color: Int,
    val userId: String,
    val danmakuId: Long?,
)

internal fun parseLiveDanmakuMessage(body: ByteArray): ParsedLiveDanmakuMessage? {
    val bodyText = body.toString(Charsets.UTF_8)
        .replace("\u0000", "")
        .trim()
    if (bodyText.isBlank()) return null

    return runCatching {
        val root = when {
            bodyText.startsWith("{") -> JSONObject(bodyText)
            bodyText.startsWith("[") -> JSONArray(bodyText).optJSONObject(0) ?: return null
            else -> return null
        }
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
