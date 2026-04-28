package com.bbttvv.app.feature.video.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PlayerActionBar(
    actions: List<PlayerAction>,
    selectedIndex: Int,
    hasFocus: Boolean,
    isDanmakuEnabled: Boolean,
    actionFocusRequesters: List<FocusRequester>,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { index, action ->
            PlayerActionButton(
                action = action,
                selected = hasFocus && index == selectedIndex,
                active = action != PlayerAction.Danmaku || isDanmakuEnabled,
                modifier = Modifier
                    .focusRequester(actionFocusRequesters[index])
                    .focusable(),
            )
        }
    }
}

@Composable
private fun PlayerActionButton(
    action: PlayerAction,
    selected: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) {
                    Color.White.copy(alpha = 0.94f)
                } else {
                    Color.Transparent
                }
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = actionIcon(action),
            contentDescription = null,
            tint = if (selected) Color(0xFF111111) else Color.White,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = actionButtonLabel(action),
            color = if (selected) Color(0xFF111111) else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private fun actionButtonLabel(action: PlayerAction): String {
    return when (action) {
        PlayerAction.Detail -> "详情"
        PlayerAction.Comments -> "评论"
        PlayerAction.Speed -> "倍速"
        PlayerAction.Quality -> "画质"
        PlayerAction.Danmaku -> "弹幕"
        PlayerAction.Audio -> "音频"
        PlayerAction.Codec -> "码率"
    }
}

private fun actionIcon(action: PlayerAction): ImageVector {
    return when (action) {
        PlayerAction.Detail -> Icons.Outlined.Info
        PlayerAction.Comments -> Icons.Outlined.Email
        PlayerAction.Speed -> Icons.Outlined.PlayArrow
        PlayerAction.Quality -> Icons.Outlined.Settings
        PlayerAction.Danmaku -> Icons.Outlined.Create
        PlayerAction.Audio -> Icons.Outlined.Settings
        PlayerAction.Codec -> Icons.Outlined.Info
    }
}
