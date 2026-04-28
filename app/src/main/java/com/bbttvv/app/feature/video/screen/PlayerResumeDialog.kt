package com.bbttvv.app.feature.video.screen

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.bbttvv.app.feature.video.viewmodel.ResumePlaybackPrompt
import com.bbttvv.app.ui.components.rememberTvDialogFocusTrap

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ResumePlaybackDialog(
    prompt: ResumePlaybackPrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogFocusRequester = rememberTvDialogFocusTrap()
    val continueFocusRequester = remember { FocusRequester() }
    val hintText = if (prompt.isCrossPage) {
        prompt.pageLabel?.let { "将跳转到${it}继续播放。" } ?: "将跳转到上次中断位置继续播放。"
    } else {
        "是否从上次中断位置继续播放？"
    }

    LaunchedEffect(prompt.targetCid, prompt.positionMs) {
        if (!runCatching { continueFocusRequester.requestFocus() }.getOrDefault(false)) {
            runCatching { dialogFocusRequester.requestFocus() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.56f)
                    .widthIn(max = 420.dp)
                    .focusRequester(dialogFocusRequester)
                    .focusGroup()
                    .focusProperties {
                        onExit = { cancelFocusChange() }
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0x14000000), RoundedCornerShape(20.dp))
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "继续播放",
                        color = Color(0xFF111111),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "上次看到 ${formatDuration(prompt.positionMs)}",
                        color = Color(0xFF111111),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    prompt.pageLabel?.let { pageLabel ->
                        Text(
                            text = pageLabel,
                            color = Color(0x99000000),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = hintText,
                        color = Color(0xB3000000),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ResumePlaybackActionButton(
                        text = "继续播放",
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .focusRequester(continueFocusRequester),
                    )
                    ResumePlaybackActionButton(
                        text = "从头播放",
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ResumePlaybackActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFFF3F3F3),
            focusedContainerColor = Color(0xFF111111),
            pressedContainerColor = Color(0xFFE6E6E6),
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Color(0x14000000)),
                shape = RoundedCornerShape(999.dp),
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color(0xFF111111)),
                shape = RoundedCornerShape(999.dp),
            ),
        ),
        modifier = modifier.onFocusChanged { focusState ->
            isFocused = focusState.hasFocus
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (isFocused) Color.White else Color(0xFF111111),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
