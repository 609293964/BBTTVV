package com.android.purebilibili.feature.list

import com.android.purebilibili.data.model.response.HistoryItem

internal fun filterAppendableHistoryItems(
    currentRenderKeys: Set<String>,
    incomingItems: List<HistoryItem>
): List<HistoryItem> {
    val seenKeys = currentRenderKeys
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toMutableSet()

    return incomingItems.filter { item ->
        seenKeys.add(resolveHistoryRenderKey(item))
    }
}
