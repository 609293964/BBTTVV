package com.bbttvv.app.core.store

import android.content.Context
import com.bbttvv.app.data.model.response.Page
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val RESUME_SP_NAME = "playback_resume_store"
private const val RESUME_KEY_PREFIX = "resume_"
private const val RESUME_MIN_POSITION_MS = 5_000L
private const val RESUME_END_GUARD_MS = 10_000L
private const val RESUME_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L

@Serializable
internal data class PlaybackResumeRecord(
    val bvid: String,
    val cid: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
)

internal data class PlaybackResumeCandidate(
    val cid: Long,
    val positionMs: Long,
    val isCrossPage: Boolean,
)

internal fun normalizeStoredResumePositionMs(
    positionMs: Long,
    durationMs: Long,
): Long {
    if (positionMs < RESUME_MIN_POSITION_MS) return 0L
    if (durationMs <= 0L) return positionMs
    val maxAllowedPositionMs = (durationMs - RESUME_END_GUARD_MS).coerceAtLeast(0L)
    if (positionMs >= maxAllowedPositionMs) return 0L
    return positionMs.coerceAtMost(maxAllowedPositionMs)
}

internal fun resolveStoredResumeCandidate(
    currentCid: Long,
    pages: List<Page>,
    record: PlaybackResumeRecord?,
    nowMs: Long = System.currentTimeMillis(),
): PlaybackResumeCandidate? {
    if (record == null) return null
    if (record.cid <= 0L) return null
    if (nowMs - record.updatedAtMs >= RESUME_MAX_AGE_MS) return null
    val normalizedPositionMs = normalizeStoredResumePositionMs(
        positionMs = record.positionMs,
        durationMs = record.durationMs,
    )
    if (normalizedPositionMs <= 0L) return null
    if (record.cid == currentCid) {
        return PlaybackResumeCandidate(
            cid = record.cid,
            positionMs = normalizedPositionMs,
            isCrossPage = false,
        )
    }
    if (pages.none { page -> page.cid == record.cid }) return null
    return PlaybackResumeCandidate(
        cid = record.cid,
        positionMs = normalizedPositionMs,
        isCrossPage = true,
    )
}

internal object PlaybackResumeStore {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context, bvid: String): PlaybackResumeRecord? {
        if (bvid.isBlank()) return null
        val raw = context.getSharedPreferences(RESUME_SP_NAME, Context.MODE_PRIVATE)
            .getString(keyFor(bvid), null)
            ?: return null
        return runCatching {
            json.decodeFromString<PlaybackResumeRecord>(raw)
        }.getOrNull()
    }

    fun save(
        context: Context,
        bvid: String,
        cid: Long,
        positionMs: Long,
        durationMs: Long,
        updatedAtMs: Long = System.currentTimeMillis(),
    ) {
        if (bvid.isBlank() || cid <= 0L) return
        val normalizedPositionMs = normalizeStoredResumePositionMs(
            positionMs = positionMs,
            durationMs = durationMs,
        )
        val prefs = context.getSharedPreferences(RESUME_SP_NAME, Context.MODE_PRIVATE)
        if (normalizedPositionMs <= 0L) {
            prefs.edit().remove(keyFor(bvid)).apply()
            return
        }
        val payload = PlaybackResumeRecord(
            bvid = bvid,
            cid = cid,
            positionMs = normalizedPositionMs,
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAtMs = updatedAtMs,
        )
        prefs.edit()
            .putString(keyFor(bvid), json.encodeToString(payload))
            .apply()
    }

    fun clear(context: Context, bvid: String) {
        if (bvid.isBlank()) return
        context.getSharedPreferences(RESUME_SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(keyFor(bvid))
            .apply()
    }

    private fun keyFor(bvid: String): String = RESUME_KEY_PREFIX + bvid
}
