package com.bbttvv.app.core.util

private val SECRET_ASSIGNMENT = Regex(
    "(?i)(SESSDATA|bili_jct|DedeUserID(?:__ckMd5)?|sid|buvid3|buvid4|access_token|refresh_token|access_key|appkey|sign|csrf|csrf_token|token|device_id|android_id|imei)=([^&;\\s]+)"
)
private val SECRET_JSON = Regex(
    "(?i)(\\\"(?:SESSDATA|bili_jct|access_token|refresh_token|access_key|csrf|csrf_token|token)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")"
)
private val AUTHORIZATION_HEADER = Regex("(?i)(Authorization\\s*:\\s*)(?:Bearer\\s+)?[^\\s,]+")
private val BEARER_TOKEN = Regex("(?i)Bearer\\s+[^\\s,]+")
private val MID_VALUE = Regex("(?i)\\b(mid|uid|vmid)[=:]\\s*\\d{4,}")
private val PHONE = Regex("\\b1[3-9]\\d{9}\\b")
private val EMAIL = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
private val IPV4 = Regex("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b")
private val MAC = Regex("([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}")
private val BVID = Regex("BV[0-9A-Za-z]{10}")
private val CONTENT_ID = Regex("(?i)\\b(cid|room_id|roomId|season_id|ep_id)[=:]\\s*\\d+")
private val SEARCH_KEYWORD = Regex("(?i)(keyword=)[^&\\s]+")

/**
 * Redacts reusable credentials and common personal/content identifiers before diagnostic text
 * reaches Logcat, in-memory buffers, persisted files, or user-triggered exports.
 */
internal fun sanitizeDiagnosticText(message: String): String {
    var sanitized = message
    sanitized = SECRET_ASSIGNMENT.replace(sanitized) { match -> "${match.groupValues[1]}=***" }
    sanitized = SECRET_JSON.replace(sanitized) { match -> "${match.groupValues[1]}***${match.groupValues[2]}" }
    sanitized = AUTHORIZATION_HEADER.replace(sanitized) { match -> "${match.groupValues[1]}***" }
    sanitized = BEARER_TOKEN.replace(sanitized, "Bearer ***")
    sanitized = MID_VALUE.replace(sanitized) { match -> "${match.groupValues[1]}=***" }
    sanitized = PHONE.replace(sanitized, "1**********")
    sanitized = EMAIL.replace(sanitized) { match ->
        val email = match.value
        val at = email.indexOf('@')
        if (at > 2) email.take(2) + "***" + email.substring(at) else "***" + email.substring(at)
    }
    sanitized = IPV4.replace(sanitized) { match ->
        val parts = match.value.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            "${parts.first()}.***.***.*"
        } else match.value
    }
    sanitized = MAC.replace(sanitized, "**:**:**:**:**:**")
    sanitized = sanitized.replace(Regex("/data/user/\\d+/[^/]+/"), "/data/user/0/***/")
    sanitized = sanitized.replace(Regex("/home/[^/]+/"), "/home/***/")
    sanitized = sanitized.replace(Regex("/Users/[^/]+/"), "/Users/***/")
    sanitized = BVID.replace(sanitized, "BV***")
    sanitized = CONTENT_ID.replace(sanitized) { match -> "${match.groupValues[1]}=***" }
    sanitized = SEARCH_KEYWORD.replace(sanitized, "$1***")
    return sanitized
}

internal fun sanitizedThrowableText(throwable: Throwable): String {
    return sanitizeDiagnosticText(throwable.stackTraceToString())
}
