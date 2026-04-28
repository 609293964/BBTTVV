package com.bbttvv.app.data.repository

internal enum class PlaybackErrorKind {
    FATAL,
    ACCESS_TOKEN_EXPIRED,
    APP_RISK_CONTROL,
    WBI_ANTI_RISK,
    TRANSIENT,
    EMPTY_PAYLOAD,
    OTHER
}

internal data class PlaybackErrorDiagnosis(
    val userMessage: String,
    val code: Int? = null,
    val kind: PlaybackErrorKind = PlaybackErrorKind.OTHER,
    val shouldInvalidateWbiKeys: Boolean = false,
    val shouldApplyAppCooldown: Boolean = false,
    val shouldRefreshAccessToken: Boolean = false,
    val isFatal: Boolean = false
)

internal object PlaybackErrorClassifier {
    fun classifyWbiApi(code: Int, message: String?): PlaybackErrorDiagnosis {
        val userMessage = classifyPlayUrlUserMessage(code = code, message = message)
        return when (code) {
            -404,
            -403,
            -10403,
            -62002 -> PlaybackErrorDiagnosis(
                userMessage = userMessage,
                code = code,
                kind = PlaybackErrorKind.FATAL,
                isFatal = true
            )

            -352 -> PlaybackErrorDiagnosis(
                userMessage = userMessage,
                code = code,
                kind = PlaybackErrorKind.WBI_ANTI_RISK
            )

            else -> PlaybackErrorDiagnosis(
                userMessage = userMessage,
                code = code
            )
        }
    }

    fun classifyWbiThrowable(error: Throwable): PlaybackErrorDiagnosis {
        val rawMessage = error.message.orEmpty()
        return if (rawMessage.contains("412")) {
            PlaybackErrorDiagnosis(
                userMessage = "WBI 请求命中风控",
                kind = PlaybackErrorKind.WBI_ANTI_RISK,
                shouldInvalidateWbiKeys = true
            )
        } else {
            PlaybackErrorDiagnosis(
                userMessage = rawMessage.ifBlank { "WBI 请求失败" },
                kind = PlaybackErrorKind.TRANSIENT
            )
        }
    }

    fun classifyAppApi(code: Int, message: String?): PlaybackErrorDiagnosis {
        val userMessage = classifyPlayUrlUserMessage(code = code, message = message)
        return when (code) {
            -101 -> PlaybackErrorDiagnosis(
                userMessage = userMessage,
                code = code,
                kind = PlaybackErrorKind.ACCESS_TOKEN_EXPIRED,
                shouldRefreshAccessToken = true
            )

            -351 -> PlaybackErrorDiagnosis(
                userMessage = userMessage,
                code = code,
                kind = PlaybackErrorKind.APP_RISK_CONTROL,
                shouldApplyAppCooldown = true
            )

            else -> PlaybackErrorDiagnosis(
                userMessage = userMessage,
                code = code
            )
        }
    }

    fun emptyPayload(label: String): PlaybackErrorDiagnosis {
        return PlaybackErrorDiagnosis(
            userMessage = "$label 返回空流",
            kind = PlaybackErrorKind.EMPTY_PAYLOAD
        )
    }

    private fun classifyPlayUrlUserMessage(code: Int, message: String?): String {
        return when (code) {
            -404 -> "视频不存在或已被删除"
            -403 -> "视频暂不可用"
            -10403 -> when {
                message?.contains("地区") == true -> "该视频在当前地区不可用"
                message?.contains("会员") == true || message?.contains("vip", ignoreCase = true) == true -> "需要大会员才能观看"
                else -> "视频需要特殊权限才能观看"
            }

            -62002 -> "视频已设为私密"
            -62004 -> "视频正在审核中"
            -62012 -> "视频已下架"
            -400 -> "请求参数错误"
            -101 -> "未登录，请先登录"
            -351 -> "请求被风控拦截"
            -352 -> "请求频率过高，请稍后再试"
            else -> "获取播放地址失败 (错误码: $code)"
        }
    }
}
