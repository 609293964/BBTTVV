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

data class SubtitleTrackMeta(
    val id: Long = 0L,
    val idStr: String = "",
    val lan: String,
    val lanDoc: String,
    val subtitleUrl: String,
    val aiStatus: Int = 0,
    val aiType: Int = 0,
    val type: Int = 0
)

data class SubtitleLanguageSelection(
    val primaryLanguage: String?,
    val secondaryLanguage: String?
)

enum class SubtitleDisplayMode {
    OFF,
    PRIMARY_ONLY,
    SECONDARY_ONLY,
    BILINGUAL
}

data class SubtitleDisplayOption(
    val mode: SubtitleDisplayMode,
    val label: String,
    val enabled: Boolean
)

data class SubtitlePreferenceSession(
    val key: String,
    val initialMode: SubtitleDisplayMode
)

data class SubtitleControlAvailability(
    val trackAvailable: Boolean,
    val primarySelectable: Boolean,
    val secondarySelectable: Boolean
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

fun isTrustedBilibiliSubtitleUrl(raw: String): Boolean {
    val normalized = normalizeBilibiliSubtitleUrl(raw)
    if (normalized.isBlank()) return false
    val uri = runCatching { java.net.URI(normalized) }.getOrNull() ?: return false
    val host = uri.host?.lowercase().orEmpty()
    if (host.isBlank()) return false

    val trustedHost = host == "hdslb.com" ||
        host.endsWith(".hdslb.com") ||
        host == "bilibili.com" ||
        host.endsWith(".bilibili.com")
    if (!trustedHost) return false

    val path = uri.path?.lowercase().orEmpty()
    return path.contains("subtitle")
}

fun isLikelyAiSubtitleTrack(track: SubtitleTrackMeta): Boolean {
    if (track.aiStatus > 0 || track.aiType > 0) return true
    val label = track.lanDoc.lowercase()
    return label.contains("ai") ||
        label.contains("自动") ||
        label.contains("机翻") ||
        label.contains("机器")
}

private fun subtitleTrackPreferenceScore(track: SubtitleTrackMeta): Int {
    var score = 0
    if (!isLikelyAiSubtitleTrack(track)) score += 100
    if (isTrustedBilibiliSubtitleUrl(track.subtitleUrl)) score += 20
    if (track.lanDoc.contains("官方")) score += 8
    if (track.id > 0L) score += 1
    return score
}

fun orderSubtitleTracksByPreference(tracks: List<SubtitleTrackMeta>): List<SubtitleTrackMeta> {
    if (tracks.size <= 1) return tracks
    return tracks.sortedWith(
        compareByDescending<SubtitleTrackMeta> { subtitleTrackPreferenceScore(it) }
            .thenByDescending { it.id }
            .thenBy { it.lanDoc }
    )
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

private fun resolveLanguageFamily(languageCode: String?): String? {
    val normalized = languageCode?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return normalized.substringBefore('-').lowercase()
}

private fun findTrackByPreferredLanguage(
    tracks: List<SubtitleTrackMeta>,
    preferredLanguage: String?
): SubtitleTrackMeta? {
    val preferred = preferredLanguage?.trim()?.takeIf { it.isNotBlank() } ?: return null
    tracks.firstOrNull { track ->
        track.lan.equals(preferred, ignoreCase = true)
    }?.let { return it }

    val preferredFamily = resolveLanguageFamily(preferred) ?: return null
    return tracks.firstOrNull { track ->
        resolveLanguageFamily(track.lan) == preferredFamily
    }
}

fun resolveDefaultSubtitleLanguages(
    tracks: List<SubtitleTrackMeta>,
    preferredPrimaryLanguage: String? = null
): SubtitleLanguageSelection {
    if (tracks.isEmpty()) {
        return SubtitleLanguageSelection(
            primaryLanguage = null,
            secondaryLanguage = null
        )
    }

    val primary = findTrackByPreferredLanguage(
        tracks = tracks,
        preferredLanguage = preferredPrimaryLanguage
    ) ?: tracks.firstOrNull { track ->
        track.lan.equals("zh-Hans", ignoreCase = true)
    } ?: tracks.firstOrNull { track ->
        track.lan.equals("zh-CN", ignoreCase = true)
    } ?: tracks.firstOrNull { track ->
        track.lan.startsWith("zh", ignoreCase = true)
    } ?: tracks.first()

    val englishSecondary = tracks.firstOrNull { track ->
        track.lan.equals("en-US", ignoreCase = true)
    } ?: tracks.firstOrNull { track ->
        track.lan.equals("en-GB", ignoreCase = true)
    } ?: tracks.firstOrNull { track ->
        track.lan.startsWith("en", ignoreCase = true)
    }
    val secondary = englishSecondary?.takeIf { it.lan != primary.lan } ?: tracks.firstOrNull { track ->
        track.lan != primary.lan
    }

    return SubtitleLanguageSelection(
        primaryLanguage = primary.lan,
        secondaryLanguage = secondary?.lan?.takeIf { it != primary.lan }
    )
}

fun resolveDefaultSubtitleDisplayMode(
    hasPrimaryTrack: Boolean,
    hasSecondaryTrack: Boolean
): SubtitleDisplayMode = when {
    hasPrimaryTrack && hasSecondaryTrack -> SubtitleDisplayMode.BILINGUAL
    hasPrimaryTrack -> SubtitleDisplayMode.PRIMARY_ONLY
    hasSecondaryTrack -> SubtitleDisplayMode.SECONDARY_ONLY
    else -> SubtitleDisplayMode.OFF
}

fun normalizeSubtitleDisplayMode(
    preferredMode: SubtitleDisplayMode,
    hasPrimaryTrack: Boolean,
    hasSecondaryTrack: Boolean
): SubtitleDisplayMode {
    if (!hasPrimaryTrack && !hasSecondaryTrack) {
        return SubtitleDisplayMode.OFF
    }
    return when (preferredMode) {
        SubtitleDisplayMode.OFF -> SubtitleDisplayMode.OFF
        SubtitleDisplayMode.PRIMARY_ONLY -> {
            if (hasPrimaryTrack) SubtitleDisplayMode.PRIMARY_ONLY else SubtitleDisplayMode.SECONDARY_ONLY
        }
        SubtitleDisplayMode.SECONDARY_ONLY -> {
            if (hasSecondaryTrack) SubtitleDisplayMode.SECONDARY_ONLY else SubtitleDisplayMode.PRIMARY_ONLY
        }
        SubtitleDisplayMode.BILINGUAL -> {
            resolveDefaultSubtitleDisplayMode(
                hasPrimaryTrack = hasPrimaryTrack,
                hasSecondaryTrack = hasSecondaryTrack
            )
        }
    }
}

fun resolveSubtitleDisplayModeByAutoPreference(
    preference: SubtitleAutoPreference,
    hasPrimaryTrack: Boolean,
    hasSecondaryTrack: Boolean,
    primaryTrackLikelyAi: Boolean,
    secondaryTrackLikelyAi: Boolean,
    isMuted: Boolean
): SubtitleDisplayMode {
    val defaultMode = resolveDefaultSubtitleDisplayMode(
        hasPrimaryTrack = hasPrimaryTrack,
        hasSecondaryTrack = hasSecondaryTrack
    )
    if (defaultMode == SubtitleDisplayMode.OFF) return SubtitleDisplayMode.OFF

    val defaultModeLikelyAi = when (defaultMode) {
        SubtitleDisplayMode.OFF -> false
        SubtitleDisplayMode.PRIMARY_ONLY -> primaryTrackLikelyAi
        SubtitleDisplayMode.SECONDARY_ONLY -> secondaryTrackLikelyAi
        SubtitleDisplayMode.BILINGUAL -> primaryTrackLikelyAi && secondaryTrackLikelyAi
    }

    return when (preference) {
        SubtitleAutoPreference.OFF -> SubtitleDisplayMode.OFF
        SubtitleAutoPreference.ON -> defaultMode
        SubtitleAutoPreference.WITHOUT_AI -> {
            if (defaultModeLikelyAi) SubtitleDisplayMode.OFF else defaultMode
        }
        SubtitleAutoPreference.AUTO -> {
            if (!defaultModeLikelyAi || isMuted) defaultMode else SubtitleDisplayMode.OFF
        }
    }
}

fun resolveSubtitlePreferenceSession(
    bvid: String,
    cid: Long,
    primaryLanguage: String?,
    secondaryLanguage: String?,
    primaryTrackLikelyAi: Boolean,
    secondaryTrackLikelyAi: Boolean,
    hasPrimaryTrack: Boolean,
    hasSecondaryTrack: Boolean,
    preference: SubtitleAutoPreference,
    isMuted: Boolean = false
): SubtitlePreferenceSession {
    val sessionKey = "${bvid.trim()}_${cid}_${primaryLanguage}_${secondaryLanguage}_${primaryTrackLikelyAi}_${secondaryTrackLikelyAi}_${preference.name}"
    val initialMode = resolveSubtitleDisplayModeByAutoPreference(
        preference = preference,
        hasPrimaryTrack = hasPrimaryTrack,
        hasSecondaryTrack = hasSecondaryTrack,
        primaryTrackLikelyAi = primaryTrackLikelyAi,
        secondaryTrackLikelyAi = secondaryTrackLikelyAi,
        isMuted = isMuted
    )
    return SubtitlePreferenceSession(
        key = sessionKey,
        initialMode = initialMode
    )
}

fun resolveSubtitleDisplayModePreference(
    previousSessionKey: String?,
    nextSessionKey: String,
    previousMode: SubtitleDisplayMode,
    nextInitialMode: SubtitleDisplayMode
): SubtitleDisplayMode {
    return if (previousSessionKey == nextSessionKey) previousMode else nextInitialMode
}

fun resolveSubtitleControlAvailability(
    primaryTrackBound: Boolean,
    secondaryTrackBound: Boolean,
    primaryCueAvailable: Boolean,
    secondaryCueAvailable: Boolean
): SubtitleControlAvailability {
    val primarySelectable = primaryTrackBound || primaryCueAvailable
    val secondarySelectable = secondaryTrackBound || secondaryCueAvailable
    return SubtitleControlAvailability(
        trackAvailable = primarySelectable || secondarySelectable,
        primarySelectable = primarySelectable,
        secondarySelectable = secondarySelectable
    )
}

fun resolveSubtitleDisplayOptions(
    primaryLabel: String,
    secondaryLabel: String,
    hasPrimaryTrack: Boolean,
    hasSecondaryTrack: Boolean
): List<SubtitleDisplayOption> {
    val options = mutableListOf(
        SubtitleDisplayOption(
            mode = SubtitleDisplayMode.OFF,
            label = "关闭",
            enabled = true
        )
    )
    if (hasPrimaryTrack) {
        options += SubtitleDisplayOption(
            mode = SubtitleDisplayMode.PRIMARY_ONLY,
            label = primaryLabel,
            enabled = true
        )
    }
    if (hasSecondaryTrack) {
        options += SubtitleDisplayOption(
            mode = SubtitleDisplayMode.SECONDARY_ONLY,
            label = secondaryLabel,
            enabled = true
        )
    }
    if (hasPrimaryTrack && hasSecondaryTrack) {
        options += SubtitleDisplayOption(
            mode = SubtitleDisplayMode.BILINGUAL,
            label = "双语",
            enabled = true
        )
    }
    return options
}

fun shouldRenderPrimarySubtitle(mode: SubtitleDisplayMode): Boolean {
    return mode == SubtitleDisplayMode.PRIMARY_ONLY || mode == SubtitleDisplayMode.BILINGUAL
}

fun shouldRenderSecondarySubtitle(mode: SubtitleDisplayMode): Boolean {
    return mode == SubtitleDisplayMode.SECONDARY_ONLY || mode == SubtitleDisplayMode.BILINGUAL
}

fun resolveSubtitleTextAt(cues: List<SubtitleCue>, positionMs: Long): String? {
    if (cues.isEmpty()) return null
    if (positionMs < 0L) return null

    var low = 0
    var high = cues.lastIndex
    while (low <= high) {
        val mid = (low + high) ushr 1
        val cue = cues[mid]
        when {
            positionMs < cue.startMs -> high = mid - 1
            positionMs > cue.endMs -> low = mid + 1
            else -> return cue.content
        }
    }
    return null
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

