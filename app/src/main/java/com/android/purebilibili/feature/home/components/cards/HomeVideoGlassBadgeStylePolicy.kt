package com.android.purebilibili.feature.home.components.cards

internal enum class HomeVideoBadgeStyle {
    GLASS,
    PLAIN
}

internal data class HomeVideoGlassBadgeStylePolicy(
    val coverStyle: HomeVideoBadgeStyle,
    val infoStyle: HomeVideoBadgeStyle
)

internal fun resolveHomeVideoGlassBadgeStylePolicy(
    showCoverGlassBadges: Boolean,
    showInfoGlassBadges: Boolean
): HomeVideoGlassBadgeStylePolicy = HomeVideoGlassBadgeStylePolicy(
    coverStyle = if (showCoverGlassBadges) HomeVideoBadgeStyle.GLASS else HomeVideoBadgeStyle.PLAIN,
    infoStyle = if (showInfoGlassBadges) HomeVideoBadgeStyle.GLASS else HomeVideoBadgeStyle.PLAIN
)
