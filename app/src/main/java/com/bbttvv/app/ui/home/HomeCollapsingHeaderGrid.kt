package com.bbttvv.app.ui.home

import android.os.SystemClock
import android.util.Log
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
import com.bbttvv.app.BuildConfig
import com.bbttvv.app.ui.components.AppTopLevelTab

internal class HomeCollapsingHeaderState {
    var collapseOffsetPx by mutableIntStateOf(0)
        private set

    private var scrollOffsetPx by mutableIntStateOf(0)
    private var suppressLowScrollOffsetsUntilMs: Long = 0L

    fun updateScrollOffset(
        scrollOffsetPx: Int,
        totalHeaderHeightPx: Int,
    ) {
        val nextScrollOffsetPx = scrollOffsetPx.coerceAtLeast(0)
        if (shouldIgnoreTransientZeroOffset(nextScrollOffsetPx, totalHeaderHeightPx)) {
            suppressLowScrollOffsetsUntilMs = SystemClock.uptimeMillis() + TransientLowOffsetSuppressMs
            logHomeFocus {
                "ignore transient zero scrollOffset: previous=${this.scrollOffsetPx} collapse=$collapseOffsetPx totalHeader=$totalHeaderHeightPx"
            }
            return
        }
        if (shouldIgnoreSuppressedLowOffset(nextScrollOffsetPx, totalHeaderHeightPx)) {
            return
        }
        if (nextScrollOffsetPx >= totalHeaderHeightPx.coerceAtLeast(0)) {
            suppressLowScrollOffsetsUntilMs = 0L
        }
        this.scrollOffsetPx = nextScrollOffsetPx
        val newCollapse = nextScrollOffsetPx.coerceIn(0, totalHeaderHeightPx.coerceAtLeast(0))
        if (collapseOffsetPx != newCollapse) {
            logHomeFocus { "collapseOffsetPx: $collapseOffsetPx -> $newCollapse  scroll=$nextScrollOffsetPx totalHeader=$totalHeaderHeightPx" }
        }
        collapseOffsetPx = newCollapse
    }

    fun reset() {
        suppressLowScrollOffsetsUntilMs = 0L
        scrollOffsetPx = 0
        collapseOffsetPx = 0
    }

    fun updateHeaderHeight(totalHeaderHeightPx: Int) {
        collapseOffsetPx = scrollOffsetPx.coerceIn(0, totalHeaderHeightPx.coerceAtLeast(0))
    }

    private fun shouldIgnoreTransientZeroOffset(
        nextScrollOffsetPx: Int,
        totalHeaderHeightPx: Int,
    ): Boolean {
        val headerHeight = totalHeaderHeightPx.coerceAtLeast(0)
        return nextScrollOffsetPx == 0 &&
            headerHeight > 0 &&
            scrollOffsetPx > headerHeight &&
            collapseOffsetPx >= headerHeight
    }

    private fun shouldIgnoreSuppressedLowOffset(
        nextScrollOffsetPx: Int,
        totalHeaderHeightPx: Int,
    ): Boolean {
        val headerHeight = totalHeaderHeightPx.coerceAtLeast(0)
        return headerHeight > 0 &&
            suppressLowScrollOffsetsUntilMs > 0L &&
            nextScrollOffsetPx in 1 until headerHeight &&
            collapseOffsetPx >= headerHeight &&
            SystemClock.uptimeMillis() <= suppressLowScrollOffsetsUntilMs
    }

    private companion object {
        private const val TransientLowOffsetSuppressMs = 320L
    }
}

private inline fun logHomeFocus(message: () -> String) {
    if (BuildConfig.DEBUG) {
        Log.d("HomeFocus", message())
    }
}

internal class HomeTabCollapsingHeaderStates {
    private val states = LinkedHashMap<AppTopLevelTab, HomeCollapsingHeaderState>()

    fun stateFor(tab: AppTopLevelTab): HomeCollapsingHeaderState {
        return states.getOrPut(tab) { HomeCollapsingHeaderState() }
    }

    fun retainVisibleTabs(visibleTabs: Set<AppTopLevelTab>) {
        val iterator = states.keys.iterator()
        while (iterator.hasNext()) {
            if (iterator.next() !in visibleTabs) {
                iterator.remove()
            }
        }
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
    collapseEnabled: Boolean = true,
    localHeader: (@Composable () -> Unit)? = null,
    grid: @Composable (topPadding: Dp, onScrollOffset: (Int) -> Unit) -> Unit,
) {
    var localHeaderHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val totalHeaderHeightPx = (topBarHeightPx + localHeaderHeightPx).coerceAtLeast(0)
    val headerTopPadding = with(density) { totalHeaderHeightPx.toDp() }
    val collapseOffsetPx = if (collapseEnabled) {
        state.collapseOffsetPx.coerceIn(0, totalHeaderHeightPx)
    } else {
        0
    }
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
                // Keep the stored offset fresh even while rendering the header expanded.
                // Detail return focus restore can enable collapse after the RecyclerView
                // has already reported this same offset.
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
