package com.bbttvv.app.data.repository

import com.bbttvv.app.data.model.response.DynamicFeedResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DynamicVideoSourcePolicyTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `forwarded video resolves original archive while non-video stays hidden`() {
        val response = json.decodeFromString<DynamicFeedResponse>(
            """
            {
              "data": {
                "items": [
                  {
                    "id_str": "forward-1",
                    "type": "DYNAMIC_TYPE_FORWARD",
                    "modules": {},
                    "orig": {
                      "id_str": "original-1",
                      "type": "DYNAMIC_TYPE_AV",
                      "modules": {
                        "module_dynamic": {
                          "major": {
                            "archive": {
                              "aid": "123",
                              "bvid": "BV1forwarded",
                              "title": "original video"
                            }
                          }
                        }
                      }
                    }
                  },
                  {
                    "id_str": "text-only",
                    "type": "DYNAMIC_TYPE_WORD",
                    "modules": {}
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val items = response.data!!.items
        assertEquals("original-1", items[0].resolveRenderableVideoSource()?.id_str)
        assertEquals("BV1forwarded", items[0].resolveRenderableVideoSource()
            ?.modules?.module_dynamic?.major?.archive?.bvid)
        assertNull(items[1].resolveRenderableVideoSource())
    }
}
