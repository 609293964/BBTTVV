package com.bbttvv.app.feature.live

/** SC 浮层遵循直播弹幕开关，并提供独立关闭能力，避免遮挡全屏画面。 */
internal fun shouldShowLiveSuperChat(
    isDanmakuEnabled: Boolean,
    flashEnabled: Boolean,
): Boolean = isDanmakuEnabled && flashEnabled
