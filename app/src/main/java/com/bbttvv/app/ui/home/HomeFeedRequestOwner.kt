package com.bbttvv.app.ui.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

internal enum class HomeFeedRequestKind {
    Append,
    Refresh
}

internal class HomeFeedRequestHandle internal constructor(
    val id: Long,
    val kind: HomeFeedRequestKind,
    private val isCurrentRequest: (Long) -> Boolean
) {
    fun isCurrent(): Boolean = isCurrentRequest(id)
}

/**
 * Owns the single recommendation-feed request. Refresh cancels and joins any older request
 * before starting so the paging state machine is never asked to run two generations at once.
 */
internal class HomeFeedRequestOwner(
    private val scope: CoroutineScope
) {
    private val lock = Any()
    private var requestId = 0L
    private var activeJob: Job? = null

    fun launchAppend(block: suspend HomeFeedRequestHandle.() -> Unit): Boolean {
        return launchRequest(
            kind = HomeFeedRequestKind.Append,
            cancelPrevious = false,
            block = block
        )
    }

    fun launchRefresh(block: suspend HomeFeedRequestHandle.() -> Unit) {
        launchRequest(
            kind = HomeFeedRequestKind.Refresh,
            cancelPrevious = true,
            block = block
        )
    }

    fun cancel() {
        val job = synchronized(lock) {
            requestId += 1L
            activeJob.also { activeJob = null }
        }
        job?.cancel()
    }

    private fun launchRequest(
        kind: HomeFeedRequestKind,
        cancelPrevious: Boolean,
        block: suspend HomeFeedRequestHandle.() -> Unit
    ): Boolean {
        val previousJob: Job?
        val nextRequestId: Long
        lateinit var nextJob: Job
        synchronized(lock) {
            previousJob = activeJob
            if (!cancelPrevious && previousJob?.isActive == true) {
                return false
            }
            nextRequestId = requestId + 1L
            requestId = nextRequestId
            val handle = HomeFeedRequestHandle(
                id = nextRequestId,
                kind = kind,
                isCurrentRequest = ::isCurrent
            )
            nextJob = scope.launch(start = CoroutineStart.LAZY) {
                if (cancelPrevious) {
                    previousJob?.cancelAndJoin()
                }
                if (handle.isCurrent()) {
                    handle.block()
                }
            }
            activeJob = nextJob
        }
        nextJob.invokeOnCompletion {
            synchronized(lock) {
                if (activeJob === nextJob) {
                    activeJob = null
                }
            }
        }
        nextJob.start()
        return true
    }

    private fun isCurrent(id: Long): Boolean {
        return synchronized(lock) { requestId == id }
    }
}
