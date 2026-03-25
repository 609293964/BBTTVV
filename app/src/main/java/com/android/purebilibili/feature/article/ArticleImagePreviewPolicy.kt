package com.android.purebilibili.feature.article

internal data class ArticleImagePreviewPayload(
    val images: List<String>,
    val initialIndex: Int
)

internal fun collectArticleBodyImageUrls(
    blocks: List<ArticleContentBlock>
): List<String> {
    return blocks.mapNotNull { block ->
        (block as? ArticleContentBlock.Image)?.url
    }
}

internal fun resolveArticleImagePreviewPayload(
    blocks: List<ArticleContentBlock>,
    tappedBlockIndex: Int
): ArticleImagePreviewPayload? {
    var tappedImageIndex: Int? = null
    val images = buildList {
        blocks.forEachIndexed { index, block ->
            val image = block as? ArticleContentBlock.Image ?: return@forEachIndexed
            add(image.url)
            if (index == tappedBlockIndex) {
                tappedImageIndex = lastIndex
            }
        }
    }
    val initialIndex = tappedImageIndex ?: return null
    return ArticleImagePreviewPayload(
        images = images,
        initialIndex = initialIndex
    )
}
