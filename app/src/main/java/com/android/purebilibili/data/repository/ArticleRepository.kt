package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.ArticleViewData
import com.android.purebilibili.feature.article.ArticleContentBlock
import com.android.purebilibili.feature.article.parseArticleContentBlocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ArticleDetailUiModel(
    val articleId: Long,
    val title: String,
    val summary: String,
    val authorName: String,
    val authorMid: Long,
    val authorFace: String,
    val publishTime: String,
    val bannerUrl: String?,
    val blocks: List<ArticleContentBlock>
)

object ArticleRepository {
    private val articleApi = NetworkModule.articleApi
    private val navApi = NetworkModule.api

    suspend fun getArticleDetail(articleId: Long): Result<ArticleDetailUiModel> = withContext(Dispatchers.IO) {
        if (articleId <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Invalid article id: $articleId"))
        }

        runCatching {
            val response = articleApi.getArticleView(
                signWithWbi(
                    mapOf(
                        "id" to articleId.toString(),
                        "gaia_source" to "main_web",
                        "web_location" to "333.976"
                    )
                )
            )

            if (response.code != 0 || response.data == null) {
                throw IllegalStateException(response.message.ifBlank { "Article detail unavailable" })
            }

            response.data.toUiModel()
        }
    }

    private fun ArticleViewData.toUiModel(): ArticleDetailUiModel {
        val parsedBlocks = parseArticleContentBlocks(
            structuredParagraphs = opus?.paragraphs().orEmpty(),
            htmlContent = content
        )

        val resolvedTitle = title
            .ifBlank { opus?.title.orEmpty() }
            .ifBlank { summary }
            .ifBlank { "专栏详情" }
        val resolvedSummary = summary.ifBlank {
            parsedBlocks.filterIsInstance<ArticleContentBlock.Paragraph>()
                .firstOrNull()
                ?.text
                .orEmpty()
        }
        val resolvedBanner = listOfNotNull(
            bannerUrl.normalizeImageUrl(),
            originImageUrls.firstOrNull()?.normalizeImageUrl(),
            imageUrls.firstOrNull()?.normalizeImageUrl(),
            parsedBlocks.filterIsInstance<ArticleContentBlock.Image>().firstOrNull()?.url
        ).firstOrNull()

        return ArticleDetailUiModel(
            articleId = id,
            title = resolvedTitle,
            summary = resolvedSummary,
            authorName = author?.name.orEmpty(),
            authorMid = author?.mid ?: 0L,
            authorFace = author?.face.normalizeImageUrl().orEmpty(),
            publishTime = FormatUtils.formatPrecisePublishTime(publishTime),
            bannerUrl = resolvedBanner,
            blocks = parsedBlocks
        )
    }

    private suspend fun signWithWbi(params: Map<String, String>): Map<String, String> {
        return try {
            val navResp = navApi.getNavInfo()
            val wbiImg = navResp.data?.wbi_img
            val imgKey = wbiImg?.img_url?.substringAfterLast("/")?.substringBefore(".") ?: ""
            val subKey = wbiImg?.sub_url?.substringAfterLast("/")?.substringBefore(".") ?: ""
            if (imgKey.isNotEmpty() && subKey.isNotEmpty()) {
                WbiUtils.sign(params, imgKey, subKey)
            } else {
                params
            }
        } catch (_: Exception) {
            params
        }
    }

    private fun String?.normalizeImageUrl(): String? {
        val value = this?.trim().orEmpty()
        if (value.isBlank()) return null
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://") -> value.replaceFirst("http://", "https://")
            else -> value
        }
    }
}
