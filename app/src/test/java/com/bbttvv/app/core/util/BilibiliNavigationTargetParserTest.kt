package com.bbttvv.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BilibiliNavigationTargetParserTest {
    @Test
    fun parsesArticleReadTargets() {
        assertEquals(
            BilibiliNavigationTarget.Article(12345L),
            BilibiliNavigationTargetParser.parse("https://www.bilibili.com/read/cv12345")
        )
        assertEquals(
            BilibiliNavigationTarget.Article(67890L),
            BilibiliNavigationTargetParser.parse("bilibili://read/mobile?id=67890")
        )
    }

    @Test
    fun parsesSearchHostsAndQueryAliases() {
        assertEquals(
            BilibiliNavigationTarget.Search("android tv"),
            BilibiliNavigationTargetParser.parse("https://search.bilibili.com/all?keyword=android%20tv")
        )
        assertEquals(
            BilibiliNavigationTarget.Search("遥控器"),
            BilibiliNavigationTargetParser.parse("bilibili://search?q=%E9%81%A5%E6%8E%A7%E5%99%A8")
        )
    }

    @Test
    fun oversizedVideoDeepLinkIsTreatedAsDynamicId() {
        val result = BilibiliUrlParser.parseDeepLink(
            "bilibili://video/1199344045210468386?page=0&comment_root_id=279569905408"
        )

        assertEquals("1199344045210468386", result.dynamicId)
        assertEquals(null, result.aid)
    }
}
