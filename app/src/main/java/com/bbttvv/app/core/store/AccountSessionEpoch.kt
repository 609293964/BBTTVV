package com.bbttvv.app.core.store

import java.util.concurrent.atomic.AtomicLong

/**
 * Process-local generation for account-scoped work.
 *
 * Async work captures [current] before it starts and must refuse to publish account-scoped
 * results after the epoch changes. Every committed account transition advances the epoch.
 */
object AccountSessionEpoch {
    private val generation = AtomicLong(0L)

    fun current(): Long = generation.get()

    fun advance(): Long = generation.incrementAndGet()

    fun isCurrent(expected: Long): Boolean = generation.get() == expected
}
