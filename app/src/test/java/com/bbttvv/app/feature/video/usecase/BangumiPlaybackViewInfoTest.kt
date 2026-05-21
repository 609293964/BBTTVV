package com.bbttvv.app.feature.video.usecase

import com.bbttvv.app.data.model.response.BangumiDetail
import com.bbttvv.app.data.model.response.BangumiEpisode
import org.junit.Assert.assertEquals
import org.junit.Test

class BangumiPlaybackViewInfoTest {
    @Test
    fun keepsEpisodePlaybackKeyWhenApiEpisodeHasBvid() {
        val viewInfo = BangumiDetail(title = "Season").toBangumiPlaybackViewInfo(
            currentEpisode = BangumiEpisode(
                id = 987L,
                aid = 654L,
                bvid = "BV1episodeFromApi",
                cid = 321L,
            ),
            playbackBvid = "ep987",
        )

        assertEquals("ep987", viewInfo.bvid)
        assertEquals(654L, viewInfo.aid)
        assertEquals(321L, viewInfo.cid)
    }

    @Test
    fun fallsBackToEpisodePlaybackKeyWhenCallerHasNoKey() {
        val viewInfo = BangumiDetail().toBangumiPlaybackViewInfo(
            currentEpisode = BangumiEpisode(
                id = 987L,
                bvid = "BV1episodeFromApi",
            ),
            playbackBvid = " ",
        )

        assertEquals("ep987", viewInfo.bvid)
    }
}
