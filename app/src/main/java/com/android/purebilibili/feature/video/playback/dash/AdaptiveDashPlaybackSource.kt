package com.android.purebilibili.feature.video.playback.dash

import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.feature.video.playback.policy.PlaybackQualityMode

data class AdaptiveDashPlaybackSource(
    val manifest: String,
    val videoTracks: List<DashVideo>,
    val audioTracks: List<DashAudio>,
    val playbackQualityMode: PlaybackQualityMode
)
