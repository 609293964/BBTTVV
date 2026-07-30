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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.bbttvv.app.feature.video.viewmodel.DanmakuVoteUiState
import com.bbttvv.app.ui.input.isTvBackKey
import com.bbttvv.app.ui.input.isTvConfirmKey
import com.bbttvv.app.ui.input.resolveTvSinglePress

internal typealias DanmakuVoteKeyHandler = (KeyEvent) -> Boolean

@Composable
internal fun BoxScope.DanmakuVoteOverlay(
    state: DanmakuVoteUiState,
    focusCoordinator: PlayerFocusCoordinator,
    onSelect: (Int) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onKeyHandlerChanged: (DanmakuVoteKeyHandler?) -> Unit,
) {
    val showing = state as? DanmakuVoteUiState.Showing
    val submitting = state as? DanmakuVoteUiState.Submitting
    val error = state as? DanmakuVoteUiState.RetryableError
    val submitted = state as? DanmakuVoteUiState.Submitted
    val prompt = showing?.prompt ?: submitting?.prompt ?: error?.prompt ?: submitted?.prompt ?: return
    val initialIndex = when {
        submitting != null -> submitting.selectedIndex
        error != null -> error.selectedIndex
        submitted != null -> submitted.selectedIndex
        prompt.selectedOptionId > 0 -> prompt.options.indexOfFirst { it.id == prompt.selectedOptionId }.coerceAtLeast(0)
        else -> 0
    }
    val overlayFocusRequester = remember(prompt.voteId) { FocusRequester() }
    val listState = rememberLazyListState()
    var activeIndex = remember(prompt.voteId) {
        mutableIntStateOf(initialIndex.coerceIn(0, prompt.options.lastIndex))
    }
    val latestState = rememberUpdatedState(state)
    val latestOnSelect = rememberUpdatedState(onSelect)
    val latestOnRetry = rememberUpdatedState(onRetry)
    val latestOnBack = rememberUpdatedState(onBack)
    val keyHandler = remember(prompt.voteId, prompt.options.size) {
        handler@{ native: KeyEvent ->
            val currentState = latestState.value
            val isBack = isTvBackKey(native.keyCode)
            val isConfirm = isTvConfirmKey(native.keyCode)
            val singlePress = resolveTvSinglePress(
                action = native.action,
                repeatCount = native.repeatCount,
                isHandledKey = isBack || isConfirm,
            )
            if (singlePress.isConsumed) {
                if (singlePress.shouldTrigger) {
                    when {
                        isBack -> latestOnBack.value()
                        currentState is DanmakuVoteUiState.RetryableError -> latestOnRetry.value()
                        currentState is DanmakuVoteUiState.Showing -> {
                            latestOnSelect.value(activeIndex.intValue)
                        }
                    }
                }
                return@handler true
            }

            when (native.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (
                        native.action == KeyEvent.ACTION_DOWN &&
                        currentState is DanmakuVoteUiState.Showing
                    ) {
                        val delta = when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> -1
                            KeyEvent.KEYCODE_DPAD_DOWN -> 1
                            else -> 0
                        }
                        activeIndex.intValue = (activeIndex.intValue + delta)
                            .coerceIn(0, prompt.options.lastIndex)
                    }
                    true
                }

                else -> false
            }
        }
    }

    LaunchedEffect(prompt.voteId, initialIndex) {
        activeIndex.intValue = initialIndex.coerceIn(0, prompt.options.lastIndex)
    }

    DisposableEffect(prompt.voteId, overlayFocusRequester) {
        val registration = focusCoordinator.registerDanmakuVoteOverlayTarget(
            object : PlayerFocusTarget {
                override fun tryRequestFocus(): Boolean = runCatching {
                    overlayFocusRequester.requestFocus()
                }.getOrDefault(false)
            },
        )
        onDispose(registration::unregister)
    }

    DisposableEffect(keyHandler) {
        onKeyHandlerChanged(keyHandler)
        onDispose {
            onKeyHandlerChanged(null)
        }
    }

    LaunchedEffect(prompt.voteId) {
        repeat(3) {
            if (focusCoordinator.requestFocus(PlayerFocusIntent.FocusDanmakuVoteOverlay)) {
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
    }

    LaunchedEffect(activeIndex.intValue) {
        listState.scrollToItem(activeIndex.intValue)
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
                .widthIn(min = 320.dp, max = 460.dp)
                .graphicsLayer {
                    scaleX = 0.5f
                    scaleY = 0.5f
                    transformOrigin = TransformOrigin(1f, 0.5f)
                }
                .background(Color(0xE61A1A1A), RoundedCornerShape(16.dp))
                .padding(20.dp)
                .focusRequester(overlayFocusRequester)
                .onPreviewKeyEvent { event ->
                    keyHandler(event.nativeKeyEvent)
                }
                .focusable(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(prompt.question, color = Color.White, fontSize = 22.sp)
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                itemsIndexed(prompt.options, key = { _, option -> option.id }) { index, option ->
                    val focused = index == activeIndex.intValue
                    val selected = prompt.selectedOptionId == option.id
                    Box(
                        modifier = Modifier
                            .border(
                                width = if (focused) 3.dp else 1.dp,
                                color = if (focused) Color.White else Color(0xFF666666),
                                shape = RoundedCornerShape(10.dp),
                            )
                            .background(
                                when {
                                    focused -> Color(0xFF3F51B5)
                                    selected -> Color(0xFF285943)
                                    else -> Color(0xFF292929)
                                },
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        val suffix = when {
                            submitting?.selectedIndex == index -> " · 提交中"
                            submitted?.selectedIndex == index || selected -> " · 已投票"
                            else -> " · ${option.count}票"
                        }
                        Text(
                            text = option.text + suffix,
                            color = Color.White,
                            fontSize = 18.sp,
                        )
                    }
                }
            }
            if (error != null) {
                Text(error.message, color = Color(0xFFFFB4AB), fontSize = 15.sp)
            } else if (submitted != null) {
                Text("投票成功", color = Color(0xFF9BE8C2), fontSize = 15.sp)
            } else if (prompt.selectedOptionId > 0) {
                Text("你已经参与过该投票", color = Color(0xFFB7C7FF), fontSize = 15.sp)
            }
        }
    }
}
