package com.bbttvv.app.ui.components

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal data class DialogFocusTargetState(
    val isAttachedToWindow: Boolean,
    val isShown: Boolean,
    val isEnabled: Boolean,
    val isFocusable: Boolean,
)

internal fun DialogFocusTargetState.isRestorableDialogFocusTarget(): Boolean {
    return isAttachedToWindow && isShown && isEnabled && isFocusable
}

@Composable
internal fun rememberTvDialogFocusTrap(): FocusRequester {
    val ownerView = LocalView.current
    val focusRequester = remember { FocusRequester() }
    val focusReturn = remember(ownerView) { DialogFocusReturn() }

    DisposableEffect(ownerView) {
        val ownerRoot = ownerView.rootView ?: ownerView
        focusReturn.capture(ownerRoot.findFocus())
        val underlyingFocusBlock = DialogUnderlyingFocusBlocker.block(ownerRoot)

        onDispose {
            underlyingFocusBlock.restore()
            val parkingHandle = DialogFocusParking.park(ownerRoot)
            focusReturn.restoreAndClear(fallback = ownerView)
            ownerView.post {
                ownerView.post {
                    parkingHandle.dispose()
                }
            }
        }
    }

    return focusRequester
}

private class DialogFocusReturn {
    private var targetRef: WeakReference<View>? = null

    fun capture(view: View?) {
        targetRef = view?.let(::WeakReference)
    }

    fun restoreAndClear(fallback: View? = null): Boolean {
        val desired = targetRef?.get()
        targetRef = null
        return restore(desired = desired, fallback = fallback)
    }

    private fun restore(
        desired: View?,
        fallback: View?,
    ): Boolean {
        val candidates = buildList {
            desired?.let(::add)
            fallback?.takeIf { it !== desired }?.let(::add)
        }
        for (candidate in candidates) {
            if (!candidate.isRestorableDialogFocusTarget()) continue
            if (candidate.requestFocus()) return true
        }
        for (candidate in candidates) {
            if (!candidate.isRestorableDialogFocusTarget()) continue
            candidate.post { candidate.requestFocus() }
            return true
        }
        return false
    }
}

private object DialogUnderlyingFocusBlocker {
    private data class FocusabilitySnapshot(
        val descendantFocusability: Int?,
        val isFocusable: Boolean,
        val isFocusableInTouchMode: Boolean,
    )

    private data class BlockedRoot(
        val snapshot: FocusabilitySnapshot,
        var depth: Int,
    )

    private val blockedRoots = WeakHashMap<View, BlockedRoot>()

    fun block(root: View): DialogUnderlyingFocusBlock {
        synchronized(blockedRoots) {
            blockedRoots[root]?.let { blockedRoot ->
                blockedRoot.depth += 1
                return DialogUnderlyingFocusBlock(root)
            }

            blockedRoots[root] =
                BlockedRoot(
                    snapshot = FocusabilitySnapshot(
                        descendantFocusability = (root as? ViewGroup)?.descendantFocusability,
                        isFocusable = root.isFocusable,
                        isFocusableInTouchMode = root.isFocusableInTouchMode,
                    ),
                    depth = 1,
                )
            applyBlocked(root)
        }
        return DialogUnderlyingFocusBlock(root)
    }

    fun restore(root: View) {
        synchronized(blockedRoots) {
            val blockedRoot = blockedRoots[root] ?: return
            blockedRoot.depth -= 1
            if (blockedRoot.depth > 0) return

            blockedRoots.remove(root)
            (root as? ViewGroup)?.let { viewGroup ->
                blockedRoot.snapshot.descendantFocusability?.let { viewGroup.descendantFocusability = it }
            }
            root.isFocusable = blockedRoot.snapshot.isFocusable
            root.isFocusableInTouchMode = blockedRoot.snapshot.isFocusableInTouchMode
        }
    }

    private fun applyBlocked(root: View) {
        (root as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        root.isFocusable = false
        root.isFocusableInTouchMode = false
    }
}

private class DialogUnderlyingFocusBlock(
    private val root: View,
) {
    private var restored = false

    fun restore() {
        if (restored) return
        restored = true
        DialogUnderlyingFocusBlocker.restore(root)
    }
}

private object DialogFocusParking {
    fun park(root: View): DialogFocusParkingHandle {
        val group = root as? ViewGroup ?: return DialogFocusParkingHandle.Noop
        val parkingView = View(root.context).apply {
            alpha = 0f
            isClickable = false
            isFocusable = true
            isFocusableInTouchMode = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        return runCatching {
            group.addView(parkingView, ViewGroup.LayoutParams(1, 1))
            parkingView.requestFocus()
            DialogFocusParkingHandle.Attached(parent = group, view = parkingView)
        }.getOrElse {
            DialogFocusParkingHandle.Noop
        }
    }
}

private sealed interface DialogFocusParkingHandle {
    fun dispose()

    data object Noop : DialogFocusParkingHandle {
        override fun dispose() = Unit
    }

    class Attached(
        private val parent: ViewGroup,
        private val view: View,
    ) : DialogFocusParkingHandle {
        private var disposed = false

        override fun dispose() {
            if (disposed) return
            disposed = true
            view.isFocusable = false
            view.isFocusableInTouchMode = false
            runCatching { parent.removeView(view) }
        }
    }
}

private fun View.isRestorableDialogFocusTarget(): Boolean {
    return DialogFocusTargetState(
        isAttachedToWindow = isAttachedToWindow,
        isShown = isShown,
        isEnabled = isEnabled,
        isFocusable = isFocusable,
    ).isRestorableDialogFocusTarget()
}
