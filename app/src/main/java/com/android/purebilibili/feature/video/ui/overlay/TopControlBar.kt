// File: feature/video/ui/overlay/TopControlBar.kt
package com.android.purebilibili.feature.video.ui.overlay

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
//  Cupertino Icons
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.*
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import com.android.purebilibili.core.ui.AppIcons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Cast
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun shouldShowDislikeInTopControlBar(widthDp: Int): Boolean = widthDp >= 980
internal fun shouldShowInteractiveActionsInTopControlBar(
    showFullscreenActionItems: Boolean
): Boolean = showFullscreenActionItems
internal fun shouldApplyStatusBarPaddingToTopControlBar(
    isFullscreen: Boolean
): Boolean = false

internal enum class BatteryChargeTone {
    CRITICAL,
    LOW,
    NORMAL
}

internal data class LandscapeBatteryVisualState(
    val percent: Int,
    val fillFraction: Float,
    val chargeTone: BatteryChargeTone
) {
    val displayText: String
        get() = "$percent%"
}

internal fun resolveLandscapeBatteryVisualState(percent: Int?): LandscapeBatteryVisualState? {
    val normalizedPercent = percent?.coerceIn(0, 100) ?: return null
    val chargeTone = when {
        normalizedPercent <= 15 -> BatteryChargeTone.CRITICAL
        normalizedPercent <= 35 -> BatteryChargeTone.LOW
        else -> BatteryChargeTone.NORMAL
    }
    return LandscapeBatteryVisualState(
        percent = normalizedPercent,
        fillFraction = normalizedPercent / 100f,
        chargeTone = chargeTone
    )
}

internal data class LandscapeTopStatusInfo(
    val battery: LandscapeBatteryVisualState? = null,
    val currentTimeText: String? = null
) {
    val isVisible: Boolean
        get() = battery != null || !currentTimeText.isNullOrBlank()
}

internal fun resolveLandscapeTopStatusInfo(
    showBatteryLevel: Boolean,
    batteryLevelPercent: Int?,
    showCurrentTime: Boolean,
    currentTimeText: String
): LandscapeTopStatusInfo {
    return LandscapeTopStatusInfo(
        battery = if (showBatteryLevel) resolveLandscapeBatteryVisualState(batteryLevelPercent) else null,
        currentTimeText = currentTimeText.takeIf { showCurrentTime }
    )
}

internal fun shouldShowLandscapeMetaRow(
    hasStatusInfo: Boolean,
    onlineCount: String
): Boolean = hasStatusInfo || onlineCount.isNotBlank()

