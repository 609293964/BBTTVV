package com.bbttvv.app.ui.home

import androidx.compose.ui.Modifier
import com.bbttvv.app.ui.input.onTvDpadKeyDown

internal fun Modifier.requestTopBarOnDpadUp(
    enabled: Boolean,
    requestTopBarFocus: () -> Unit
): Modifier {
    return onTvDpadKeyDown(
        enabled = enabled,
        onUp = {
            requestTopBarFocus()
            true
        },
    )
}
