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
