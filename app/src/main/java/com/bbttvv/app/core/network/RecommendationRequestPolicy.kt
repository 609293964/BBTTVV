package com.bbttvv.app.core.network

internal fun recommendationCookieHeader(
    host: String, path: String, anonymous: Boolean,
    sessData: String, csrf: String, buvid3: String,
): String {
    if (host != "api.bilibili.com" || path != "/x/web-interface/wbi/index/top/feed/rcmd") return ""
    fun valid(value: String) = value.isNotBlank() && value.none { it == ';' || it == '\r' || it == '\n' }
    return buildList {
        if (valid(buvid3)) add("buvid3=$buvid3")
        if (!anonymous && valid(sessData)) add("SESSDATA=$sessData")
        if (!anonymous && valid(csrf)) add("bili_jct=$csrf")
    }.joinToString("; ")
}
