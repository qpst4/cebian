package com.slideindex.app.shake

import android.content.Context
import com.slideindex.app.gesture.GestureAction

/** Runtime environment for shake gesture detection and dispatch. */
interface ShakeRuntimePort {
    fun isAccessibilityServiceEnabled(): Boolean
    fun isAccessibilityConnected(): Boolean
    fun performAccessibilityAction(action: GestureAction): Boolean
    fun captureGestureForegroundPackage()
    fun foregroundPackage(): String?
    fun isLockScreenActive(): Boolean
    fun isLandscape(): Boolean
    fun overlayActionContext(): Context
    fun screenCenterX(): Float
    fun screenCenterY(): Float
}
