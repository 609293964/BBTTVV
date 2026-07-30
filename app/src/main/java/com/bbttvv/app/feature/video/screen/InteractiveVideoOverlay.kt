package com.bbttvv.app.feature.video.screen

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.bbttvv.app.feature.video.viewmodel.InteractiveVideoUiState

@Composable
internal fun BoxScope.InteractiveVideoOverlay(
    state: InteractiveVideoUiState,
    focusCoordinator: PlayerFocusCoordinator,
    onSelect: (Int) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val visible = state as? InteractiveVideoUiState.Showing
    val error = state as? InteractiveVideoUiState.RetryableError
    val loading = state as? InteractiveVideoUiState.LoadingBranch
    val options = visible?.options ?: error?.options ?: loading?.options ?: return
    val question = visible?.question ?: error?.question ?: loading?.question.orEmpty()
    val initialIndex = visible?.defaultIndex ?: error?.selectedIndex ?: loading?.selectedIndex ?: 0
    val requesters = remember(options) { List(options.size) { FocusRequester() } }
    val listState = rememberLazyListState()

    LaunchedEffect(state, initialIndex) {
        if (state is InteractiveVideoUiState.Showing || state is InteractiveVideoUiState.RetryableError) {
            listState.scrollToItem(initialIndex.coerceIn(0, options.lastIndex))
            focusCoordinator.requestFocus(PlayerFocusIntent.FocusInteractiveOption(initialIndex))
            withFrameNanos { }
            focusCoordinator.drainPendingFocus()
        }
    }

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .zIndex(20f)
            .fillMaxHeight()
            .padding(end = 56.dp, top = 48.dp, bottom = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 300.dp, max = 440.dp)
                .background(Color(0xE61A1A1A), RoundedCornerShape(16.dp))
                .padding(20.dp)
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (native.action != KeyEvent.ACTION_DOWN || native.repeatCount != 0) return@onPreviewKeyEvent false
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
                        KeyEvent.KEYCODE_BACK -> {
                            onBack()
                            true
                        }
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(question.ifBlank { "请选择剧情分支" }, color = Color.White, fontSize = 22.sp)
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                itemsIndexed(options, key = { _, option -> "${option.edgeId}:${option.cid}" }) { index, option ->
                    var focused by remember { mutableStateOf(false) }
                    DisposableEffect(index, requesters[index]) {
                        val registration = focusCoordinator.registerInteractiveOptionTarget(
                            index,
                            object : PlayerFocusTarget {
                                override fun tryRequestFocus(): Boolean = runCatching {
                                    requesters[index].requestFocus()
                                }.getOrDefault(false)
                            },
                        )
                        onDispose(registration::unregister)
                    }
                    Box(
                        modifier = Modifier
                            .focusRequester(requesters[index])
                            .focusProperties {
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                                if (index == 0) up = FocusRequester.Cancel
                                if (index == options.lastIndex) down = FocusRequester.Cancel
                            }
                            .onFocusChanged { focused = it.isFocused }
                            .border(
                                width = if (focused) 3.dp else 1.dp,
                                color = if (focused) Color.White else Color(0xFF666666),
                                shape = RoundedCornerShape(10.dp),
                            )
                            .background(
                                if (focused) Color(0xFF3F51B5) else Color(0xFF292929),
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                            .onPreviewKeyEvent { event ->
                                val native = event.nativeKeyEvent
                                if (native.action != KeyEvent.ACTION_DOWN || native.repeatCount != 0) return@onPreviewKeyEvent false
                                when (native.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                        if (state is InteractiveVideoUiState.RetryableError) onRetry()
                                        else if (state is InteractiveVideoUiState.Showing) onSelect(index)
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
                                    else -> false
                                }
                            }
                            .focusable(),
                    ) {
                        Text(
                            text = if (state is InteractiveVideoUiState.LoadingBranch && index == initialIndex) {
                                "${option.text} · 加载中"
                            } else option.text,
                            color = Color.White,
                            fontSize = 18.sp,
                        )
                    }
                }
            }
            if (error != null) Text(error.message, color = Color(0xFFFFB4AB), fontSize = 15.sp)
        }
    }
}
