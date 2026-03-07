package com.android.purebilibili.feature.dynamic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.repository.DynamicRepository
import com.android.purebilibili.feature.dynamic.components.DynamicCardV2
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.ChevronBackward

private sealed interface DynamicDetailUiState {
    data object Loading : DynamicDetailUiState
    data class Success(val item: DynamicItem) : DynamicDetailUiState
    data class Error(val message: String) : DynamicDetailUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicDetailScreen(
    dynamicId: String,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onUserClick: (Long) -> Unit,
    onLiveClick: (roomId: Long, title: String, uname: String) -> Unit = { _, _, _ -> }
) {
    var retryToken by rememberSaveable { mutableIntStateOf(0) }
    val uiState by produceState<DynamicDetailUiState>(
        initialValue = DynamicDetailUiState.Loading,
        key1 = dynamicId,
        key2 = retryToken
    ) {
        value = DynamicDetailUiState.Loading
        value = DynamicRepository.getDynamicDetail(dynamicId).fold(
            onSuccess = { item -> DynamicDetailUiState.Success(item) },
            onFailure = { error ->
                DynamicDetailUiState.Error(error.message ?: "动态详情加载失败")
            }
        )
    }

    val context = LocalContext.current
    val gifImageLoader = context.imageLoader

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("动态详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CupertinoIcons.Outlined.ChevronBackward, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            DynamicDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    com.android.purebilibili.core.ui.CutePersonLoadingIndicator()
                }
            }

            is DynamicDetailUiState.Error -> {
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
                            Text("重试")
                        }
                    }
                }
            }

            is DynamicDetailUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .responsiveContentWidth(),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    item {
                        DynamicCardV2(
                            item = state.item,
                            onVideoClick = onVideoClick,
                            onUserClick = onUserClick,
                            onLiveClick = onLiveClick,
                            gifImageLoader = gifImageLoader
                        )
                    }
                }
            }
        }
    }
}
