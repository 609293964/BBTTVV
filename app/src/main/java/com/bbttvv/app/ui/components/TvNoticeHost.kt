package com.bbttvv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

internal const val TV_NOTICE_INFO_DURATION_MS = 2_500L
internal const val TV_NOTICE_ERROR_DURATION_MS = 4_000L
internal const val TV_NOTICE_EXIT_DURATION_MS = 2_000L

enum class TvNoticeKind {
    Info,
    Error,
}

@Immutable
internal data class TvNotice(
    val id: Long,
    val message: String,
    val kind: TvNoticeKind,
    val durationMs: Long,
)

@Stable
class TvNoticeHostState {
    internal var currentNotice by mutableStateOf<TvNotice?>(null)
        private set
    private var nextId = 0L

    fun show(
        message: String,
        kind: TvNoticeKind = TvNoticeKind.Info,
        durationMs: Long = defaultDurationMs(kind),
    ): Long {
        val normalized = message.trim()
        if (normalized.isEmpty()) return 0L
        val id = ++nextId
        currentNotice = TvNotice(
            id = id,
            message = normalized,
            kind = kind,
            durationMs = durationMs.coerceAtLeast(1L),
        )
        return id
    }

    internal fun dismiss(id: Long) {
        if (currentNotice?.id == id) {
            currentNotice = null
        }
    }
}

val LocalTvNoticeHostState = staticCompositionLocalOf<TvNoticeHostState> {
    error("TvNoticeHostState is not provided")
}

@Composable
fun rememberTvNoticeHostState(): TvNoticeHostState = remember { TvNoticeHostState() }

@Composable
fun TvNoticeHost(
    state: TvNoticeHostState,
    modifier: Modifier = Modifier,
) {
    val notice = state.currentNotice ?: return
    LaunchedEffect(notice.id) {
        delay(notice.durationMs)
        state.dismiss(notice.id)
    }

    val backgroundColor = when (notice.kind) {
        TvNoticeKind.Info -> Color(0xF21A2028)
        TvNoticeKind.Error -> Color(0xFF8E2635).copy(alpha = 0.98f)
    }
    val borderColor = when (notice.kind) {
        TvNoticeKind.Info -> Color.White.copy(alpha = 0.18f)
        TvNoticeKind.Error -> Color.White.copy(alpha = 0.28f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 40.dp)
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = notice.message,
            color = Color.White,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .background(backgroundColor, RoundedCornerShape(14.dp))
                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 22.dp, vertical = 14.dp),
        )
    }
}

private fun defaultDurationMs(kind: TvNoticeKind): Long = when (kind) {
    TvNoticeKind.Info -> TV_NOTICE_INFO_DURATION_MS
    TvNoticeKind.Error -> TV_NOTICE_ERROR_DURATION_MS
}
