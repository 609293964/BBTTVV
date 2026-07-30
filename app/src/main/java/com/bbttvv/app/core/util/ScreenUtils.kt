package com.bbttvv.app.core.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.annotation.MainThread
import java.util.WeakHashMap

internal class PlaybackKeepScreenOnOwner

internal class PlaybackKeepScreenOnOwnerRegistry {
    private val activeOwners = mutableSetOf<PlaybackKeepScreenOnOwner>()

    fun update(
        owner: PlaybackKeepScreenOnOwner,
        keepScreenOn: Boolean,
    ): Boolean {
        if (keepScreenOn) {
            activeOwners += owner
        } else {
            activeOwners -= owner
        }
        return activeOwners.isNotEmpty()
    }

    val activeOwnerCount: Int
        get() = activeOwners.size
}

object ScreenUtils {
    private val playbackKeepScreenOnOwners = WeakHashMap<Activity, PlaybackKeepScreenOnOwnerRegistry>()

    @MainThread
    internal fun setPlaybackKeepScreenOn(
        context: Context,
        owner: PlaybackKeepScreenOnOwner,
        keepScreenOn: Boolean,
    ) {
        val activity = context.findActivity() ?: return
        val registry = playbackKeepScreenOnOwners[activity] ?: if (keepScreenOn) {
            PlaybackKeepScreenOnOwnerRegistry().also {
                playbackKeepScreenOnOwners[activity] = it
            }
        } else {
            return
        }
        val shouldKeepScreenOn = registry.update(owner, keepScreenOn)
        if (!shouldKeepScreenOn) {
            playbackKeepScreenOnOwners.remove(activity)
        }

        val window = activity.window
        if (shouldKeepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @MainThread
    internal fun releasePlaybackKeepScreenOn(
        context: Context,
        owner: PlaybackKeepScreenOnOwner,
    ) {
        setPlaybackKeepScreenOn(
            context = context,
            owner = owner,
            keepScreenOn = false,
        )
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
