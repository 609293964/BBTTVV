package com.bbttvv.app.core.plugin

import com.bbttvv.app.data.model.response.VideoItem

enum class RecommendationMode {
    RELAX,
    LEARN
}

data class RecommendationCreatorSignal(
    val mid: Long,
    val name: String,
    val score: Double,
    val watchCount: Int
)

data class RecommendationFeedbackSignals(
    val consumedBvids: Set<String> = emptySet(),
    val dislikedBvids: Set<String> = emptySet(),
    val dislikedCreatorMids: Set<Long> = emptySet(),
    val dislikedKeywords: Set<String> = emptySet()
)

data class RecommendationSceneSignals(
    val eyeCareNightActive: Boolean,
    val nowEpochSec: Long = System.currentTimeMillis() / 1000L
)

data class RecommendationRequest(
    val candidateVideos: List<VideoItem>,
    val historyVideos: List<VideoItem>,
    val creatorSignals: List<RecommendationCreatorSignal> = emptyList(),
    val feedbackSignals: RecommendationFeedbackSignals = RecommendationFeedbackSignals(),
    val sceneSignals: RecommendationSceneSignals = RecommendationSceneSignals(eyeCareNightActive = false),
    val mode: RecommendationMode,
    val queueLimit: Int,
    val groupLimit: Int
)

data class RecommendedVideo(
    val video: VideoItem,
    val score: Double,
    val confidence: Float,
    val explanation: String,
    val actions: List<RecommendationAction> = emptyList()
)

data class RecommendationAction(
    val id: String,
    val label: String,
    val targetBvid: String? = null
)

data class RecommendationGroup(
    val id: String,
    val title: String,
    val items: List<RecommendationGroupItem>
)

data class RecommendationGroupItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val score: Double? = null
)

data class RecommendationResult(
    val sourcePluginId: String,
    val mode: RecommendationMode,
    val items: List<RecommendedVideo>,
    val groups: List<RecommendationGroup> = emptyList(),
    val historySampleCount: Int = 0,
    val sceneSignals: RecommendationSceneSignals = RecommendationSceneSignals(eyeCareNightActive = false),
    val generatedAt: Long = System.currentTimeMillis()
)

interface RecommendationPluginApi : Plugin {
    override val capabilityManifest: PluginCapabilityManifest

    fun buildRecommendations(request: RecommendationRequest): RecommendationResult
}
