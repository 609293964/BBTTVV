package com.bbttvv.app.data.model.response

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchModelsParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun videoSearchKeepsNavigationMetadataAndCleanDisplayTitle() {
        val item = json.decodeFromString<SearchVideoItem>(
            """
            {
              "type": "video",
              "id": "123",
              "bvid": "BV1xx411c7mD",
              "arcurl": "https://www.bilibili.com/video/BV1xx411c7mD",
              "title": "<em class=\"keyword\">测试</em>&amp;标题",
              "pic": "//i0.hdslb.com/cover.jpg",
              "duration": "01:30"
            }
            """.trimIndent()
        )

        val video = item.toVideoItem()

        assertEquals("测试&标题", video.title)
        assertEquals(item.title, video.searchHighlightedTitle)
        assertEquals("video", video.contentType)
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD", video.navigationUrl)
        assertEquals("https://i0.hdslb.com/cover.jpg", video.pic)
        assertEquals(90, video.duration)
    }
}
