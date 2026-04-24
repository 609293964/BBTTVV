package com.bbttvv.app.feature.video.danmaku

import com.bytedance.danmaku.render.engine.render.draw.text.TextData

class WeightedTextData : TextData() {
    var danmakuId: Long = 0L
    var userHash: String = ""
    var weight: Int = 0
    var pool: Int = 0
}
