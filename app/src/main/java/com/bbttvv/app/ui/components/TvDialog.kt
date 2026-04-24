package com.bbttvv.app.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    suppressConfirmKey: Boolean = false,
    onSuppressConfirmKeyConsumed: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    val dialogFocusRequester = rememberTvDialogFocusTrap()

    BackHandler {
        onDismissRequest()
    }

    LaunchedEffect(title) {
        runCatching { dialogFocusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { keyEvent ->
                    val event = keyEvent.nativeKeyEvent
                    if (isTvDialogConfirmKey(event.keyCode) && suppressConfirmKey) {
                        if (event.action == AndroidKeyEvent.ACTION_UP) {
                            onSuppressConfirmKeyConsumed()
                        }
                        return@onPreviewKeyEvent true
                    }
                    false
                }
                .background(Color(0x99000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth(0.56f)
                    .widthIn(min = 360.dp, max = 560.dp)
                    .focusRequester(dialogFocusRequester)
                    .focusGroup()
                    .focusProperties {
                        onExit = { cancelFocusChange() }
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xF21A2028))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 26.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}

@Composable
fun TvConfirmDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    suppressConfirmKey: Boolean = false,
    onSuppressConfirmKeyConsumed: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit
) {
    TvDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        suppressConfirmKey = suppressConfirmKey,
        onSuppressConfirmKeyConsumed = onSuppressConfirmKeyConsumed,
        content = {
            Text(
                text = message,
                color = Color(0xDDEAF2F8),
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        },
        actions = actions
    )
}

private fun isTvDialogConfirmKey(keyCode: Int): Boolean {
    return keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
}
