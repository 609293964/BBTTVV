package com.bbttvv.app.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.KeyEvent

/** Opt-in handler for TCL Smart TV Pro remote scan code 0x700d1. */
class TclShortcutAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val matchesShortcut = event.keyCode == KeyEvent.KEYCODE_UNKNOWN && event.scanCode == 0x000700d1
        if (!matchesShortcut) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                ?.let(::startActivity)
        }
        return true
    }
}
