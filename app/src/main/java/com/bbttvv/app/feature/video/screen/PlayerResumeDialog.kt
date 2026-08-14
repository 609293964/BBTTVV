package com.bbttvv.app.feature.video.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.bbttvv.app.feature.video.viewmodel.ResumePlaybackPrompt
import com.bbttvv.app.ui.components.TvDialog
import com.bbttvv.app.ui.components.TvDialogActionButton

@Composable
internal fun ResumePlaybackDialog(
    prompt: ResumePlaybackPrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val continueFocusRequester = remember { FocusRequester() }
    val hintText = if (prompt.isCrossPage) {
        prompt.pageLabel?.let { "将跳转到${it}继续播放。" } ?: "将跳转到上次中断位置继续播放。"
    } else {
        "是否从上次中断位置继续播放？"
    }

    LaunchedEffect(prompt.targetCid, prompt.positionMs) {
        runCatching { continueFocusRequester.requestFocus() }
    }

    TvDialog(
        title = "继续播放",
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 420.dp),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "上次看到 ${formatDuration(prompt.positionMs)}",
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                prompt.pageLabel?.let { pageLabel ->
                    Text(
                        text = pageLabel,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = hintText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        },
        actions = {
            TvDialogActionButton(
                text = "继续播放",
                onClick = onConfirm,
                modifier = Modifier.focusRequester(continueFocusRequester),
            )
            TvDialogActionButton(
                text = "从头播放",
                onClick = onDismiss,
            )
        },
    )
}
