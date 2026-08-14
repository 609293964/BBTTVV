package com.bbttvv.app.feature.video.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.bbttvv.app.ui.theme.LocalTvSemanticColors

@Composable
internal fun PlayerOptionsPanel(
    title: String,
    options: List<PanelOption>,
    selectedIndex: Int,
    visualEffectsState: PlayerVisualEffectsState,
    optionFocusRequesters: List<FocusRequester>,
    layout: PlayerPanelLayout = PlayerPanelLayout.Floating,
    modifier: Modifier = Modifier,
) {
    val usesSettingRows = options.any { it.presentation == PanelOptionPresentation.Setting }
    val panelWidth = if (usesSettingRows) 320.dp else 232.dp
    val panelMaxHeight = if (usesSettingRows) 440.dp else 320.dp
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

    val semanticColors = LocalTvSemanticColors.current
    val panelBgColor = semanticColors.panelContainer
    val panelBorderColor = semanticColors.panelBorder
    val titleTextColor = semanticColors.primaryText

    if (layout == PlayerPanelLayout.RightSidebar) {
        PlayerSettingsSidebar(
            title = title,
            options = options,
            selectedIndex = selectedIndex,
            listState = listState,
            optionFocusRequesters = optionFocusRequesters,
            modifier = modifier,
        )
        return
    }

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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                color = titleTextColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (options.isEmpty()) {
                Text(
                    text = "当前格式不支持切换",
                    color = semanticColors.secondaryText,
                    fontSize = 14.sp,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = panelMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
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

@Composable
private fun PlayerSettingsSidebar(
    title: String,
    options: List<PanelOption>,
    selectedIndex: Int,
    listState: LazyListState,
    optionFocusRequesters: List<FocusRequester>,
    modifier: Modifier = Modifier,
) {
    PlayerRightSidebarScaffold(
        modifier = modifier,
        header = { colors ->
            Text(
                text = title,
                color = colors.primaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        },
    ) { colors ->
        if (options.isEmpty()) {
            Text(
                text = "当前格式不支持切换",
                color = colors.secondaryText,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                itemsIndexed(
                    items = options,
                    key = { _, option -> option.key },
                ) { index, option ->
                    PlayerSidebarSettingRow(
                        option = option,
                        selected = index == selectedIndex,
                        colors = colors,
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
private fun PlayerSidebarSettingRow(
    option: PanelOption,
    selected: Boolean,
    colors: PlayerRightSidebarColors,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = LocalIsLightTheme.current
    val accentColor = colors.accent
    val titleColor = when {
        !option.isEnabled -> if (isLightTheme) Color.Black.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.42f)
        selected && isLightTheme -> accentColor
        else -> colors.primaryText
    }
    val subtitleColor = when {
        !option.isEnabled -> if (isLightTheme) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.32f)
        else -> colors.secondaryText.copy(alpha = if (isLightTheme) 1f else 0.94f)
    }
    val focusedBackground = colors.focusedContainer

    Box(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .background(if (selected) focusedBackground else Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = option.label,
                    color = titleColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                option.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        color = subtitleColor,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            option.valueText?.let { valueText ->
                Text(
                    text = valueText,
                    color = if (selected) accentColor else titleColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = if (selected) 0.dp else 18.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 1.dp, max = 1.dp)
                    .background(colors.divider),
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accentColor),
            )
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

    val semanticColors = LocalTvSemanticColors.current
    val contentColor = when {
        selected -> semanticColors.focusContent
        option.isEnabled -> semanticColors.primaryText
        else -> semanticColors.disabledText
    }
    val rowBgColor = if (selected) semanticColors.focusContainer else Color.Transparent

    Row(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(rowBgColor)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (option.isSelected) "✓" else " ",
            color = contentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(18.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = option.label,
                color = contentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            option.subtitle?.let {
                Text(
                    text = it,
                    color = contentColor.copy(alpha = if (selected) 0.72f else 0.62f),
                    fontSize = 12.sp,
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
    val semanticColors = LocalTvSemanticColors.current
    val titleColor = when {
        selected -> semanticColors.focusContent
        option.isEnabled -> semanticColors.primaryText
        else -> semanticColors.disabledText
    }

    val subtitleColor = when {
        selected -> semanticColors.focusContent.copy(alpha = 0.85f)
        option.isEnabled -> semanticColors.secondaryText
        else -> semanticColors.disabledText
    }

    val rowBgColor = if (selected) {
        semanticColors.surfaceSelected
    } else {
        semanticColors.surfaceSubtle
    }

    val rowBorderColor = if (selected) {
        semanticColors.accent
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            option.subtitle?.let {
                Text(
                    text = it,
                    color = subtitleColor,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        option.valueText?.let {
            val pillBgColor = if (selected) {
                semanticColors.focusContent.copy(alpha = 0.18f)
            } else {
                semanticColors.surfaceSubtle
            }
            val pillBorderColor = if (selected) {
                semanticColors.focusContent.copy(alpha = 0.24f)
            } else {
                semanticColors.border
            }
            val pillTextColor = if (selected) {
                semanticColors.focusContent
            } else {
                semanticColors.primaryText
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}
