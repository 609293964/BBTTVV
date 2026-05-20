package com.bbttvv.app.data.model.response

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BangumiModelsParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun bangumiEpisodeParsesNormalLongPubTime() {
        val jsonString = """
            {
              "id": 12345,
              "aid": 98765,
              "bvid": "BV1xx411c7Tz",
              "cid": 54321,
              "title": "第1话",
              "long_title": "番剧第一集",
              "cover": "https://i0.hdslb.com/cover.jpg",
              "duration": 1450000,
              "pub_time": 1716200000
            }
        """.trimIndent()
        
        val episode = json.decodeFromString<BangumiEpisode>(jsonString)
        assertEquals(12345L, episode.id)
        assertEquals(98765L, episode.aid)
        assertEquals("BV1xx411c7Tz", episode.bvid)
        assertEquals(54321L, episode.cid)
        assertEquals(1716200000L, episode.pubTime)
    }

    @Test
    fun bangumiEpisodeParsesFormattedStringPubTime() {
        // 测试 yyyy-MM-dd HH:mm:ss 格式的字符串
        val jsonString = """
            {
              "id": 12345,
              "aid": 98765,
              "bvid": "BV1xx411c7Tz",
              "cid": 54321,
              "title": "第1话",
              "long_title": "番剧第一集",
              "cover": "https://i0.hdslb.com/cover.jpg",
              "duration": 1450000,
              "pub_time": "2024-12-14 22:00:00"
            }
        """.trimIndent()

        val episode = json.decodeFromString<BangumiEpisode>(jsonString)
        // 2024-12-14 22:00:00 在 GMT+8 时区下为 1734184800 秒级时间戳
        assertEquals(1734184800L, episode.pubTime)
    }

    @Test
    fun bangumiEpisodeParsesInvalidStringPubTimeGracefully() {
        // 测试无法解析的字符串，应能降级并返回 0L
        val jsonString = """
            {
              "id": 12345,
              "aid": 98765,
              "bvid": "BV1xx411c7Tz",
              "cid": 54321,
              "title": "第1话",
              "long_title": "番剧第一集",
              "cover": "https://i0.hdslb.com/cover.jpg",
              "duration": 1450000,
              "pub_time": "NotADateString"
            }
        """.trimIndent()

        val episode = json.decodeFromString<BangumiEpisode>(jsonString)
        assertEquals(0L, episode.pubTime)
    }

    @Test
    fun bangumiDetailResponseParsesWithDifferentPubTimeFormats() {
        val jsonString = """
            {
              "code": 0,
              "message": "success",
              "result": {
                "season_id": 999,
                "title": "超酷番剧",
                "episodes": [
                  {
                    "id": 1001,
                    "pub_time": 1716200000
                  },
                  {
                    "id": 1002,
                    "pub_time": "2024-12-14 22:00:00"
                  },
                  {
                    "id": 1003,
                    "pub_time": "NotADateString"
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<BangumiDetailResponse>(jsonString)
        assertEquals(0, response.code)
        val episodes = response.result?.episodes!!
        assertEquals(3, episodes.size)
        assertEquals(1716200000L, episodes[0].pubTime)
        assertEquals(1734184800L, episodes[1].pubTime)
        assertEquals(0L, episodes[2].pubTime)
    }

    @Test
    fun bangumiRepositoryLimitEpisodesInJsonFiltersCorrectly() {
        // 构建一个包含 25 集的超长剧集 JSON
        val episodeListJson = (1..25).map { index ->
            """
            {
              "id": ${1000 + index},
              "aid": ${index * 10},
              "bvid": "BV1xx${index}",
              "cid": ${index * 100},
              "title": "第${index}话",
              "long_title": "番剧第${index}集"
            }
            """.trimIndent()
        }.joinToString(",")

        val jsonString = """
            {
              "code": 0,
              "message": "success",
              "result": {
                "season_id": 999,
                "title": "超酷番剧",
                "user_status": {
                  "follow": 1,
                  "progress": {
                    "last_ep_id": 1022,
                    "last_ep_index": "第22话"
                  }
                },
                "episodes": [
                  $episodeListJson
                ]
              }
            }
        """.trimIndent()

        // 测试 targetEpId = 0，但有历史进度 (1022L) 时，它会自动识别并保留目标集数（第 22 集）
        val processedJson = com.bbttvv.app.data.repository.BangumiRepository.limitEpisodesInJson(jsonString, maxEpisodes = 15, targetEpId = 0L)
        val response = json.decodeFromString<BangumiDetailResponse>(processedJson)
        val episodes = response.result?.episodes!!
        
        // 应该截取 15 集
        assertEquals(15, episodes.size)
        // 应该保留前 10 集 (id 1001 到 1010)
        for (i in 0..9) {
            assertEquals((1001 + i).toLong(), episodes[i].id)
        }
        // 由于 last_ep_id = 1022，剩下的 5 集 (15 - 10) 应该以第 22 集 (id 1022, index 21) 为中心进行截取
        // targetIndex = 21 (第 22 集)，remainingCount = 5，start = 21 - 2 = 19，end = 21 + 3 = 24.
        // 所以 middleAndTail 保留 index 19 到 23 (id 1020 到 1024)
        assertEquals(1020L, episodes[10].id)
        assertEquals(1021L, episodes[11].id)
        assertEquals(1022L, episodes[12].id) // target 成功保留！
        assertEquals(1023L, episodes[13].id)
        assertEquals(1024L, episodes[14].id)
    }
}
