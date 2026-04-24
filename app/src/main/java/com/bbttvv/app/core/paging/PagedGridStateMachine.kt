package com.bbttvv.app.core.paging

/**
 * Android-free pagination state machine for grid/list screens.
 *
 * It keeps page-key, loading, end-reached, and refresh-generation transitions in
 * one place so stale in-flight requests cannot overwrite newer refreshes.
 */
class PagedGridStateMachine<K>(
    private val initialKey: K,
) {
    data class State<K>(
        val nextKey: K,
        val isLoading: Boolean,
        val endReached: Boolean,
        val generation: Long,
    )

    data class Update<K, Item>(
        val items: List<Item>,
        val nextKey: K,
        val endReached: Boolean,
    )

    sealed interface LoadResult<out Item> {
        val isRefresh: Boolean

        data class Applied<Item>(
            val items: List<Item>,
            override val isRefresh: Boolean,
        ) : LoadResult<Item>

        data class Skipped(
            override val isRefresh: Boolean,
            val reason: Reason,
        ) : LoadResult<Nothing> {
            enum class Reason {
                AlreadyLoading,
                EndReached,
            }
        }

        data class Aborted(
            override val isRefresh: Boolean,
        ) : LoadResult<Nothing>

        data class IgnoredStale(
            override val isRefresh: Boolean,
        ) : LoadResult<Nothing>
    }

    private val lock = Any()

    @Volatile
    private var state: State<K> = State(
        nextKey = initialKey,
        isLoading = false,
        endReached = false,
        generation = 0L,
    )

    fun snapshot(): State<K> = state

    fun reset() {
        resetTo(initialKey)
    }

    fun resetTo(nextKey: K) {
        synchronized(lock) {
            state = State(
                nextKey = nextKey,
                isLoading = false,
                endReached = false,
                generation = state.generation + 1,
            )
        }
    }

    suspend fun <Fetched, Item> loadNextPage(
        isRefresh: Boolean,
        fetch: suspend (key: K) -> Fetched?,
        reduce: (key: K, fetched: Fetched) -> Update<K, Item>,
    ): LoadResult<Item> {
        val generationAtStart: Long
        val keyAtStart: K
        synchronized(lock) {
            val current = state
            if (current.endReached) {
                return LoadResult.Skipped(
                    isRefresh = isRefresh,
                    reason = LoadResult.Skipped.Reason.EndReached,
                )
            }
            if (current.isLoading) {
                return LoadResult.Skipped(
                    isRefresh = isRefresh,
                    reason = LoadResult.Skipped.Reason.AlreadyLoading,
                )
            }
            generationAtStart = current.generation
            keyAtStart = current.nextKey
            state = current.copy(isLoading = true)
        }

        try {
            val fetched = fetch(keyAtStart)
            if (fetched == null) {
                synchronized(lock) {
                    val current = state
                    if (current.generation == generationAtStart) {
                        state = current.copy(isLoading = false)
                    }
                }
                return LoadResult.Aborted(isRefresh = isRefresh)
            }

            val update = reduce(keyAtStart, fetched)
            synchronized(lock) {
                val current = state
                if (current.generation != generationAtStart) {
                    return LoadResult.IgnoredStale(isRefresh = isRefresh)
                }
                state = current.copy(
                    nextKey = update.nextKey,
                    isLoading = false,
                    endReached = update.endReached,
                )
            }
            return LoadResult.Applied(items = update.items, isRefresh = isRefresh)
        } catch (throwable: Throwable) {
            synchronized(lock) {
                val current = state
                if (current.generation == generationAtStart) {
                    state = current.copy(isLoading = false)
                }
            }
            throw throwable
        }
    }
}

fun <Item> PagedGridStateMachine.LoadResult<Item>.appliedOrNull():
    PagedGridStateMachine.LoadResult.Applied<Item>? {
    return this as? PagedGridStateMachine.LoadResult.Applied<Item>
}
