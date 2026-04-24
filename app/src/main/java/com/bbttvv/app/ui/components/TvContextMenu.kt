package com.bbttvv.app.ui.components

import android.view.View
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.lang.ref.WeakReference

@Immutable
internal data class TvContextMenuAction(
    val text: String,
    val onClick: () -> Unit,
)

@Composable
internal fun TvContextMenu(
    actions: List<TvContextMenuAction>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    suppressConfirmKey: Boolean = false,
    onSuppressConfirmKeyConsumed: () -> Unit = {},
) {
    if (actions.isEmpty()) return

    val firstFocusRequester = remember { FocusRequester() }
    val ownerView = LocalView.current
    val focusReturn = remember(ownerView) { ContextMenuFocusReturn() }

    BackHandler {
        onDismissRequest()
    }

    DisposableEffect(ownerView) {
        val ownerRoot = ownerView.rootView ?: ownerView
        focusReturn.capture(ownerRoot.findFocus())
        onDispose {
            focusReturn.restoreAndClear(fallback = ownerView)
        }
    }

    LaunchedEffect(actions.size) {
        withFrameNanos { }
        runCatching { firstFocusRequester.requestFocus() }
    }

    Column(
        modifier = modifier
            .widthIn(min = 126.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.76f))
            .focusGroup()
            .onPreviewKeyEvent { keyEvent ->
                val event = keyEvent.nativeKeyEvent
                if (isTvConfirmKey(event.keyCode) && suppressConfirmKey) {
                    if (event.action == AndroidKeyEvent.ACTION_UP) {
                        onSuppressConfirmKeyConsumed()
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.action != AndroidKeyEvent.ACTION_DOWN) {
                    return@onPreviewKeyEvent false
                }

                when (event.keyCode) {
                    AndroidKeyEvent.KEYCODE_BACK,
                    AndroidKeyEvent.KEYCODE_ESCAPE,
                    AndroidKeyEvent.KEYCODE_MENU,
                    AndroidKeyEvent.KEYCODE_BUTTON_B,
                    -> {
                        onDismissRequest()
                        true
                    }

                    else -> false
                }
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        actions.forEachIndexed { index, action ->
            TvContextMenuButton(
                text = action.text,
                onClick = action.onClick,
                modifier = if (index == 0) {
                    Modifier.focusRequester(firstFocusRequester)
                } else {
                    Modifier
                },
            )
        }
    }
}

private fun isTvConfirmKey(keyCode: Int): Boolean {
    return keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
}

private class ContextMenuFocusReturn {
    private var targetRef: WeakReference<View>? = null

    fun capture(view: View?) {
        targetRef = view?.let(::WeakReference)
    }

    fun restoreAndClear(fallback: View? = null): Boolean {
        val desired = targetRef?.get()
        targetRef = null
        return restore(desired = desired, fallback = fallback)
    }

    private fun restore(
        desired: View?,
        fallback: View?,
    ): Boolean {
        val candidates = buildList {
            desired?.let(::add)
            fallback?.takeIf { it !== desired }?.let(::add)
        }
        for (candidate in candidates) {
            if (!candidate.isRestorableContextMenuFocusTarget()) continue
            if (candidate.requestFocus()) return true
        }
        for (candidate in candidates) {
            if (!candidate.isRestorableContextMenuFocusTarget()) continue
            candidate.post {
                if (candidate.isRestorableContextMenuFocusTarget()) {
                    candidate.requestFocus()
                }
            }
            return true
        }
        return false
    }
}

private fun View.isRestorableContextMenuFocusTarget(): Boolean {
    return isAttachedToWindow && isShown && isEnabled && isFocusable
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvContextMenuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else Color.White.copy(alpha = 0.16f),
        animationSpec = tween(durationMillis = 150),
        label = "TvContextMenuButtonBackground",
    )
    val textColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF111418) else Color.White.copy(alpha = 0.88f),
        animationSpec = tween(durationMillis = 150),
        label = "TvContextMenuButtonText",
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "TvContextMenuButtonScale",
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.hasFocus }
            .fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = shape, focusedShape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
        ),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None, Glow.None),
    ) {
        Box(
            modifier = Modifier
                .background(backgroundColor, shape)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
