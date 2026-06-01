package com.bbttvv.app.feature.plugin

import com.bbttvv.app.core.network.policy.HomeFeedAnonymizerRuntime
import com.bbttvv.app.core.network.policy.HomeFeedAnonymizerStatsSnapshot
import com.bbttvv.app.core.plugin.Plugin
import com.bbttvv.app.core.plugin.PluginCapability
import com.bbttvv.app.core.plugin.PluginCapabilityManifest
import com.bbttvv.app.core.util.Logger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val HOME_FEED_ANONYMIZER_PLUGIN_ID = "home_feed_anonymizer"
const val HOME_FEED_ANONYMIZER_WEB_ENDPOINT =
    "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd"

private const val HomeFeedAnonymizerPluginTag = "HomeFeedAnonymizerPlugin"
private val HomeFeedAnonymizerTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

class HomeFeedAnonymizerPlugin : Plugin {
    override val id: String = HOME_FEED_ANONYMIZER_PLUGIN_ID
    override val name: String = "初见推荐"
    override val description: String = "仅在 Web 首页推荐接口使用匿名访客标识，隐藏登录 Cookie，让推荐流更接近未登录公共热门。"
    override val version: String = "1.0.2"
    override val author: String = "BiliPai项目组"
    override val capabilityManifest: PluginCapabilityManifest = PluginCapabilityManifest(
        pluginId = id,
        displayName = name,
        version = version,
        apiVersion = 1,
        entryClassName = "com.bbttvv.app.feature.plugin.HomeFeedAnonymizerPlugin",
        capabilities = setOf(
            PluginCapability.RECOMMENDATION_CANDIDATES,
            PluginCapability.NETWORK
        )
    )

    val enabled: Boolean
        get() = HomeFeedAnonymizerRuntime.enabled

    val statsSnapshot: HomeFeedAnonymizerStatsSnapshot
        get() = HomeFeedAnonymizerRuntime.statsSnapshot

    override suspend fun onEnable() {
        if (!HomeFeedAnonymizerRuntime.enabled) {
            HomeFeedAnonymizerRuntime.rotateAnonymousSession()
        }
        HomeFeedAnonymizerRuntime.setEnabled(true)
        Logger.d(HomeFeedAnonymizerPluginTag, "初见推荐已启用：Web 首页推荐接口将使用匿名 buvid3，不携带登录 Cookie")
    }

    override suspend fun onDisable() {
        HomeFeedAnonymizerRuntime.setEnabled(false)
        Logger.d(HomeFeedAnonymizerPluginTag, "初见推荐已禁用：Web 首页推荐接口恢复登录 Cookie")
    }

    fun resetStats() {
        HomeFeedAnonymizerRuntime.resetStats()
    }
}

data class HomeFeedAnonymizerInfoRow(
    val label: String,
    val summary: String,
    val fullContent: String,
    val maxLines: Int
)

data class HomeFeedAnonymizerStatsUiModel(
    val stateRow: HomeFeedAnonymizerInfoRow,
    val totalRow: HomeFeedAnonymizerInfoRow,
    val lastHitRow: HomeFeedAnonymizerInfoRow,
    val scopeRow: HomeFeedAnonymizerInfoRow
) {
    val rows: List<HomeFeedAnonymizerInfoRow>
        get() = listOf(stateRow, totalRow, lastHitRow, scopeRow)
}

fun buildHomeFeedAnonymizerStatsUiModel(
    snapshot: HomeFeedAnonymizerStatsSnapshot,
    enabled: Boolean
): HomeFeedAnonymizerStatsUiModel {
    val stateSummary = if (enabled) "运行中" else "未启用"
    val lastHitSummary = snapshot.lastHitHost ?: "暂无命中"
    val lastHitAtMs = snapshot.lastHitAtMs
    val lastHitFullContent = if (lastHitAtMs == null) {
        "最近命中：暂无。启用后刷新首页推荐，命中 Web 首页推荐接口时会记录。"
    } else {
        buildString {
            append("最近命中：")
            append(formatHomeFeedAnonymizerTime(lastHitAtMs))
            append("\nHost：")
            append(snapshot.lastHitHost.orEmpty())
            append("\nPath：")
            append(snapshot.lastHitEncodedPath.orEmpty())
        }
    }

    return HomeFeedAnonymizerStatsUiModel(
        stateRow = HomeFeedAnonymizerInfoRow(
            label = "状态",
            summary = stateSummary,
            fullContent = "状态：$stateSummary。插件只影响 Web 首页推荐接口，会保留匿名访客标识并移除登录 Cookie，不影响播放、评论、动态、收藏和移动端推荐流。",
            maxLines = 1
        ),
        totalRow = HomeFeedAnonymizerInfoRow(
            label = "匿名化请求",
            summary = "${snapshot.totalHits} 次",
            fullContent = "匿名化请求：${snapshot.totalHits} 次。本统计仅记录本机命中次数，不记录账号、Cookie、视频内容或推荐结果。",
            maxLines = 1
        ),
        lastHitRow = HomeFeedAnonymizerInfoRow(
            label = "最近命中",
            summary = lastHitSummary,
            fullContent = lastHitFullContent,
            maxLines = 1
        ),
        scopeRow = HomeFeedAnonymizerInfoRow(
            label = "影响范围",
            summary = "仅 Web 首页推荐接口",
            fullContent = "影响范围：仅 $HOME_FEED_ANONYMIZER_WEB_ENDPOINT。启用后该接口请求使用本次启动的匿名 buvid3，不携带登录 Cookie，其他接口保持原登录态。频繁刷新可能触发访客侧限流；首页异常时请先关闭插件验证。",
            maxLines = 2
        )
    )
}

fun buildHomeFeedAnonymizerCreditRows(): List<HomeFeedAnonymizerInfoRow> {
    return listOf(
        HomeFeedAnonymizerInfoRow(
            label = "原作者",
            summary = "wangdaodao",
            fullContent = "原作者：wangdaodao。TabulaBili 原项目提出了清理 B 站首页推荐 Cookie 的思路。\nhttps://github.com/wangdaodaodao/TabulaBili",
            maxLines = 1
        ),
        HomeFeedAnonymizerInfoRow(
            label = "Plus 改版",
            summary = "tjsky",
            fullContent = "Plus 改版：tjsky。TabulaBili-Plus 提供了更完整的说明和扩展分发版本。\nhttps://github.com/tjsky/TabulaBili",
            maxLines = 1
        )
    )
}

private fun formatHomeFeedAnonymizerTime(epochMs: Long): String {
    return Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(HomeFeedAnonymizerTimeFormatter)
}
