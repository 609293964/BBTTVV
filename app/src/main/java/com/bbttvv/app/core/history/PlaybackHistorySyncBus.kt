package com.bbttvv.app.core.history

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PlaybackHistorySyncEvent(
    val mid: Long,
    val bvid: String,
    val cid: Long,
    val reportedAtMs: Long = System.currentTimeMillis()
)

object PlaybackHistorySyncBus {
    private val _events = MutableSharedFlow<PlaybackHistorySyncEvent>(
        replay = 1,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events = _events.asSharedFlow()

    fun publish(
        mid: Long,
        bvid: String,
        cid: Long
    ) {
        if (mid <= 0L || bvid.isBlank() || cid <= 0L) return
        _events.tryEmit(
            PlaybackHistorySyncEvent(
                mid = mid,
                bvid = bvid,
                cid = cid
            )
        )
    }
}
