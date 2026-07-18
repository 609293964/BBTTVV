package com.bbttvv.app.ui.components

/**
 * Records one user-initiated horizontal focus move.
 *
 * Programmatic focus restoration must not be interpreted as a request to select a tab.
 * Using the exact expected target avoids leaking a boolean permission into a later focus event.
 */
internal class AppTopBarDirectionalSelectionTracker {
    private var expectedTarget: AppTopLevelTab? = null

    fun expect(target: AppTopLevelTab) {
        expectedTarget = target
    }

    fun consume(target: AppTopLevelTab): Boolean {
        val matches = expectedTarget == target
        expectedTarget = null
        return matches
    }

    fun clear() {
        expectedTarget = null
    }
}
