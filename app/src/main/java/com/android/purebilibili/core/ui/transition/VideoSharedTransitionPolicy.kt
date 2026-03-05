package com.android.purebilibili.core.ui.transition

internal enum class VideoSharedTransitionProfile {
    COVER_ONLY,
    COVER_AND_METADATA
}

internal const val VIDEO_SHARED_COVER_ASPECT_RATIO = 16f / 10f

internal fun resolveVideoSharedTransitionProfile(): VideoSharedTransitionProfile {
    // Prefer a unified return choreography: cover + key metadata move back together.
    return VideoSharedTransitionProfile.COVER_AND_METADATA
}

internal fun shouldEnableVideoCoverSharedTransition(
    transitionEnabled: Boolean,
    hasSharedTransitionScope: Boolean,
    hasAnimatedVisibilityScope: Boolean
): Boolean {
    return transitionEnabled &&
        hasSharedTransitionScope &&
        hasAnimatedVisibilityScope
}

internal fun shouldEnableVideoMetadataSharedTransition(
    coverSharedEnabled: Boolean,
    isQuickReturnLimited: Boolean,
    profile: VideoSharedTransitionProfile = resolveVideoSharedTransitionProfile()
): Boolean {
    if (!coverSharedEnabled) return false
    // Keep metadata linked during quick return to avoid cover-only snapback.
    if (isQuickReturnLimited && profile == VideoSharedTransitionProfile.COVER_ONLY) return false
    return profile == VideoSharedTransitionProfile.COVER_AND_METADATA
}
