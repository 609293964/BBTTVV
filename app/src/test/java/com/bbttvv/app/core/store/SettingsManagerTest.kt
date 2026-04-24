package com.bbttvv.app.core.store

import com.bbttvv.app.core.util.SubtitleAutoPreference
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsManagerTest {

    @Test
    fun `normalizeHomeRefreshCount clamps values into supported range`() {
        assertEquals(10, normalizeHomeRefreshCount(1))
        assertEquals(20, normalizeHomeRefreshCount(20))
        assertEquals(40, normalizeHomeRefreshCount(99))
    }

    @Test
    fun `resolveSubtitleAutoPreference falls back to ON for unknown values`() {
        assertEquals(SubtitleAutoPreference.OFF, resolveSubtitleAutoPreference("OFF"))
        assertEquals(SubtitleAutoPreference.AUTO, resolveSubtitleAutoPreference("AUTO"))
        assertEquals(SubtitleAutoPreference.ON, resolveSubtitleAutoPreference(null))
        assertEquals(SubtitleAutoPreference.ON, resolveSubtitleAutoPreference("unexpected"))
    }

    @Test
    fun `normalizeUserAgent falls back to default for blank values`() {
        assertEquals(DEFAULT_APP_USER_AGENT, normalizeUserAgent(null))
        assertEquals(DEFAULT_APP_USER_AGENT, normalizeUserAgent("   "))
        assertEquals("Custom UA", normalizeUserAgent("  Custom UA  "))
    }
}
