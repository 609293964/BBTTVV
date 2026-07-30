@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bbttvv.app.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.bbttvv.app.data.model.response.ReplyPicture
import com.bbttvv.app.ui.input.isTvConfirmKey
import kotlin.math.roundToInt

private const val COMMENT_PICTURE_FALLBACK_ASPECT_RATIO = 16f / 9f
private const val COMMENT_PICTURE_THUMBNAIL_MAX_EDGE_PX = 480
private const val COMMENT_PICTURE_VIEWER_MAX_WIDTH_PX = 1920
private const val COMMENT_PICTURE_VIEWER_MAX_HEIGHT_PX = 1080

internal data class CommentPictureUiModel(
    val url: String,
    val aspectRatio: Float,
)

internal data class CommentPictureRequestSize(
    val widthPx: Int,
    val heightPx: Int,
)

internal fun List<ReplyPicture>?.toCommentPictureUiModels(): List<CommentPictureUiModel> {
    return orEmpty().mapNotNull { picture ->
        val normalizedUrl = normalizeCommentPictureUrl(picture.imgSrc) ?: return@mapNotNull null
        val aspectRatio = if (picture.imgWidth > 0 && picture.imgHeight > 0) {
            picture.imgWidth.toFloat() / picture.imgHeight.toFloat()
        } else {
            COMMENT_PICTURE_FALLBACK_ASPECT_RATIO
        }
        CommentPictureUiModel(
            url = normalizedUrl,
            aspectRatio = aspectRatio.takeIf { it.isFinite() && it > 0f }
                ?: COMMENT_PICTURE_FALLBACK_ASPECT_RATIO,
        )
    }
}

private fun normalizeCommentPictureUrl(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null
    return when {
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("http://") -> trimmed.replaceFirst("http://", "https://")
        else -> trimmed
    }
}

internal fun resolveCommentPictureThumbnailSize(
    aspectRatio: Float,
    maxEdgePx: Int = COMMENT_PICTURE_THUMBNAIL_MAX_EDGE_PX,
): CommentPictureRequestSize {
    val safeRatio = aspectRatio.takeIf { it.isFinite() && it > 0f }
        ?: COMMENT_PICTURE_FALLBACK_ASPECT_RATIO
    val safeMaxEdge = maxEdgePx.coerceAtLeast(1)
    return if (safeRatio >= 1f) {
        CommentPictureRequestSize(
            widthPx = safeMaxEdge,
            heightPx = (safeMaxEdge / safeRatio).roundToInt().coerceAtLeast(1),
        )
    } else {
        CommentPictureRequestSize(
            widthPx = (safeMaxEdge * safeRatio).roundToInt().coerceAtLeast(1),
            heightPx = safeMaxEdge,
        )
    }
}

internal fun resolveCommentPictureViewerSize(
    aspectRatio: Float,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
): CommentPictureRequestSize {
    val safeRatio = aspectRatio.takeIf { it.isFinite() && it > 0f }
        ?: COMMENT_PICTURE_FALLBACK_ASPECT_RATIO
    val widthLimit = viewportWidthPx.coerceIn(1, COMMENT_PICTURE_VIEWER_MAX_WIDTH_PX)
    val heightLimit = viewportHeightPx.coerceIn(1, COMMENT_PICTURE_VIEWER_MAX_HEIGHT_PX)
    val widthFromHeight = (heightLimit * safeRatio).roundToInt().coerceAtLeast(1)
    return if (widthFromHeight <= widthLimit) {
        CommentPictureRequestSize(widthPx = widthFromHeight, heightPx = heightLimit)
    } else {
        CommentPictureRequestSize(
            widthPx = widthLimit,
            heightPx = (widthLimit / safeRatio).roundToInt().coerceAtLeast(1),
        )
    }
}

internal data class CommentPictureConfirmState(
    val longPressHandled: Boolean = false,
)

internal data class CommentPictureConfirmTransition(
    val state: CommentPictureConfirmState,
    val consumed: Boolean,
    val openPictures: Boolean,
)

internal enum class CommentCardShortConfirmAction {
    OpenReplies,
    OpenPictures,
    None,
}

internal fun resolveCommentCardShortConfirmAction(
    hasReplies: Boolean,
    hasPictures: Boolean,
    replyNavigationEnabled: Boolean,
): CommentCardShortConfirmAction {
    return when {
        replyNavigationEnabled && hasReplies -> CommentCardShortConfirmAction.OpenReplies
        hasPictures -> CommentCardShortConfirmAction.OpenPictures
        else -> CommentCardShortConfirmAction.None
    }
}

