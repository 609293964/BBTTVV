package com.bbttvv.app.feature.video.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bbttvv.app.ui.theme.LocalIsLightTheme

@Immutable
internal data class PlayerRightSidebarColors(
    val container: Color,
    val divider: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val focusedContainer: Color,
)

private val LightPlayerRightSidebarColors = PlayerRightSidebarColors(
    container = Color(0xFFF8F9FB),
    divider = Color.Black.copy(alpha = 0.10f),
    primaryText = Color(0xFF18191C),
    secondaryText = Color(0xFF61666D),
    accent = Color(0xFFFB7299),
    focusedContainer = Color.Black.copy(alpha = 0.045f),
)

private val DarkPlayerRightSidebarColors = PlayerRightSidebarColors(
    container = Color(0xFF141518),
    divider = Color.White.copy(alpha = 0.16f),
    primaryText = Color.White,
    secondaryText = Color.White.copy(alpha = 0.72f),
    accent = Color(0xFFFB7299),
    focusedContainer = Color.White.copy(alpha = 0.09f),
)

@Composable
internal fun PlayerRightSidebarScaffold(
    modifier: Modifier = Modifier,
    header: @Composable RowScope.(PlayerRightSidebarColors) -> Unit,
    content: @Composable ColumnScope.(PlayerRightSidebarColors) -> Unit,
) {
    val isLightTheme = LocalIsLightTheme.current
    val colors = if (isLightTheme) LightPlayerRightSidebarColors else DarkPlayerRightSidebarColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(colors.container),
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(colors.divider),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                header(colors)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.divider),
            )
            content(colors)
        }
    }
}
