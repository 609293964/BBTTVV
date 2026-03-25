package com.android.purebilibili.feature.article

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleContentBlockParserTest {

    @Test
    fun `parseArticleContentBlocks extracts heading paragraph and image from structured paragraphs`() {
        val blocks = parseArticleContentBlocks(
            structuredParagraphs = listOf(
                paragraph(
                    headingWords = listOf("主标题")
                ),
                paragraph(
                    textWords = listOf("第一段", "内容")
                ),
                paragraph(
                    imageUrl = "https://i0.hdslb.com/bfs/article/test-cover.png",
                    imageWidth = 1080,
                    imageHeight = 720
                )
            ),
            htmlContent = null
        )

        assertEquals(
            listOf(
                ArticleContentBlock.Heading(text = "主标题"),
                ArticleContentBlock.Paragraph(text = "第一段内容"),
                ArticleContentBlock.Image(
                    url = "https://i0.hdslb.com/bfs/article/test-cover.png",
                    width = 1080,
                    height = 720
                )
            ),
            blocks
        )
    }

    @Test
    fun `parseArticleContentBlocks falls back to html paragraphs and images when structured content is empty`() {
        val blocks = parseArticleContentBlocks(
            structuredParagraphs = emptyList(),
            htmlContent = """
                <h1>老专栏标题</h1>
                <p>第一段文字</p>
                <figure><img data-src="//i0.hdslb.com/bfs/article/test-inline.png" width="640" height="480" /></figure>
                <p><strong>第二段</strong>文字</p>
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ArticleContentBlock.Heading(text = "老专栏标题"),
                ArticleContentBlock.Paragraph(text = "第一段文字"),
                ArticleContentBlock.Image(
                    url = "https://i0.hdslb.com/bfs/article/test-inline.png",
                    width = 640,
                    height = 480
                ),
                ArticleContentBlock.Paragraph(text = "第二段文字")
            ),
            blocks
        )
    }

    private fun paragraph(
        textWords: List<String> = emptyList(),
        headingWords: List<String> = emptyList(),
        imageUrl: String? = null,
        imageWidth: Int? = null,
        imageHeight: Int? = null
    ): JsonObject {
        val content = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()

        if (textWords.isNotEmpty()) {
            content["text"] = JsonObject(
                mapOf(
                    "nodes" to JsonArray(
                        textWords.map { word ->
                            JsonObject(
                                mapOf(
                                    "word" to JsonObject(
                                        mapOf("words" to JsonPrimitive(word))
                                    )
                                )
                            )
                        }
                    )
                )
            )
        }

        if (headingWords.isNotEmpty()) {
            content["heading"] = JsonObject(
                mapOf(
                    "nodes" to JsonArray(
                        headingWords.map { word ->
                            JsonObject(
                                mapOf(
                                    "word" to JsonObject(
                                        mapOf("words" to JsonPrimitive(word))
                                    )
                                )
                            )
                        }
                    )
                )
            )
        }

        if (imageUrl != null) {
            content["pic"] = JsonObject(
                mapOf(
                    "pics" to JsonArray(
                        listOf(
                            JsonObject(
                                mapOf(
                                    "url" to JsonPrimitive(imageUrl),
                                    "width" to JsonPrimitive(imageWidth ?: 0),
                                    "height" to JsonPrimitive(imageHeight ?: 0)
                                )
                            )
                        )
                    )
                )
            )
        }

        return JsonObject(content)
    }
}
