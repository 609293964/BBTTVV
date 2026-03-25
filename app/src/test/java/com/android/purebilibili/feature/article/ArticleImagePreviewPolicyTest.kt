package com.android.purebilibili.feature.article

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleImagePreviewPolicyTest {

    @Test
    fun `resolveArticleImagePreviewPayload collects images in original order and maps tapped image index`() {
        val blocks = listOf(
            ArticleContentBlock.Paragraph(text = "intro"),
            ArticleContentBlock.Image(url = "https://example.com/1.jpg", width = 100, height = 100),
            ArticleContentBlock.Heading(text = "middle"),
            ArticleContentBlock.Image(url = "https://example.com/2.jpg", width = 200, height = 150),
            ArticleContentBlock.Image(url = "https://example.com/3.jpg", width = 300, height = 250)
        )

        val payload = resolveArticleImagePreviewPayload(
            blocks = blocks,
            tappedBlockIndex = 3
        )

        assertEquals(
            ArticleImagePreviewPayload(
                images = listOf(
                    "https://example.com/1.jpg",
                    "https://example.com/2.jpg",
                    "https://example.com/3.jpg"
                ),
                initialIndex = 1
            ),
            payload
        )
    }

    @Test
    fun `resolveArticleImagePreviewPayload returns null when tapped block is not an image`() {
        val blocks = listOf(
            ArticleContentBlock.Paragraph(text = "intro"),
            ArticleContentBlock.Image(url = "https://example.com/1.jpg", width = 100, height = 100)
        )

        val payload = resolveArticleImagePreviewPayload(
            blocks = blocks,
            tappedBlockIndex = 0
        )

        assertNull(payload)
    }

    @Test
    fun `resolveArticleImagePreviewPayload returns null when tapped index is outside blocks`() {
        val payload = resolveArticleImagePreviewPayload(
            blocks = listOf(ArticleContentBlock.Image(url = "https://example.com/1.jpg", width = 100, height = 100)),
            tappedBlockIndex = 5
        )

        assertNull(payload)
    }
}
