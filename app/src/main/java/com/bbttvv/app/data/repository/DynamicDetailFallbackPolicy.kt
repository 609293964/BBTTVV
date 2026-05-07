package com.bbttvv.app.data.repository

import com.bbttvv.app.data.model.response.DynamicItem

internal fun shouldFallbackForDynamicDetail(item: DynamicItem): Boolean {
    val modules = item.modules
    val hasDescText = modules.module_dynamic?.desc?.text?.isNotBlank() == true
    val hasMajorContent = modules.module_dynamic?.major != null
    val hasOrig = item.orig != null

    // 可渲染内容都没有时，说明解析结构可能不兼容，应该走 fallback
    val hasRenderableContent = hasDescText || hasMajorContent || hasOrig
    return !hasRenderableContent
}