internal fun resolveCommentPictureConfirmEvent(
    state: CommentPictureConfirmState,
    isKeyDown: Boolean,
    repeatCount: Int,
    hasPictures: Boolean,
): CommentPictureConfirmTransition {
    if (!hasPictures) {
        return CommentPictureConfirmTransition(
            state = CommentPictureConfirmState(),
            consumed = false,
            openPictures = false,
        )
    }
    if (isKeyDown && repeatCount <= 0) {
        return CommentPictureConfirmTransition(
            state = CommentPictureConfirmState(),
            consumed = false,
            openPictures = false,
        )
    }
    if (isKeyDown) {
        return CommentPictureConfirmTransition(
            state = CommentPictureConfirmState(longPressHandled = true),
            consumed = true,
            openPictures = !state.longPressHandled,
        )
    }
    if (state.longPressHandled) {
        return CommentPictureConfirmTransition(
            state = CommentPictureConfirmState(),
            consumed = true,
            openPictures = false,
        )
    }
    return CommentPictureConfirmTransition(
        state = CommentPictureConfirmState(),
        consumed = false,
        openPictures = false,
    )
}

@Composable
internal fun commentPictureConfirmModifier(
    hasPictures: Boolean,
    onOpenPicturesFromLongPress: () -> Unit,
): Modifier {
    var confirmState by remember(hasPictures) { mutableStateOf(CommentPictureConfirmState()) }
    return Modifier.onPreviewKeyEvent { keyEvent ->
        val nativeEvent = keyEvent.nativeKeyEvent
        if (!isTvConfirmKey(nativeEvent.keyCode)) return@onPreviewKeyEvent false
        val transition = resolveCommentPictureConfirmEvent(
            state = confirmState,
            isKeyDown = nativeEvent.action == AndroidKeyEvent.ACTION_DOWN,
            repeatCount = nativeEvent.repeatCount,
            hasPictures = hasPictures,
        )
        confirmState = transition.state
        if (transition.openPictures) {
            onOpenPicturesFromLongPress()
        }
        transition.consumed
    }
}

internal data class CommentImageViewerState(
    val pictures: List<CommentPictureUiModel>,
    val currentIndex: Int,
    val sourceKey: String,
    val suppressInitialConfirmKeyUp: Boolean = false,
) {
    init {
        require(pictures.isNotEmpty())
    }

    val currentPicture: CommentPictureUiModel
        get() = pictures[currentIndex.coerceIn(0, pictures.lastIndex)]

    fun move(delta: Int): CommentImageViewerState {
        if (pictures.isEmpty()) return this
        return copy(currentIndex = (currentIndex + delta).coerceIn(0, pictures.lastIndex))
    }
}

internal fun createCommentImageViewerState(
    pictures: List<CommentPictureUiModel>,
    sourceKey: String,
    suppressInitialConfirmKeyUp: Boolean,
): CommentImageViewerState? {
    if (pictures.isEmpty()) return null
    return CommentImageViewerState(
        pictures = pictures,
        currentIndex = 0,
        sourceKey = sourceKey,
        suppressInitialConfirmKeyUp = suppressInitialConfirmKeyUp,
    )
}

internal enum class CommentImageViewerKeyAction {
    Previous,
    Next,
    Dismiss,
    None,
}

internal data class CommentImageViewerKeyTransition(
    val action: CommentImageViewerKeyAction,
    val consumed: Boolean,
    val suppressInitialConfirmKeyUp: Boolean,
)

internal fun resolveCommentImageViewerKeyEvent(
    keyCode: Int,
    isKeyDown: Boolean,
    suppressInitialConfirmKeyUp: Boolean,
): CommentImageViewerKeyTransition {
    return when (keyCode) {
        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> CommentImageViewerKeyTransition(
            action = if (isKeyDown) CommentImageViewerKeyAction.Previous else CommentImageViewerKeyAction.None,
            consumed = true,
            suppressInitialConfirmKeyUp = suppressInitialConfirmKeyUp,
        )

        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> CommentImageViewerKeyTransition(
            action = if (isKeyDown) CommentImageViewerKeyAction.Next else CommentImageViewerKeyAction.None,
            consumed = true,
            suppressInitialConfirmKeyUp = suppressInitialConfirmKeyUp,
        )

        AndroidKeyEvent.KEYCODE_DPAD_UP,
        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
        -> CommentImageViewerKeyTransition(
            action = CommentImageViewerKeyAction.None,
            consumed = true,
            suppressInitialConfirmKeyUp = suppressInitialConfirmKeyUp,
        )

        else -> {
            if (!isTvConfirmKey(keyCode)) {
                CommentImageViewerKeyTransition(
                    action = CommentImageViewerKeyAction.None,
                    consumed = false,
                    suppressInitialConfirmKeyUp = suppressInitialConfirmKeyUp,
                )
            } else if (isKeyDown) {
                CommentImageViewerKeyTransition(
                    action = CommentImageViewerKeyAction.None,
                    consumed = true,
                    suppressInitialConfirmKeyUp = suppressInitialConfirmKeyUp,
                )
            } else if (suppressInitialConfirmKeyUp) {
                CommentImageViewerKeyTransition(
                    action = CommentImageViewerKeyAction.None,
                    consumed = true,
                    suppressInitialConfirmKeyUp = false,
                )
            } else {
                CommentImageViewerKeyTransition(
                    action = CommentImageViewerKeyAction.Dismiss,
                    consumed = true,
                    suppressInitialConfirmKeyUp = false,
                )
            }
        }
    }
}

