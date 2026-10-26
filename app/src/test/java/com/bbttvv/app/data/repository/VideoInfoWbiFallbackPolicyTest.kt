package com.bbttvv.app.data.repository

import com.bbttvv.app.data.model.response.VideoDetailResponse
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class VideoInfoWbiFallbackPolicyTest {
    private fun httpError(code: Int): HttpException {
        return HttpException(Response.error<Any>(code, "".toResponseBody(null)))
    }

    private class FakeEndpoints(
        private val legacy: () -> VideoDetailResponse,
        wbiCodes: List<Int>,
    ) {
        private val pendingWbiCodes = ArrayDeque(wbiCodes)
        var legacyCalls = 0
        var wbiCalls = 0
        var keyInvalidations = 0

        fun resolve(state: VideoInfoEndpointState): VideoDetailResponse = runBlocking {
            resolveVideoInfoWithWbiFallback(
                state = state,
                legacyRequest = {
                    legacyCalls++
                    legacy()
                },
                wbiRequest = {
                    wbiCalls++
                    VideoDetailResponse(code = pendingWbiCodes.removeFirst())
                },
                invalidateWbiKeys = { keyInvalidations++ },
            )
        }
    }

    private fun blockedLegacy(): VideoDetailResponse = throw httpError(412)

    @Test
    fun `legacy success never touches wbi`() {
        val state = VideoInfoEndpointState()
        val endpoints = FakeEndpoints(legacy = { VideoDetailResponse(code = 0) }, wbiCodes = emptyList())

        assertEquals(0, endpoints.resolve(state).code)
        assertEquals(0, endpoints.wbiCalls)
        assertFalse(state.shouldSkipLegacy())
    }

    @Test
    fun `legacy 412 falls back to wbi and remembers block`() {
        val state = VideoInfoEndpointState()
        val endpoints = FakeEndpoints(legacy = ::blockedLegacy, wbiCodes = listOf(0, 0))

        assertEquals(0, endpoints.resolve(state).code)
        assertTrue(state.shouldSkipLegacy())

        assertEquals(0, endpoints.resolve(state).code)
        assertEquals(1, endpoints.legacyCalls)
        assertEquals(2, endpoints.wbiCalls)
    }

    @Test
    fun `wbi risk control code refreshes keys and retries once`() {
        val state = VideoInfoEndpointState()
        state.markLegacyBlocked()
        val endpoints = FakeEndpoints(legacy = ::blockedLegacy, wbiCodes = listOf(-352, 0))

        assertEquals(0, endpoints.resolve(state).code)
        assertEquals(1, endpoints.keyInvalidations)
        assertEquals(2, endpoints.wbiCalls)
        assertTrue(state.shouldSkipLegacy())
    }

    @Test
    fun `persistent wbi risk control clears block so legacy is tried next`() {
        val state = VideoInfoEndpointState()
        state.markLegacyBlocked()
        val endpoints = FakeEndpoints(legacy = ::blockedLegacy, wbiCodes = listOf(-412, -412))

        assertEquals(-412, endpoints.resolve(state).code)
        assertEquals(2, endpoints.wbiCalls)
        assertFalse(state.shouldSkipLegacy())
    }

    @Test
    fun `fallback does not remember block when wbi is also risk controlled`() {
        val state = VideoInfoEndpointState()
        val endpoints = FakeEndpoints(legacy = ::blockedLegacy, wbiCodes = listOf(-403, -403))

        assertEquals(-403, endpoints.resolve(state).code)
        assertFalse(state.shouldSkipLegacy())
    }

    @Test
    fun `video not found keeps block and does not refresh keys`() {
        val state = VideoInfoEndpointState()
        state.markLegacyBlocked()
        val endpoints = FakeEndpoints(legacy = ::blockedLegacy, wbiCodes = listOf(-404))

        assertEquals(-404, endpoints.resolve(state).code)
        assertEquals(0, endpoints.keyInvalidations)
        assertTrue(state.shouldSkipLegacy())
    }

    @Test
    fun `direct wbi exception clears block`() {
        val state = VideoInfoEndpointState()
        state.markLegacyBlocked()

        assertThrows(IOException::class.java) {
            runBlocking {
                resolveVideoInfoWithWbiFallback(
                    state = state,
                    legacyRequest = { VideoDetailResponse(code = 0) },
                    wbiRequest = { throw IOException("timeout") },
                    invalidateWbiKeys = {},
                )
            }
        }
        assertFalse(state.shouldSkipLegacy())
    }

    @Test
    fun `non risk legacy errors propagate without fallback`() {
        val state = VideoInfoEndpointState()
        val endpoints = FakeEndpoints(legacy = { throw httpError(500) }, wbiCodes = emptyList())

        assertThrows(HttpException::class.java) { endpoints.resolve(state) }
        assertEquals(0, endpoints.wbiCalls)
        assertFalse(state.shouldSkipLegacy())
    }

    @Test
    fun `http 412 from legacy view falls back to wbi view`() {
        assertTrue(shouldFallbackToWbiVideoInfo(httpError(412)))
    }

    @Test
    fun `other http and network errors do not fall back`() {
        assertFalse(shouldFallbackToWbiVideoInfo(httpError(404)))
        assertFalse(shouldFallbackToWbiVideoInfo(httpError(500)))
        assertFalse(shouldFallbackToWbiVideoInfo(IOException("timeout")))
    }

    @Test
    fun `only risk control api code falls back`() {
        assertTrue(shouldFallbackToWbiVideoInfo(apiCode = -412))
        assertFalse(shouldFallbackToWbiVideoInfo(apiCode = 0))
        assertFalse(shouldFallbackToWbiVideoInfo(apiCode = -404))
        assertFalse(shouldFallbackToWbiVideoInfo(apiCode = -403))
    }

    @Test
    fun `endpoint state starts with legacy and remembers risk control block`() {
        val state = VideoInfoEndpointState()
        assertFalse(state.shouldSkipLegacy())

        state.markLegacyBlocked()
        assertTrue(state.shouldSkipLegacy())
    }

    @Test
    fun `direct wbi failure returns to legacy first`() {
        val state = VideoInfoEndpointState()
        state.markLegacyBlocked()

        state.onDirectWbiFailed()
        assertFalse(state.shouldSkipLegacy())
    }

    @Test
    fun `wbi params prefer bvid and include signature`() {
        val params = buildVideoInfoWbiParams(
            bvid = "BV1xx411c7mD",
            aid = 170001L,
            imgKey = "abcdefghijklmnopqrstuvwxyz012345",
            subKey = "6789ABCDEFGHIJKLMNOPQRSTUVWXYZabcd",
        )

        assertEquals("BV1xx411c7mD", params["bvid"])
        assertFalse(params.containsKey("aid"))
        assertFalse(params["wts"].isNullOrBlank())
        assertFalse(params["w_rid"].isNullOrBlank())
    }

    @Test
    fun `wbi params use aid when bvid is missing`() {
        val params = buildVideoInfoWbiParams(
            bvid = "",
            aid = 170001L,
            imgKey = "abcdefghijklmnopqrstuvwxyz012345",
            subKey = "6789ABCDEFGHIJKLMNOPQRSTUVWXYZabcd",
        )

        assertEquals("170001", params["aid"])
        assertFalse(params.containsKey("bvid"))
        assertFalse(params["w_rid"].isNullOrBlank())
    }
}
