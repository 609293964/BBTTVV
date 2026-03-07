package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.data.repository.AiSummaryFetchDiagnosis
import com.android.purebilibili.data.repository.AiSummaryFetchStatus

enum class AiSummaryPromptTone {
    INFO,
    MUTED,
    WARNING
}

data class AiSummaryPromptState(
    val title: String,
    val message: String,
    val tone: AiSummaryPromptTone,
    val actionLabel: String? = null
)

fun initialAiSummaryPromptState(): AiSummaryPromptState {
    return AiSummaryPromptState(
        title = "AI 总结加载中",
        message = "正在获取这条视频的 AI 总结，请稍等一下。",
        tone = AiSummaryPromptTone.INFO
    )
}

fun queuedAiSummaryPendingPromptState(): AiSummaryPromptState {
    return AiSummaryPromptState(
        title = "AI 总结暂未生成完成",
        message = "这条视频还在后台排队，稍后下拉刷新或重新进入页面再试。",
        tone = AiSummaryPromptTone.MUTED,
        actionLabel = "重新获取"
    )
}

internal fun resolveAiSummaryPromptState(
    diagnosis: AiSummaryFetchDiagnosis
): AiSummaryPromptState? {
    return when (diagnosis.status) {
        AiSummaryFetchStatus.AVAILABLE -> null
        AiSummaryFetchStatus.QUEUED -> AiSummaryPromptState(
            title = "AI 总结生成中",
            message = "这条视频还在排队生成总结，稍后会自动再试一次。",
            tone = AiSummaryPromptTone.INFO
        )
        AiSummaryFetchStatus.UNAUTHORIZED -> AiSummaryPromptState(
            title = "登录后可查看 AI 总结",
            message = "当前桌面接口需要登录态，登录后再打开视频详情试试。",
            tone = AiSummaryPromptTone.MUTED
        )
        AiSummaryFetchStatus.NO_SPEECH -> AiSummaryPromptState(
            title = "暂未识别到可总结语音",
            message = "这条视频可能语音较少，当前没有生成 AI 总结。",
            tone = AiSummaryPromptTone.MUTED
        )
        AiSummaryFetchStatus.NO_SUMMARY -> AiSummaryPromptState(
            title = "当前没有 AI 总结",
            message = "这条视频暂时没有可展示的 AI 总结内容。",
            tone = AiSummaryPromptTone.MUTED
        )
        AiSummaryFetchStatus.UNSUPPORTED -> AiSummaryPromptState(
            title = "当前视频暂不支持 AI 总结",
            message = "桌面端接口返回该视频暂不支持 AI 总结。",
            tone = AiSummaryPromptTone.MUTED
        )
        AiSummaryFetchStatus.RETRYABLE_FAILURE -> AiSummaryPromptState(
            title = "AI 总结加载失败",
            message = "这次请求被风控或网络打断了，稍后重进页面通常可以恢复。",
            tone = AiSummaryPromptTone.WARNING,
            actionLabel = "重试"
        )
        AiSummaryFetchStatus.API_ERROR,
        AiSummaryFetchStatus.FAILURE -> AiSummaryPromptState(
            title = "AI 总结暂时不可用",
            message = "接口这次没有成功返回结果，可以稍后再试或导出日志反馈。",
            tone = AiSummaryPromptTone.WARNING,
            actionLabel = "重试"
        )
    }
}
