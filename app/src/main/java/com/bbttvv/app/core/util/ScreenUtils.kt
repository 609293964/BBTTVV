package com.bbttvv.app.core.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.PowerManager
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object ScreenUtils {
    private var playbackWakeLock: PowerManager.WakeLock? = null

    fun setFullScreen(context: Context, isFull: Boolean) {
        val activity = context.findActivity() ?: return
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (isFull) {
            // 切横屏
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            // 隐藏状态栏和导航栏
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        } else {
            // TV 退出全屏后仍保持横屏
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            // 显示状态栏
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun setPlaybackKeepScreenOn(context: Context, keepScreenOn: Boolean) {
        val activity = context.findActivity() ?: return
        val window = activity.window
        val decorView = window.decorView
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        decorView.keepScreenOn = keepScreenOn
        updatePlaybackWakeLock(activity = activity, keepScreenOn = keepScreenOn)
    }

    @Suppress("DEPRECATION")
    private fun updatePlaybackWakeLock(activity: Activity, keepScreenOn: Boolean) {
        val powerManager = activity.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return
        if (keepScreenOn) {
            val wakeLock = playbackWakeLock ?: powerManager
                .newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                    "BBTTVV:PlaybackScreenOn"
                )
                .apply {
                    setReferenceCounted(false)
                }
                .also { playbackWakeLock = it }
            if (!wakeLock.isHeld) {
                wakeLock.acquire()
            }
        } else {
            playbackWakeLock?.takeIf { it.isHeld }?.release()
        }
    }

    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }
}
