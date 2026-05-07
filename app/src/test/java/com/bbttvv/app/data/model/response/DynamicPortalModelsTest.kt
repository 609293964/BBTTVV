package com.bbttvv.app.data.model.response

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicPortalModelsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun parsesPortalUpListObjectItems() {
        val response = json.decodeFromString<DynamicPortalResponse>(
            """
            {
              "code": 0,
              "data": {
                "up_list": {
                  "items": [
                    {
                      "mid": 101,
                      "name": "updated",
                      "face": "https://example.com/a.jpg",
                      "has_update": true
                    },
                    {
                      "mid": 102,
                      "name": "quiet",
                      "face": "https://example.com/b.jpg",
                      "has_update": false
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val items = response.data?.up_list?.items.orEmpty()
        assertEquals(2, items.size)
        assertEquals(101L, items[0].mid)
        assertEquals("updated", items[0].name)
        assertEquals("https://example.com/a.jpg", items[0].face)
        assertTrue(items[0].has_update)
        assertFalse(items[1].has_update)
    }

    @Test
    fun parsesPortalUpListArrayShape() {
        val response = json.decodeFromString<DynamicPortalResponse>(
            """
            {
              "code": 0,
              "data": {
                "up_list": [
                  {
                    "uid": "201",
                    "uname": "array user",
                    "avatar": "https://example.com/c.jpg",
                    "has_update": 1
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val item = response.data?.up_list?.items.orEmpty().single()
        assertEquals(201L, item.mid)
        assertEquals("array user", item.name)
        assertEquals("https://example.com/c.jpg", item.face)
        assertTrue(item.has_update)
    }
}
