package com.slideindex.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
enum class ShakeActionPickTarget {
    BASIC,
    LOCK_SCREEN,
    PER_APP,
    FACE_DOWN,
}

fun ShakeActionPickTarget.returnNavKey(packageName: String = ""): AppNavKey = when (this) {
    ShakeActionPickTarget.BASIC,
    ShakeActionPickTarget.FACE_DOWN,
    -> AppNavKey.ShakeGestures
    ShakeActionPickTarget.LOCK_SCREEN -> AppNavKey.ShakeLockScreenSettings
    ShakeActionPickTarget.PER_APP -> AppNavKey.ShakePerAppActions(packageName)
}
