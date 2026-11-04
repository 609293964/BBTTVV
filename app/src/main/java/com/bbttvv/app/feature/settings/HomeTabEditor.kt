package com.bbttvv.app.feature.settings

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.HomeTabLayout
import com.bbttvv.app.ui.components.TvDialog
import com.bbttvv.app.ui.components.TvDialogActionButton
import com.bbttvv.app.ui.theme.LocalTvOverlayPalette

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun HomeTabEditor(
    initialLayout: HomeTabLayout,
    onDismiss: () -> Unit,
    onSave: (HomeTabLayout) -> Unit,
) {
    var order by remember { mutableStateOf(initialLayout.order) }
    var visible by remember { mutableStateOf(initialLayout.visible) }
    val orderedTabs = order
    val visibleTabs = visible
    val listState = rememberLazyListState()
    TvDialog(
        title = "编辑导航栏",
        onDismissRequest = onDismiss,
        returnFocusKey = "settings_home_tabs",
        returnFocusFallbackKeys = listOf("settings:back"),
        content = {
            Text("上下选择栏目，左右调整顺序，确认切换显示。末行保存，返回取消。", color = LocalTvOverlayPalette.current.title)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 210.dp),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(orderedTabs, key = { it.stableId }) { tab ->
                    val name = when (tab) {
                        AppTopLevelTab.RECOMMEND -> "首页（推荐）"
                        AppTopLevelTab.TODAY_WATCH -> "推荐单（需启用插件）"
                        else -> tab.title
                    }
                    val status = when {
                        tab.alwaysVisible -> "固定显示"
                        tab in visibleTabs -> "显示"
                        else -> "隐藏"
                    }
                    fun toggleVisibility() {
                        if (!tab.alwaysVisible) {
                            visible = if (tab in visible) visible - tab else visible + tab
                        }
                    }
                    TvDialogActionButton(
                        text = "$name · $status",
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { key ->
                            val event = key.nativeKeyEvent
                            val delta = when (event.keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> -1
                                KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                                else -> return@onPreviewKeyEvent false
                            }
                            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                                val index = order.indexOf(tab)
                                val next = index + delta
                                if (next in order.indices) {
                                    listState.requestScrollToItem(next)
                                    order = order.toMutableList().apply { add(next, removeAt(index)) }
                                }
                            }
                            true
                        },
                        onClick = ::toggleVisibility,
                    )
                }
                item(key = "save") {
                    TvDialogActionButton(
                        text = "保存导航栏",
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSave(HomeTabLayout(order, visible)) },
                    )
                }
            }
        },
        actions = {},
    )
}
