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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
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
import com.bbttvv.app.ui.input.isTvConfirmKey
import com.bbttvv.app.ui.input.isTvModalDismissKey
import com.bbttvv.app.ui.theme.LocalIsLightTheme
import com.bbttvv.app.ui.theme.LocalTvOverlayPalette

@Immutable
internal data class TvContextMenuAction(
    val text: String,
    val supportingText: String? = null,
    val accentColor: Color? = null,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TvContextMenu(
    actions: List<TvContextMenuAction>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "视频操作",
    subtitle: String = "选择对这条视频的处理方式",
    suppressConfirmKey: Boolean = false,
    onSuppressConfirmKeyConsumed: () -> Unit = {},
) {
    if (actions.isEmpty()) return

    val firstFocusRequester = remember { FocusRequester() }
    val dialogFocusRequester = rememberTvDialogFocusTrap()
    val isLightTheme = LocalIsLightTheme.current
    val overlayPalette = LocalTvOverlayPalette.current

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
                .background(overlayPalette.scrim)
                .onPreviewKeyEvent { keyEvent ->
                    val event = keyEvent.nativeKeyEvent
                    if (isTvConfirmKey(event.keyCode) && suppressConfirmKey) {
                        if (event.action == AndroidKeyEvent.ACTION_UP) {
                            onSuppressConfirmKeyConsumed()
                        }
                        return@onPreviewKeyEvent true
                    }

                    if (isTvModalDismissKey(event.keyCode)) {
                        if (event.action == AndroidKeyEvent.ACTION_UP) {
                            onDismissRequest()
                        }
                        return@onPreviewKeyEvent true
                    }
                    false
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth(0.38f)
                    .widthIn(min = 340.dp, max = 460.dp)
                    .focusRequester(dialogFocusRequester)
                    .focusGroup()
                    .focusProperties {
                        onExit = { cancelFocusChange() }
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(overlayPalette.contextMenuContainer)
                    .border(
                        width = 1.dp,
                        color = overlayPalette.contextMenuBorder,
                        shape = RoundedCornerShape(24.dp),
                    )
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            text = subtitle,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    actions.forEachIndexed { index, action ->
                        TvContextMenuButton(
                            text = action.text,
                            supportingText = action.supportingText,
                            icon = action.resolvedIcon(),
                            accentColor = action.accentColor ?: action.defaultAccentColor(),
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

private fun TvContextMenuAction.resolvedIcon(): ImageVector {
    return when {
        icon != null -> icon
        text.contains("稍后") -> Icons.Outlined.DateRange
        text.contains("不感兴趣") -> Icons.Outlined.Close
        else -> Icons.Outlined.Info
    }
}

private fun TvContextMenuAction.defaultAccentColor(): Color {
    return when {
        text.contains("不感兴趣") -> Color(0xFFFF8EA3)
        text.contains("稍后") -> Color(0xFF7CCBFF)
        else -> Color(0xFF9DD8FF)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvContextMenuButton(
    text: String,
    supportingText: String?,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val isLightTheme = LocalIsLightTheme.current
    val shape = RoundedCornerShape(18.dp)
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) {
            accentColor.copy(alpha = 0.22f)
        } else {
            if (isLightTheme) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.07f)
        },
        animationSpec = tween(durationMillis = 150),
        label = "TvContextMenuButtonBackground",
    )
    val textColor by animateColorAsState(
        targetValue = if (isFocused) {
            Color.White
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 150),
        label = "TvContextMenuButtonText",
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.025f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "TvContextMenuButtonScale",
    )
    val iconBackgroundColor by animateColorAsState(
        targetValue = if (isFocused) {
            accentColor.copy(alpha = 0.95f)
        } else {
            accentColor.copy(alpha = 0.18f)
        },
        animationSpec = tween(durationMillis = 150),
        label = "TvContextMenuButtonIconBackground",
    )
    val iconTintColor by animateColorAsState(
        targetValue = if (isFocused) {
            Color.White
        } else {
            accentColor
        },
        animationSpec = tween(durationMillis = 150),
        label = "TvContextMenuButtonIconTint",
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
                border = BorderStroke(
                    1.dp,
                    if (isLightTheme) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f),
                ),
                shape = shape,
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, accentColor.copy(alpha = 0.95f)),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None, Glow.None),
    ) {
        Row(
            modifier = Modifier
                .height(if (supportingText == null) 56.dp else 68.dp)
                .background(backgroundColor, shape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTintColor,
                    modifier = Modifier.size(19.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = text,
                    color = textColor,
                    fontSize = 15.5f.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        color = textColor.copy(alpha = if (isFocused) 0.78f else 0.58f),
                        fontSize = 12.5f.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
