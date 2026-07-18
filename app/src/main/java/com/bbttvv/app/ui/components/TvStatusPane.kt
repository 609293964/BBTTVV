package com.bbttvv.app.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bbttvv.app.ui.theme.LocalIsLightTheme

@Composable
fun TvStatusPane(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionFocusRequester: FocusRequester? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    secondaryActionFocusRequester: FocusRequester? = null,
    requestInitialFocus: Boolean = true,
    onDpadUp: () -> Boolean = { false },
) {
    val isLightTheme = LocalIsLightTheme.current
    val fallbackActionFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val fallbackSecondaryActionFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val resolvedActionFocusRequester = actionFocusRequester ?: fallbackActionFocusRequester
    val resolvedSecondaryActionFocusRequester =
        secondaryActionFocusRequester ?: fallbackSecondaryActionFocusRequester
    val hasAction = !actionLabel.isNullOrBlank() && onAction != null
    val hasSecondaryAction = !secondaryActionLabel.isNullOrBlank() && onSecondaryAction != null

    LaunchedEffect(hasAction, requestInitialFocus, resolvedActionFocusRequester) {
        if (hasAction && requestInitialFocus) {
            withFrameNanos { }
            runCatching { resolvedActionFocusRequester.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                val event = keyEvent.nativeKeyEvent
                event.action == AndroidKeyEvent.ACTION_DOWN &&
                    event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP &&
                    onDpadUp()
            }
            .padding(horizontal = 48.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                color = if (isLightTheme) Color(0xFF18191C) else Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = message,
            color = if (isLightTheme) Color(0xFF61666D) else Color.White.copy(alpha = 0.78f),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
        )
        if (hasAction || hasSecondaryAction) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hasAction) {
                    TvDialogActionButton(
                        text = actionLabel.orEmpty(),
                        onClick = { onAction.invoke() },
                        modifier = Modifier.focusRequester(resolvedActionFocusRequester),
                        minWidth = 112.dp,
                    )
                }
                if (hasSecondaryAction) {
                    TvDialogActionButton(
                        text = secondaryActionLabel.orEmpty(),
                        onClick = { onSecondaryAction.invoke() },
                        modifier = Modifier.focusRequester(resolvedSecondaryActionFocusRequester),
                        minWidth = 112.dp,
                    )
                }
            }
        }
    }
}
