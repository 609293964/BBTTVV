package com.bbttvv.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.feature.plugin.TodayWatchPluginConfig
import com.bbttvv.app.ui.components.AppTopBarDefaults
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.HomeSecondaryTabRow
import com.bbttvv.app.ui.components.TvConfirmDialog
import com.bbttvv.app.ui.components.TvDialogActionButton

private const val TodayWatchGridColumnCount = 4

@Composable
internal fun TodayWatchScreen(
    plan: TodayWatchPlan,
    config: TodayWatchPluginConfig,
    selectedMode: TodayWatchMode,
    isLoading: Boolean,
    errorMessage: String?,
    onModeActivated: (TodayWatchMode) -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onNotInterested: (VideoItem) -> Unit,
    onVideoFocused: (VideoItem) -> Unit,
    onContentRowFocused: (Int) -> Unit,
    focusCoordinator: HomeFocusCoordinator,
    modifier: Modifier = Modifier
) {
    val previewVideos = remember(plan.videoQueue, config.queuePreviewLimit) {
        plan.videoQueue.take(config.queuePreviewLimit)
    }
    val relaxFocusRequester = remember { FocusRequester() }
    val learnFocusRequester = remember { FocusRequester() }
    val modeFocusRequesters = remember { listOf(relaxFocusRequester, learnFocusRequester) }
    val modes = remember { TodayWatchMode.entries.toList() }
    val modeLabels = remember { modes.map { it.label } }
    val selectedModeIndex = modes.indexOf(selectedMode).let { if (it >= 0) it else 0 }
    val gridFocusState = remember { HomeRecommendGridFocusState() }
    var pendingFeedbackVideo by remember { mutableStateOf<VideoItem?>(null) }

    val requestContentFocus = remember(selectedMode, onContentRowFocused) {
        {
            val requester = if (selectedMode == TodayWatchMode.RELAX) {
                relaxFocusRequester
            } else {
                learnFocusRequester
            }
            runCatching {
                val focused = requester.requestFocus()
                if (focused) {
                    onContentRowFocused(0)
                }
                focused
            }.getOrDefault(false)
        }
    }

    DisposableEffect(focusCoordinator, requestContentFocus) {
        val registration = focusCoordinator.registerContentTarget(
            tab = AppTopLevelTab.TODAY_WATCH,
            region = HomeFocusRegion.ContentTabs,
            target = object : HomeFocusTarget {
                override fun tryRequestFocus(): Boolean {
                    return requestContentFocus()
                }
            }
        )
        onDispose {
            registration.unregister()
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        HomeSecondaryTabRow(
            tabs = modeLabels,
            selectedIndex = selectedModeIndex,
            onTabSelected = { index -> modes.getOrNull(index)?.let(onModeActivated) },
            onSelectedTabConfirmed = { index -> modes.getOrNull(index)?.let(onModeActivated) },
            onTabFocused = { onContentRowFocused(0) },
            itemFocusRequesters = modeFocusRequesters,
            onDpadUp = { focusCoordinator.handleContentTabsDpadUp(AppTopLevelTab.TODAY_WATCH) },
            onDpadDown = {
                focusCoordinator.handleContentTabsDpadDown(AppTopLevelTab.TODAY_WATCH)
            },
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        )

        if (config.showUpRank && plan.upRanks.isNotEmpty()) {
            TodayWatchUpSummaryRow(
                plan = plan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AppTopBarDefaults.HeaderContentHorizontalPadding,
                        end = AppTopBarDefaults.HeaderContentHorizontalPadding,
                        bottom = 14.dp
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                previewVideos.isEmpty() && isLoading -> {
                    TodayWatchStatus("正在生成推荐单...")
                }

                previewVideos.isEmpty() && !errorMessage.isNullOrBlank() -> {
                    TodayWatchStatus(errorMessage)
                }

                previewVideos.isEmpty() -> {
                    TodayWatchStatus("暂时没有可展示的推荐单")
                }

                else -> {
                    VideoCardRecyclerGrid(
                        videos = previewVideos,
                        contentPadding = PaddingValues(
                            start = AppTopBarDefaults.HeaderContentHorizontalPadding,
                            end = AppTopBarDefaults.HeaderContentHorizontalPadding,
                            top = AppTopBarDefaults.HeaderContentTopPadding,
                            bottom = AppTopBarDefaults.HomeVideoGridBottomPadding
                        ),
                        modifier = Modifier.fillMaxSize(),
                        gridColumnCount = TodayWatchGridColumnCount,
                        focusState = gridFocusState,
                        focusCoordinator = focusCoordinator,
                        focusTab = AppTopLevelTab.TODAY_WATCH,
                        onFocusedRowChanged = onContentRowFocused,
                        onTopRowDpadUp = {
                            focusCoordinator.handleGridTopEdge(AppTopLevelTab.TODAY_WATCH)
                        },
                        onBackToTopBar = { focusCoordinator.handleContentWantsTopBar() },
                        supportingText = if (config.showReasonHint) {
                            { video ->
                                plan.explanationByBvid[video.bvid].orEmpty().ifBlank {
                                    if (selectedMode == TodayWatchMode.RELAX) "轻松向筛选" else "学习向筛选"
                                }
                            }
                        } else {
                            null
                        },
                        onVideoFocused = { video, _ ->
                            onVideoFocused(video)
                        },
                        onVideoMenu = { video ->
                            pendingFeedbackVideo = video
                        },
                        onVideoClick = { video, _ ->
                            onOpenVideo(video)
                        }
                    )
                }
            }
        }
    }

    pendingFeedbackVideo?.let { video ->
        TvConfirmDialog(
            title = "推荐单操作",
            message = "将“不感兴趣”反馈给这条视频，并从当前推荐单移除。",
            onDismissRequest = { pendingFeedbackVideo = null },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = { pendingFeedbackVideo = null }
                )
                TvDialogActionButton(
                    text = "不感兴趣",
                    contentColor = Color(0xFFFFD0D8),
                    onClick = {
                        onNotInterested(video)
                        pendingFeedbackVideo = null
                    }
                )
            }
        )
    }
}

@Composable
private fun TodayWatchStatus(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun TodayWatchUpSummaryRow(
    plan: TodayWatchPlan,
    modifier: Modifier = Modifier
) {
    val upSummary = remember(plan.upRanks) {
        plan.upRanks.joinToString(" / ") { it.name }
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TodayWatchInfoCard(
            title = "偏好 UP",
            value = upSummary,
            modifier = Modifier.fillMaxWidth(0.55f)
        )
    }
}

@Composable
private fun TodayWatchInfoCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0x12000000), androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = title, color = Color(0xB3FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
