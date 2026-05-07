package com.bbttvv.app.data.repository

import retrofit2.HttpException

internal const val DYNAMIC_FETCH_MAX_ATTEMPTS = 3

internal fun isDynamicRiskControlApiError(code: Int, message: String): Boolean {
    if (code == -352 || code == 22015) return true
    val text = message.lowercase()
    return text.contains("风控") || text.contains("risk")
}

internal fun isDynamicRateLimitApiError(code: Int, message: String): Boolean {
    if (code in setOf(-412, -509, 34004)) return true
    val text = message.lowercase()
    return text.contains("412") ||
        text.contains("429") ||
        text.contains("precondition")
}

internal fun isRetryableDynamicApiError(code: Int, message: String): Boolean {
    return false
}

internal fun isRetryableDynamicException(error: Throwable): Boolean {
    return when (error) {
        is HttpException -> error.code() in setOf(500, 502, 503, 504)
        else -> {
            val text = error.message.orEmpty().lowercase()
            !isDynamicRateLimitApiError(code = -1, message = text) &&
                (text.contains("timeout") || text.contains("reset"))
        }
    }
}

internal fun resolveDynamicRetryDelayMs(attempt: Int): Long {
    return when (attempt) {
        1 -> 250L
        2 -> 700L
        else -> 1200L
    }
}

internal fun resolveDynamicFriendlyErrorMessage(code: Int, message: String): String {
    return when {
        code == -101 -> "未登录，请先登录"
        isDynamicRiskControlApiError(code = code, message = message) ->
            "触发风控，请稍后重试"
        code == 429 || isDynamicRateLimitApiError(code = code, message = message) ->
            "请求过于频繁，请稍后重试"
        message.isBlank() -> "加载失败，请稍后重试"
        else -> message
    }
}

