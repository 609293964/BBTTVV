package com.bbttvv.app.data.model.response

import kotlinx.serialization.Serializable

// --- 直播弹幕表情 Response ---
@Serializable
data class LiveEmoticonResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: List<LiveDanmakuEmoticon>? = null // data 字段实际上是一个包含 pkg_id 等信息的复杂结构，但简化处理直接解析需要的列表
) {
    // 实际 API 返回结构可能较复杂 (data -> data -> [pkg] -> [emotes])
    // 我们定义一个简化的辅助结构来接收 data
    @Serializable
    data class Data(
        val data: List<EmoticonPkg>? = null
    )
}

// 对应 API: https://api.live.bilibili.com/xlive/web-ucenter/v2/emoticon/GetEmoticons
// 实际返回根对象 code, message, data(Object)
// data(Object) -> data(Array) -> Pkg -> emoticons(Array)
// 为了方便解析，建议使用更精确的嵌套结构

@Serializable
data class LiveEmoticonRootResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: LiveEmoticonData? = null
)

@Serializable
data class LiveEmoticonData(
    val data: List<EmoticonPkg>? = null
)

@Serializable
data class EmoticonPkg(
    val pkg_id: Int = 0,
    val pkg_name: String = "",
    val emoticons: List<LiveDanmakuEmoticon>? = null
)

@Serializable
data class LiveDanmakuEmoticon(
    val emoji: String = "",
    val url: String = "",
    val des: String = ""
)

