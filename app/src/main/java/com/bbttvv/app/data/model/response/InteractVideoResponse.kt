package com.bbttvv.app.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InteractEdgeInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 0,
    val data: InteractEdgeInfoData? = null
)

@Serializable
data class InteractEdgeInfoData(
    val title: String = "",
    @SerialName("edge_id")
    val edgeId: Long = 0,
    @SerialName("story_list")
    val storyList: List<InteractStoryNode> = emptyList(),
    val edges: InteractEdgeNode? = null,
    val preload: InteractPreloadInfo? = null,
    @SerialName("hidden_vars")
    val hiddenVars: List<InteractHiddenVar> = emptyList(),
    @SerialName("no_tutorial")
    val noTutorial: Int = 0,
    @SerialName("no_backtracking")
    val noBacktracking: Int = 0,
    @SerialName("no_evaluation")
    val noEvaluation: Int = 0,
    @SerialName("is_leaf")
    val isLeaf: Int = 0
)

@Serializable
data class InteractStoryNode(
    @SerialName("node_id")
    val nodeId: Long = 0,
    @SerialName("edge_id")
    val edgeId: Long = 0,
    val title: String = "",
    val cid: Long = 0,
    @SerialName("start_pos")
    val startPos: Long = 0,
    @SerialName("is_current")
    val isCurrent: Int = 0
)

@Serializable
data class InteractEdgeNode(
    val dimension: InteractDimension? = null,
    val questions: List<InteractQuestion> = emptyList(),
    val skin: InteractQuestionSkin? = null
)

@Serializable
data class InteractQuestion(
    val id: Long = 0,
    val type: Int = 0,
    @SerialName("start_time_r")
    val startTimeR: Int = 0,
    val duration: Int = -1,
    @SerialName("pause_video")
    val pauseVideo: Int = 0,
    val title: String = "",
    @SerialName("fade_in_time")
    val fadeInTime: Int = 0,
    @SerialName("fade_out_time")
    val fadeOutTime: Int = 0,
    val choices: List<InteractChoice> = emptyList()
)

@Serializable
data class InteractChoice(
    val id: Long = 0,
    @SerialName("platform_action")
    val platformAction: String = "",
    @SerialName("native_action")
    val nativeAction: String = "",
    val condition: String = "",
    val cid: Long = 0,
    val x: Int = 0,
    val y: Int = 0,
    @SerialName("text_align")
    val textAlign: Int = 0,
    val option: String = "",
    @SerialName("is_default")
    val isDefault: Int = 0,
    @SerialName("is_hidden")
    val isHidden: Int = 0
)

@Serializable
data class InteractDimension(
    val width: Int = 0,
    val height: Int = 0,
    val rotate: Int = 0,
    val sar: String = ""
)

@Serializable
data class InteractQuestionSkin(
    @SerialName("choice_image")
    val choiceImage: String = "",
    @SerialName("title_text_color")
    val titleTextColor: String = "",
    @SerialName("progressbar_color")
    val progressBarColor: String = ""
)

@Serializable
data class InteractPreloadInfo(
    val video: List<InteractPreloadVideo> = emptyList()
)

@Serializable
data class InteractPreloadVideo(
    val aid: Long = 0,
    val cid: Long = 0
)

@Serializable
data class InteractHiddenVar(
    val value: Double = 0.0,
    val id: String = "",
    @SerialName("id_v2")
    val idV2: String = "",
    val type: Int = 0
)

