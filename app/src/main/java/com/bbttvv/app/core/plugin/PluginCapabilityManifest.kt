package com.bbttvv.app.core.plugin

import kotlinx.serialization.Serializable

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
