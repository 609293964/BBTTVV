package com.bbttvv.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Text
import com.bbttvv.app.ui.components.TvDialogActionButton
import com.bbttvv.app.ui.components.rememberTvDialogFocusTrap
import com.bbttvv.app.ui.theme.LocalIsLightTheme
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
                .width(300.dp)
                .focusRequester(popupFocusRequester)
                .focusGroup()
                .focusProperties { onExit = { cancelFocusChange() } }
                .shadow(16.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(panelBackground)
                .border(1.dp, panelBorder, RoundedCornerShape(14.dp))
                .padding(10.dp),
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
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 1.dp),
            ) {
                itemsIndexed(
                    items = options,
                    key = { _, option -> option.label },
                ) { index, option ->
                    TvDialogActionButton(
                        text = if (index == selectedIndex) "当前 · ${option.label}" else option.label,
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
                        minWidth = 0.dp,
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
