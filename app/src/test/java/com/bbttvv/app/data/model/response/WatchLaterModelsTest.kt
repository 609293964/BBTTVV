package com.bbttvv.app.data.model.response

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchLaterModelsTest {

    @Test
    fun `watch later item maps api fields into video item`() {
        val video = WatchLaterItem(
            aid = 123L,
            bvid = "BV123",
            title = "稍后再看视频",
            pic = "https://example.com/cover.jpg",
            duration = 95,
            pubdate = 1710000000L,
            owner = WatchLaterOwner(mid = 42L, name = "UP 主", face = "https://example.com/face.jpg"),
            stat = WatchLaterStat(view = 1000, danmaku = 80, reply = 7, like = 30, coin = 4, favorite = 5, share = 6)
        ).toVideoItem()

        assertEquals(123L, video.id)
        assertEquals(123L, video.aid)
        assertEquals("BV123", video.bvid)
        assertEquals("稍后再看视频", video.title)
        assertEquals("https://example.com/cover.jpg", video.pic)
        assertEquals(95, video.duration)
        assertEquals(1710000000L, video.pubdate)
        assertEquals(42L, video.owner.mid)
        assertEquals("UP 主", video.owner.name)
        assertEquals(1000, video.stat.view)
        assertEquals(80, video.stat.danmaku)
        assertEquals(7, video.stat.reply)
    }

    @Test
    fun `watch later item falls back to safe defaults`() {
        val video = WatchLaterItem(aid = 9L).toVideoItem()

        assertEquals(9L, video.id)
        assertEquals(9L, video.aid)
        assertEquals("", video.bvid)
        assertEquals("", video.title)
        assertEquals("", video.pic)
        assertEquals(0, video.duration)
        assertEquals(0L, video.pubdate)
        assertEquals(0L, video.owner.mid)
        assertEquals("", video.owner.name)
        assertEquals(0, video.stat.view)
    }
}
