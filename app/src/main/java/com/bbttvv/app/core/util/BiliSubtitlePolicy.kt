package com.bbttvv.app.core.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val content: String
)

enum class SubtitleAutoPreference {
    OFF,
    ON,
    WITHOUT_AI,
    AUTO
}

private val SUBTITLE_JSON = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun normalizeBilibiliSubtitleUrl(raw: String): String {
    val trimmed = raw.trim()
    return when {
        trimmed.isEmpty() -> ""
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("http://") -> "https://${trimmed.removePrefix("http://")}"
        else -> trimmed
    }
}

fun parseBiliSubtitleBody(rawJson: String): List<SubtitleCue> {
    if (rawJson.isBlank()) return emptyList()
    return try {
        val root = SUBTITLE_JSON.parseToJsonElement(rawJson).jsonObject
        val body = root["body"]?.asJsonArrayOrNull().orEmpty()
        body.mapNotNull { item ->
            val obj = item.asJsonObjectOrNull() ?: return@mapNotNull null
            val fromSeconds = obj["from"].asDoubleOrNull() ?: return@mapNotNull null
            val toSeconds = obj["to"].asDoubleOrNull() ?: return@mapNotNull null
            val content = obj["content"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (content.isBlank()) return@mapNotNull null
            val startMs = (fromSeconds * 1000.0).toLong().coerceAtLeast(0L)
            val endMs = (toSeconds * 1000.0).toLong().coerceAtLeast(startMs)
            SubtitleCue(
                startMs = startMs,
                endMs = endMs,
                content = content
            )
        }.sortedBy { cue -> cue.startMs }
    } catch (_: Throwable) {
        emptyList()
    }
}

private fun JsonElement?.asJsonObjectOrNull(): JsonObject? {
    return (this as? JsonObject) ?: runCatching { this?.jsonObject }.getOrNull()
}

private fun JsonElement?.asJsonArrayOrNull(): JsonArray? {
    return (this as? JsonArray) ?: runCatching { this?.jsonArray }.getOrNull()
}

private fun JsonElement?.asDoubleOrNull(): Double? {
    return when (this) {
        null -> null
        else -> this.jsonPrimitive.doubleOrNull
    }
}

