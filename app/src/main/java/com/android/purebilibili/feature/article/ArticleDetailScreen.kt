package com.android.purebilibili.feature.article

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.data.repository.ArticleDetailUiModel
import com.android.purebilibili.data.repository.ArticleRepository
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog

private const val ARTICLE_BANNER_CORNER_RADIUS_DP = 20f
private const val ARTICLE_BODY_IMAGE_CORNER_RADIUS_DP = 18f

private sealed interface ArticleDetailUiState {
    data object Loading : ArticleDetailUiState
    data class Success(val article: ArticleDetailUiModel) : ArticleDetailUiState
    data class Error(val message: String) : ArticleDetailUiState
}

private data class ArticleImagePreviewRequest(
    val images: List<String>,
    val initialIndex: Int,
    val sourceRect: Rect?,
    val sourceCornerRadiusDp: Float
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ArticleDetailScreen(
    articleId: Long,
    initialTitle: String,
    transitionEnabled: Boolean = false,
    onBack: (Boolean) -> Unit,
    onUserClick: (Long) -> Unit
) {
    var retryToken by rememberSaveable { mutableIntStateOf(0) }
    val backLabel = stringResource(R.string.common_back)
    val retryLabel = stringResource(R.string.common_retry)
    val loadFailedMessage = stringResource(R.string.dynamic_detail_load_failed)
    val uiState by produceState<ArticleDetailUiState>(
        initialValue = ArticleDetailUiState.Loading,
        key1 = articleId,
        key2 = retryToken
    ) {
        value = ArticleDetailUiState.Loading
        value = ArticleRepository.getArticleDetail(articleId).fold(
            onSuccess = { article -> ArticleDetailUiState.Success(article) },
            onFailure = { error ->
                ArticleDetailUiState.Error(error.message ?: loadFailedMessage)
            }
        )
    }

    val screenTitle = when (val state = uiState) {
        is ArticleDetailUiState.Success -> state.article.title
        else -> initialTitle.ifBlank { "专栏详情" }
    }
    var sharedReturnReady by remember { mutableStateOf(false) }

    BackHandler {
        onBack(sharedReturnReady)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { onBack(sharedReturnReady) }) {
                        Icon(rememberAppBackIcon(), contentDescription = backLabel)
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            ArticleDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is ArticleDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { retryToken++ }) {
                            Text(retryLabel)
                        }
                    }
                }
            }

            is ArticleDetailUiState.Success -> {
                ArticleDetailContent(
                    article = state.article,
                    paddingValues = paddingValues,
                    transitionEnabled = transitionEnabled,
                    onSharedReturnReadyChange = { sharedReturnReady = it },
                    onUserClick = onUserClick
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ArticleDetailContent(
    article: ArticleDetailUiModel,
    paddingValues: PaddingValues,
    transitionEnabled: Boolean,
    onSharedReturnReadyChange: (Boolean) -> Unit,
    onUserClick: (Long) -> Unit
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val listState = rememberLazyListState()
    val sharedTransitionEnabled = transitionEnabled &&
        sharedTransitionScope != null &&
        animatedVisibilityScope != null &&
        article.articleId > 0L
    val coverTransitionKey = remember(article.articleId) {
        resolveArticleSharedTransitionKey(article.articleId, ArticleSharedElementSlot.COVER)
    }
    val sharedReturnReady = remember(listState.firstVisibleItemIndex) {
        shouldEnableArticleSharedReturn(
            firstVisibleItemIndex = listState.firstVisibleItemIndex
        )
    }
    val bodyImageUrls = remember(article.blocks) {
        collectArticleBodyImageUrls(article.blocks)
    }
    val hasBannerImage = !article.bannerUrl.isNullOrBlank()
    val previewImages = remember(article.bannerUrl, bodyImageUrls) {
        buildList {
            article.bannerUrl?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(bodyImageUrls)
        }
    }
    val bodyImageIndexOffset = if (hasBannerImage) 1 else 0
    val bottomSafeAreaPadding = resolveArticleDetailBottomPadding(
        navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    )
    var bannerSourceRect by remember(article.bannerUrl) {
        mutableStateOf<Rect?>(null)
    }
    val bodyImageSourceRects = remember(article.blocks) {
        mutableStateMapOf<Int, Rect>()
    }
    var imagePreviewRequest by remember(article.bannerUrl, article.blocks) {
        mutableStateOf<ArticleImagePreviewRequest?>(null)
    }
    val baseBannerModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .onGloballyPositioned { coordinates ->
            bannerSourceRect = coordinates.boundsInWindow()
        }
        .clickable(enabled = previewImages.isNotEmpty()) {
            imagePreviewRequest = ArticleImagePreviewRequest(
                images = previewImages,
                initialIndex = 0,
                sourceRect = bannerSourceRect,
                sourceCornerRadiusDp = ARTICLE_BANNER_CORNER_RADIUS_DP
            )
        }
    val bannerModifier = if (sharedTransitionEnabled && !article.bannerUrl.isNullOrBlank()) {
        with(requireNotNull(sharedTransitionScope)) {
            baseBannerModifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = coverTransitionKey),
                animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                boundsTransform = { _, _ -> spring(dampingRatio = 0.82f, stiffness = 260f) },
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(20.dp))
            )
        }
    } else {
        baseBannerModifier
    }
    LaunchedEffect(sharedReturnReady) {
        onSharedReturnReadyChange(sharedReturnReady)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .responsiveContentWidth(),
        state = listState,
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 12.dp,
            end = 20.dp,
            bottom = bottomSafeAreaPadding
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!article.bannerUrl.isNullOrBlank()) {
            item {
                AsyncImage(
                    model = article.bannerUrl,
                    contentDescription = article.title,
                    modifier = bannerModifier,
                    contentScale = ContentScale.FillWidth
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (article.authorName.isNotBlank() || article.publishTime.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (article.authorFace.isNotBlank()) {
                            AsyncImage(
                                model = article.authorFace,
                                contentDescription = article.authorName,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = article.authorMid > 0) {
                                        if (article.authorMid > 0) onUserClick(article.authorMid)
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column(
                            modifier = Modifier.padding(start = if (article.authorFace.isNotBlank()) 12.dp else 0.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (article.authorName.isNotBlank()) {
                                Text(
                                    text = article.authorName,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.clickable(enabled = article.authorMid > 0) {
                                        if (article.authorMid > 0) onUserClick(article.authorMid)
                                    }
                                )
                            }
                            if (article.publishTime.isNotBlank()) {
                                Text(
                                    text = article.publishTime,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (article.summary.isNotBlank() && article.blocks.none { it is ArticleContentBlock.Paragraph && it.text == article.summary }) {
                    Text(
                        text = article.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        itemsIndexed(article.blocks) { index, block ->
            when (block) {
                is ArticleContentBlock.Heading -> {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                is ArticleContentBlock.Paragraph -> {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                is ArticleContentBlock.Image -> {
                    AsyncImage(
                        model = block.url,
                        contentDescription = "${article.title}-$index",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .onGloballyPositioned { coordinates ->
                                bodyImageSourceRects[index] = coordinates.boundsInWindow()
                            }
                            .clickable {
                                val payload = resolveArticleImagePreviewPayload(
                                    blocks = article.blocks,
                                    tappedBlockIndex = index
                                ) ?: return@clickable
                                imagePreviewRequest = ArticleImagePreviewRequest(
                                    images = previewImages,
                                    initialIndex = payload.initialIndex + bodyImageIndexOffset,
                                    sourceRect = bodyImageSourceRects[index],
                                    sourceCornerRadiusDp = ARTICLE_BODY_IMAGE_CORNER_RADIUS_DP
                                )
                            },
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }
    }

    imagePreviewRequest?.let { request ->
        ImagePreviewDialog(
            images = request.images,
            initialIndex = request.initialIndex,
            sourceRect = request.sourceRect,
            sourceCornerRadiusDp = request.sourceCornerRadiusDp,
            onDismiss = { imagePreviewRequest = null }
        )
    }
}
