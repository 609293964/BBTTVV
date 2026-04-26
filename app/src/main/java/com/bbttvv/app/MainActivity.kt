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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.bbttvv.app.app.BbtvApplication
import com.bbttvv.app.navigation.AppNavigation
import com.bbttvv.app.ui.focus.LocalTvFocusEscapeGuard
import com.bbttvv.app.ui.focus.TvFocusEscapeGuard
import com.bbttvv.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val tvFocusEscapeGuard = TvFocusEscapeGuard()

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isDebugInspectorInfoEnabled = false
        setContent {
            LaunchedEffect(Unit) {
                withFrameNanos { }
                (application as? BbtvApplication)?.onFirstFrameRendered()
            }
            AppTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    CompositionLocalProvider(
                        LocalTvFocusEscapeGuard provides tvFocusEscapeGuard,
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
