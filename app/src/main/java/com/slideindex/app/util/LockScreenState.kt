package com.slideindex.app.util

import android.app.KeyguardManager
import android.content.Context
import android.view.accessibility.AccessibilityWindowInfo

object LockScreenState {
    fun detectActive(
        context: Context,
        windows: List<AccessibilityWindowInfo>? = null,
    ): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        if (keyguard.isKeyguardLocked) return true
        if (keyguard.isDeviceLocked) return true
        if (windows?.any { it.type == WINDOW_TYPE_KEYGUARD } == true) return true
        return false
    }

    private const val WINDOW_TYPE_KEYGUARD = 6

    fun isActive(context: Context): Boolean {
        if (TriggerEnvironmentState.lockScreenActive) return true
        return detectActive(context)
    }
}
