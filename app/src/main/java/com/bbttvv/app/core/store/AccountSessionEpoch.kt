package com.bbttvv.app.core.store

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-local generation for account-scoped work.
 *
 * Async work captures [current] before it starts and must refuse to publish account-scoped
 * results after the epoch changes. Every committed account transition advances the epoch.
 */
object AccountSessionEpoch {
    private val generation = AtomicLong(0L)
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()

    fun current(): Long = generation.get()

    fun advance(): Long = generation.incrementAndGet().also { next ->
        revision.update { maxOf(it, next) }
    }

    fun isCurrent(expected: Long): Boolean = generation.get() == expected
}