@Composable
internal fun CommentPictureThumbnailRow(
    pictures: List<CommentPictureUiModel>,
    modifier: Modifier = Modifier,
    thumbnailHeight: Dp = 96.dp,
    maxVisible: Int = 3,
) {
    val visiblePictures = pictures.take(maxVisible.coerceAtLeast(0))
    if (visiblePictures.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visiblePictures.forEachIndexed { index, picture ->
            val size = remember(picture.aspectRatio) {
                resolveCommentPictureThumbnailSize(picture.aspectRatio)
            }
            Box {
                AsyncImage(
                    model = rememberSizedImageModel(
                        url = picture.url,
                        widthPx = size.widthPx,
                        heightPx = size.heightPx,
                    ),
                    contentDescription = "评论图片 ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(thumbnailHeight)
                        .aspectRatio(picture.aspectRatio)
                        .widthIn(max = 180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                )
                if (index == visiblePictures.lastIndex && pictures.size > visiblePictures.size) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.58f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+${pictures.size - visiblePictures.size}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CommentPictureCompactPreview(
    pictures: List<CommentPictureUiModel>,
    modifier: Modifier = Modifier,
) {
    val firstPicture = pictures.firstOrNull() ?: return
    val size = remember(firstPicture.aspectRatio) {
        resolveCommentPictureThumbnailSize(firstPicture.aspectRatio)
    }
    Box(
        modifier = modifier
            .width(118.dp)
            .height(66.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        AsyncImage(
            model = rememberSizedImageModel(
                url = firstPicture.url,
                widthPx = size.widthPx,
                heightPx = size.heightPx,
            ),
            contentDescription = "评论图片",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (pictures.size > 1) {
            Text(
                text = "共 ${pictures.size} 张",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(topStart = 7.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
internal fun CommentImageViewer(
    state: CommentImageViewerState,
    onStateChanged: (CommentImageViewerState) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val dialogFocusRequester = rememberTvDialogFocusTrap()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val viewportWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val viewportHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    var suppressInitialConfirmKeyUp by remember(state.sourceKey) {
        mutableStateOf(state.suppressInitialConfirmKeyUp)
    }
    var isLoading by remember(state.currentIndex, state.currentPicture.url) { mutableStateOf(true) }
    var loadFailed by remember(state.currentIndex, state.currentPicture.url) { mutableStateOf(false) }
    val requestSize = remember(
        state.currentPicture.aspectRatio,
        viewportWidthPx,
        viewportHeightPx,
    ) {
        resolveCommentPictureViewerSize(
            aspectRatio = state.currentPicture.aspectRatio,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
        )
    }

    BackHandler(onBack = onDismissRequest)
    LaunchedEffect(state.sourceKey) {
        runCatching { dialogFocusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .focusRequester(dialogFocusRequester)
                .focusGroup()
                .focusProperties {
                    onExit = { cancelFocusChange() }
                }
                .onPreviewKeyEvent { keyEvent ->
                    val event = keyEvent.nativeKeyEvent
                    val transition = resolveCommentImageViewerKeyEvent(
                        keyCode = event.keyCode,
                        isKeyDown = event.action == AndroidKeyEvent.ACTION_DOWN,
                        suppressInitialConfirmKeyUp = suppressInitialConfirmKeyUp,
                    )
                    suppressInitialConfirmKeyUp = transition.suppressInitialConfirmKeyUp
                    when (transition.action) {
                        CommentImageViewerKeyAction.Previous -> onStateChanged(state.move(-1))
                        CommentImageViewerKeyAction.Next -> onStateChanged(state.move(1))
                        CommentImageViewerKeyAction.Dismiss -> onDismissRequest()
                        CommentImageViewerKeyAction.None -> Unit
                    }
                    transition.consumed
                }
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = rememberSizedImageModel(
                    url = state.currentPicture.url,
                    widthPx = requestSize.widthPx,
                    heightPx = requestSize.heightPx,
                ),
                contentDescription = "评论图片 ${state.currentIndex + 1}",
                contentScale = ContentScale.Fit,
                onLoading = {
                    isLoading = true
                    loadFailed = false
                },
                onSuccess = {
                    isLoading = false
                    loadFailed = false
                },
                onError = {
                    isLoading = false
                    loadFailed = true
                },
                modifier = Modifier
                    .fillMaxSize(0.90f),
            )

            if (isLoading || loadFailed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.42f)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(16.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (loadFailed) "图片加载失败" else "图片加载中…",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 18.sp,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "${state.currentIndex + 1} / ${state.pictures.size}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "左右切换 · 确认或返回关闭",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}
