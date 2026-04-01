package com.android.purebilibili.feature.video.playback.policy

sealed class PlaybackQualityMode {
    abstract val lockedQualityId: Int?

    data object AUTO : PlaybackQualityMode() {
        override val lockedQualityId: Int? = null
    }

    data class LOCKED(
        val qualityId: Int
    ) : PlaybackQualityMode() {
        override val lockedQualityId: Int = qualityId
    }

    companion object {
        fun fromQualityId(qualityId: Int): PlaybackQualityMode {
            return if (qualityId > 0) LOCKED(qualityId) else AUTO
        }
    }
}