/**
 * Top Control Bar Component
 * 
 * Redesigned to match official Bilibili landscape layout:
 * - Left: Back button, Title (Marquee), Online count
 * - Right: Action buttons (Like, Dislike, Coin, Share, Cast, More)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopControlBar(
    title: String,
    onlineCount: String = "",
    isFullscreen: Boolean,
    showBatteryLevel: Boolean = false,
    showCurrentTime: Boolean = true,
    showInteractiveActions: Boolean = true,
    onBack: () -> Unit,
    // Interactions
    isLiked: Boolean = false,
    isCoined: Boolean = false,
    onLikeClick: () -> Unit = {},
    onDislikeClick: () -> Unit = {},
    onCoinClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onCastClick: () -> Unit = {}, // Added Cast callback
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val layoutPolicy = remember(configuration.screenWidthDp) {
        resolveTopControlBarLayoutPolicy(
            widthDp = configuration.screenWidthDp
        )
    }
    val showDislikeAction = remember(configuration.screenWidthDp) {
        showInteractiveActions && shouldShowDislikeInTopControlBar(widthDp = configuration.screenWidthDp)
    }
    val showInteractiveActionGroup = remember(showInteractiveActions) {
        shouldShowInteractiveActionsInTopControlBar(
            showFullscreenActionItems = showInteractiveActions
        )
    }
    val currentTimeText by produceState(initialValue = formatCurrentTime()) {
        while (true) {
            value = formatCurrentTime()
            val now = System.currentTimeMillis()
            val nextMinuteDelay = (60_000L - (now % 60_000L)).coerceAtLeast(1_000L)
            delay(nextMinuteDelay)
        }
    }
    val batteryLevelPercent by produceState<Int?>(initialValue = null, showBatteryLevel) {
        if (!showBatteryLevel) {
            value = null
            return@produceState
        }
        while (true) {
            value = resolveBatteryLevelPercent(context)
            delay(30_000L)
        }
    }
    val statusInfo = remember(currentTimeText, batteryLevelPercent, showBatteryLevel, showCurrentTime) {
        resolveLandscapeTopStatusInfo(
            showBatteryLevel = showBatteryLevel,
            batteryLevelPercent = batteryLevelPercent,
            showCurrentTime = showCurrentTime,
            currentTimeText = currentTimeText
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (shouldApplyStatusBarPaddingToTopControlBar(isFullscreen = isFullscreen)) {
                    Modifier.statusBarsPadding()
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = layoutPolicy.horizontalPaddingDp.dp,
                vertical = layoutPolicy.verticalPaddingDp.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- Left Section: Back & Info ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f) // Text takes remaining space
            ) {
                // Back Button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(layoutPolicy.buttonSizeDp.dp)
                ) {
                    Icon(
                        imageVector = CupertinoIcons.Default.ChevronBackward, 
                        contentDescription = "Back", 
                        tint = Color.White,
                        modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(layoutPolicy.backToTitleSpacingDp.dp))

                // 标题与右侧图标保持同一行
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = layoutPolicy.titleFontSp.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee() // Marquee effect for long text
                )
            }
            
            Spacer(modifier = Modifier.width(layoutPolicy.sectionGapDp.dp)) // Space between text and actions
            
            // --- Right Section: Actions ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(layoutPolicy.actionSpacingDp.dp)
            ) {
                if (showInteractiveActionGroup) {
                    // Like
                    ActionIcon(
                        icon = if (isLiked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "点赞",
                        isActive = isLiked,
                        onClick = onLikeClick,
                        buttonSizeDp = layoutPolicy.buttonSizeDp,
                        iconSizeDp = layoutPolicy.iconSizeDp
                    )

                    if (showDislikeAction) {
                        // Dislike
                        ActionIcon(
                            icon = Icons.Outlined.ThumbDown,
                            contentDescription = "不喜欢",
                            isActive = false,
                            onClick = onDislikeClick,
                            buttonSizeDp = layoutPolicy.buttonSizeDp,
                            iconSizeDp = layoutPolicy.iconSizeDp
                        )
                    }

                    // Coin
                    ActionIcon(
                        icon = AppIcons.BiliCoin,
                        contentDescription = "投币",
                        isActive = isCoined,
                        onClick = onCoinClick,
                        buttonSizeDp = layoutPolicy.buttonSizeDp,
                        iconSizeDp = layoutPolicy.iconSizeDp
                    )

                    // Share
                    ActionIcon(
                        icon = Icons.Outlined.Share,
                        contentDescription = "分享",
                        isActive = false,
                        onClick = onShareClick,
                        buttonSizeDp = layoutPolicy.buttonSizeDp,
                        iconSizeDp = layoutPolicy.iconSizeDp
                    )
                }

                // Cast (Added back)
                ActionIcon(
                    icon = Icons.Outlined.Cast,
                    contentDescription = "投屏",
                    isActive = false,
                    onClick = onCastClick,
                    buttonSizeDp = layoutPolicy.buttonSizeDp,
                    iconSizeDp = layoutPolicy.iconSizeDp
                )
                
                // More (Three dots)
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(layoutPolicy.buttonSizeDp.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "更多",
                        tint = Color.White,
                        modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
                    )
                }
            }
        }

        if (shouldShowLandscapeMetaRow(statusInfo.isVisible, onlineCount)) {
            Column(
                modifier = Modifier.padding(
                    start = layoutPolicy.onlineCountStartPaddingDp.dp,
                    top = layoutPolicy.timeBottomSpacingDp.dp
                ),
                verticalArrangement = Arrangement.spacedBy(layoutPolicy.onlineCountTopPaddingDp.dp)
            ) {
                statusInfo.battery?.let { battery ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BatteryStatusIcon(
                            state = battery,
                            modifier = Modifier.size(
                                width = (layoutPolicy.timeFontSp + 14).dp,
                                height = (layoutPolicy.timeFontSp + 4).dp
                            )
                        )
                        Text(
                            text = battery.displayText,
                            color = resolveBatteryStatusTint(battery).copy(alpha = 0.95f),
                            fontSize = layoutPolicy.timeFontSp.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        statusInfo.currentTimeText?.let { timeText ->
                            Text(
                                text = timeText,
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = layoutPolicy.timeFontSp.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } ?: statusInfo.currentTimeText?.let { timeText ->
                    Text(
                        text = timeText,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = layoutPolicy.timeFontSp.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (onlineCount.isNotEmpty()) {
                    Text(
                        text = onlineCount,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = layoutPolicy.onlineCountFontSp.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

private fun formatCurrentTime(): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date())
}

private fun resolveBatteryLevelPercent(context: Context): Int? {
    return runCatching {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return@runCatching null
        (level * 100f / scale).toInt().coerceIn(0, 100)
    }.getOrNull()
}

@Composable
private fun BatteryStatusIcon(
    state: LandscapeBatteryVisualState,
    modifier: Modifier = Modifier
) {
    val tint = resolveBatteryStatusTint(state)
    Canvas(modifier = modifier) {
        val strokeWidth = size.height * 0.12f
        val terminalWidth = size.width * 0.09f
        val bodyWidth = size.width - terminalWidth - strokeWidth
        val bodyHeight = size.height * 0.76f
        val bodyTop = (size.height - bodyHeight) / 2f
        val cornerRadius = CornerRadius(bodyHeight * 0.18f, bodyHeight * 0.18f)
        val innerPadding = strokeWidth * 1.1f
        val fillWidth = ((bodyWidth - innerPadding * 2f) * state.fillFraction).coerceAtLeast(0f)

        drawRoundRect(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = Offset(0f, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = Offset(bodyWidth + strokeWidth * 0.35f, size.height * 0.34f),
            size = Size(terminalWidth, size.height * 0.28f),
            cornerRadius = CornerRadius(terminalWidth / 2f, terminalWidth / 2f)
        )
        if (fillWidth > 0f) {
            drawRoundRect(
                color = tint,
                topLeft = Offset(innerPadding, bodyTop + innerPadding),
                size = Size(fillWidth, bodyHeight - innerPadding * 2f),
                cornerRadius = CornerRadius(cornerRadius.x / 1.5f, cornerRadius.y / 1.5f)
            )
        }
    }
}

private fun resolveBatteryStatusTint(
    state: LandscapeBatteryVisualState
): Color {
    return when (state.chargeTone) {
        BatteryChargeTone.CRITICAL -> Color(0xFFFF5A5F)
        BatteryChargeTone.LOW -> Color(0xFFFFB347)
        BatteryChargeTone.NORMAL -> Color.White.copy(alpha = 0.92f)
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    buttonSizeDp: Int = 32,
    iconSizeDp: Int = 24
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(buttonSizeDp.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(iconSizeDp.dp)
        )
    }
}
