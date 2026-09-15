package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.core.store.player.DanmakuSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

internal fun danmakuMaskRequests(
    media: Flow<Pair<String, Long>?>,
    settings: Flow<DanmakuSettings>,
): Flow<Pair<String, Long>?> = combine(media, settings) { currentMedia, currentSettings ->
    currentMedia.takeIf { currentSettings.smartMaskEnabled }
}.distinctUntilChanged()
