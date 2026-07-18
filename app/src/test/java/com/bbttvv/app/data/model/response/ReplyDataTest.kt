package com.bbttvv.app.data.model.response

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyDataTest {
    @Test
    fun subReplyCountUsesDetailRootCountWhenPageCountIsMissing() {
        val data = ReplyData(
            root = ReplyItem(count = 2, rcount = 7),
            replies = listOf(ReplyItem(rpid = 1L))
        )

        assertEquals(7, data.getSubReplyCount())
    }

    @Test
    fun subReplyCountPrefersRemotePageCountOverRootPreviewCount() {
        val data = ReplyData(
            page = ReplyPage(count = 5),
            root = ReplyItem(count = 2, rcount = 1)
        )

        assertEquals(5, data.getSubReplyCount())
    }

    @Test
    fun replyContentMessageUnescapesHtmlEntitiesWhenDecoded() {
        val json = Json { ignoreUnknownKeys = true }
        val content = json.decodeFromString<ReplyContent>(
            """{"message":"I&#39;m &quot;watching&quot; &amp; testing"}"""
        )

        assertEquals("I'm \"watching\" & testing", content.message)
    }
}
