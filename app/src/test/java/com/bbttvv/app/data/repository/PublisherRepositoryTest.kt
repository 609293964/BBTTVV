package com.bbttvv.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException

class PublisherRepositoryTest {
    @Test
    fun `duration parser handles mm ss`() {
        assertEquals(624, parsePublisherVideoDuration("10:24"))
    }

    @Test
    fun `duration parser handles hh mm ss`() {
        assertEquals(3930, parsePublisherVideoDuration("1:05:30"))
    }

    @Test
    fun `duration parser falls back to zero on invalid text`() {
        assertEquals(0, parsePublisherVideoDuration("abc"))
    }

    @Test
    fun `http 412 message is normalized`() {
        val exception = HttpException(
            Response.error<Unit>(
                412,
                "risk".toResponseBody("text/plain".toMediaType())
            )
        )

        assertEquals("请求过于频繁，请稍后重试", resolvePublisherVideosThrowableMessage(exception))
        assertTrue(shouldRetryPublisherVideosThrowable(exception))
    }

    @Test
    fun `api rate limit message is normalized`() {
        assertEquals("请求过于频繁，请稍后重试", resolvePublisherVideosApiMessage(-412, ""))
        assertTrue(shouldRetryPublisherVideosApi(-412, ""))
    }

    @Test
    fun `timeout message is normalized`() {
        assertEquals(
            "网络请求超时，请稍后重试",
            resolvePublisherVideosThrowableMessage(SocketTimeoutException("timeout"))
        )
    }

    @Test
    fun `raw http 412 string is normalized`() {
        assertEquals(
            "请求过于频繁，请稍后重试",
            normalizePublisherVideoErrorMessage("HTTP 412 Precondition Failed")
        )
    }
}
