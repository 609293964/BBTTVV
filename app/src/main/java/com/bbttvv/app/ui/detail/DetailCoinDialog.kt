package com.bbttvv.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.bbttvv.app.ui.components.TvDialog

@Composable
internal fun DetailCoinDialog(
    currentCoinCount: Int,
    accountCoinBalance: Int?,
    isLiked: Boolean,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (count: Int, alsoLike: Boolean) -> Unit,
) {
    val remainingCoins = resolveDetailTripleCoinRequestCount(currentCoinCount)
    val maxSelectableCoins = accountCoinBalance
        ?.let { balance -> minOf(remainingCoins, balance.coerceAtLeast(0)) }
        ?: remainingCoins
    val safeMaxSelectableCoins = maxSelectableCoins.coerceIn(0, DetailTripleTargetCoinCount)
    var selectedCount by remember(currentCoinCount, accountCoinBalance) {
        mutableStateOf(if (safeMaxSelectableCoins >= 1) 1 else 0)
    }
    var alsoLike by remember(isLiked) { mutableStateOf(!isLiked) }
    val oneCoinFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(safeMaxSelectableCoins) {
        selectedCount = when {
            safeMaxSelectableCoins <= 0 -> 0
            selectedCount <= 0 -> 1
            else -> selectedCount.coerceAtMost(safeMaxSelectableCoins)
        }
        val target = if (safeMaxSelectableCoins > 0) oneCoinFocusRequester else cancelFocusRequester
        runCatching { target.requestFocus() }
    }

    TvDialog(
        title = "投币",
        onDismissRequest = onDismiss,
        content = {
            Text(
                text = "本视频已投 ${currentCoinCount.coerceIn(0, DetailTripleTargetCoinCount)}/$DetailTripleTargetCoinCount 枚",
                color = DetailMutedTextColor,
                fontSize = 15.sp,
            )
            Text(
                text = accountCoinBalance?.let { balance -> "账号硬币 $balance 枚" } ?: "账号硬币 --",
                color = DetailMutedTextColor,
                fontSize = 15.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DetailCoinCountOption(
                    count = 1,
                    enabled = safeMaxSelectableCoins >= 1 && !isLoading,
                    selected = selectedCount == 1,
                    focusRequester = oneCoinFocusRequester,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedCount = 1 },
                )
                DetailCoinCountOption(
                    count = 2,
                    enabled = safeMaxSelectableCoins >= 2 && !isLoading,
                    selected = selectedCount == 2,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedCount = 2 },
                )
            }
            if (!isLiked) {
                DetailPillButton(
                    label = if (alsoLike) "同时点赞：开" else "同时点赞：关",
                    selected = alsoLike,
                    enabled = !isLoading,
                    onClick = { alsoLike = !alsoLike },
                    metrics = DetailCompactActionPillMetrics,
                )
            } else {
                Text(
                    text = "已点赞",
                    color = DetailMutedTextColor,
                    fontSize = 14.sp,
                )
            }
            if (remainingCoins <= 0) {
                Text(
                    text = "本视频已投满硬币",
                    color = DetailMutedTextColor,
                    fontSize = 14.sp,
                )
            } else if (safeMaxSelectableCoins <= 0) {
                Text(
                    text = "硬币余额不足",
                    color = DetailMutedTextColor,
                    fontSize = 14.sp,
                )
            }
        },
        actions = {
            DetailPillButton(
                label = "取消",
                modifier = Modifier.weight(1f),
                focusRequester = cancelFocusRequester,
                enabled = !isLoading,
                onClick = onDismiss,
                metrics = DetailCompactActionPillMetrics,
            )
            if (selectedCount > 0 && !isLoading) {
                DetailPillButton(
                    label = "确认投${selectedCount}枚",
                    modifier = Modifier.weight(1f),
                    selected = true,
                    onClick = {
                        onConfirm(selectedCount, alsoLike && !isLiked)
                    },
                    metrics = DetailCompactActionPillMetrics,
                )
            } else {
                DetailDisabledPill(
                    label = if (isLoading) "投币中" else "确认投币",
                    modifier = Modifier.weight(1f),
                    metrics = DetailCompactActionPillMetrics,
                )
            }
        },
    )
}

@Composable
private fun DetailCoinCountOption(
    count: Int,
    enabled: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    if (enabled) {
        DetailPillButton(
            label = "${count}枚",
            modifier = modifier,
            selected = selected,
            focusRequester = focusRequester,
            onClick = onClick,
        )
    } else {
        DetailDisabledPill(
            label = "${count}枚",
            modifier = modifier,
        )
    }
}
