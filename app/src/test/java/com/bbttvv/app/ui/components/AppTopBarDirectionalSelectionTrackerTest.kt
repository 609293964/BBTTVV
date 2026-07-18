package com.bbttvv.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTopBarDirectionalSelectionTrackerTest {
    @Test
    fun `expected directional target is consumed once`() {
        val tracker = AppTopBarDirectionalSelectionTracker()

        tracker.expect(AppTopLevelTab.POPULAR)

        assertTrue(tracker.consume(AppTopLevelTab.POPULAR))
        assertFalse(tracker.consume(AppTopLevelTab.POPULAR))
    }

    @Test
    fun `unexpected focus cannot inherit directional selection`() {
        val tracker = AppTopBarDirectionalSelectionTracker()

        tracker.expect(AppTopLevelTab.POPULAR)

        assertFalse(tracker.consume(AppTopLevelTab.SEARCH))
        assertFalse(tracker.consume(AppTopLevelTab.POPULAR))
    }

    @Test
    fun `programmatic focus cancellation clears directional target`() {
        val tracker = AppTopBarDirectionalSelectionTracker()

        tracker.expect(AppTopLevelTab.DYNAMIC)
        tracker.clear()

        assertFalse(tracker.consume(AppTopLevelTab.DYNAMIC))
    }
}
