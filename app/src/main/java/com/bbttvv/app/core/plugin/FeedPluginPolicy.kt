package com.bbttvv.app.core.plugin

import kotlinx.coroutines.CancellationException

/** One failure disables only that plugin for the current batch; later plugins still run. */
internal fun <Item, Filter> filterFeedBatch(
    items: List<Item>, filters: List<Filter>,
    shouldShow: (Filter, Item) -> Boolean,
    onFailure: (Filter, Exception) -> Unit,
): List<Item> {
    val failed = mutableSetOf<Filter>()
    return items.filter { item ->
        filters.all { filter ->
            if (filter in failed) true else try { shouldShow(filter, item) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failed.add(filter); onFailure(filter, error); true }
        }
    }
}
