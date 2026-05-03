package com.bbttvv.app.core.paging

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedGridStateMachineTest {
    @Test
    fun resetIgnoresInFlightRequest() = runBlocking {
        val machine = PagedGridStateMachine(initialKey = 1)
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()

        val job = launch {
            val result = machine.loadNextPage(
                isRefresh = true,
                fetch = { key ->
                    started.complete(Unit)
                    gate.await()
                    key
                },
                reduce = { key, _ ->
                    PagedGridStateMachine.Update(
                        items = listOf("stale"),
                        nextKey = key + 1,
                        endReached = false,
                    )
                },
            )

            assertTrue(result is PagedGridStateMachine.LoadResult.IgnoredStale)
        }

        started.await()
        machine.reset()
        gate.complete(Unit)
        job.join()

        val snapshot = machine.snapshot()
        assertEquals(1, snapshot.nextKey)
        assertFalse(snapshot.isLoading)
        assertFalse(snapshot.endReached)
    }

    @Test
    fun resetAllowsNewRequestToApplyWhileOldRequestIsIgnored() = runBlocking {
        val machine = PagedGridStateMachine(initialKey = 1)
        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()

        val oldJob = launch {
            val result = machine.loadNextPage(
                isRefresh = false,
                fetch = { key ->
                    oldStarted.complete(Unit)
                    releaseOld.await()
                    key
                },
                reduce = { key, _ ->
                    PagedGridStateMachine.Update(
                        items = listOf("old"),
                        nextKey = key + 1,
                        endReached = false,
                    )
                },
            )

            assertTrue(result is PagedGridStateMachine.LoadResult.IgnoredStale)
        }

        oldStarted.await()
        machine.reset()

        val freshResult = machine.loadNextPage(
            isRefresh = true,
            fetch = { key -> key },
            reduce = { key, _ ->
                PagedGridStateMachine.Update(
                    items = listOf("fresh"),
                    nextKey = key + 1,
                    endReached = false,
                )
            },
        )

        releaseOld.complete(Unit)
        oldJob.join()

        assertTrue(freshResult is PagedGridStateMachine.LoadResult.Applied)
        assertEquals(2, machine.snapshot().nextKey)
        assertFalse(machine.snapshot().isLoading)
    }

    @Test
    fun endReachedSkipsFetch() = runBlocking {
        val machine = PagedGridStateMachine(initialKey = 1)
        var fetchCount = 0

        val first = machine.loadNextPage(
            isRefresh = true,
            fetch = { key ->
                fetchCount += 1
                key
            },
            reduce = { key, _ ->
                PagedGridStateMachine.Update(
                    items = emptyList<String>(),
                    nextKey = key,
                    endReached = true,
                )
            },
        )
        assertTrue(first is PagedGridStateMachine.LoadResult.Applied)

        val second = machine.loadNextPage(
            isRefresh = false,
            fetch = { key ->
                fetchCount += 1
                key
            },
            reduce = { key, _ ->
                PagedGridStateMachine.Update(
                    items = listOf("never"),
                    nextKey = key + 1,
                    endReached = false,
                )
            },
        )

        assertTrue(second is PagedGridStateMachine.LoadResult.Skipped)
        assertEquals(1, fetchCount)
    }

    @Test
    fun refreshSkipsWhileLoadMoreIsInFlight() = runBlocking {
        val machine = PagedGridStateMachine(initialKey = 1)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var refreshFetchCount = 0

        val loadMoreJob = launch {
            val result = machine.loadNextPage(
                isRefresh = false,
                fetch = { key ->
                    started.complete(Unit)
                    release.await()
                    key
                },
                reduce = { key, _ ->
                    PagedGridStateMachine.Update(
                        items = listOf("page"),
                        nextKey = key + 1,
                        endReached = false,
                    )
                },
            )

            assertTrue(result is PagedGridStateMachine.LoadResult.Applied)
        }

        started.await()
        val refreshResult = machine.refresh(
            fetch = { key ->
                refreshFetchCount += 1
                key
            },
            reduce = { key, _ ->
                PagedGridStateMachine.Update(
                    items = listOf("refresh"),
                    nextKey = key + 1,
                    endReached = false,
                )
            },
        )

        assertTrue(refreshResult is PagedGridStateMachine.LoadResult.Skipped)
        val skipped = refreshResult as PagedGridStateMachine.LoadResult.Skipped
        assertTrue(skipped.isRefresh)
        assertEquals(PagedGridStateMachine.LoadResult.Skipped.Reason.AlreadyLoading, skipped.reason)
        assertEquals(0, refreshFetchCount)

        release.complete(Unit)
        loadMoreJob.join()
    }

    @Test
    fun refreshRestartsFromInitialKeyAfterEndReached() = runBlocking {
        val machine = PagedGridStateMachine(initialKey = 1)
        var refreshKey = 0

        val first = machine.loadNextPage(
            isRefresh = false,
            fetch = { key -> key },
            reduce = { _, _ ->
                PagedGridStateMachine.Update(
                    items = emptyList<String>(),
                    nextKey = 99,
                    endReached = true,
                )
            },
        )
        assertTrue(first is PagedGridStateMachine.LoadResult.Applied)
        assertTrue(machine.snapshot().endReached)

        val refreshed = machine.refresh(
            fetch = { key ->
                refreshKey = key
                key
            },
            reduce = { key, _ ->
                PagedGridStateMachine.Update(
                    items = listOf("fresh"),
                    nextKey = key + 1,
                    endReached = false,
                )
            },
        )

        assertTrue(refreshed is PagedGridStateMachine.LoadResult.Applied)
        val applied = refreshed as PagedGridStateMachine.LoadResult.Applied
        assertTrue(applied.isRefresh)
        assertEquals(listOf("fresh"), applied.items)
        assertEquals(1, refreshKey)
        val snapshot = machine.snapshot()
        assertEquals(2, snapshot.nextKey)
        assertFalse(snapshot.isLoading)
        assertFalse(snapshot.endReached)
    }

    @Test
    fun refreshAbortDoesNotLockStateMachine() = runBlocking {
        val machine = PagedGridStateMachine(initialKey = 1)

        val aborted = machine.refresh<Int, String>(
            fetch = { null },
            reduce = { key, _ ->
                PagedGridStateMachine.Update(
                    items = listOf("never"),
                    nextKey = key + 1,
                    endReached = false,
                )
            },
        )

        assertTrue(aborted is PagedGridStateMachine.LoadResult.Aborted)
        assertTrue(aborted.isRefresh)
        assertFalse(machine.snapshot().isLoading)

        val retry = machine.loadNextPage(
            isRefresh = false,
            fetch = { key -> key },
            reduce = { key, _ ->
                PagedGridStateMachine.Update(
                    items = listOf("ok"),
                    nextKey = key + 1,
                    endReached = false,
                )
            },
        )

        assertTrue(retry is PagedGridStateMachine.LoadResult.Applied)
        assertEquals(2, machine.snapshot().nextKey)
        assertFalse(machine.snapshot().isLoading)
    }

    @Test
    fun refreshExceptionDoesNotLockStateMachine() = runBlocking {
        val machine = PagedGridStateMachine(initialKey = 1)
        var attempts = 0

        val thrown = runCatching {
            machine.refresh<Int, String>(
                fetch = {
                    attempts += 1
                    error("boom")
                },
                reduce = { key, _ ->
                    PagedGridStateMachine.Update(
                        items = emptyList(),
                        nextKey = key,
                        endReached = false,
                    )
                },
            )
        }

        assertTrue(thrown.isFailure)
        assertEquals(1, attempts)
        assertFalse(machine.snapshot().isLoading)

        val retry = machine.loadNextPage(
            isRefresh = false,
            fetch = { key ->
                attempts += 1
                key
            },
            reduce = { key, _ ->
                PagedGridStateMachine.Update(
                    items = listOf("ok"),
                    nextKey = key + 1,
                    endReached = false,
                )
            },
        )

        assertTrue(retry is PagedGridStateMachine.LoadResult.Applied)
        assertEquals(2, attempts)
        assertEquals(2, machine.snapshot().nextKey)
        assertFalse(machine.snapshot().isLoading)
    }

    @Test
    fun exceptionDoesNotLockStateMachine() = runBlocking {
        val machine = PagedGridStateMachine(initialKey = 1)
        var attempts = 0

        runCatching {
            machine.loadNextPage(
                isRefresh = false,
                fetch = {
                    attempts += 1
                    error("boom")
                },
                reduce = { key, _ ->
                    PagedGridStateMachine.Update(
                        items = emptyList<Unit>(),
                        nextKey = key,
                        endReached = false,
                    )
                },
            )
        }

        assertEquals(1, attempts)
        assertFalse(machine.snapshot().isLoading)

        val result = machine.loadNextPage(
            isRefresh = false,
            fetch = { key ->
                attempts += 1
                key
            },
            reduce = { key, _ ->
                PagedGridStateMachine.Update(
                    items = listOf("ok"),
                    nextKey = key + 1,
                    endReached = false,
                )
            },
        )

        assertTrue(result is PagedGridStateMachine.LoadResult.Applied)
        assertEquals(2, attempts)
        assertEquals(2, machine.snapshot().nextKey)
        assertFalse(machine.snapshot().isLoading)
    }
}
