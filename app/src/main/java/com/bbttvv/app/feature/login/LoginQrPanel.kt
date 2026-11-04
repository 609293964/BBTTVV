package com.bbttvv.app.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.Icon
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginQrPanel(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    refreshFocusRequester: FocusRequester? = null,
    onRefreshFocused: () -> Unit = {},
    viewModel: TvLoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = com.bbttvv.app.ui.theme.LocalTvSemanticColors.current

    LaunchedEffect(Unit) {
        viewModel.loadQrCode()
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onLoginSuccess()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPolling()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "手机客户端扫描登录",
            color = colors.primaryText,
            fontSize = 20.sp,
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val qrSize = minOf(maxWidth, maxHeight, 320.dp)
            Box(Modifier.size(qrSize), contentAlignment = Alignment.Center) {
                when {
                    uiState.qrBitmap != null -> Image(
                        bitmap = uiState.qrBitmap!!.asImageBitmap(),
                        contentDescription = "登录二维码",
                        modifier = Modifier.fillMaxSize(),
                        filterQuality = FilterQuality.None,
                    )
                    uiState.isLoading -> Text("加载中…", color = colors.secondaryText)
                    else -> Text("二维码加载失败", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (uiState.errorMessage != null || uiState.isScanned) {
            Text(
                text = uiState.errorMessage ?: uiState.statusText,
                color = if (uiState.errorMessage == null) colors.secondaryText else MaterialTheme.colorScheme.error,
                maxLines = 2,
            )
        }
        Surface(
            onClick = { viewModel.loadQrCode() },
            modifier = Modifier
                .then(refreshFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged { if (it.isFocused) onRefreshFocused() },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = colors.surfaceSubtle,
                contentColor = colors.primaryText,
                focusedContainerColor = colors.focusContainer,
                focusedContentColor = colors.focusContent,
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(2.dp, colors.accent),
                    shape = CircleShape,
                ),
            ),
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新二维码",
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
