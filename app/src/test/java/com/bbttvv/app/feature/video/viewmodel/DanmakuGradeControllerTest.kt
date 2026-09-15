package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.feature.video.danmaku.DanmakuProto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuGradeControllerTest {
    @Test
    fun `grade submits once retries same score and updates participants only on success`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val requests = mutableListOf<DanmakuVoteSubmitRequest>()
        val responses = listOf(CompletableDeferred<Result<Unit>>(), CompletableDeferred<Result<Unit>>())
        val controller = DanmakuVoteController(scope, submitVote = { request ->
            requests += request
            responses[requests.lastIndex].await()
        }, nowElapsedMs = { 0L })
        try {
            controller.initialize(1, 2, listOf(grade()))
            controller.syncPlaybackPosition(1200)
            controller.selectOption(3)
            controller.selectOption(4)
            assertEquals(1, requests.size)
            assertEquals(8, requests.single().option.gradeScore)
            assertEquals(1L, requests.single().aid)
            assertEquals(2L, requests.single().cid)
            assertEquals(1200L, requests.single().progressMs)
            responses[0].complete(Result.failure(IllegalStateException("网络失败")))
            val error = controller.uiState.value as DanmakuVoteUiState.RetryableError
            assertEquals(3, error.selectedIndex)
            assertEquals(49, error.prompt.participantCount)
            controller.retry()
            assertEquals(requests[0], requests[1])
            responses[1].complete(Result.success(Unit))
            val submitted = controller.uiState.value as DanmakuVoteUiState.Submitted
            assertEquals(4, submitted.prompt.selectedOptionId)
            assertEquals(50, submitted.prompt.participantCount)
            controller.selectOption(0)
            assertEquals(2, requests.size)
        } finally {
            controller.cancel()
            scope.cancel()
        }
    }

    @Test
    fun `dismiss or media change ignores even non cancellable late responses`() {
        listOf(false, true).forEach { changeMedia ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val response = CompletableDeferred<Result<Unit>>()
            val controller = DanmakuVoteController(scope, submitVote = {
                withContext(NonCancellable) { response.await() }
            }, nowElapsedMs = { 0L })
            try {
                controller.initialize(1, 2, listOf(grade()))
                controller.syncPlaybackPosition(1000)
                controller.selectOption(2)
                if (changeMedia) {
                    controller.initialize(3, 4, listOf(grade()))
                    controller.syncPlaybackPosition(1000)
                } else controller.hide()
                val expected = controller.uiState.value
                response.complete(Result.success(Unit))
                assertEquals(expected, controller.uiState.value)
            } finally {
                response.complete(Result.success(Unit))
                controller.cancel()
                scope.cancel()
            }
        }
    }

    @Test
    fun `vote with same numeric id remains eligible after grade is dismissed`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = DanmakuVoteController(scope, nowElapsedMs = { 0L })
        try {
            controller.initialize(1, 2, listOf(grade(), DanmakuProto.CommandDm(
                id = 7, command = "#VOTE#", progress = 2000,
                extra = """{"vote_id":99,"question":"投票标题","options":[{"idx":1,"desc":"选项"}]}""",
            )))
            controller.syncPlaybackPosition(1000)
            controller.hide()
            controller.syncPlaybackPosition(2000)
            assertEquals(DanmakuVoteKind.Vote, (controller.uiState.value as DanmakuVoteUiState.Showing).prompt.kind)
        } finally {
            controller.cancel()
            scope.cancel()
        }
    }

    @Test
    fun `previously rated grade cannot submit again`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var submitted = false
        val controller = DanmakuVoteController(scope, submitVote = {
            submitted = true
            Result.success(Unit)
        }, nowElapsedMs = { 0L })
        try {
            controller.initialize(1, 2, listOf(grade(midScore = 6)))
            controller.syncPlaybackPosition(1000)
            controller.selectOption(4)
            assertTrue(!submitted)
            assertEquals(3, (controller.uiState.value as DanmakuVoteUiState.Showing).prompt.selectedOptionId)
        } finally {
            controller.cancel()
            scope.cancel()
        }
    }

    private fun grade(midScore: Int = 0) = DanmakuProto.CommandDm(
        command = "#GRADE#", progress = 1000,
        extra = """{"grade_id":99,"msg":"作者自定义问题","count":49,"mid_score":$midScore,"duration":5000}""",
    )
}
