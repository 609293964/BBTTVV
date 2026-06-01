package com.bbttvv.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.bbttvv.app.data.model.response.ViewInfo
import com.bbttvv.app.ui.theme.LocalIsLightTheme

private const val DetailPreviewBackgroundCoverWidthPx = 320
private const val DetailPreviewBackgroundCoverHeightPx = 180
private const val DetailPreviewCoverWidthPx = 640
private const val DetailPreviewCoverHeightPx = 360

@Composable
internal fun DetailPreviewShell(
    previewInfo: ViewInfo,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isLightTheme = LocalIsLightTheme.current
    val pageBackgroundColor = if (isLightTheme) Color(0xFFF4F6F8) else Color(0xFF141414)
    val primaryTextColor = if (isLightTheme) Color(0xFF18191C) else Color.White
    val mutedTextColor = if (isLightTheme) Color(0xFF61666D) else DetailMutedTextColor
    val placeholderColor = if (isLightTheme) Color(0x0C000000) else Color.White.copy(alpha = 0.08f)
    val backgroundCoverModel = remember(context, previewInfo.pic) {
        buildSizedImageRequest(
            context,
            previewInfo.pic,
            DetailPreviewBackgroundCoverWidthPx,
            DetailPreviewBackgroundCoverHeightPx
        )
    }
    val coverModel = remember(context, previewInfo.pic) {
        buildSizedImageRequest(
            context,
            previewInfo.pic,
            DetailPreviewCoverWidthPx,
            DetailPreviewCoverHeightPx
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackgroundColor)
    ) {
        DetailCoverBackdrop(model = backgroundCoverModel)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 56.dp, top = 64.dp, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = previewInfo.title.replace("\n", " "),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    maxLines = 2,
                    lineHeight = 38.sp,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = previewInfo.owner.name.ifBlank { "正在加载详情" },
                    fontSize = 16.sp,
                    color = mutedTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                DetailPreviewMetricsRow(previewInfo, mutedTextColor)
                DetailPreviewPillRow(placeholderColor)
                Spacer(modifier = Modifier.height(16.dp))
                DetailPreviewLine(width = 360.dp, color = placeholderColor)
                DetailPreviewLine(width = 520.dp, color = placeholderColor)
                DetailPreviewLine(width = 440.dp, color = placeholderColor)
            }

            AsyncImage(
                model = coverModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                modifier = Modifier
                    .width(360.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(placeholderColor)
            )
        }
    }
}

@Composable
private fun DetailPreviewMetricsRow(
    previewInfo: ViewInfo,
    textColor: Color
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailPreviewMetric(text = "${formatNumber(previewInfo.stat.view)}播放", color = textColor)
        DetailPreviewMetric(text = "${formatNumber(previewInfo.stat.danmaku)}弹幕", color = textColor)
        if (previewInfo.stat.reply > 0) {
            DetailPreviewMetric(text = "${formatNumber(previewInfo.stat.reply)}评论", color = textColor)
        }
    }
}

@Composable
private fun DetailPreviewMetric(
    text: String,
    color: Color
) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = color,
        maxLines = 1
    )
}

@Composable
private fun DetailPreviewPillRow(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailPreviewPill(width = 88.dp, color = color)
        DetailPreviewPill(width = 72.dp, color = color)
        DetailPreviewPill(width = 72.dp, color = color)
        DetailPreviewPill(width = 72.dp, color = color)
    }
}

@Composable
private fun DetailPreviewPill(
    width: Dp,
    color: Color
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(color)
    )
}

@Composable
private fun DetailPreviewLine(
    width: Dp,
    color: Color
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(12.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(color)
    )
}
