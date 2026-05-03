package com.bbttvv.app.core.player

enum class AudioBalanceLevel(
    val prefValue: String,
    val label: String,
) {
    Off("off", "关"),
    Low("low", "低"),
    Medium("medium", "中"),
    High("high", "高");

    companion object {
        val ordered: List<AudioBalanceLevel> = entries

        fun fromPrefValue(value: String): AudioBalanceLevel {
            return when (value.trim()) {
                Low.prefValue -> Low
                Medium.prefValue -> Medium
                High.prefValue -> High
                else -> Off
            }
        }
    }
}
