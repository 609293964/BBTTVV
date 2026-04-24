package com.bbttvv.app.ui.home

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal fun View.isValidFocusTarget(): Boolean {
    return visibility == View.VISIBLE &&
        isAttachedToWindow &&
        isShown &&
        isEnabled &&
        isFocusable
}

internal fun View.isSameOrDescendantOf(ancestor: View): Boolean {
    var current: View? = this
    while (current != null) {
        if (current === ancestor) return true
        current = current.parent as? View
    }
    return false
}

internal fun RecyclerView.requestFocusParking(): Boolean {
    if (!isValidFocusTarget()) return false
    val previousDescendantFocusability = descendantFocusability
    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    val focused = requestFocus()
    descendantFocusability = previousDescendantFocusability
    return focused
}

internal fun RecyclerView.installChildFocusParkingOnDetach(): RecyclerViewFocusProtectionHandle {
    return RecyclerViewChildFocusParking.install(this)
}

internal fun RecyclerView.parkFocusForDataSetReset(): Boolean {
    val handle = installChildFocusParkingOnDetach()
    handle.protectFocusDuringNextLayout()
    return requestFocusParking()
}

internal class RecyclerViewFocusProtectionHandle internal constructor(
    recyclerView: RecyclerView,
) : RecyclerView.OnChildAttachStateChangeListener {
    private val recyclerViewRef = WeakReference(recyclerView)
    private var protectDetachUntilUptimeMs: Long = 0L
    internal var isDisposed: Boolean = false
        private set

    fun protectFocusDuringNextLayout(windowMs: Long = FocusProtectionWindowMs) {
        val recycler = recyclerViewRef.get() ?: return
        val protectedUntil = SystemClock.uptimeMillis() + windowMs
        protectDetachUntilUptimeMs = maxOf(protectDetachUntilUptimeMs, protectedUntil)
        recycler.postDelayed(
            {
                if (SystemClock.uptimeMillis() >= protectDetachUntilUptimeMs) {
                    protectDetachUntilUptimeMs = 0L
                }
            },
            windowMs,
        )
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        recyclerViewRef.get()?.removeOnChildAttachStateChangeListener(this)
        RecyclerViewChildFocusParking.uninstall(this)
    }

    override fun onChildViewAttachedToWindow(view: View) = Unit

    override fun onChildViewDetachedFromWindow(view: View) {
        val recycler = recyclerViewRef.get() ?: return
        val focusedView = recycler.rootView?.findFocus()
        val shouldPark = FocusProtectionPolicy.shouldParkFocusOnDetach(
            recyclerCanTakeFocus = recycler.isValidFocusTarget(),
            detachedViewHadFocus = view.hasFocus(),
            focusedViewWasInsideDetachedView = focusedView?.isSameOrDescendantOf(view) == true,
            focusedViewWasInsideRecycler = focusedView?.isSameOrDescendantOf(recycler) == true,
            isProtectedDataSetChange = SystemClock.uptimeMillis() <= protectDetachUntilUptimeMs,
        )
        if (shouldPark && recycler.requestFocusParking()) {
            protectDetachUntilUptimeMs = 0L
        }
    }

    internal fun recyclerViewOrNull(): RecyclerView? = recyclerViewRef.get()

    private companion object {
        private const val FocusProtectionWindowMs = 750L
    }
}

internal object FocusProtectionPolicy {
    fun shouldParkFocusOnDetach(
        recyclerCanTakeFocus: Boolean,
        detachedViewHadFocus: Boolean,
        focusedViewWasInsideDetachedView: Boolean,
        focusedViewWasInsideRecycler: Boolean,
        isProtectedDataSetChange: Boolean,
    ): Boolean {
        if (!recyclerCanTakeFocus) return false
        return detachedViewHadFocus ||
            focusedViewWasInsideDetachedView ||
            (isProtectedDataSetChange && focusedViewWasInsideRecycler)
    }
}

private object RecyclerViewChildFocusParking {
    private val installedRecyclerViews: MutableMap<RecyclerView, RecyclerViewFocusProtectionHandle> =
        WeakHashMap()

    fun install(recyclerView: RecyclerView): RecyclerViewFocusProtectionHandle {
        synchronized(installedRecyclerViews) {
            installedRecyclerViews[recyclerView]
                ?.takeUnless { it.isDisposed }
                ?.let { return it }

            val handle = RecyclerViewFocusProtectionHandle(recyclerView)
            recyclerView.addOnChildAttachStateChangeListener(handle)
            installedRecyclerViews[recyclerView] = handle
            return handle
        }
    }

    fun uninstall(handle: RecyclerViewFocusProtectionHandle) {
        val recyclerView = handle.recyclerViewOrNull() ?: return
        synchronized(installedRecyclerViews) {
            if (installedRecyclerViews[recyclerView] === handle) {
                installedRecyclerViews.remove(recyclerView)
            }
        }
    }
}
