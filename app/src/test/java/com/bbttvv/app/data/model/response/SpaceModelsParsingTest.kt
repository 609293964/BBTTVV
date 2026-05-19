package com.bbttvv.app.data.model.response

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SpaceModelsParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun spaceArticleListParsesOpusFieldsAndDisplayImages() {
        val response = json.decodeFromString<SpaceArticleResponse>(
            """
            {
              "code": 0,
              "data": {
                "articles": [
                  {
                    "opus_id": "987654321",
                    "content": "长文标题",
                    "jump_url": "https://www.bilibili.com/read/cv987654321",
                    "cover": { "url": "https://i0.hdslb.com/cover.jpg", "width": 640, "height": 360 },
                    "stat": { "view": "123", "reply": "4" }
                  }
                ],
                "count": 12,
                "has_more": true,
                "offset": "next"
              }
            }
            """.trimIndent()
        )

        val data = response.data!!
        val item = data.lists.single()
        assertEquals(987654321L, item.id)
        assertEquals("长文标题", item.title)
        assertEquals("https://www.bilibili.com/read/cv987654321", item.jump_url)
        assertEquals(123, item.stats?.view)
        assertEquals(12, data.total)
        assertEquals(listOf("https://i0.hdslb.com/cover.jpg"), item.displayImageUrls())
    }

    @Test
    fun spaceDynamicArticleMajorKeepsJumpUrlAndCovers() {
        val response = json.decodeFromString<SpaceDynamicResponse>(
            """
            {
              "code": 0,
              "data": {
                "items": [
                  {
                    "id_str": "1",
                    "modules": {
                      "module_dynamic": {
                        "major": {
                          "type": "MAJOR_TYPE_ARTICLE",
                          "article": {
                            "id": "12345",
                            "title": "专栏",
                            "covers": ["https://i0.hdslb.com/a.jpg"],
                            "jump_url": "https://www.bilibili.com/read/cv12345"
                          }
                        }
                      }
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val article = response.data!!.items.single().modules.module_dynamic!!.major!!.article!!
        assertEquals(12345L, article.id)
        assertEquals("https://www.bilibili.com/read/cv12345", article.jump_url)
        assertEquals(listOf("https://i0.hdslb.com/a.jpg"), article.covers)
    }
}
