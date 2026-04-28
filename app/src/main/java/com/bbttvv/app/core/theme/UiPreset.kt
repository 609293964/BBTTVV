package com.bbttvv.app.core.theme

import androidx.compose.runtime.staticCompositionLocalOf

enum class UiPreset(val value: Int, val label: String) {
    IOS(0, "iOS"),
    MD3(1, "安卓原生");

    companion object {
        fun fromValue(value: Int): UiPreset = entries.find { it.value == value } ?: IOS
    }
}

data class UiRenderingProfile(
    val useMaterialChrome: Boolean,
    val useMaterialMotion: Boolean,
    val useMaterialIcons: Boolean
)

fun resolveUiRenderingProfile(preset: UiPreset): UiRenderingProfile {
    return when (preset) {
        UiPreset.IOS -> UiRenderingProfile(
            useMaterialChrome = false,
            useMaterialMotion = false,
            useMaterialIcons = false
        )

        UiPreset.MD3 -> UiRenderingProfile(
            useMaterialChrome = true,
            useMaterialMotion = true,
            useMaterialIcons = true
        )
    }
}

val LocalUiPreset = staticCompositionLocalOf { UiPreset.IOS }
val LocalDynamicColorActive = staticCompositionLocalOf { false }

