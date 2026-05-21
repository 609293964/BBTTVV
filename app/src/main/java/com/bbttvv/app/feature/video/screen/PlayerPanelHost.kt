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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
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

import com.bbttvv.app.ui.theme.LocalIsLightTheme

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
    val panelMaxHeight = if (usesSettingRows) 380.dp else 240.dp
    val panelCorner = if (usesSettingRows) 18.dp else 16.dp

    val listState = rememberLazyListState()
    val panelIdentity = title + ":" + options.joinToString(separator = "|") { option -> option.key }
    LaunchedEffect(panelIdentity, selectedIndex) {
        if (selectedIndex in options.indices) {
            if (!listState.isItemVisible(selectedIndex)) {
                listState.scrollToItem(selectedIndex)
            }
            withFrameNanos { }
            runCatching {
                optionFocusRequesters[selectedIndex].requestFocus()
            }
        }
    }

    val isLightTheme = LocalIsLightTheme.current
    val panelBgColor = if (isLightTheme) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.65f)
    val panelBorderColor = if (isLightTheme) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f)
    val titleTextColor = if (isLightTheme) Color(0xFF18191C) else Color.White

    Box(
        modifier = modifier
            .width(panelWidth)
    ) {
        // 1. 隔离的背景层：只负责模糊与背景填充
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(panelCorner))
                .playerPanelSurfaceEffect(visualEffectsState)
                .background(panelBgColor)
                .border(1.dp, panelBorderColor, RoundedCornerShape(panelCorner))
        )

        // 2. 清晰的前景层：负责展示内容，完全不受模糊影响
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                color = titleTextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (options.isEmpty()) {
                Text(
                    text = "当前格式不支持切换",
                    color = if (isLightTheme) Color(0xFF18191C).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = panelMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    itemsIndexed(
                         items = options,
                         key = { _, option -> option.key },
                    ) { index, option ->
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
}

private fun LazyListState.isItemVisible(index: Int): Boolean {
    return layoutInfo.visibleItemsInfo.any { item -> item.index == index }
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

    val isLightTheme = LocalIsLightTheme.current
    val contentColor = when {
        selected -> {
            if (isLightTheme) Color.White else Color(0xFF111111)
        }
        option.isEnabled -> {
            if (isLightTheme) Color(0xFF18191C) else Color.White
        }
        else -> {
            if (isLightTheme) Color(0xFF18191C).copy(alpha = 0.38f) else Color.White.copy(alpha = 0.42f)
        }
    }

    val rowBgColor = if (selected) {
        if (isLightTheme) Color(0xFFFB7299) else Color.White.copy(alpha = 0.94f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(rowBgColor)
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
    val isLightTheme = LocalIsLightTheme.current
    val titleColor = when {
        selected -> {
            Color.White
        }
        option.isEnabled -> {
            if (isLightTheme) Color(0xFF18191C) else Color(0xF2FFFFFF)
        }
        else -> {
            if (isLightTheme) Color(0xFF18191C).copy(alpha = 0.38f) else Color.White.copy(alpha = 0.42f)
        }
    }

    val subtitleColor = when {
        selected -> {
            if (isLightTheme) Color.White.copy(alpha = 0.85f) else Color(0xFFB9D1FF)
        }
        option.isEnabled -> {
            if (isLightTheme) Color(0xFF61666D) else Color(0x99E4EBF5)
        }
        else -> {
            if (isLightTheme) Color(0xFF18191C).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.32f)
        }
    }

    val rowBgColor = if (selected) {
        if (isLightTheme) Color(0xFFFB7299) else Color.White.copy(alpha = 0.20f)
    } else {
        if (isLightTheme) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.05f)
    }

    val rowBorderColor = if (selected) {
        if (isLightTheme) Color(0xFFFB7299) else Color.White.copy(alpha = 0.40f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rowBgColor)
            .border(
                width = 1.dp,
                color = rowBorderColor,
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
            val pillBgColor = if (selected) {
                if (isLightTheme) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.18f)
            } else {
                if (isLightTheme) Color.Black.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.24f)
            }
            val pillBorderColor = if (selected) {
                if (isLightTheme) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.24f)
            } else {
                if (isLightTheme) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.10f)
            }
            val pillTextColor = if (selected) {
                Color.White
            } else {
                if (isLightTheme) Color(0xFF18191C) else Color(0xFFE5ECF7)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(pillBgColor)
                    .border(
                        width = 1.dp,
                        color = pillBorderColor,
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = it,
                    color = pillTextColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}
