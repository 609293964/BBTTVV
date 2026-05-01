package com.bbttvv.app.core.store

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class TodayWatchFeedbackSnapshot(
    val dislikedBvids: Set<String> = emptySet(),
    val dislikedCreatorMids: Set<Long> = emptySet(),
    val dislikedKeywords: Set<String> = emptySet(),
    val recentDislikedVideos: List<TodayWatchDislikedVideoSnapshot> = emptyList()
)

data class TodayWatchDislikedVideoSnapshot(
    val bvid: String,
    val title: String,
    val creatorName: String,
    val creatorMid: Long,
    val dislikedAtMillis: Long
)

@Serializable
private data class TodayWatchDislikedVideoPayload(
    val bvid: String,
    val title: String = "",
    val creatorName: String = "",
    val creatorMid: Long = 0L,
    val dislikedAtMillis: Long = 0L
)

@Serializable
private data class TodayWatchFeedbackPayload(
    val dislikedBvids: List<String> = emptyList(),
    val dislikedCreatorMids: List<Long> = emptyList(),
    val dislikedKeywords: List<String> = emptyList(),
    val recentDislikedVideos: List<TodayWatchDislikedVideoPayload> = emptyList()
)

private const val MAX_DISLIKED_BVIDS = 200
private const val MAX_DISLIKED_CREATORS = 120
private const val MAX_DISLIKED_KEYWORDS = 80
private const val MAX_RECENT_DISLIKED_VIDEOS = 24

fun TodayWatchFeedbackSnapshot.withDislikedVideoFeedback(
    video: TodayWatchDislikedVideoSnapshot,
    keywords: Set<String>
): TodayWatchFeedbackSnapshot {
    val normalizedBvid = video.bvid.trim()
    if (normalizedBvid.isBlank()) return this
    val normalizedVideo = video.copy(
        bvid = normalizedBvid,
        title = video.title.trim(),
        creatorName = video.creatorName.trim()
    )
    val recentVideos = (recentDislikedVideos.filterNot { it.bvid == normalizedBvid } + normalizedVideo)
        .takeLast(MAX_RECENT_DISLIKED_VIDEOS)
    return copy(
        dislikedBvids = (dislikedBvids + normalizedBvid)
            .filter { it.isNotBlank() }
            .takeLast(MAX_DISLIKED_BVIDS)
            .toSet(),
        dislikedCreatorMids = (dislikedCreatorMids + normalizedVideo.creatorMid)
            .filter { it > 0L }
            .takeLast(MAX_DISLIKED_CREATORS)
            .toSet(),
        dislikedKeywords = (dislikedKeywords + keywords)
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .takeLast(MAX_DISLIKED_KEYWORDS)
            .toSet(),
        recentDislikedVideos = recentVideos
    )
}

object TodayWatchFeedbackStore {
    private const val PREFS_NAME = "today_watch_feedback"
    private const val KEY_PAYLOAD = "feedback_payload_v1"

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
                        .toSet(),
                    recentDislikedVideos = payload.recentDislikedVideos
                        .mapNotNull { item ->
                            val bvid = item.bvid.trim()
                            if (bvid.isBlank()) {
                                null
                            } else {
                                TodayWatchDislikedVideoSnapshot(
                                    bvid = bvid,
                                    title = item.title.trim(),
                                    creatorName = item.creatorName.trim(),
                                    creatorMid = item.creatorMid,
                                    dislikedAtMillis = item.dislikedAtMillis
                                )
                            }
                        }
                        .takeLast(MAX_RECENT_DISLIKED_VIDEOS)
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
                    .takeLast(MAX_DISLIKED_KEYWORDS),
                recentDislikedVideos = snapshot.recentDislikedVideos
                    .filter { it.bvid.isNotBlank() }
                    .takeLast(MAX_RECENT_DISLIKED_VIDEOS)
                    .map { item ->
                        TodayWatchDislikedVideoPayload(
                            bvid = item.bvid.trim(),
                            title = item.title.trim(),
                            creatorName = item.creatorName.trim(),
                            creatorMid = item.creatorMid,
                            dislikedAtMillis = item.dislikedAtMillis
                        )
                    }
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

