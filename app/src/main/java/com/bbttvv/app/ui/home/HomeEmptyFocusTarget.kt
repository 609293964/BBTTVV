package com.bbttvv.app.ui.home

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.focus.TvFocusSandboxAnchor
import com.bbttvv.app.ui.focus.rememberTvFocusAnchorState

@Composable
internal fun HomeEmptyFocusTarget(
    tab: AppTopLevelTab,
    focusCoordinator: HomeFocusCoordinator?,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onDpadUp: () -> Boolean = {
        focusCoordinator?.handleGridTopEdge(tab) == true
    },
    content: @Composable BoxScope.() -> Unit,
) {
    val anchorState = rememberTvFocusAnchorState()

    DisposableEffect(tab, focusCoordinator, isActive, anchorState) {
        val registration = if (isActive && focusCoordinator != null) {
            focusCoordinator.registerContentTarget(
                tab = tab,
                region = HomeFocusRegion.Grid,
                target = object : HomeFocusTarget {
                    override fun tryRequestFocus(): Boolean {
                        return anchorState.requestFocus()
                    }

                    override fun hasFocus(): Boolean {
                        return anchorState.hasFocus
                    }

                    override fun hasFocusOnRequestedTarget(): Boolean {
                        return anchorState.hasFocus
                    }
                },
            )
        } else {
            null
        }

        onDispose {
            registration?.unregister()
        }
    }

    TvFocusSandboxAnchor(
        state = anchorState,
        modifier = modifier,
        onDpadUp = onDpadUp,
        content = content,
    )
}
