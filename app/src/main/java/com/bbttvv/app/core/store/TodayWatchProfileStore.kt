package com.bbttvv.app.core.store

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.ln

data class CreatorSignalSnapshot(
    val mid: Long,
    val name: String,
    val score: Double,
    val watchCount: Int
)

@Serializable
private data class TodayWatchCreatorProfile(
    val mid: Long,
    val name: String = "",
    val totalWatchSec: Long = 0L,
    val engagementEvents: Int = 0,
    val lastWatchAtSec: Long = 0L
)

@Serializable
private data class TodayWatchProfilePayload(
    val creators: List<TodayWatchCreatorProfile> = emptyList()
)

object TodayWatchProfileStore {
    private const val PREFS_NAME = "today_watch_profile"
    private const val KEY_PAYLOAD = "creator_profile_payload_v1"
    private const val MAX_CREATORS = 160

    private val lock = Any()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun recordWatchProgress(
        context: Context,
        mid: Long,
        creatorName: String,
        deltaWatchSec: Long,
        watchedAtSec: Long = System.currentTimeMillis() / 1000L
    ) {
        if (mid <= 0L) return
        val normalizedDelta = deltaWatchSec.coerceAtLeast(0L)
        if (normalizedDelta <= 0L) return

        synchronized(lock) {
            val profiles = loadProfiles(context)
                .associateBy { it.mid }
                .toMutableMap()
            val old = profiles[mid]
            val updated = TodayWatchCreatorProfile(
                mid = mid,
                name = creatorName.ifBlank { old?.name.orEmpty() },
                totalWatchSec = (old?.totalWatchSec ?: 0L) + normalizedDelta,
                engagementEvents = (old?.engagementEvents ?: 0) + 1,
                lastWatchAtSec = watchedAtSec
            )
            profiles[mid] = updated

            val trimmed = profiles.values
                .sortedWith(
                    compareByDescending<TodayWatchCreatorProfile> { it.lastWatchAtSec }
                        .thenByDescending { it.totalWatchSec }
                )
                .take(MAX_CREATORS)

            saveProfiles(context, trimmed)
        }
    }

    fun getCreatorSignals(
        context: Context,
        nowEpochSec: Long = System.currentTimeMillis() / 1000L,
        limit: Int = 20
    ): List<CreatorSignalSnapshot> {
        val normalizedLimit = limit.coerceIn(1, 60)
        return synchronized(lock) {
            loadProfiles(context)
                .map { profile ->
                    val engagementScore =
                        ln(profile.totalWatchSec.toDouble() + 1.0) * 0.92 +
                            ln(profile.engagementEvents.toDouble() + 1.0) * 0.66
                    val recencyScore = when (val days = ((nowEpochSec - profile.lastWatchAtSec).coerceAtLeast(0L) / 86_400.0)) {
                        in 0.0..1.0 -> 1.15
                        in 1.0..3.0 -> 0.85
                        in 3.0..7.0 -> 0.55
                        in 7.0..30.0 -> 0.2
                        else -> -0.1
                    }

                    CreatorSignalSnapshot(
                        mid = profile.mid,
                        name = profile.name.ifBlank { "UP主${profile.mid}" },
                        score = engagementScore + recencyScore,
                        watchCount = profile.engagementEvents.coerceAtLeast(1)
                    )
                }
                .sortedByDescending { it.score }
                .take(normalizedLimit)
        }
    }

    private fun loadProfiles(context: Context): List<TodayWatchCreatorProfile> {
        val raw = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PAYLOAD, null)
            .orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<TodayWatchProfilePayload>(raw).creators
        }.getOrElse {
            emptyList()
        }
    }

    private fun saveProfiles(context: Context, creators: List<TodayWatchCreatorProfile>) {
        val raw = json.encodeToString(TodayWatchProfilePayload(creators = creators))
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PAYLOAD, raw)
            .apply()
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

