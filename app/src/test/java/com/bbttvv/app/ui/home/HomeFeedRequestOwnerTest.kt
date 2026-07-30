package com.bbttvv.app.ui.home

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedRequestOwnerTest {
    @Test
    fun `refresh cancels and joins an active append before starting`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val owner = HomeFeedRequestOwner(scope)
        val appendStarted = CompletableDeferred<Unit>()
        val appendCancelled = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Boolean>()

        try {
            assertTrue(
                owner.launchAppend {
                    appendStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        appendCancelled.complete(Unit)
                    }
                }
            )
            appendStarted.await()

            owner.launchRefresh {
                refreshStarted.complete(appendCancelled.isCompleted && isCurrent())
            }

            assertTrue(withTimeout(2_000L) { refreshStarted.await() })
        } finally {
            owner.cancel()
            scope.cancel()
        }
    }

    @Test
    fun `rapid refresh keeps only the latest request current`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val owner = HomeFeedRequestOwner(scope)
        val firstStarted = CompletableDeferred<Unit>()
        val firstCancelled = CompletableDeferred<Unit>()
        val latestCurrent = CompletableDeferred<Boolean>()

        try {
            owner.launchRefresh {
                firstStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    firstCancelled.complete(Unit)
                }
            }
            firstStarted.await()

            owner.launchRefresh {
                latestCurrent.complete(firstCancelled.isCompleted && isCurrent())
            }

            assertTrue(withTimeout(2_000L) { latestCurrent.await() })
        } finally {
            owner.cancel()
            scope.cancel()
        }
    }

    @Test
    fun `duplicate append is rejected while append is active`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val owner = HomeFeedRequestOwner(scope)
        val appendStarted = CompletableDeferred<Unit>()
        val events = CopyOnWriteArrayList<String>()

        try {
            assertTrue(
                owner.launchAppend {
                    events += "first"
                    appendStarted.complete(Unit)
                    awaitCancellation()
                }
            )
            appendStarted.await()
            assertFalse(
                owner.launchAppend {
                    events += "second"
                }
            )
            assertEquals(listOf("first"), events)
        } finally {
            owner.cancel()
            scope.cancel()
        }
    }

    @Test
    fun `cancelled request cannot commit after the replacement starts`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val owner = HomeFeedRequestOwner(scope)
        val firstStarted = CompletableDeferred<Unit>()
        val staleCommitAllowed = CompletableDeferred<Boolean>()
        val latestStarted = CompletableDeferred<Unit>()

        try {
            owner.launchRefresh {
                firstStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    staleCommitAllowed.complete(isCurrent())
                }
            }
            firstStarted.await()

            owner.launchRefresh {
                latestStarted.complete(Unit)
            }

            withTimeout(2_000L) { latestStarted.await() }
            assertFalse(withTimeout(2_000L) { staleCommitAllowed.await() })
        } finally {
            owner.cancel()
            scope.cancel()
        }
    }
}
