package com.bbttvv.app.core.store

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class TodayWatchFeedbackSnapshot(
    val dislikedBvids: Set<String> = emptySet(),
    val dislikedCreatorMids: Set<Long> = emptySet(),
    val dislikedKeywords: Set<String> = emptySet()
)

@Serializable
private data class TodayWatchFeedbackPayload(
    val dislikedBvids: List<String> = emptyList(),
    val dislikedCreatorMids: List<Long> = emptyList(),
    val dislikedKeywords: List<String> = emptyList()
)

object TodayWatchFeedbackStore {
    private const val PREFS_NAME = "today_watch_feedback"
    private const val KEY_PAYLOAD = "feedback_payload_v1"
    private const val MAX_DISLIKED_BVIDS = 200
    private const val MAX_DISLIKED_CREATORS = 120
    private const val MAX_DISLIKED_KEYWORDS = 80

    private val lock = Any()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun getSnapshot(context: Context): TodayWatchFeedbackSnapshot {
        return synchronized(lock) {
            val raw = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PAYLOAD, null)
                .orEmpty()
            if (raw.isBlank()) return@synchronized TodayWatchFeedbackSnapshot()

            runCatching {
                json.decodeFromString<TodayWatchFeedbackPayload>(raw)
            }.map { payload ->
                TodayWatchFeedbackSnapshot(
                    dislikedBvids = payload.dislikedBvids
                        .filter { it.isNotBlank() }
                        .takeLast(MAX_DISLIKED_BVIDS)
                        .toSet(),
                    dislikedCreatorMids = payload.dislikedCreatorMids
                        .filter { it > 0L }
                        .takeLast(MAX_DISLIKED_CREATORS)
                        .toSet(),
                    dislikedKeywords = payload.dislikedKeywords
                        .map { it.trim().lowercase() }
                        .filter { it.isNotBlank() }
                        .takeLast(MAX_DISLIKED_KEYWORDS)
                        .toSet()
                )
            }.getOrDefault(TodayWatchFeedbackSnapshot())
        }
    }

    fun saveSnapshot(context: Context, snapshot: TodayWatchFeedbackSnapshot) {
        synchronized(lock) {
            val payload = TodayWatchFeedbackPayload(
                dislikedBvids = snapshot.dislikedBvids
                    .filter { it.isNotBlank() }
                    .takeLast(MAX_DISLIKED_BVIDS),
                dislikedCreatorMids = snapshot.dislikedCreatorMids
                    .filter { it > 0L }
                    .takeLast(MAX_DISLIKED_CREATORS),
                dislikedKeywords = snapshot.dislikedKeywords
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .takeLast(MAX_DISLIKED_KEYWORDS)
            )
            val raw = json.encodeToString(payload)
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PAYLOAD, raw)
                .apply()
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PAYLOAD)
                .apply()
        }
    }
}

