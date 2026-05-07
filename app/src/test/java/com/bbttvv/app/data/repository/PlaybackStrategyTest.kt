package com.bbttvv.app.data.repository

import com.bbttvv.app.data.model.response.Dash
import com.bbttvv.app.data.model.response.DashVideo
import com.bbttvv.app.data.model.response.PlayUrlData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStrategyTest {
    @Test
    fun `playback error classifier marks fatal WBI errors`() {
        val diagnosis = PlaybackErrorClassifier.classifyWbiApi(
            code = -10403,
            message = "需要会员"
        )

        assertTrue(diagnosis.isFatal)
        assertEquals(PlaybackErrorKind.FATAL, diagnosis.kind)
        assertEquals("需要大会员才能观看", diagnosis.userMessage)
    }

    @Test
    fun `logged in strategy uses app fallback after dash misses`() = runBlocking {
        PlaybackSessionManager.recordAppApiSuccess()
        val request = PlaybackStrategyRequest(
            bvid = "BV1test",
            cid = 100L,
            targetQn = 80,
            audioLang = null,
            requestKind = PlayUrlRequestKind.EXPLICIT,
            isLoggedIn = true,
            hasAccessToken = true
        )
        val gateway = PlaybackGateway(
            fetchDash = { _, _ ->
                PlaybackSourceResult(
                    diagnosis = PlaybackErrorClassifier.emptyPayload("WBI playurl")
                )
            },
            fetchApp = { requestedQn, _ ->
                PlaybackSourceResult(data = dashPayload(quality = requestedQn))
            },
            fetchLegacy = {
                throw AssertionError("legacy fallback should not run after APP success")
            },
            fetchGuest = {
                throw AssertionError("guest fallback should not run after APP success")
            }
        )

        val result = LoggedInPlaybackStrategy.resolve(request = request, gateway = gateway)

        assertEquals(PlayUrlSource.APP, result?.source)
        assertEquals(80, result?.data?.quality)
    }

    @Test
    fun `logged in auto highest rejects downgraded dash and uses app fallback for 1080`() = runBlocking {
        PlaybackSessionManager.recordAppApiSuccess()
        val request = PlaybackStrategyRequest(
            bvid = "BV1auto",
            cid = 101L,
            targetQn = 80,
            audioLang = null,
            requestKind = PlayUrlRequestKind.AUTO_HIGHEST,
            isLoggedIn = true,
            hasAccessToken = true
        )
        val gateway = PlaybackGateway(
            fetchDash = { _, _ ->
                PlaybackSourceResult(
                    data = dashPayload(
                        quality = 64,
                        acceptQuality = listOf(80, 64),
                        dashVideoIds = listOf(64)
                    )
                )
            },
            fetchApp = { requestedQn, _ ->
                PlaybackSourceResult(data = dashPayload(quality = requestedQn))
            },
            fetchLegacy = {
                throw AssertionError("legacy fallback should not run after APP recovers 1080")
            },
            fetchGuest = {
                throw AssertionError("guest fallback should not run after APP recovers 1080")
            }
        )

        val result = LoggedInPlaybackStrategy.resolve(request = request, gateway = gateway)

        assertEquals(PlayUrlSource.APP, result?.source)
        assertEquals(80, result?.data?.quality)
    }

    @Test
    fun `guest strategy rejects downgraded explicit legacy result`() = runBlocking {
        val request = PlaybackStrategyRequest(
            bvid = "BV1guest",
            cid = 200L,
            targetQn = 120,
            audioLang = null,
            requestKind = PlayUrlRequestKind.EXPLICIT,
            isLoggedIn = false,
            hasAccessToken = false
        )
        val gateway = PlaybackGateway(
            fetchDash = { _, _ ->
                PlaybackSourceResult(
                    diagnosis = PlaybackErrorClassifier.emptyPayload("WBI playurl")
                )
            },
            fetchApp = { _, _ ->
                throw AssertionError("guest strategy should not use app fallback")
            },
            fetchLegacy = { _ ->
                PlaybackSourceResult(data = dashPayload(quality = 80))
            },
            fetchGuest = {
                throw AssertionError("guest strategy should not use guest fallback list")
            }
        )

        val result = GuestPlaybackStrategy.resolve(request = request, gateway = gateway)

        assertEquals(null, result)
    }

    private fun dashPayload(
        quality: Int,
        acceptQuality: List<Int> = listOf(quality),
        dashVideoIds: List<Int> = listOf(quality)
    ): PlayUrlData {
        return PlayUrlData(
            quality = quality,
            acceptQuality = acceptQuality,
            dash = Dash(
                video = dashVideoIds.map { videoQuality ->
                    DashVideo(
                        id = videoQuality,
                        baseUrl = "https://example.com/video-$videoQuality.m4s"
                    )
                }.ifEmpty {
                    listOf(DashVideo(
                        id = quality,
                        baseUrl = "https://example.com/video-$quality.m4s"
                    ))
                }
            )
        )
    }
}
