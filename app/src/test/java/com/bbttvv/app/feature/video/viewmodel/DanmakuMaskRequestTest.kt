package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.core.store.player.DanmakuSettings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuMaskRequestTest {
    @Test
    fun `enabling during playback loads current media and disable clears it`() = runBlocking {
        withTimeout(5_000) {
            val media = MutableStateFlow<Pair<String, Long>?>("BV-first" to 1L)
            val settings = MutableStateFlow(DanmakuSettings())
            val requests = Channel<Pair<String, Long>?>(Channel.UNLIMITED)
            val collector = launch {
                danmakuMaskRequests(media, settings).collect { requests.send(it) }
            }
            try {
                assertEquals(null, requests.receive())
                settings.value = settings.value.copy(smartMaskEnabled = true)
                assertEquals("BV-first" to 1L, requests.receive())
                settings.value = settings.value.copy(smartMaskEnabled = false)
                assertEquals(null, requests.receive())
                media.value = "BV-second" to 2L
                settings.value = settings.value.copy(smartMaskEnabled = true)
                assertEquals("BV-second" to 2L, requests.receive())
            } finally {
                collector.cancel()
                requests.close()
            }
        }
    }

    @Test
    fun `same media and unrelated settings do not reload while new media invalidates mask`() = runBlocking {
        withTimeout(5_000) {
            val media = MutableStateFlow<Pair<String, Long>?>("BV-first" to 1L)
            val settings = MutableStateFlow(DanmakuSettings(smartMaskEnabled = true))
            val requests = Channel<Pair<String, Long>?>(Channel.UNLIMITED)
            val collector = launch {
                danmakuMaskRequests(media, settings).collect { requests.send(it) }
            }
            try {
                assertEquals("BV-first" to 1L, requests.receive())
                media.value = "BV-first" to 1L // Same media after quality/CDN replacement.
                settings.value = settings.value.copy(opacity = 0.4f)
                media.value = null // New playback session.
                assertEquals(null, requests.receive())
                media.value = "BV-first" to 2L // Another part of the same video.
                assertEquals("BV-first" to 2L, requests.receive())
            } finally {
                collector.cancel()
                requests.close()
            }
        }
    }
}
