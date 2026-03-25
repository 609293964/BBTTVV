package com.android.purebilibili.feature.article

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.resolveBottomSafeAreaPadding

internal fun resolveArticleDetailBottomPadding(
    navigationBarsBottom: Dp,
    extraBottomPadding: Dp = 24.dp
): Dp {
    return resolveBottomSafeAreaPadding(
        navigationBarsBottom = navigationBarsBottom,
        extraBottomPadding = extraBottomPadding
    )
}
