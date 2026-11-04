package com.bbttvv.app.core.player

import com.bbttvv.app.core.store.player.DanmakuSettings

internal data class DanmakuFilterPreferences(
    val followBiliShield: Boolean,
    val aiShieldEnabled: Boolean,
    val aiShieldLevel: Int,
    val allowScroll: Boolean,
    val allowTop: Boolean,
    val allowBottom: Boolean,
    val allowColor: Boolean,
    val allowSpecial: Boolean,
)

internal fun DanmakuSettings.filterPreferences() = DanmakuFilterPreferences(
    followBiliShield = followBiliShield,
    aiShieldEnabled = aiShieldEnabled,
    aiShieldLevel = aiShieldLevel,
    allowScroll = allowScroll,
    allowTop = allowTop,
    allowBottom = allowBottom,
    allowColor = allowColor,
    allowSpecial = allowSpecial,
)
