package com.bbttvv.app.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginQrPanel(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvLoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x14000000), RoundedCornerShape(42.dp))
            .padding(28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "扫码登录",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "请使用手机哔哩哔哩 App 扫描下方二维码",
                color = Color(0xD9FFFFFF),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 22.dp)
            )

            Box(
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(30.dp))
                    .padding(18.dp)
            ) {
                when {
                    uiState.qrBitmap != null -> {
                        Image(
                            bitmap = uiState.qrBitmap!!.asImageBitmap(),
                            contentDescription = "登录二维码",
                            modifier = Modifier.size(300.dp)
                        )
                    }

                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.size(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "加载中...", color = Color.Black)
                        }
                    }

                    else -> {
                        Box(
                            modifier = Modifier.size(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "二维码加载失败", color = Color.Black)
                        }
                    }
                }
            }

            Text(
                text = uiState.errorMessage ?: uiState.statusText,
                color = if (uiState.errorMessage == null) Color(0xFFE2E8F0) else Color(0xFFF4B1B8),
                modifier = Modifier.padding(top = 18.dp, bottom = 22.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = { viewModel.loadQrCode() },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFFFB7299),
                        focusedContainerColor = Color.White
                    )
                ) {
                    Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Text(text = "刷新二维码", color = Color.White)
                    }
                }
            }
        }
    }
}
