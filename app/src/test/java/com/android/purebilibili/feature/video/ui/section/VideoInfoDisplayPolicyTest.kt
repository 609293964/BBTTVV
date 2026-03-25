package com.android.purebilibili.feature.video.ui.section

import com.android.purebilibili.data.model.response.AiModelResult
import com.android.purebilibili.data.model.response.AiOutline
import com.android.purebilibili.data.model.response.AiSummaryData
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoInfoDisplayPolicyTest {

    @Test
    fun aiSummaryEntryShownOnlyWhenEnabledAndContentExists() {
        val aiSummary = AiSummaryData(
            code = 0,
            modelResult = AiModelResult(
                summary = "这是一段 AI 摘要",
                outline = listOf(AiOutline(title = "开场", timestamp = 12))
            )
        )

        assertTrue(shouldShowAiSummaryEntry(aiSummary, isAiSummaryEntryEnabled = true))
        assertFalse(shouldShowAiSummaryEntry(aiSummary, isAiSummaryEntryEnabled = false))
    }

    @Test
    fun aiSummaryEntryHiddenWhenPayloadMissingOrEmpty() {
        val emptySummary = AiSummaryData(
            code = 0,
            modelResult = AiModelResult(summary = "", outline = emptyList())
        )

        assertFalse(shouldShowAiSummaryEntry(aiSummary = null, isAiSummaryEntryEnabled = true))
        assertFalse(shouldShowAiSummaryEntry(emptySummary, isAiSummaryEntryEnabled = true))
    }

    @Test
    fun inlineOwnerIdentityShownOnlyWhenLeadingAvatarHidden() {
        assertTrue(shouldShowInlineOwnerIdentity(showOwnerAvatar = false))
        assertFalse(shouldShowInlineOwnerIdentity(showOwnerAvatar = true))
    }

    @Test
    fun publishTimeRow_emphasizesCurrentAffairsVideosFromPartitionKeywords() {
        assertTrue(
            shouldEmphasizePrecisePublishTime(
                partitionName = "资讯",
                title = "今晚新闻速递"
            )
        )
    }

    @Test
    fun publishTimeRow_emphasizesCurrentAffairsVideosFromTitleKeywords() {
        assertTrue(
            shouldEmphasizePrecisePublishTime(
                partitionName = "科技",
                title = "联合国发布会最新回应"
            )
        )
    }

    @Test
    fun publishTimeRow_keepsOrdinaryVideosLightweight() {
        assertFalse(
            shouldEmphasizePrecisePublishTime(
                partitionName = "动画",
                title = "今天继续补番"
            )
        )
    }

    @Test
    fun publishTimeRow_usesRelativeAndPreciseTextForCurrentAffairsVideos() {
        assertEquals(
            "发布时间 2小时前  ·  2024-03-01 02:00",
            resolvePublishTimeRowText(
                pubdate = 1_709_258_400L,
                partitionName = "资讯",
                title = "例行发布会速报",
                nowMs = 1_709_265_600_000L,
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("UTC")
            )
        )
    }

    @Test
    fun publishTimeRow_usesRelativeOnlyTextForOrdinaryVideos() {
        assertEquals(
            "发布于 2小时前",
            resolvePublishTimeRowText(
                pubdate = 1_709_258_400L,
                partitionName = "动画",
                title = "新番混剪",
                nowMs = 1_709_265_600_000L,
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("UTC")
            )
        )
    }

    @Test
    fun publishTimeRow_allowsHomepageCardsToEmphasizeFromTitleOnly() {
        assertEquals(
            "发布时间 2小时前  ·  2024-03-01 02:00",
            resolvePublishTimeRowText(
                pubdate = 1_709_258_400L,
                partitionName = "",
                title = "联合国发布会速报",
                nowMs = 1_709_265_600_000L,
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("UTC")
            )
        )
    }

    @Test
    fun dynamicPublishTimeRow_usesDynamicLabelForOrdinaryVideos() {
        assertEquals(
            "动态发布 2小时前",
            resolveDynamicPublishTimeRowText(
                publishTs = 1_709_258_400L,
                title = "新番混剪",
                nowMs = 1_709_265_600_000L,
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("UTC")
            )
        )
    }

    @Test
    fun dynamicPublishTimeRow_usesRelativeAndPreciseTextForCurrentAffairsVideos() {
        assertEquals(
            "动态发布 2小时前  ·  2024-03-01 02:00",
            resolveDynamicPublishTimeRowText(
                publishTs = 1_709_258_400L,
                title = "外交部最新回应",
                nowMs = 1_709_265_600_000L,
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("UTC")
            )
        )
    }
}
