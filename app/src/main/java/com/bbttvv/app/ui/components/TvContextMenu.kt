package com.bbttvv.app.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

@Immutable
internal data class TvContextMenuAction(
    val text: String,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalComposeUiApi::class)
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
    val dialogFocusRequester = rememberTvDialogFocusTrap()

    BackHandler {
        onDismissRequest()
    }

    LaunchedEffect(actions.size) {
        withFrameNanos { }
        if (!runCatching { firstFocusRequester.requestFocus() }.getOrDefault(false)) {
            runCatching { dialogFocusRequester.requestFocus() }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
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
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth(0.42f)
                    .widthIn(min = 320.dp, max = 420.dp)
                    .focusRequester(dialogFocusRequester)
                    .focusGroup()
                    .focusProperties {
                        onExit = { cancelFocusChange() }
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xF21A2028))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(24.dp),
                    )
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "更多操作",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = "选择对当前视频的处理方式",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    actions.forEachIndexed { index, action ->
                        TvContextMenuButton(
                            text = action.text,
                            icon = action.icon(),
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
        }
    }
}

private fun isTvConfirmKey(keyCode: Int): Boolean {
    return keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
}

private fun TvContextMenuAction.icon(): ImageVector {
    return when {
        text.contains("稍后") -> Icons.Outlined.DateRange
        else -> Icons.Outlined.Info
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvContextMenuButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.White.copy(alpha = 0.08f)
        },
        animationSpec = tween(durationMillis = 150),
        label = "TvContextMenuButtonBackground",
    )
    val textColor by animateColorAsState(
        targetValue = if (isFocused) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 150),
        label = "TvContextMenuButtonText",
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.035f else 1f,
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
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shape = shape,
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.86f)),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None, Glow.None),
    ) {
        Row(
            modifier = Modifier
                .height(52.dp)
                .background(backgroundColor, shape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isFocused) {
                            Color.White.copy(alpha = 0.20f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(17.dp),
                )
            }
            Text(
                text = text,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
