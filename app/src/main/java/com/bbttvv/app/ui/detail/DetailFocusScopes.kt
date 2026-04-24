@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.bbttvv.app.ui.detail

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.abs

internal const val DetailContainerSizeTolerancePx = 2f

internal val DetailNoScrollBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        return 0f
    }
}

internal class DetailHorizontalOnlyBringIntoViewSpec(
    private val delegate: BringIntoViewSpec,
    private val horizontalContainerSizePx: Float
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        return if (abs(containerSize - horizontalContainerSizePx) <= DetailContainerSizeTolerancePx) {
            delegate.calculateScrollDistance(offset, size, containerSize)
        } else {
            0f
        }
    }
}

@Composable
internal fun DetailInitialFocusScrollScope(
    disableTvFocusPivot: Boolean,
    horizontalFocusContainerWidth: Dp?,
    content: @Composable () -> Unit
) {
    val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
    val density = LocalDensity.current
    val horizontalContainerSizePx = horizontalFocusContainerWidth?.let { width ->
        with(density) { width.toPx() }
    }
    val horizontalBringIntoViewSpec = remember(
        defaultBringIntoViewSpec,
        horizontalContainerSizePx
    ) {
        horizontalContainerSizePx?.let { containerSizePx ->
            DetailHorizontalOnlyBringIntoViewSpec(
                delegate = defaultBringIntoViewSpec,
                horizontalContainerSizePx = containerSizePx
            )
        }
    }
    val bringIntoViewSpec = when {
        disableTvFocusPivot -> DetailNoScrollBringIntoViewSpec
        horizontalBringIntoViewSpec != null -> horizontalBringIntoViewSpec
        else -> defaultBringIntoViewSpec
    }
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides bringIntoViewSpec,
        content = content
    )
}

@Composable
internal fun DetailStaticFocusArea(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides DetailNoScrollBringIntoViewSpec,
        content = content
    )
}

internal fun Modifier.detailHorizontalFocusRail(
    horizontalContainerWidth: Dp,
    onRailFocusChanged: (Boolean) -> Unit = {},
    onHorizontalRailFocusChanged: (Dp?) -> Unit
): Modifier = this
    .onPreviewKeyEvent { event ->
        if (event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN) {
            when (event.nativeKeyEvent.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    onHorizontalRailFocusChanged(horizontalContainerWidth)
                }

                AndroidKeyEvent.KEYCODE_DPAD_UP,
                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                    onHorizontalRailFocusChanged(null)
                }
            }
        }
        false
    }
    .onFocusChanged { focusState ->
        onRailFocusChanged(focusState.hasFocus)
        if (!focusState.hasFocus) {
            onHorizontalRailFocusChanged(null)
        }
    }
