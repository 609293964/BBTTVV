package com.bbttvv.app.core.util

private val SECRET_ASSIGNMENT = Regex(
    "(?i)(SESSDATA|bili_jct|DedeUserID(?:__ckMd5)?|sid|buvid3|buvid4|b_nut|_uuid|access_token|refresh_token|access_key|appkey|sign|csrf|csrf_token|token|auth_code|device_id|android_id|imei)=([^&;\\s]+)"
)
private val SECRET_JSON = Regex(
    "(?i)(\"(?:SESSDATA|bili_jct|access_token|refresh_token|access_key|csrf|csrf_token|token|auth_code|device_id)\"\\s*:\\s*\")[^\"]*(\")"
)
private val AUTHORIZATION_HEADER = Regex("(?i)(Authorization\\s*:\\s*)(?:Bearer\\s+)?[^\\s,]+")
private val BEARER_TOKEN = Regex("(?i)Bearer\\s+[^\\s,]+")
private val MID_VALUE = Regex("(?i)\\b(mid|uid|vmid)[=:]\\s*\\d{4,}")
private val MID_JSON = Regex("(?i)(\"(?:mid|uid)\"\\s*:\\s*)\\d+")
private val PHONE = Regex("\\b1[3-9]\\d{9}\\b")
private val EMAIL = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
private val IPV4 = Regex("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b")
private val IPV6 = Regex("\\b[0-9a-fA-F:]{15,}\\b")
private val MAC = Regex("([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}")
private val BVID = Regex("BV[0-9A-Za-z]{10}")
private val AV_ID = Regex("\\bav\\d{4,}\\b", RegexOption.IGNORE_CASE)
private val AID_JSON = Regex("(?i)(\"aid\"\\s*:\\s*)\\d+")
private val CONTENT_ID = Regex("(?i)\\b(cid|room_id|roomId|season_id|ep_id)[=:]\\s*\\d+")
private val CONTENT_ID_JSON = Regex("(?i)(\"cid\"\\s*:\\s*)\\d+")
private val SEARCH_KEYWORD = Regex("(?i)(keyword=)[^&\\s]+")
private val SEARCH_KEYWORD_JSON = Regex("(?i)(\"keyword\"\\s*:\\s*\")[^\"]*(\")")
private val SEARCH_LABEL = Regex("(?i)(Search:\\s*)[^\\n]+")
private val FACE_JSON = Regex("(?i)(\"face\"\\s*:\\s*\")[^\"]*(\")")
private val TEL_JSON = Regex("(?i)(\"tel\"\\s*:\\s*\")[^\"]*(\")")
private val NAME_JSON = Regex("(?i)(\"name\"\\s*:\\s*\")([^\"]{2,})(\")")
private val VIDEO_TITLE_ASSIGNMENT = Regex("(?i)(video_title=)([^&\\s]{3,})")
private val TITLE_JSON = Regex("(?i)(\"title\"\\s*:\\s*\")([^\"]{3,})(\")")

/**
 * Redacts reusable credentials and common personal/content identifiers before diagnostic text
 * reaches Logcat, in-memory buffers, persisted files, or user-triggered exports.
 *
 * Keep this function Android-free so JVM tests can verify the exact privacy boundary.
 */
internal fun sanitizeDiagnosticText(message: String): String {
    var sanitized = message

    sanitized = SECRET_ASSIGNMENT.replace(sanitized) { match ->
        "${match.groupValues[1]}=***"
    }
    sanitized = SECRET_JSON.replace(sanitized) { match ->
        "${match.groupValues[1]}***${match.groupValues[2]}"
    }
    sanitized = AUTHORIZATION_HEADER.replace(sanitized) { match ->
        "${match.groupValues[1]}***"
    }
    sanitized = BEARER_TOKEN.replace(sanitized, "Bearer ***")

    sanitized = MID_VALUE.replace(sanitized) { match ->
        "${match.groupValues[1]}=***"
    }
    sanitized = MID_JSON.replace(sanitized) { match ->
        "${match.groupValues[1]}***"
    }
    sanitized = PHONE.replace(sanitized, "1**********")
    sanitized = EMAIL.replace(sanitized) { match ->
        val email = match.value
        val at = email.indexOf('@')
        if (at > 2) email.take(2) + "***" + email.substring(at)
        else "***" + email.substring(at)
    }

    sanitized = IPV4.replace(sanitized) { match ->
        val parts = match.value.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            "${parts.first()}.***.***.*"
        } else {
            match.value
        }
    }
    sanitized = IPV6.replace(sanitized, "***:***:***")
    sanitized = MAC.replace(sanitized, "**:**:**:**:**:**")

    sanitized = sanitized.replace(Regex("/data/user/\\d+/[^/]+/"), "/data/user/0/***/")
    sanitized = sanitized.replace(Regex("/storage/emulated/\\d+/"), "/storage/emulated/0/")
    sanitized = sanitized.replace(Regex("/home/[^/]+/"), "/home/***/")
    sanitized = sanitized.replace(Regex("/Users/[^/]+/"), "/Users/***/")

    sanitized = FACE_JSON.replace(sanitized) { match ->
        "${match.groupValues[1]}***${match.groupValues[2]}"
    }
    sanitized = TEL_JSON.replace(sanitized) { match ->
        "${match.groupValues[1]}***${match.groupValues[2]}"
    }
    sanitized = NAME_JSON.replace(sanitized) { match ->
        val name = match.groupValues[2]
        "${match.groupValues[1]}${name.take(1)}***${match.groupValues[3]}"
    }

    sanitized = BVID.replace(sanitized, "BV***")
    sanitized = AV_ID.replace(sanitized, "av***")
    sanitized = AID_JSON.replace(sanitized) { match ->
        "${match.groupValues[1]}***"
    }
    sanitized = CONTENT_ID.replace(sanitized) { match ->
        "${match.groupValues[1]}=***"
    }
    sanitized = CONTENT_ID_JSON.replace(sanitized) { match ->
        "${match.groupValues[1]}***"
    }

    sanitized = SEARCH_KEYWORD.replace(sanitized) { match ->
        "${match.groupValues[1]}***"
    }
    sanitized = SEARCH_KEYWORD_JSON.replace(sanitized) { match ->
        "${match.groupValues[1]}***${match.groupValues[2]}"
    }
    sanitized = SEARCH_LABEL.replace(sanitized) { match ->
        "${match.groupValues[1]}***"
    }

    sanitized = VIDEO_TITLE_ASSIGNMENT.replace(sanitized) { match ->
        val title = match.groupValues[2]
        "${match.groupValues[1]}${title.take(2)}***"
    }
    sanitized = TITLE_JSON.replace(sanitized) { match ->
        val title = match.groupValues[2]
        "${match.groupValues[1]}${title.take(2)}***${match.groupValues[3]}"
    }

    return sanitized
}

internal fun sanitizedThrowableText(throwable: Throwable): String {
    return sanitizeDiagnosticText(throwable.stackTraceToString())
}
