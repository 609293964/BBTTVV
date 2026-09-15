package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.data.model.response.ReplyData
import com.bbttvv.app.data.model.response.ReplyItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerCommentControllerTest {
    @Test
    fun `switching sort restores cached list without another request`() {
        val requestedModes = mutableListOf<Int>()
        val controller = PlayerCommentController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            currentAid = { 100L },
            fallbackCommentCount = { 2 },
            isVideoLoading = { false },
            commentsLoader = { _, _, _, mode ->
                requestedModes += mode
                Result.success(
                    ReplyData(replies = listOf(ReplyItem(rpid = if (mode == 3) 30L else 20L)))
                )
            },
        )

        controller.ensureLoaded()
        controller.changeSort(PlayerCommentSortMode.Time)
        controller.changeSort(PlayerCommentSortMode.Hot)

        assertEquals(listOf(3, 2), requestedModes)
        assertEquals(PlayerCommentSortMode.Hot, controller.uiState.value.sortMode)
        assertEquals(listOf(30L), controller.uiState.value.items.map { it.rpid })
    }

    @Test
    fun `reset clears sort cache for the next media session`() {
        var requestCount = 0
        val controller = PlayerCommentController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            currentAid = { 100L },
            fallbackCommentCount = { 1 },
            isVideoLoading = { false },
            commentsLoader = { _, _, _, _ ->
                requestCount += 1
                Result.success(ReplyData(replies = listOf(ReplyItem(rpid = requestCount.toLong()))))
            },
        )

        controller.ensureLoaded()
        controller.reset()
        controller.ensureLoaded()

        assertEquals(2, requestCount)
        assertEquals(listOf(2L), controller.uiState.value.items.map { it.rpid })
    }
}
