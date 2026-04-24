package com.bbttvv.app.core.store

import android.content.Context
import com.bbttvv.app.data.model.response.FollowingUser
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class FollowingCacheSnapshot(
    val mid: Long,
    val total: Int,
    val users: List<FollowingUser>,
    val cachedAtMs: Long
)

@Serializable
private data class FollowingCachePayload(
    val mid: Long = 0L,
    val total: Int = 0,
    val users: List<FollowingUser> = emptyList(),
    val cachedAtMs: Long = 0L
)

object FollowingCacheStore {
    private const val PREFS_NAME = "following_cache"
    private const val KEY_PAYLOAD = "following_payload_v1"
    private const val MAX_CACHE_USERS = 2000

    private val lock = Any()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun getSnapshot(context: Context, mid: Long): FollowingCacheSnapshot? {
        if (mid <= 0L) return null
        return synchronized(lock) {
            val raw = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PAYLOAD, null)
                .orEmpty()
            if (raw.isBlank()) return@synchronized null

            val payload = runCatching {
                json.decodeFromString<FollowingCachePayload>(raw)
            }.getOrNull() ?: return@synchronized null

            if (payload.mid != mid) return@synchronized null
            val normalizedUsers = payload.users
                .asSequence()
                .filter { it.mid > 0L }
                .distinctBy { it.mid }
                .take(MAX_CACHE_USERS)
                .toList()
            if (normalizedUsers.isEmpty()) return@synchronized null

            FollowingCacheSnapshot(
                mid = payload.mid,
                total = payload.total.coerceAtLeast(normalizedUsers.size),
                users = normalizedUsers,
                cachedAtMs = payload.cachedAtMs
            )
        }
    }

    fun saveSnapshot(
        context: Context,
        mid: Long,
        total: Int,
        users: List<FollowingUser>,
        cachedAtMs: Long = System.currentTimeMillis()
    ) {
        if (mid <= 0L) return
        synchronized(lock) {
            val normalizedUsers = users
                .asSequence()
                .filter { it.mid > 0L }
                .distinctBy { it.mid }
                .take(MAX_CACHE_USERS)
                .toList()

            if (normalizedUsers.isEmpty()) {
                clear(context)
                return
            }

            val payload = FollowingCachePayload(
                mid = mid,
                total = total.coerceAtLeast(normalizedUsers.size),
                users = normalizedUsers,
                cachedAtMs = cachedAtMs
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

