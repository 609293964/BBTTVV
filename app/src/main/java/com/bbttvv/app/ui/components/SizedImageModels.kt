package com.bbttvv.app.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import coil.size.Precision
import com.bbttvv.app.core.util.FormatUtils

@Composable
fun rememberSizedImageModel(
    url: String,
    widthPx: Int,
    heightPx: Int
): ImageRequest {
    val context = LocalContext.current
    return remember(context, url, widthPx, heightPx) {
        buildSizedImageModel(
            context = context,
            url = url,
            widthPx = widthPx,
            heightPx = heightPx
        )
    }
}

fun buildSizedImageModel(
    context: Context,
    url: String,
    widthPx: Int,
    heightPx: Int
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(FormatUtils.buildSizedImageUrl(normalizeSizedImageUrl(url), widthPx, heightPx))
        .crossfade(false)
        .allowHardware(true)
        .precision(Precision.INEXACT)
        .size(widthPx, heightPx)
        .build()
}

private fun normalizeSizedImageUrl(url: String): String {
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> url.replaceFirst("http://", "https://")
        else -> url
    }
}
