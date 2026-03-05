package com.android.purebilibili.feature.home.components.cards

internal data class VideoCardScrollLiteVisualPolicy(
    val coverShadowElevationDp: Float,
    val showCoverGradientMask: Boolean,
    val showHistoryProgressBar: Boolean,
    val showCompactStatsOnCover: Boolean,
    val showSecondaryStatsRow: Boolean
)

internal fun resolveVideoCardScrollLiteVisualPolicy(
    scrollLiteModeEnabled: Boolean,
    compactStatsOnCover: Boolean
): VideoCardScrollLiteVisualPolicy {
    if (scrollLiteModeEnabled) {
        return VideoCardScrollLiteVisualPolicy(
            coverShadowElevationDp = 1f,
            showCoverGradientMask = false,
            showHistoryProgressBar = false,
            showCompactStatsOnCover = compactStatsOnCover,
            showSecondaryStatsRow = !compactStatsOnCover
        )
    }

    return VideoCardScrollLiteVisualPolicy(
        coverShadowElevationDp = 1f,
        showCoverGradientMask = true,
        showHistoryProgressBar = true,
        showCompactStatsOnCover = compactStatsOnCover,
        showSecondaryStatsRow = !compactStatsOnCover
    )
}

internal data class StoryVideoCardScrollLiteVisualPolicy(
    val coverShadowElevationDp: Float,
    val showSecondaryStatsRow: Boolean
)

internal fun resolveStoryVideoCardScrollLiteVisualPolicy(
    scrollLiteModeEnabled: Boolean
): StoryVideoCardScrollLiteVisualPolicy {
    return if (scrollLiteModeEnabled) {
        StoryVideoCardScrollLiteVisualPolicy(
            coverShadowElevationDp = 3f,
            showSecondaryStatsRow = true
        )
    } else {
        StoryVideoCardScrollLiteVisualPolicy(
            coverShadowElevationDp = 6f,
            showSecondaryStatsRow = true
        )
    }
}
