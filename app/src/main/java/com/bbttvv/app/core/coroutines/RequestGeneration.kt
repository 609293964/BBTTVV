package com.bbttvv.app.core.coroutines

/**
 * Monotonic generation guard for async work whose older results must never overwrite newer state.
 * Thread-safe because completion callbacks may run on different dispatchers.
 */
internal class RequestGeneration {
    private var generation: Long = 0L

    @Synchronized
    fun next(): Long {
        generation += 1L
        return generation
    }

    @Synchronized
    fun current(): Long = generation

    @Synchronized
    fun isCurrent(expected: Long): Boolean = generation == expected

    @Synchronized
    fun invalidate(): Long = next()
}
