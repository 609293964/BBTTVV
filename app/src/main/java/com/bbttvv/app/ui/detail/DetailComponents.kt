package com.bbttvv.app.ui.detail

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.request.ImageRequest
import coil.size.Precision
import com.bbttvv.app.core.util.FormatUtils
import java.util.Locale

internal val DetailBackdropExtraScrimColor = Color(0x33000000)
internal val DetailBackdropGradientColors = listOf(
    Color(0xD8141414),
    Color(0xB8141414),
    Color(0xE0141414)
)
internal val DetailPrimaryPillColor = Color(0xFFF4F6F8)
internal val DetailPrimaryTextColor = Color(0xFF121212)
internal val DetailSecondaryPillColor = Color(0x22000000)
internal val DetailCardColor = Color(0x22000000)
internal val DetailAccentColor = Color(0xFFFB7299)
internal val DetailMutedTextColor = Color(0xFFB7B7B7)

internal data class DetailPillMetrics(
    val height: Dp,
    val minWidth: Dp,
    val horizontalPadding: Dp,
    val textSize: TextUnit,
    val iconSpacing: Dp,
    val focusedScale: Float
)

internal val DetailDefaultPillMetrics = DetailPillMetrics(
    height = 44.dp,
    minWidth = 74.dp,
    horizontalPadding = 16.dp,
    textSize = 14.sp,
    iconSpacing = 8.dp,
    focusedScale = 1.0f
)

internal val DetailCompactActionPillMetrics = DetailPillMetrics(
    height = 32.dp,
    minWidth = 58.dp,
    horizontalPadding = 16.dp,
    textSize = 14.sp,
    iconSpacing = 8.dp,
    focusedScale = 1.0f
)

@Composable
internal fun DetailMessageCard(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DetailCardColor)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(text = text, color = DetailMutedTextColor, fontSize = 14.sp)
    }
}

@Composable
internal fun DetailPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    active: Boolean = false,
    metrics: DetailPillMetrics = DetailDefaultPillMetrics,
    focusRequester: FocusRequester? = null,
    leadingContent: (@Composable (Color) -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val contentColor = when {
        selected || isFocused -> DetailPrimaryTextColor
        active -> DetailAccentColor
        else -> Color.White
    }
    val composedModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else {
        modifier
    }

    val containerColor = when {
        selected || isFocused -> DetailPrimaryPillColor
        else -> Color.Transparent
    }

    val borderWidth = if (selected || isFocused) 0.dp else 1.5.dp
    val borderColor = if (selected || isFocused) Color.Transparent else Color(0x66FFFFFF)

    Box(
        modifier = composedModifier
            .height(metrics.height)
            .defaultMinSize(minWidth = metrics.minWidth)
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(999.dp)
            )
            .onFocusChanged { focusState -> isFocused = focusState.hasFocus }
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = metrics.horizontalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingContent?.let { content ->
                content(contentColor)
                Spacer(modifier = Modifier.width(metrics.iconSpacing))
            }
            Text(
                text = label,
                color = contentColor,
                fontSize = metrics.textSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun DetailDisabledPill(
    label: String,
    metrics: DetailPillMetrics = DetailDefaultPillMetrics
) {
    Box(
        modifier = Modifier
            .height(metrics.height)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Transparent)
            .border(
                width = 1.5.dp,
                color = Color(0x33FFFFFF),
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = metrics.horizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = metrics.textSize,
            fontWeight = FontWeight.Medium
        )
    }
}

internal fun buildSizedImageRequest(
    context: Context,
    url: String,
    widthPx: Int,
    heightPx: Int
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(FormatUtils.buildSizedImageUrl(url, widthPx, heightPx))
        .crossfade(false)
        .allowHardware(true)
        .precision(Precision.INEXACT)
        .size(widthPx, heightPx)
        .build()
}

internal fun formatNumber(number: Int): String {
    return if (number >= 10000) {
        String.format(Locale.US, "%.1fw", number / 10000.0)
    } else {
        number.toString()
    }
}

internal fun normalizeImageUrl(url: String): String {
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> url.replaceFirst("http://", "https://")
        else -> url
    }
}
