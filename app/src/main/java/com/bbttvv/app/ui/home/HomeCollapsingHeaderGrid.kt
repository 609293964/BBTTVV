package com.bbttvv.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex

internal class HomeCollapsingHeaderState {
    var collapseOffsetPx by mutableIntStateOf(0)
        private set

    private var scrollOffsetPx by mutableIntStateOf(0)

    fun updateScrollOffset(
        scrollOffsetPx: Int,
        totalHeaderHeightPx: Int,
    ) {
        this.scrollOffsetPx = scrollOffsetPx.coerceAtLeast(0)
        collapseOffsetPx = this.scrollOffsetPx.coerceIn(0, totalHeaderHeightPx.coerceAtLeast(0))
    }

    fun reset() {
        scrollOffsetPx = 0
        collapseOffsetPx = 0
    }

    fun updateHeaderHeight(totalHeaderHeightPx: Int) {
        collapseOffsetPx = scrollOffsetPx.coerceIn(0, totalHeaderHeightPx.coerceAtLeast(0))
    }
}

@Composable
internal fun rememberHomeCollapsingHeaderState(): HomeCollapsingHeaderState {
    return remember { HomeCollapsingHeaderState() }
}

@Composable
internal fun HomeCollapsingHeaderGrid(
    topBarHeightPx: Int,
    state: HomeCollapsingHeaderState,
    modifier: Modifier = Modifier,
    localHeader: (@Composable () -> Unit)? = null,
    grid: @Composable (topPadding: Dp, onScrollOffset: (Int) -> Unit) -> Unit,
) {
    var localHeaderHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val totalHeaderHeightPx = (topBarHeightPx + localHeaderHeightPx).coerceAtLeast(0)
    val headerTopPadding = with(density) { totalHeaderHeightPx.toDp() }
    val collapseOffsetPx = state.collapseOffsetPx.coerceIn(0, totalHeaderHeightPx)
    val canShowGrid = localHeader == null || localHeaderHeightPx > 0

    LaunchedEffect(localHeader == null) {
        if (localHeader == null) {
            localHeaderHeightPx = 0
        }
    }

    LaunchedEffect(totalHeaderHeightPx) {
        state.updateHeaderHeight(totalHeaderHeightPx)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        if (canShowGrid) {
            // Avoid painting RecyclerView for one frame with only TopBar padding while
            // the local TV header is still being measured during tab switches.
            grid(headerTopPadding) { scrollOffsetPx ->
                state.updateScrollOffset(
                    scrollOffsetPx = scrollOffsetPx,
                    totalHeaderHeightPx = totalHeaderHeightPx,
                )
            }
        }

        if (localHeader != null) {
            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .onSizeChanged { size ->
                        localHeaderHeightPx = size.height
                        state.updateHeaderHeight(topBarHeightPx + size.height)
                    }
                    .graphicsLayer {
                        translationY = (topBarHeightPx - collapseOffsetPx).toFloat()
                    }
            ) {
                localHeader()
            }
        }
    }
}
