package com.bbttvv.app.feature.video.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

@Composable
internal fun PlayerOptionsPanel(
    title: String,
    options: List<PanelOption>,
    selectedIndex: Int,
    visualEffectsState: PlayerVisualEffectsState,
    optionFocusRequesters: List<FocusRequester>,
    modifier: Modifier = Modifier,
) {
    val usesSettingRows = options.any { it.presentation == PanelOptionPresentation.Setting }
    val panelWidth = if (usesSettingRows) 236.dp else 168.dp
    val panelMaxHeight = if (usesSettingRows) 156.dp else 118.dp
    val panelCorner = if (usesSettingRows) 18.dp else 16.dp
    Column(
        modifier = modifier
            .width(panelWidth)
            .clip(RoundedCornerShape(panelCorner))
            .playerPanelSurfaceEffect(visualEffectsState)
            .background(Color.Black.copy(alpha = 0.65f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(panelCorner))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (options.isEmpty()) {
            Text(
                text = "当前格式不支持切换",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 10.sp,
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = panelMaxHeight),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                itemsIndexed(options) { index, option ->
                    PlayerOptionRow(
                        option = option,
                        selected = index == selectedIndex,
                        modifier = Modifier
                            .focusRequester(optionFocusRequesters[index])
                            .focusable(enabled = option.isEnabled),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerOptionRow(
    option: PanelOption,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (option.presentation == PanelOptionPresentation.Setting) {
        PlayerSettingOptionRow(
            option = option,
            selected = selected,
            modifier = modifier,
        )
        return
    }
    val contentColor = when {
        selected -> Color(0xFF111111)
        option.isEnabled -> Color.White
        else -> Color.White.copy(alpha = 0.42f)
    }
    Row(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White.copy(alpha = 0.94f) else Color.Transparent)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (option.isSelected) "✓" else " ",
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(13.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = option.label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            option.subtitle?.let {
                Text(
                    text = it,
                    color = contentColor.copy(alpha = if (selected) 0.72f else 0.62f),
                    fontSize = 7.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlayerSettingOptionRow(
    option: PanelOption,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val titleColor = when {
        selected -> Color.White
        option.isEnabled -> Color(0xF2FFFFFF)
        else -> Color.White.copy(alpha = 0.42f)
    }
    val subtitleColor = when {
        selected -> Color(0xFFB9D1FF)
        option.isEnabled -> Color(0x99E4EBF5)
        else -> Color.White.copy(alpha = 0.32f)
    }
    Row(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    Color.White.copy(alpha = 0.20f)
                } else {
                    Color.White.copy(alpha = 0.05f)
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) Color.White.copy(alpha = 0.40f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = option.label,
                color = titleColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            option.subtitle?.let {
                Text(
                    text = it,
                    color = subtitleColor,
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        option.valueText?.let {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selected) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.24f),
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = it,
                    color = if (selected) Color.White else Color(0xFFE5ECF7),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}


