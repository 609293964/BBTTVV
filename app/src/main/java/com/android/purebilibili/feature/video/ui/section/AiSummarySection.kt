package com.android.purebilibili.feature.video.ui.section

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.data.model.response.AiSummaryData
import com.android.purebilibili.feature.video.viewmodel.AiSummaryPromptState
import com.android.purebilibili.feature.video.viewmodel.AiSummaryPromptTone
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.*
import io.github.alexzhirkevich.cupertino.icons.outlined.*

/**
 * AI Video Summary Card
 */
@Composable
fun AiSummaryCard(
    aiSummary: AiSummaryData?,
    onTimestampClick: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!hasAiSummaryContent(aiSummary)) return

    val modelResult = requireNotNull(aiSummary?.modelResult)
    val collapsedPreview = remember(modelResult.summary, modelResult.outline) {
        modelResult.summary.takeIf { it.isNotBlank() } ?: "查看分段总结和时间点"
    }
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .animateContentSize(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (expanded) 0.46f else 0.32f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CupertinoIcons.Default.Sparkles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI 总结",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = collapsedPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = if (expanded) CupertinoIcons.Default.ChevronUp else CupertinoIcons.Default.ChevronDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (modelResult.summary.isNotBlank()) {
                        Text(
                            text = modelResult.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = if (modelResult.outline.isNotEmpty()) 12.dp else 0.dp)
                        )
                    }

                    if (modelResult.outline.isNotEmpty()) {
                        modelResult.outline.forEach { outlineItem ->
                            OutlineItemRow(
                                title = outlineItem.title,
                                timestamp = outlineItem.timestamp,
                                onClick = { onTimestampClick?.invoke(outlineItem.timestamp * 1000L) }
                            )

                            outlineItem.partOutline.forEach { part ->
                                OutlineItemRow(
                                    title = part.content,
                                    timestamp = part.timestamp,
                                    isSubItem = true,
                                    onClick = { onTimestampClick?.invoke(part.timestamp * 1000L) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiSummaryPromptCard(
    promptState: AiSummaryPromptState,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val containerColor = when (promptState.tone) {
        AiSummaryPromptTone.INFO -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        AiSummaryPromptTone.MUTED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        AiSummaryPromptTone.WARNING -> MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
    }
    val accentColor = when (promptState.tone) {
        AiSummaryPromptTone.INFO -> MaterialTheme.colorScheme.primary
        AiSummaryPromptTone.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
        AiSummaryPromptTone.WARNING -> MaterialTheme.colorScheme.error
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = containerColor,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (promptState.tone == AiSummaryPromptTone.INFO) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = accentColor
                        )
                    } else {
                        Icon(
                            imageVector = if (promptState.tone == AiSummaryPromptTone.WARNING) {
                                CupertinoIcons.Default.ExclamationmarkCircle
                            } else {
                                CupertinoIcons.Default.InfoCircle
                            },
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = promptState.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = promptState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!promptState.actionLabel.isNullOrBlank() && onActionClick != null) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = onActionClick,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(promptState.actionLabel)
                }
            }
        }
    }
}

@Composable
private fun OutlineItemRow(
    title: String,
    timestamp: Long,
    isSubItem: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = if (isSubItem) 16.dp else 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (!isSubItem) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(6.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(12.dp))
        } else {
             Spacer(modifier = Modifier.width(4.dp)) // Indent for sub items aligned with bullet?
        }
        
        Column(modifier = Modifier.weight(1f)) {
             Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Time Button
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.clickable(onClick = onClick) // extra click area
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = CupertinoIcons.Outlined.Clock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatTimestamp(timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatTimestamp(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
