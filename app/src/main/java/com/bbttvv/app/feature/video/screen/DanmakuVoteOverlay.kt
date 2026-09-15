package com.bbttvv.app.feature.video.screen

import android.view.KeyEvent
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import com.bbttvv.app.feature.video.viewmodel.DanmakuVoteUiState
import com.bbttvv.app.feature.video.viewmodel.DanmakuVoteKind

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
    val configuration = LocalConfiguration.current
    val compactDialogWidth = (configuration.screenWidthDp * 0.30f).dp
        .coerceIn(300.dp, 420.dp)
    val compactDialogHeight = (configuration.screenHeightDp * 0.40f).dp
        .coerceIn(180.dp, 440.dp)
    val showing = state as? DanmakuVoteUiState.Showing
    val submitting = state as? DanmakuVoteUiState.Submitting
    val error = state as? DanmakuVoteUiState.RetryableError
    val submitted = state as? DanmakuVoteUiState.Submitted
    val prompt = showing?.prompt ?: submitting?.prompt ?: error?.prompt ?: submitted?.prompt ?: return
    val remainingSeconds = remember(prompt.interactionKey, showing?.deadlineElapsedMs) {
        mutableIntStateOf(
            showing?.deadlineElapsedMs
                ?.let { ((it - SystemClock.elapsedRealtime()).coerceAtLeast(0L) + 999L) / 1000L }
                ?.toInt()
                ?: 0,
        )
    }
    LaunchedEffect(prompt.interactionKey, showing?.deadlineElapsedMs) {
        val deadline = showing?.deadlineElapsedMs ?: return@LaunchedEffect
        while (true) {
            val remainingMs = deadline - SystemClock.elapsedRealtime()
            remainingSeconds.intValue = ((remainingMs.coerceAtLeast(0L) + 999L) / 1000L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            if (remainingMs <= 0L) return@LaunchedEffect
            kotlinx.coroutines.delay(1_000L)
        }
    }
    val initialIndex = when {
        submitting != null -> submitting.selectedIndex
        error != null -> error.selectedIndex
        submitted != null -> submitted.selectedIndex
        prompt.selectedOptionId > 0 -> prompt.options.indexOfFirst { it.id == prompt.selectedOptionId }.coerceAtLeast(0)
        else -> 0
    }
    val overlayFocusRequester = remember(prompt.interactionKey) { FocusRequester() }
    val listState = rememberLazyListState()
    val activeIndex = remember(prompt.interactionKey) {
        mutableIntStateOf(initialIndex.coerceIn(0, prompt.options.lastIndex))
    }
    val latestState = rememberUpdatedState(state)
    val latestOnSelect = rememberUpdatedState(onSelect)
    val latestOnRetry = rememberUpdatedState(onRetry)
    val latestOnBack = rememberUpdatedState(onBack)
    val keyHandler = remember(prompt.interactionKey, prompt.options.size) {
        handler@{ native: KeyEvent ->
            val currentState = latestState.value
            val keyState = when (currentState) {
                is DanmakuVoteUiState.Showing -> if (currentState.prompt.selectedOptionId > 0) {
                    DanmakuVoteKeyState.Submitted
                } else DanmakuVoteKeyState.Showing
                is DanmakuVoteUiState.Submitting -> DanmakuVoteKeyState.Submitting
                is DanmakuVoteUiState.RetryableError -> DanmakuVoteKeyState.RetryableError
                is DanmakuVoteUiState.Submitted -> DanmakuVoteKeyState.Submitted
                DanmakuVoteUiState.Hidden -> return@handler false
            }
            val decision = resolveDanmakuVoteKeyDecision(
                action = native.action,
                keyCode = native.keyCode,
                repeatCount = native.repeatCount,
                state = keyState,
                currentIndex = activeIndex.intValue,
                lastIndex = prompt.options.lastIndex,
                horizontal = prompt.kind == DanmakuVoteKind.Grade,
            )
            activeIndex.intValue = decision.nextIndex
            when (decision.command) {
                DanmakuVoteKeyCommand.Select -> latestOnSelect.value(decision.nextIndex)
                DanmakuVoteKeyCommand.Retry -> latestOnRetry.value()
                DanmakuVoteKeyCommand.Dismiss -> latestOnBack.value()
                DanmakuVoteKeyCommand.None -> Unit
            }
            decision.consumed
        }
    }

    LaunchedEffect(prompt.interactionKey, initialIndex) {
        activeIndex.intValue = initialIndex.coerceIn(0, prompt.options.lastIndex)
    }

    DisposableEffect(prompt.interactionKey, overlayFocusRequester) {
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

    LaunchedEffect(prompt.interactionKey) {
        repeat(3) {
            if (focusCoordinator.requestFocus(PlayerFocusIntent.FocusDanmakuVoteOverlay)) {
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
    }

    LaunchedEffect(prompt.interactionKey, activeIndex.intValue) {
        if (prompt.kind == DanmakuVoteKind.Vote) listState.scrollToItem(activeIndex.intValue)
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
                .widthIn(min = 300.dp, max = compactDialogWidth)
                .heightIn(min = 180.dp, max = compactDialogHeight)
                .background(Color(0xE61A1A1A), RoundedCornerShape(16.dp))
                .padding(20.dp)
                .focusRequester(overlayFocusRequester)
                .focusable(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    prompt.question, modifier = Modifier.weight(1f), color = Color.White, fontSize = 22.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                if (showing != null && remainingSeconds.intValue > 0) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color(0x55666666), RoundedCornerShape(8.dp))
                            .background(Color(0x33222222), RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "${remainingSeconds.intValue}s",
                            color = Color(0xFF9E9E9E),
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                }
                if (prompt.kind == DanmakuVoteKind.Grade) {
                    Text("${prompt.participantCount}人参与", color = Color(0xFFBDBDBD), fontSize = 18.sp)
                }
            }
            if (prompt.kind == DanmakuVoteKind.Grade) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    prompt.options.forEachIndexed { index, option ->
                        val focused = index == activeIndex.intValue
                        val selected = prompt.selectedOptionId > 0 && option.id <= prompt.selectedOptionId
                        val preview = prompt.selectedOptionId == 0 && index <= activeIndex.intValue
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "${option.gradeScore!! / 2}星" }
                                .border(
                                    width = 3.dp,
                                    color = if (focused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .background(
                                    if (focused) Color(0xFF3F51B5) else Color.Transparent,
                                    RoundedCornerShape(10.dp),
                                )
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (preview || selected) "★" else "☆",
                                color = if (selected) Color(0xFFFFD54F) else Color.White,
                                fontSize = 42.sp,
                            )
                        }
                    }
                }
            } else LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 250.dp),
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
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (error != null) {
                Text(error.message, color = Color(0xFFFFB4AB), fontSize = 15.sp)
                Text("确认重试 · 返回关闭", color = Color.White, fontSize = 15.sp)
            } else if (submitting != null && prompt.kind == DanmakuVoteKind.Grade) {
                Text("正在提交 ${submitting.selectedIndex + 1} 星 · 返回关闭", color = Color.White, fontSize = 15.sp)
            } else if (submitted != null) {
                Text(if (prompt.kind == DanmakuVoteKind.Grade) "打分成功" else "投票成功", color = Color(0xFF9BE8C2), fontSize = 15.sp)
            } else if (prompt.selectedOptionId > 0) {
                Text(if (prompt.kind == DanmakuVoteKind.Grade) "你已经参与过该打分" else "你已经参与过该投票", color = Color(0xFFB7C7FF), fontSize = 15.sp)
            } else if (prompt.kind == DanmakuVoteKind.Grade) {
                Text("${activeIndex.intValue + 1} 星 · 左右选择 · 确认打分 · 返回关闭", color = Color.White, fontSize = 15.sp)
            }
        }
    }
}
