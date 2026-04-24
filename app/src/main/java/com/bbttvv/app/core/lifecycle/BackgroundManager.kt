package com.bbttvv.app.core.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.CopyOnWriteArraySet

object BackgroundManager : DefaultLifecycleObserver {

    interface BackgroundStateListener {
        fun onEnterBackground() {}
        fun onEnterForeground() {}
    }

    private val listeners = CopyOnWriteArraySet<BackgroundStateListener>()

    @Volatile
    private var initialized = false

    @Volatile
    var isInBackground: Boolean = false
        private set

    fun init() {
        if (initialized) return
        initialized = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun addListener(listener: BackgroundStateListener) {
        listeners += listener
    }

    fun removeListener(listener: BackgroundStateListener) {
        listeners -= listener
    }

    override fun onStart(owner: LifecycleOwner) {
        if (!isInBackground) return
        isInBackground = false
        listeners.forEach { it.onEnterForeground() }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (isInBackground) return
        isInBackground = true
        listeners.forEach { it.onEnterBackground() }
    }
}
