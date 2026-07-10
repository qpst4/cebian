package com.slideindex.app.shake

import com.slideindex.app.gesture.GestureAction

fun resolveShakeAction(
    shake: ShakeGestureSettings,
    type: ShakeGestureType,
    foregroundPackage: String?,
    lockScreenActive: Boolean,
): GestureAction {
    if (lockScreenActive && shake.lockScreenShakeEnabled) {
        shake.lockScreenActions[type]?.takeIf { it != GestureAction.None }?.let { return it }
    }

    if (shake.independentAppShakeEnabled && foregroundPackage != null) {
        shake.perAppActions[foregroundPackage]?.get(type)?.takeIf { it != GestureAction.None }?.let { return it }
    }

    return shake.actionFor(type)
}
