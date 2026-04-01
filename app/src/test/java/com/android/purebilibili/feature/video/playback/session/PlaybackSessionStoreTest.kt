package com.android.purebilibili.feature.video.playback.session

import com.android.purebilibili.feature.video.playback.loader.PlaybackRequest
import com.android.purebilibili.feature.video.policy.ResumePlaybackSuggestion
import com.android.purebilibili.feature.video.viewmodel.PlaybackEndAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlaybackSessionStoreTest {

    @Test
    fun `setResumeSuggestion should update session state`() {
        val store = PlaybackSessionStore()
        val suggestion = ResumePlaybackSuggestion(
            targetBvid = "BV1resume",
            targetCid = 2233L,
            targetLabel = "P2",
            positionMs = 60_000L
        )

        store.setResumeSuggestion(suggestion)

        assertEquals(suggestion, store.state.value.resumeSuggestion)
    }

    @Test
    fun `consumeResumeSuggestion should clear stored suggestion after returning it`() {
        val store = PlaybackSessionStore()
        val suggestion = ResumePlaybackSuggestion(
            targetBvid = "BV1resume",
            targetCid = 3344L,
            targetLabel = "P3",
            positionMs = 90_000L
        )
        store.setResumeSuggestion(suggestion)

        val consumed = store.consumeResumeSuggestion()

        assertEquals(suggestion, consumed)
        assertNull(store.state.value.resumeSuggestion)
    }

    @Test
    fun `recordCompletionAction should persist the last action`() {
        val store = PlaybackSessionStore()

        store.recordCompletionAction(PlaybackEndAction.AUTO_CONTINUE)

        assertEquals(
            PlaybackEndAction.AUTO_CONTINUE,
            store.state.value.lastCompletionAction
        )
    }

    @Test
    fun `updateCurrentMedia should keep current playback identity in session state`() {
        val store = PlaybackSessionStore()

        store.updateCurrentMedia(
            bvid = "BV1session",
            cid = 4455L
        )

        assertEquals("BV1session", store.state.value.currentBvid)
        assertEquals(4455L, store.state.value.currentCid)
    }

    @Test
    fun `clearCurrentMedia should reset current playback identity`() {
        val store = PlaybackSessionStore()
        store.setCurrentRequest(PlaybackRequest.create(bvid = "BV1session"))
        store.updateCurrentMedia(
            bvid = "BV1session",
            cid = 4455L
        )

        store.clearCurrentMedia()

        assertEquals("", store.state.value.currentBvid)
        assertEquals(0L, store.state.value.currentCid)
        assertNull(store.state.value.currentRequest)
    }

    @Test
    fun `next request tokens should increment independently`() {
        val store = PlaybackSessionStore()

        val load1 = store.nextLoadRequestToken()
        val load2 = store.nextLoadRequestToken()
        val subtitle1 = store.nextSubtitleToken()
        val subtitle2 = store.nextSubtitleToken()

        assertEquals(1L, load1)
        assertEquals(2L, load2)
        assertEquals(1L, subtitle1)
        assertEquals(2L, subtitle2)
        assertEquals(2L, store.state.value.currentLoadRequestToken)
        assertEquals(2L, store.state.value.subtitleLoadToken)
        assertTrue(store.state.value.currentLoadRequestToken == load2)
    }

    @Test
    fun `beginLoadRequest should store request and advance request tokens`() {
        val store = PlaybackSessionStore()
        val request = PlaybackRequest.create(
            bvid = "BV1load",
            cid = 2233L,
            aid = 7788L
        )

        val context = store.beginLoadRequest(request)

        assertSame(request, context.request)
        assertEquals(1L, context.requestToken)
        assertEquals(1L, context.subtitleToken)
        assertSame(request, store.state.value.currentRequest)
        assertEquals("BV1load", store.state.value.currentBvid)
        assertEquals(1L, store.state.value.currentLoadRequestToken)
        assertEquals(1L, store.state.value.subtitleLoadToken)
    }

    @Test
    fun `clearCurrentRequest should remove active request context`() {
        val store = PlaybackSessionStore()
        store.beginLoadRequest(PlaybackRequest.create(bvid = "BV1load"))

        store.clearCurrentRequest()

        assertNull(store.state.value.currentRequest)
    }

    @Test
    fun `blockVideoCodec should normalize codec key and persist in session state`() {
        val store = PlaybackSessionStore()

        store.blockVideoCodec(" av01.0.08M.08 ")

        assertContains(store.state.value.blockedVideoCodecs, "av01")
    }
}
