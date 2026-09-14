package com.bbttvv.app.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {
    @Test
    fun `credential query values are redacted from diagnostic text`() {
        val raw = "https://example.test/video?access_key=secret-access&sign=secret-sign&csrf=secret-csrf"

        val sanitized = sanitizeDiagnosticText(raw)

        assertFalse(sanitized.contains("secret-access"))
        assertFalse(sanitized.contains("secret-sign"))
        assertFalse(sanitized.contains("secret-csrf"))
        assertTrue(sanitized.contains("access_key=***"))
        assertTrue(sanitized.contains("sign=***"))
        assertTrue(sanitized.contains("csrf=***"))
    }

    @Test
    fun `throwable text is sanitized before diagnostics`() {
        val throwable = IllegalStateException(
            "request failed: SESSDATA=session-secret auth_code=qr-secret " +
                "https://example.test/?refresh_token=refresh-secret"
        )

        val sanitized = sanitizedThrowableText(throwable)

        assertFalse(sanitized.contains("session-secret"))
        assertFalse(sanitized.contains("qr-secret"))
        assertFalse(sanitized.contains("refresh-secret"))
        assertTrue(sanitized.contains("SESSDATA=***"))
        assertTrue(sanitized.contains("auth_code=***"))
        assertTrue(sanitized.contains("refresh_token=***"))
    }

    @Test
    fun `crash snapshot never embeds raw throwable secrets`() {
        val throwable = IllegalArgumentException(
            "playback failed access_key=plain-secret&sign=plain-sign"
        )
        val content = buildCrashSnapshotContent(
            throwable = throwable,
            entries = listOf(
                LogCollector.LogEntry(
                    timestamp = 0L,
                    level = "E",
                    tag = "Test",
                    message = "csrf=entry-secret"
                )
            ),
            exportedAtMillis = 0L,
            appVersionName = "test",
            versionCode = 1,
            manufacturer = "test",
            model = "test",
            androidRelease = "test",
            apiLevel = 1
        )

        assertFalse(content.contains("plain-secret"))
        assertFalse(content.contains("plain-sign"))
        assertFalse(content.contains("entry-secret"))
        assertTrue(content.contains("access_key=***"))
        assertTrue(content.contains("sign=***"))
        assertTrue(content.contains("csrf=***"))
    }
}
