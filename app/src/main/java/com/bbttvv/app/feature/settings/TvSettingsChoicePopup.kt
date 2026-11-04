package com.bbttvv.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Text
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import com.bbttvv.app.ui.components.rememberTvDialogFocusTrap
import com.bbttvv.app.ui.theme.LocalIsLightTheme
import com.bbttvv.app.ui.theme.LocalTvSemanticColors
import kotlin.math.roundToInt

internal data class TvSettingsChoiceOption<T>(
    val value: T,
    val label: String,
)

@Composable
internal fun <T> TvSettingsChoicePopup(
    title: String,
    options: List<TvSettingsChoiceOption<T>>,
    selectedValue: T,
    anchorBounds: Rect?,
    returnFocusKey: String,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val selectedIndex = options.indexOfFirst { it.value == selectedValue }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val selectedFocusRequester = remember(selectedIndex) { FocusRequester() }
    val popupFocusRequester = rememberTvDialogFocusTrap(returnFocusKey = returnFocusKey)
    val density = LocalDensity.current
    val isLightTheme = LocalIsLightTheme.current
    val panelBackground = if (isLightTheme) Color(0xFFF7F8FA) else Color(0xFA1A2028)
    val panelBorder = if (isLightTheme) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.18f)
    val titleColor = if (isLightTheme) Color(0xFF61666D) else Color(0xBFFFFFFF)
    val positionProvider = remember(anchorBounds, density) {
        SettingsChoicePopupPositionProvider(
            targetBounds = anchorBounds,
            gapPx = with(density) { 6.dp.roundToPx() },
            edgePaddingPx = with(density) { 18.dp.roundToPx() },
        )
    }

    Popup(
        onDismissRequest = onDismissRequest,
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = true,
        ),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .width(264.dp)
                .focusRequester(popupFocusRequester)
                .focusGroup()
                .focusProperties { onExit = { cancelFocusChange() } }
                .shadow(8.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(panelBackground)
                .border(1.dp, panelBorder, RoundedCornerShape(10.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 264.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(3.dp),
            ) {
                itemsIndexed(
                    items = options,
                    key = { _, option -> option.label },
                ) { index, option ->
                    SettingsChoiceMenuRow(
                        text = option.label,
                        selected = index == selectedIndex,
                        onClick = { onSelect(option.value) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (index == selectedIndex) {
                                    Modifier.focusRequester(selectedFocusRequester)
                                } else {
                                    Modifier
                                }
                            ),
                    )
                }
            }
        }
    }

    LaunchedEffect(selectedIndex, options.size) {
        if (options.isNotEmpty()) {
            listState.scrollToItem(selectedIndex)
            withFrameNanos { }
            runCatching { selectedFocusRequester.requestFocus() }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsChoiceMenuRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = LocalTvSemanticColors.current
    val shape = RoundedCornerShape(7.dp)
    val contentColor = if (focused) colors.focusContent else colors.primaryText

    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) colors.surfaceSelected else Color.Transparent,
            focusedContainerColor = colors.focusContainer,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, colors.accent),
                shape = shape,
            ),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = text,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "当前选项",
                    tint = if (focused) contentColor else colors.accent,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Spacer(Modifier.size(18.dp))
            }
        }
    }
}

internal class SettingsChoicePopupPositionProvider(
    private val targetBounds: Rect?,
    private val gapPx: Int,
    private val edgePaddingPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val target = targetBounds?.let { bounds ->
            IntRect(
                left = bounds.left.roundToInt(),
                top = bounds.top.roundToInt(),
                right = bounds.right.roundToInt(),
                bottom = bounds.bottom.roundToInt(),
            )
        } ?: anchorBounds
        val desiredX = target.right - popupContentSize.width
        val belowY = target.bottom + gapPx
        val aboveY = target.top - popupContentSize.height - gapPx
        val desiredY = if (belowY + popupContentSize.height <= windowSize.height - edgePaddingPx) {
            belowY
        } else {
            aboveY
        }
        val maxX = (windowSize.width - popupContentSize.width - edgePaddingPx)
            .coerceAtLeast(edgePaddingPx)
        val maxY = (windowSize.height - popupContentSize.height - edgePaddingPx)
            .coerceAtLeast(edgePaddingPx)
        return IntOffset(
            x = desiredX.coerceIn(edgePaddingPx, maxX),
            y = desiredY.coerceIn(edgePaddingPx, maxY),
        )
    }
}
