package com.bbttvv.app.data.model.response

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorBlockModelsTest {
    @Test
    fun `cid accepts string response and chapter remains navigable`() {
        val segment = Json { ignoreUnknownKeys = true }.decodeFromString<SponsorSegment>(
            """{"segment":[12.5,12.5],"UUID":"chapter-1","category":"chapter","actionType":"chapter","cid":"987"}"""
        )

        assertEquals(987L, segment.cid)
        assertEquals(12_500L, segment.startTimeMs)
        assertTrue(segment.isNavigationType)
    }
}
