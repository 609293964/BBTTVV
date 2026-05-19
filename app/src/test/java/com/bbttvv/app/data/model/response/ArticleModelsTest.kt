package com.bbttvv.app.data.model.response

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun articleDetailKeepsOpsContentBlocks() {
        val response = json.decodeFromString<ArticleDetailResponse>(
            """
            {
              "code": 0,
              "data": {
                "id": "123",
                "title": "专栏",
                "ops": [
                  { "insert": "第一段" },
                  { "attributes": { "bold": true }, "insert": "第二段" }
                ]
              }
            }
            """.trimIndent()
        )

        val article = response.data!!
        assertEquals(123L, article.id)
        assertEquals("专栏", article.title)
        assertEquals("第一段", article.ops.first()["insert"]?.jsonPrimitive?.content)
        assertEquals(2, article.ops.size)
    }
}
