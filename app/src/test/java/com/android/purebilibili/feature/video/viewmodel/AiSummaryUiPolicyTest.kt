package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.data.repository.AiSummaryFetchDiagnosis
import com.android.purebilibili.data.repository.AiSummaryFetchStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AiSummaryUiPolicyTest {

    @Test
    fun initialLoadingStateShowsEntryPrompt() {
        val prompt = initialAiSummaryPromptState()

        assertEquals("AI 总结加载中", prompt.title)
        assertEquals(AiSummaryPromptTone.INFO, prompt.tone)
    }

    @Test
    fun availableSummaryDoesNotShowPrompt() {
        val prompt = resolveAiSummaryPromptState(
            AiSummaryFetchDiagnosis(
                status = AiSummaryFetchStatus.AVAILABLE,
                reason = "available"
            )
        )

        assertNull(prompt)
    }

    @Test
    fun queuedSummaryShowsGeneratingPrompt() {
        val prompt = resolveAiSummaryPromptState(
            AiSummaryFetchDiagnosis(
                status = AiSummaryFetchStatus.QUEUED,
                reason = "queued",
                stid = "0",
                shouldRetryLater = true
            )
        )

        assertEquals("AI 总结生成中", prompt?.title)
        assertEquals(AiSummaryPromptTone.INFO, prompt?.tone)
    }

    @Test
    fun queuedSummaryAfterRetryShowsSettledPrompt() {
        val prompt = queuedAiSummaryPendingPromptState()

        assertEquals("AI 总结暂未生成完成", prompt.title)
        assertEquals(AiSummaryPromptTone.MUTED, prompt.tone)
        assertEquals("重新获取", prompt.actionLabel)
    }

    @Test
    fun unauthorizedSummarySuggestsLogin() {
        val prompt = resolveAiSummaryPromptState(
            AiSummaryFetchDiagnosis(
                status = AiSummaryFetchStatus.UNAUTHORIZED,
                reason = "unauthorized"
            )
        )

        assertEquals("登录后可查看 AI 总结", prompt?.title)
        assertEquals(AiSummaryPromptTone.MUTED, prompt?.tone)
    }

    @Test
    fun retryableFailureShowsRetryMessage() {
        val prompt = resolveAiSummaryPromptState(
            AiSummaryFetchDiagnosis(
                status = AiSummaryFetchStatus.RETRYABLE_FAILURE,
                reason = "http_412",
                shouldRetryRequest = true
            )
        )

        assertEquals("AI 总结加载失败", prompt?.title)
        assertEquals(AiSummaryPromptTone.WARNING, prompt?.tone)
        assertEquals("重试", prompt?.actionLabel)
    }
}
