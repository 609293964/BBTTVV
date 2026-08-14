package com.bbttvv.app.feature.plugin

import com.bbttvv.app.core.plugin.FeedKind
import com.bbttvv.app.data.model.response.Owner
import com.bbttvv.app.data.model.response.Stat
import com.bbttvv.app.data.model.response.VideoItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiliNaraFeedFilterPluginTest {
    private fun video(
        title: String = "普通视频",
        mid: Long = 1001L,
        duration: Int = 300,
        view: Int = 100_000,
        like: Int = 5_000,
        tname: String = "生活",
        isFollowed: Boolean = false,
    ) = VideoItem(
        title = title,
        owner = Owner(mid = mid, name = "UP$mid"),
        stat = Stat(view = view, like = like),
        duration = duration,
        tname = tname,
        isFollowed = isFollowed,
    )

    private fun shows(
        config: PiliNaraFeedFilterConfig,
        item: VideoItem = video(),
        kind: FeedKind = FeedKind.HOME_RECOMMEND,
    ): Boolean = shouldShowFeedItem(
        config = config,
        item = item,
        feedKind = kind,
        titleRegex = PiliNaraFeedFilterPlugin.compileKeywords(config.titleKeywords),
        zoneRegex = PiliNaraFeedFilterPlugin.compileKeywords(config.zoneKeywords),
    )

    @Test
    fun defaults_show_everything() {
        assertTrue(shows(PiliNaraFeedFilterConfig()))
    }

    @Test
    fun hides_items_below_numeric_thresholds() {
        assertFalse(shows(PiliNaraFeedFilterConfig(minDurationSeconds = 60), video(duration = 20)))
        assertFalse(shows(PiliNaraFeedFilterConfig(minPlayCount = 1_000), video(view = 999)))
        assertFalse(shows(PiliNaraFeedFilterConfig(minLikeRatioPercent = 2), video(view = 1_000, like = 19)))
        assertTrue(shows(PiliNaraFeedFilterConfig(minLikeRatioPercent = 2), video(view = 1_000, like = 20)))
    }

    @Test
    fun whitelist_and_followed_recommendations_are_exempt() {
        val rules = PiliNaraFeedFilterConfig(
            titleKeywords = "广告",
            blockedMids = setOf(7L),
            whitelistMids = setOf(7L),
        )
        assertTrue(shows(rules, video(title = "广告", mid = 7L)))
        assertTrue(shows(PiliNaraFeedFilterConfig(minDurationSeconds = 60), video(duration = 10, isFollowed = true)))
    }

    @Test
    fun filters_keywords_and_mids() {
        assertFalse(shows(PiliNaraFeedFilterConfig(titleKeywords = "广告\n震惊"), video(title = "震惊！必看")))
        assertFalse(shows(PiliNaraFeedFilterConfig(zoneKeywords = "游戏"), video(tname = "游戏")))
        assertFalse(shows(PiliNaraFeedFilterConfig(blockedMids = setOf(1001L))))
    }

    @Test
    fun popular_filtering_is_opt_in_and_regions_are_unchanged() {
        val rules = PiliNaraFeedFilterConfig(titleKeywords = "广告")
        val ad = video(title = "广告")
        assertTrue(shows(rules, ad, FeedKind.HOME_POPULAR))
        assertFalse(shows(rules.copy(applyToPopular = true), ad, FeedKind.HOME_POPULAR))
        assertTrue(shows(rules.copy(applyToPopular = true), ad, FeedKind.HOME_REGION))
    }
}
