package com.android.purebilibili.feature.search

import com.android.purebilibili.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

private val opusRedirectPattern = Regex("""(?:^|/)opus/(\d+)(?:[/?#]|$)""")

internal sealed interface ArticleNavigationTarget {
    data class NativeArticle(val articleId: Long) : ArticleNavigationTarget
    data class NativeDynamic(val dynamicId: String) : ArticleNavigationTarget
}

internal fun buildArticleWebUrl(articleId: Long): String? {
    if (articleId <= 0L) return null
    return "https://www.bilibili.com/read/cv$articleId"
}

internal fun resolveArticleWebUrlFromRedirect(
    articleId: Long,
    redirectUrl: String?
): String? {
    val fallbackUrl = buildArticleWebUrl(articleId) ?: return null
    val opusId = redirectUrl
        ?.trim()
        ?.let { opusRedirectPattern.find(it)?.groupValues?.getOrNull(1) }
        ?.takeIf { it.isNotBlank() }
    return if (opusId != null) {
        "https://www.bilibili.com/opus/$opusId"
    } else {
        fallbackUrl
    }
}

internal fun resolveArticleNavigationTargetFromRedirect(
    articleId: Long,
    redirectUrl: String?
): ArticleNavigationTarget? {
    buildArticleWebUrl(articleId) ?: return null
    val opusId = redirectUrl
        ?.trim()
        ?.let { opusRedirectPattern.find(it)?.groupValues?.getOrNull(1) }
        ?.takeIf { it.isNotBlank() }
    return if (opusId != null) {
        ArticleNavigationTarget.NativeDynamic(dynamicId = opusId)
    } else {
        ArticleNavigationTarget.NativeArticle(articleId = articleId)
    }
}

internal suspend fun resolveArticleWebUrl(articleId: Long): String? {
    val fallbackUrl = buildArticleWebUrl(articleId) ?: return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val client = NetworkModule.okHttpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
            val request = Request.Builder()
                .url(fallbackUrl)
                .method("HEAD", null)
                .build()
            client.newCall(request).execute().use { response ->
                resolveArticleWebUrlFromRedirect(
                    articleId = articleId,
                    redirectUrl = response.header("Location")
                )
            }
        }.getOrDefault(fallbackUrl)
    }
}

internal suspend fun resolveArticleNavigationTarget(articleId: Long): ArticleNavigationTarget? {
    val fallbackUrl = buildArticleWebUrl(articleId) ?: return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val client = NetworkModule.okHttpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
            val request = Request.Builder()
                .url(fallbackUrl)
                .method("HEAD", null)
                .build()
            client.newCall(request).execute().use { response ->
                resolveArticleNavigationTargetFromRedirect(
                    articleId = articleId,
                    redirectUrl = response.header("Location")
                ) ?: ArticleNavigationTarget.NativeArticle(articleId = articleId)
            }
        }.getOrDefault(ArticleNavigationTarget.NativeArticle(articleId = articleId))
    }
}
