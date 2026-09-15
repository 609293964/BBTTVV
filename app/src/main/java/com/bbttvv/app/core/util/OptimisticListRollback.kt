package com.bbttvv.app.core.util

/** Restores only the item owned by a failed optimistic mutation, without replacing newer list state. */
internal fun <T> restoreItemIfMissing(
    current: List<T>,
    item: T,
    originalIndex: Int,
    sameItem: (T) -> Boolean
): List<T> {
    if (current.any(sameItem)) return current
    val insertionIndex = originalIndex.coerceIn(0, current.size)
    return current.toMutableList().apply { add(insertionIndex, item) }
}
