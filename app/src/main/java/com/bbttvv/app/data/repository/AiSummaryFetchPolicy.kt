package com.bbttvv.app.data.repository

import com.bbttvv.app.data.model.response.AiModelResult
import com.bbttvv.app.data.model.response.AiSummaryResponse
import retrofit2.HttpException

internal enum class AiSummaryFetchStatus {
    AVAILABLE,
    QUEUED,
    NO_SPEECH,
    NO_SUMMARY,
    UNSUPPORTED,
    UNAUTHORIZED,
    API_ERROR,
    RETRYABLE_FAILURE,
    FAILURE
}

internal data class AiSummaryFetchDiagnosis(
    val status: AiSummaryFetchStatus,
    val reason: String,
    val rootCode: Int? = null,
    val dataCode: Int? = null,
    val stid: String? = null,
    val shouldRetryRequest: Boolean = false,
    val shouldRetryLater: Boolean = false
)

internal fun diagnoseAiSummaryResponse(response: AiSummaryResponse): AiSummaryFetchDiagnosis {
    if (response.code == -101) {
        return AiSummaryFetchDiagnosis(
            status = AiSummaryFetchStatus.UNAUTHORIZED,
            reason = "unauthorized",
            rootCode = response.code
        )
    }
    if (response.code != 0) {
        return AiSummaryFetchDiagnosis(
            status = AiSummaryFetchStatus.API_ERROR,
            reason = "api_error",
            rootCode = response.code
        )
    }

    val data = response.data
        ?: return AiSummaryFetchDiagnosis(
            status = AiSummaryFetchStatus.NO_SUMMARY,
            reason = "missing_data",
            rootCode = response.code
        )

    val stid = data.stid.trim()
    return when {
        data.code == 0 && hasRenderableAiSummaryContent(data.modelResult) -> AiSummaryFetchDiagnosis(
            status = AiSummaryFetchStatus.AVAILABLE,
            reason = "available",
            rootCode = response.code,
            dataCode = data.code,
            stid = stid
        )

        data.code == 0 -> AiSummaryFetchDiagnosis(
            status = AiSummaryFetchStatus.NO_SUMMARY,
            reason = "empty_content",
            rootCode = response.code,
            dataCode = data.code,
            stid = stid
        )

        data.code == 1 && stid == "0" -> AiSummaryFetchDiagnosis(
            status = AiSummaryFetchStatus.QUEUED,
            reason = "queued",
            rootCode = response.code,
            dataCode = data.code,
            stid = stid,
            shouldRetryLater = true
        )

        data.code == 1 && stid.isBlank() -> AiSummaryFetchDiagnosis(
            status = AiSummaryFetchStatus.NO_SPEECH,
            reason = "no_speech",
            rootCode = response.code,
            dataCode = data.code,
            stid = stid
        )

        data.code == -1 -> AiSummaryFetchDiagnosis(
            status = AiSummaryFetchStatus.UNSUPPORTED,
            reason = "unsupported",
            rootCode = response.code,
            dataCode = data.code,
            stid = stid
        )

        data.code == 1 -> AiSummaryFetchDiagnosis(
            status = AiSummaryFetchStatus.NO_SUMMARY,
            reason = "no_summary",
            rootCode = response.code,
            dataCode = data.code,
            stid = stid
        )

        else -> AiSummaryFetchDiagnosis(
            status = AiSummaryFetchStatus.NO_SUMMARY,
            reason = "unexpected_payload",
            rootCode = response.code,
            dataCode = data.code,
            stid = stid
        )
    }
}

internal fun diagnoseAiSummaryFailure(throwable: Throwable): AiSummaryFetchDiagnosis {
    val httpCode = (throwable as? HttpException)?.code()
    val message = throwable.message.orEmpty()
    val isRetryable = httpCode == 412 || message.contains("412")

    return AiSummaryFetchDiagnosis(
        status = if (isRetryable) AiSummaryFetchStatus.RETRYABLE_FAILURE else AiSummaryFetchStatus.FAILURE,
        reason = when {
            httpCode == 412 -> "http_412"
            httpCode != null -> "http_$httpCode"
            message.isNotBlank() -> message
            else -> throwable::class.simpleName ?: "unknown_error"
        },
        rootCode = httpCode,
        shouldRetryRequest = isRetryable
    )
}

private fun hasRenderableAiSummaryContent(modelResult: AiModelResult?): Boolean {
    val summary = modelResult?.summary.orEmpty()
    val outline = modelResult?.outline.orEmpty()
    return summary.isNotBlank() || outline.isNotEmpty()
}

