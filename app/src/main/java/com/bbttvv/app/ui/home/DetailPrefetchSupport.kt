package com.bbttvv.app.ui.home

internal object FocusSummaryPrefetchDelayPolicy {
    fun delayMillis(previousFocusAtMs: Long?, currentFocusAtMs: Long): Long {
        val intervalMs = previousFocusAtMs?.let { currentFocusAtMs - it } ?: return 0L
        return when {
            intervalMs < 0L -> 0L
            intervalMs < 100L -> 250L
            intervalMs < 250L -> 100L
            else -> 0L
        }
    }
}
