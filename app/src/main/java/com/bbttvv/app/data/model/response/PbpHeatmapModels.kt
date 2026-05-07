package com.bbttvv.app.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PbpHeatmapResponse(
    @SerialName("step_sec")
    val stepSec: Double = 0.0,
    @SerialName("events")
    val events: PbpHeatmapEvents = PbpHeatmapEvents(),
    @SerialName("debug")
    val debug: String = "",
)

@Serializable
data class PbpHeatmapEvents(
    @SerialName("default")
    val default: List<Double> = emptyList(),
)
