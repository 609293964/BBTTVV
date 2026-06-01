package com.bbttvv.app.feature.plugin

import com.bbttvv.app.core.network.policy.HomeFeedAnonymizerRuntime
import com.bbttvv.app.core.network.policy.HomeFeedAnonymizerStatsSnapshot
import com.bbttvv.app.core.plugin.PluginCapability
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedAnonymizerPluginTest {

    @Test
    fun `plugin declares metadata and capabilities`() {
        val plugin = HomeFeedAnonymizerPlugin()

        assertEquals(HOME_FEED_ANONYMIZER_PLUGIN_ID, plugin.id)
        assertEquals("初见推荐", plugin.name)
        assertEquals("1.0.1", plugin.version)
        assertEquals("BiliPai项目组", plugin.author)
        assertFalse(plugin.unavailable)
        assertEquals(
            setOf(
                PluginCapability.RECOMMENDATION_CANDIDATES,
                PluginCapability.NETWORK
            ),
            plugin.capabilityManifest.capabilities
        )
    }

    @Test
    fun `plugin enable and disable updates runtime state`() = runBlocking {
        val plugin = HomeFeedAnonymizerPlugin()
        HomeFeedAnonymizerRuntime.setEnabled(false)

        plugin.onEnable()
        assertTrue(HomeFeedAnonymizerRuntime.enabled)

        plugin.onDisable()
        assertFalse(HomeFeedAnonymizerRuntime.enabled)
    }

    @Test
    fun `credits include original author and plus fork`() {
        val credits = buildHomeFeedAnonymizerCreditRows()

        assertEquals(listOf("原作者", "Plus 改版"), credits.map { it.label })
        assertEquals("wangdaodao", credits[0].summary)
        assertTrue(credits[0].fullContent.contains("https://github.com/wangdaodaodao/TabulaBili"))
        assertEquals("tjsky", credits[1].summary)
        assertTrue(credits[1].fullContent.contains("https://github.com/tjsky/TabulaBili"))
    }

    @Test
    fun `stats ui model formats empty hit state`() {
        val empty = buildHomeFeedAnonymizerStatsUiModel(
            snapshot = HomeFeedAnonymizerStatsSnapshot(),
            enabled = false
        )

        assertEquals("未启用", empty.stateRow.summary)
        assertEquals("0 次", empty.totalRow.summary)
        assertEquals("暂无命中", empty.lastHitRow.summary)
        assertEquals(1, empty.totalRow.maxLines)
        assertEquals(1, empty.lastHitRow.maxLines)
    }

    @Test
    fun `stats ui model formats hit state`() {
        val model = buildHomeFeedAnonymizerStatsUiModel(
            snapshot = HomeFeedAnonymizerStatsSnapshot(
                totalHits = 12L,
                lastHitAtMs = 1_700_000_000_000L,
                lastHitHost = "api.bilibili.com",
                lastHitEncodedPath = "/x/web-interface/wbi/index/top/feed/rcmd"
            ),
            enabled = true
        )

        assertEquals("运行中", model.stateRow.summary)
        assertEquals("12 次", model.totalRow.summary)
        assertEquals("api.bilibili.com", model.lastHitRow.summary)
        assertTrue(model.lastHitRow.fullContent.contains("/x/web-interface/wbi/index/top/feed/rcmd"))
        assertTrue(model.scopeRow.fullContent.contains(HOME_FEED_ANONYMIZER_WEB_ENDPOINT))
        assertTrue(model.scopeRow.fullContent.contains("关闭插件"))
        assertEquals(2, model.scopeRow.maxLines)
    }
}
