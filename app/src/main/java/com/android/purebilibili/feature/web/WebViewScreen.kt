package com.android.purebilibili.feature.web

import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.ChevronBackward
import kotlinx.coroutines.launch

/**
 * WebViewScreen - 应用内浏览器
 * 
 * 支持拦截 Bilibili 链接并跳转到原生界面：
 * - 视频: bilibili.com/video/BV... 或 av...
 * - UP主空间: space.bilibili.com/{mid}
 * - 直播: live.bilibili.com/{roomId}
 * - 番剧: bilibili.com/bangumi/play/ss{id} 或 ep{id}
 * - 音乐: music.bilibili.com/h5/music-detail?music_id=...
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String,
    title: String? = null,
    onBack: () -> Unit,
    // [新增] 链接拦截回调
    onVideoClick: ((bvid: String) -> Unit)? = null,
    onSpaceClick: ((mid: Long) -> Unit)? = null,
    onLiveClick: ((roomId: Long) -> Unit)? = null,
    onBangumiClick: ((seasonId: Long, epId: Long) -> Unit)? = null,
    onMusicClick: ((musicId: String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = title ?: "浏览器",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CupertinoIcons.Outlined.ChevronBackward, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        
                        // [核心] 自定义 WebViewClient 拦截 Bilibili 链接
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val requestUrl = request?.url?.toString() ?: return false
                                return handleBilibiliUrl(
                                    webView = view,
                                    urlString = requestUrl,
                                    hasUserGesture = request.hasGesture()
                                )
                            }
                            
                            // 兼容旧版 API
                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                return url?.let {
                                    handleBilibiliUrl(
                                        webView = view,
                                        urlString = it,
                                        hasUserGesture = false
                                    )
                                } ?: false
                            }
                            
                            /**
                             * 处理 Bilibili URL 拦截
                             * @param webView WebView 实例，用于加载转换后的 URL
                             * @return true 表示已拦截处理，false 表示继续加载网页
                             */
                            private fun handleBilibiliUrl(
                                webView: WebView?,
                                urlString: String,
                                hasUserGesture: Boolean
                            ): Boolean {
                                android.util.Log.d("WebViewScreen", "🔗 Intercepting URL: $urlString")
                                try {
                                    val uri = android.net.Uri.parse(urlString)
                                    val scheme = uri.scheme ?: ""
                                    val host = uri.host ?: ""
                                    
                                    android.util.Log.d("WebViewScreen", "🔍 Scheme: $scheme, Host: $host")

                                    fun dispatchTarget(target: BilibiliNavigationTarget): Boolean {
                                        return when (target) {
                                            is BilibiliNavigationTarget.Video -> {
                                                onVideoClick?.invoke(target.videoId)
                                                onVideoClick != null
                                            }

                                            is BilibiliNavigationTarget.Space -> {
                                                onSpaceClick?.invoke(target.mid)
                                                onSpaceClick != null
                                            }

                                            is BilibiliNavigationTarget.Live -> {
                                                onLiveClick?.invoke(target.roomId)
                                                onLiveClick != null
                                            }

                                            is BilibiliNavigationTarget.BangumiSeason -> {
                                                onBangumiClick?.invoke(target.seasonId, 0)
                                                onBangumiClick != null
                                            }

                                            is BilibiliNavigationTarget.BangumiEpisode -> {
                                                onBangumiClick?.invoke(0, target.epId)
                                                onBangumiClick != null
                                            }

                                            is BilibiliNavigationTarget.Music -> {
                                                onMusicClick?.invoke(target.musicId)
                                                onMusicClick != null
                                            }

                                            is BilibiliNavigationTarget.Dynamic -> false
                                            is BilibiliNavigationTarget.Search -> false
                                        }
                                    }

                                    when (val action = resolveWebViewNavigationAction(urlString, hasUserGesture)) {
                                        is WebViewNavigationAction.Block -> {
                                            android.util.Log.d("WebViewScreen", "⛔ Blocked navigation: $urlString")
                                            return true
                                        }

                                        is WebViewNavigationAction.LoadInWebView -> {
                                            android.util.Log.d("WebViewScreen", "🔄 Deep link -> ${action.url}")
                                            webView?.loadUrl(action.url)
                                            return true
                                        }

                                        is WebViewNavigationAction.DispatchTarget -> {
                                            if (dispatchTarget(action.target)) {
                                                android.util.Log.d("WebViewScreen", "✅ Routed target: ${action.target}")
                                                return true
                                            }
                                        }

                                        WebViewNavigationAction.AllowWebLoad -> Unit
                                    }

                                    if (scheme == "bilibili" || scheme == "bili") {
                                        android.util.Log.w("WebViewScreen", "⚠️ Blocked unknown deep link: $urlString")
                                        return true
                                    }

                                    if (host.contains("b23.tv")) {
                                        scope.launch {
                                            val resolvedTarget = BilibiliNavigationTargetParser.resolve(urlString)
                                            if (resolvedTarget != null && dispatchTarget(resolvedTarget)) {
                                                android.util.Log.d("WebViewScreen", "✅ Routed resolved short link: $resolvedTarget")
                                            } else {
                                                webView?.post { webView.loadUrl(urlString) }
                                            }
                                        }
                                        return true
                                    }
                                    
                                } catch (e: Exception) {
                                    android.util.Log.e("WebViewScreen", "URL parsing error: ${e.message}")
                                }
                                
                                return false // 不拦截，继续加载
                            }
                        }
                        
                        loadUrl(url)
                    }
                },
                update = { webView ->
                    // Avoid reloading on recomposition if URL hasn't changed
                    if (webView.url != url) {
                        webView.loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
