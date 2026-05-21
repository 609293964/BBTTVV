package com.bbttvv.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.bbttvv.app.app.BbtvApplication
import com.bbttvv.app.navigation.AppNavigation
import com.bbttvv.app.ui.focus.LocalTvFocusEscapeGuard
import com.bbttvv.app.ui.focus.LocalTvFocusReturn
import com.bbttvv.app.ui.focus.TvFocusEscapeGuard
import com.bbttvv.app.ui.focus.TvFocusReturn
import com.bbttvv.app.ui.theme.AppTheme

/**
 * 应用主 Activity
 *
 * 通过 CompositionLocalProvider 注入 TvFocusEscapeGuard 和 TvFocusReturn 全局焦点基础设施。
 * dispatchKeyEvent 中优先由 TvFocusEscapeGuard 处理焦点逃逸事件。
 * 首帧渲染后通知 BbtvApplication.onFirstFrameRendered() 触发延迟启动任务。
 */
class MainActivity : ComponentActivity() {
    private val tvFocusEscapeGuard = TvFocusEscapeGuard()
    private val tvFocusReturn = TvFocusReturn()

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isDebugInspectorInfoEnabled = false
        setContent {
            val themeMode by com.bbttvv.app.core.store.SettingsManager.getThemeMode(this)
                .collectAsStateWithLifecycle(initialValue = com.bbttvv.app.core.store.SettingsManager.ThemeMode.DARK)

            LaunchedEffect(Unit) {
                withFrameNanos { }
                (application as? BbtvApplication)?.onFirstFrameRendered()
            }
            AppTheme(themeMode = themeMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    CompositionLocalProvider(
                        LocalTvFocusEscapeGuard provides tvFocusEscapeGuard,
                        LocalTvFocusReturn provides tvFocusReturn,
                    ) {
                        AppNavigation()
                    }
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (tvFocusEscapeGuard.handleKeyEvent(event, currentFocus)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
