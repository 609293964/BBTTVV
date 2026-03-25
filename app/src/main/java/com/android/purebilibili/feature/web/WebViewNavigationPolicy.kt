package com.android.purebilibili.feature.web

import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser

internal sealed interface WebViewNavigationAction {
    data object AllowWebLoad : WebViewNavigationAction
    data object Block : WebViewNavigationAction
    data class LoadInWebView(val url: String) : WebViewNavigationAction
    data class DispatchTarget(val target: BilibiliNavigationTarget) : WebViewNavigationAction
}

internal fun resolveWebViewNavigationAction(
    urlString: String,
    hasUserGesture: Boolean
): WebViewNavigationAction {
    val normalizedUrl = urlString.trim().lowercase()
    val isCustomScheme = normalizedUrl.startsWith("bilibili://") || normalizedUrl.startsWith("bili://")

    if (isCustomScheme) {
        if (!hasUserGesture) {
            return WebViewNavigationAction.Block
        }

        val convertedUrl = convertDeepLinkToWebUrl(urlString)
        return if (convertedUrl != null) {
            WebViewNavigationAction.LoadInWebView(convertedUrl)
        } else {
            WebViewNavigationAction.Block
        }
    }

    val target = BilibiliNavigationTargetParser.parse(urlString)
    return if (target != null) {
        WebViewNavigationAction.DispatchTarget(target)
    } else {
        WebViewNavigationAction.AllowWebLoad
    }
}

internal fun convertDeepLinkToWebUrl(rawUrl: String): String? {
    val normalized = rawUrl.trim()
    val withoutScheme = when {
        normalized.startsWith("bilibili://", ignoreCase = true) -> normalized.removePrefix("bilibili://")
        normalized.startsWith("bili://", ignoreCase = true) -> normalized.removePrefix("bili://")
        else -> return null
    }
    val pathWithoutQuery = withoutScheme.substringBefore("?").trim('/')
    if (pathWithoutQuery.isBlank()) return null

    val pathSegments = pathWithoutQuery.split("/").filter { it.isNotBlank() }
    if (pathSegments.isEmpty()) return null
    val host = pathSegments.first()

    android.util.Log.d("WebViewScreen", "🔗 Converting deep link: host=$host, segments=$pathSegments")

    return when {
        host == "video" -> {
            val videoId = pathSegments.getOrNull(1)
            if (videoId != null) {
                if (videoId.startsWith("BV")) {
                    "https://m.bilibili.com/video/$videoId"
                } else {
                    val numericId = videoId.toLongOrNull()
                    if (numericId != null && numericId > 10_000_000_000L) {
                        android.util.Log.w("WebViewScreen", "⚠️ Blocking invalid video ID: $numericId")
                        null
                    } else {
                        "https://m.bilibili.com/video/av$videoId"
                    }
                }
            } else {
                null
            }
        }

        host == "space" -> {
            val mid = pathSegments.getOrNull(1)
            if (mid != null) "https://space.bilibili.com/$mid" else null
        }

        host == "live" -> {
            val roomId = pathSegments.getOrNull(1)
            if (roomId != null) "https://live.bilibili.com/$roomId" else null
        }

        host == "bangumi" -> {
            if (pathSegments.getOrNull(1) == "season") {
                val ssid = pathSegments.getOrNull(2)
                if (ssid != null) "https://m.bilibili.com/bangumi/play/ss$ssid" else null
            } else null
        }

        else -> null
    }
}
