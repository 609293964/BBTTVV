package com.bbttvv.app.ui.home

import androidx.compose.runtime.Stable

import kotlin.math.max
import kotlin.math.min

@Stable
internal class TvGridDpadController<K : Any>(
    private val spanCountProvider: () -> Int,
) {
    private var focusedKey: K? = null
    private var focusedIndex: Int = -1
    private var focusedColumn: Int = -1
    private var pendingLoadMoreAnchor: PendingAnchor<K>? = null
    private var parkedForDataChange: Boolean = false

    fun onItemFocused(key: K, index: Int) {
        val spanCount = spanCountProvider().coerceAtLeast(1)
        focusedKey = key
        focusedIndex = index
        focusedColumn = index.floorMod(spanCount)
        pendingLoadMoreAnchor = null
        parkedForDataChange = false
    }

    fun prepareForLoadMore(keys: List<K>): Boolean {
        val index = focusedIndex.takeIf { it in keys.indices }
            ?: focusedKey?.let(keys::indexOf).takeIf { it != null && it >= 0 }
            ?: return false
        val spanCount = spanCountProvider().coerceAtLeast(1)
        pendingLoadMoreAnchor = PendingAnchor(
            key = keys[index],
            index = index,
            column = index.floorMod(spanCount),
            oldItemCount = keys.size,
        )
        parkedForDataChange = true
        return true
    }

    fun prepareForDataChange() {
        parkedForDataChange = true
    }

    fun consumeAppendFocusIndex(keys: List<K>): Int? {
        val pending = pendingLoadMoreAnchor ?: return null
        if (keys.size <= pending.oldItemCount) return null
        val spanCount = spanCountProvider().coerceAtLeast(1)
        val sameColumnNextRow = pending.index + spanCount
        val targetIndex = when {
            sameColumnNextRow in pending.oldItemCount until keys.size -> sameColumnNextRow
            else -> (pending.oldItemCount until keys.size)
                .firstOrNull { it.floorMod(spanCount) == pending.column }
                ?: pending.oldItemCount.takeIf { it in keys.indices }
        } ?: return null

        pendingLoadMoreAnchor = null
        parkedForDataChange = false
        focusedIndex = targetIndex
        focusedColumn = targetIndex.floorMod(spanCount)
        focusedKey = keys[targetIndex]
        return targetIndex
    }

    fun fallbackIndexAfterDataChange(keys: List<K>): Int? {
        if (!parkedForDataChange || keys.isEmpty()) return null
        val spanCount = spanCountProvider().coerceAtLeast(1)
        val keyedIndex = focusedKey?.let(keys::indexOf)?.takeIf { it >= 0 }
        val column = focusedColumn.takeIf { it in 0 until spanCount }
        val clampedIndex = focusedIndex.takeIf { it >= 0 }?.let { min(it, keys.lastIndex) }
        val targetIndex = keyedIndex
            ?: clampedIndex?.let { index ->
                column?.let { targetColumn ->
                    nearestIndexInColumn(
                        startIndex = index,
                        column = targetColumn,
                        spanCount = spanCount,
                        itemCount = keys.size,
                    )
                } ?: index
            }
            ?: 0

        parkedForDataChange = false
        focusedIndex = targetIndex
        focusedColumn = targetIndex.floorMod(spanCount)
        focusedKey = keys[targetIndex]
        return targetIndex
    }

    fun edgeFor(index: Int, itemCount: Int): DpadGridEdge? {
        val spanCount = spanCountProvider().coerceAtLeast(1)
        return DpadGridEdgePolicy.resolve(
            position = index,
            itemCount = itemCount,
            spanCount = spanCount,
        )
    }

    private data class PendingAnchor<K : Any>(
        val key: K,
        val index: Int,
        val column: Int,
        val oldItemCount: Int,
    )

    private companion object {
        fun nearestIndexInColumn(
            startIndex: Int,
            column: Int,
            spanCount: Int,
            itemCount: Int,
        ): Int {
            val safeStart = startIndex.coerceIn(0, max(0, itemCount - 1))
            return (safeStart downTo 0).firstOrNull { it.floorMod(spanCount) == column }
                ?: (safeStart until itemCount).firstOrNull { it.floorMod(spanCount) == column }
                ?: safeStart
        }

        fun Int.floorMod(divisor: Int): Int {
            return ((this % divisor) + divisor) % divisor
        }
    }
}
