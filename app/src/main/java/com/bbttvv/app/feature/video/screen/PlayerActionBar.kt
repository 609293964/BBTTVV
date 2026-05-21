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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import com.bbttvv.app.ui.theme.LocalIsLightTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PlayerActionBar(
    actions: List<PlayerAction>,
    selectedIndex: Int,
    hasFocus: Boolean,
    isDanmakuEnabled: Boolean,
    actionFocusRequesters: List<FocusRequester>,
) {
    val isLightTheme = LocalIsLightTheme.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isLightTheme) Color.White.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.15f))
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
    val isLightTheme = LocalIsLightTheme.current
    val buttonBgColor = if (selected) {
        if (isLightTheme) Color(0xFFFB7299) else Color.White.copy(alpha = 0.94f)
    } else {
        Color.Transparent
    }
    
    val iconColor = when {
        selected -> if (isLightTheme) Color.White else Color(0xFF111111)
        isLightTheme -> if (active) Color(0xFF18191C) else Color(0xFF18191C).copy(alpha = 0.45f)
        else -> if (active) Color.White else Color.White.copy(alpha = 0.54f)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(buttonBgColor)
            .size(40.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = actionIcon(action),
            contentDescription = action.label,
            tint = iconColor,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun actionIcon(action: PlayerAction): ImageVector {
    return when (action) {
        PlayerAction.Comments -> Icons.Outlined.Email
        PlayerAction.Speed -> Icons.Outlined.PlayArrow
        PlayerAction.Quality -> Icons.Outlined.Settings
        PlayerAction.Danmaku -> Icons.Outlined.Create
        PlayerAction.Debug -> Icons.Outlined.Info
    }
}

