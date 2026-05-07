package com.bbttvv.app.core.util

fun preferHttpsUrl(raw: String): String {
    val trimmed = raw.trim()
    return when {
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("http://") -> "https://${trimmed.removePrefix("http://")}"
        else -> trimmed
    }
}
