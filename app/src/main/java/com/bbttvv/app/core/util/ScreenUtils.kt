package com.bbttvv.app.core.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager

object ScreenUtils {
    fun setPlaybackKeepScreenOn(context: Context, keepScreenOn: Boolean) {
        val activity = context.findActivity() ?: return
        val window = activity.window
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
