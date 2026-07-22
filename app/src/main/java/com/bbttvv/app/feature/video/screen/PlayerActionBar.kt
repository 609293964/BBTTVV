package com.bbttvv.app.feature.video.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
import androidx.compose.material.icons.outlined.DateRange
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
            .background(Color.Black.copy(alpha = 0.68f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = actions.getOrNull(selectedIndex)?.label ?: "操作",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.width(72.dp),
        )
        actions.forEachIndexed { index, action ->
            key(action) {
                PlayerActionButton(
                    action = action,
                    selected = hasFocus && index == selectedIndex,
                    active = action != PlayerAction.Danmaku || isDanmakuEnabled,
                    focusRequester = actionFocusRequesters[index],
                )
            }
        }
    }
}

@Composable
private fun PlayerActionButton(
    action: PlayerAction,
    selected: Boolean,
    active: Boolean,
    focusRequester: FocusRequester,
) {
    val buttonBgColor = if (selected) {
        Color.White.copy(alpha = 0.96f)
    } else {
        Color.Transparent
    }
    
    val iconColor = when {
        selected -> Color(0xFF111111)
        else -> if (active) Color.White else Color.White.copy(alpha = 0.54f)
    }

    Row(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .clip(RoundedCornerShape(999.dp))
            .background(buttonBgColor)
            .size(48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = actionIcon(action),
            contentDescription = action.label,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun actionIcon(action: PlayerAction): ImageVector {
    return when (action) {
        PlayerAction.Comments -> Icons.Outlined.Email
        PlayerAction.Speed -> Icons.Outlined.PlayArrow
        PlayerAction.Quality -> Icons.Outlined.Settings
        PlayerAction.Danmaku -> Icons.Outlined.Create
        PlayerAction.SponsorNavigation -> Icons.Outlined.DateRange
        PlayerAction.Debug -> Icons.Outlined.Info
    }
}
