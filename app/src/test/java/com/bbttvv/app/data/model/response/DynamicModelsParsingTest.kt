package com.bbttvv.app.data.model.response

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicModelsParsingTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun dynamicFeedSkipsMalformedItems() {
        val response = json.decodeFromString<DynamicFeedResponse>(
            """
            {
              "code": 0,
              "data": {
                "items": [
                  {
                    "id_str": { "unexpected": "object" },
                    "type": "DYNAMIC_TYPE_AV",
                    "modules": {}
                  },
                  {
                    "id_str": "good-dynamic",
                    "type": "DYNAMIC_TYPE_AV",
                    "visible": true,
                    "modules": {
                      "module_author": {
                        "mid": 123,
                        "name": "up"
                      },
                      "module_dynamic": {
                        "major": {
                          "type": "MAJOR_TYPE_ARCHIVE",
                          "archive": {
                            "aid": "456",
                            "bvid": "BV1xx411c7mD",
                            "title": "valid video"
                          }
                        }
                      }
                    }
                  }
                ],
                "offset": "next-page",
                "has_more": true
              }
            }
            """.trimIndent()
        )

        val data = response.data!!
        assertEquals(listOf("good-dynamic"), data.items.map { it.id_str })
        assertEquals("next-page", data.offset)
        assertEquals(true, data.has_more)
    }

    @Test
    fun dynamicFeedAcceptsNumericTypeAndFollowingFlag() {
        val response = json.decodeFromString<DynamicFeedResponse>(
            """
            {
              "code": 0,
              "data": {
                "items": [
                  {
                    "id_str": "numeric-fields",
                    "type": 8,
                    "modules": {
                      "module_author": {
                        "mid": 123,
                        "name": "up",
                        "following": 1
                      }
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val item = response.data!!.items.single()
        assertEquals("8", item.type)
        assertEquals(true, item.modules.module_author?.following)
    }
}
