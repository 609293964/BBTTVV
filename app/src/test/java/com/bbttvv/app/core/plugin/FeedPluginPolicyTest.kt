package com.bbttvv.app.core.plugin

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class FeedPluginPolicyTest {
    @Test fun `broken plugin is skipped for the rest of a page while other filters still run`() {
        var calls = 0
        var failures = 0
        val result = filterFeedBatch(listOf(1, 2, 3), listOf("broken", "even"), { filter, item ->
            if (filter == "broken") { calls++; error("fixture") }
            item % 2 == 0
        }, { _, _ -> failures++ })
        assertEquals(listOf(2), result)
        assertEquals(1, calls)
        assertEquals(1, failures)
    }
    @Test(expected = CancellationException::class)
    fun `cancelled feed work never publishes a partial result`() {
        filterFeedBatch(listOf(1), listOf("cancelled"), { _, _ -> throw CancellationException() }, { _, _ -> })
    }
}
