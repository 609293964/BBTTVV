package com.bbttvv.app.feature.live

import java.util.ArrayDeque
import java.util.LinkedHashSet

internal class LiveSuperChatQueue(
    private val nowEpochSeconds: () -> Long,
) {
    private val pending = ArrayDeque<LiveSuperChat>()
    private val seenIds = LinkedHashSet<Long>()

    var current: LiveSuperChat? = null
        private set

    fun offer(item: LiveSuperChat): Boolean {
        if (item.endTimeSeconds?.let { it <= nowEpochSeconds() } == true) return false
        if (item.id > 0L && !seenIds.add(item.id)) return false
        while (seenIds.size > MAX_SEEN_IDS) {
            seenIds.iterator().run {
                if (hasNext()) {
                    next()
                    remove()
                }
            }
        }
        if (pending.size >= MAX_PENDING_ITEMS) pending.removeFirst()
        pending.addLast(item)
        return true
    }

    fun startNext(): LiveSuperChat? {
        current?.let { return it }
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (next.endTimeSeconds?.let { it <= nowEpochSeconds() } != true) {
                current = next
                return next
            }
        }
        return null
    }

    fun finishCurrent(expected: LiveSuperChat): Boolean {
        if (current !== expected) return false
        current = null
        return true
    }

    fun delete(ids: Set<Long>): Boolean {
        if (ids.isEmpty()) return false
        val removedCurrent = current?.id in ids
        if (removedCurrent) current = null
        pending.removeAll { it.id in ids }
        return removedCurrent
    }

    fun clear() {
        current = null
        pending.clear()
        seenIds.clear()
    }

    companion object {
        private const val MAX_PENDING_ITEMS = 50
        private const val MAX_SEEN_IDS = 512
    }
}

internal fun LiveSuperChat.displayDurationMs(nowEpochSeconds: Long): Long? {
    val remainingMs = endTimeSeconds?.let { end ->
        val remaining = end - nowEpochSeconds
        if (remaining <= 0L) return null
        remaining.coerceAtMost(MAX_DISPLAY_SECONDS) * 1_000L
    }
    val requestedMs = durationSeconds
        ?.takeIf { it > 0L }
        ?.coerceAtMost(MAX_DISPLAY_SECONDS)
        ?.times(1_000L)
    return minOf(
        MAX_DISPLAY_MS,
        remainingMs ?: MAX_DISPLAY_MS,
        requestedMs ?: MAX_DISPLAY_MS,
    ).takeIf { it > 0L }
}

private const val MAX_DISPLAY_SECONDS = 10L
private const val MAX_DISPLAY_MS = MAX_DISPLAY_SECONDS * 1_000L
