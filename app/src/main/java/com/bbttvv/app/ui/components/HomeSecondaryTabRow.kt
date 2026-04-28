package com.bbttvv.app.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeSecondaryTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onSelectedTabConfirmed: (Int) -> Unit = onTabSelected,
    onTabFocused: (Int) -> Unit = {},
    itemFocusRequesters: List<FocusRequester> = emptyList(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 32.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(10.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    onDpadUp: (() -> Boolean)? = null,
    onDpadDown: (() -> Boolean)? = null,
) {
    if (tabs.isEmpty()) return

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp),
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment
    ) {
        itemsIndexed(
            items = tabs,
            key = { index, label -> "$index:$label" },
            contentType = { _, _ -> "home_secondary_tab" }
        ) { index, label ->
            HomeSecondaryTab(
                label = label,
                selected = index == selectedIndex,
                first = index == 0,
                last = index == tabs.lastIndex,
                focusRequester = itemFocusRequesters.getOrNull(index),
                onClick = {
                    if (index == selectedIndex) {
                        onSelectedTabConfirmed(index)
                    } else {
                        onTabSelected(index)
                    }
                },
                onFocus = { onTabFocused(index) },
                onDpadUp = onDpadUp,
                onDpadDown = onDpadDown,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeSecondaryTab(
    label: String,
    selected: Boolean,
    first: Boolean,
    last: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    onDpadUp: (() -> Boolean)?,
    onDpadDown: (() -> Boolean)?,
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { keyEvent ->
                val event = keyEvent.nativeKeyEvent
                if (event.action != AndroidKeyEvent.ACTION_DOWN) {
                    return@onPreviewKeyEvent false
                }
                when (event.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> onDpadUp?.invoke() == true
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> onDpadDown?.invoke() == true
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> first
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> last
                    else -> false
                }
            }
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (focusState.isFocused) {
                    onFocus()
                }
            },
        shape = ClickableSurfaceDefaults.shape(
            shape = CircleShape,
            focusedShape = CircleShape
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent
        ),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None, Glow.None)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    color = when {
                        isFocused -> Color.White
                        else -> Color(0x22000000)
                    },
                    shape = CircleShape
                )
                .padding(horizontal = 15.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = when {
                    isFocused -> Color.Black
                    selected -> Color.White
                    else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f)
                },
                fontSize = 15.sp,
                fontWeight = if (isFocused || selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
