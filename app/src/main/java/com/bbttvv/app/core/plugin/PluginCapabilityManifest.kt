package com.bbttvv.app.core.plugin

import kotlinx.serialization.Serializable

/**
 * 插件能力枚举
 *
 * 声明插件可请求的系统能力，用于授权验证：
 * - PLAYER_STATE: 读取播放器状态
 * - PLAYER_CONTROL: 控制播放器行为
 * - DANMAKU_STREAM: 读取弹幕流
 * - DANMAKU_MUTATION: 修改弹幕（过滤/样式）
 * - PLAYBACK_CDN: 重写播放 CDN 候选 URL
 * - RECOMMENDATION_CANDIDATES: 构建推荐队列
 * - LOCAL_HISTORY_READ: 读取本地观看历史
 * - LOCAL_FEEDBACK_READ: 读取本地反馈数据
 * - NETWORK: 发起网络请求
 * - PLUGIN_STORAGE: 使用插件专属存储
 */
@Serializable
enum class PluginCapability {
    PLAYER_STATE,
    PLAYER_CONTROL,
    DANMAKU_STREAM,
    DANMAKU_MUTATION,
    PLAYBACK_CDN,
    RECOMMENDATION_CANDIDATES,
    LOCAL_HISTORY_READ,
    LOCAL_FEEDBACK_READ,
    NETWORK,
    PLUGIN_STORAGE
}

/**
 * 插件能力声明清单
 *
 * 每个插件必须提供此清单，声明所需的系统能力。
 * 通过 resolvePluginCapabilityGrants() 与用户审批的能力取交集进行授权验证。
 */
@Serializable
data class PluginCapabilityManifest(
    val pluginId: String,
    val displayName: String,
    val version: String,
    val apiVersion: Int,
    val entryClassName: String,
    val capabilities: Set<PluginCapability>
)

data class PluginCapabilityGrants(
    val granted: Set<PluginCapability>
) {
    fun isGranted(capability: PluginCapability): Boolean = capability in granted
}

fun resolvePluginCapabilityGrants(
    manifest: PluginCapabilityManifest,
    userApprovedCapabilities: Set<PluginCapability>
): PluginCapabilityGrants {
    return PluginCapabilityGrants(
        granted = manifest.capabilities.intersect(userApprovedCapabilities)
    )
}

sealed interface RecommendationPluginAccessDecision {
    data object Granted : RecommendationPluginAccessDecision
    data class MissingCapabilities(
        val missing: Set<PluginCapability>
    ) : RecommendationPluginAccessDecision
}

fun validateRecommendationPluginAccess(
    grants: PluginCapabilityGrants
): RecommendationPluginAccessDecision {
    val required = setOf(PluginCapability.RECOMMENDATION_CANDIDATES)
    val missing = required.filterNot { grants.isGranted(it) }.toSet()
    return if (missing.isEmpty()) {
        RecommendationPluginAccessDecision.Granted
    } else {
        RecommendationPluginAccessDecision.MissingCapabilities(missing)
    }
}
