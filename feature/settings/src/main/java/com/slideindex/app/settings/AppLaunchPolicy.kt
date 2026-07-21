package com.slideindex.app.settings

enum class AppLaunchPolicy(val id: Int) {
    ALWAYS_FULLSCREEN(0),
    ALWAYS_FREE_WINDOW(1),
    FULLSCREEN_LONG_PRESS_FREE_WINDOW(2),
    FREE_WINDOW_LONG_PRESS_FULLSCREEN(3),
    ;

    fun usesLongPress(): Boolean =
        this == FULLSCREEN_LONG_PRESS_FREE_WINDOW || this == FREE_WINDOW_LONG_PRESS_FULLSCREEN

    companion object {
        fun fromId(id: Int): AppLaunchPolicy =
            entries.firstOrNull { it.id == id } ?: ALWAYS_FULLSCREEN
    }
}

fun AppSettings.resolvedLaunchPolicy(): AppLaunchPolicy = AppLaunchPolicy.fromId(appLaunchPolicyId)

fun AppSettings.shouldLaunchFullscreen(longPressTriggered: Boolean): Boolean {
    if (!freeWindowEnabled) return true
    return when (resolvedLaunchPolicy()) {
        AppLaunchPolicy.ALWAYS_FULLSCREEN -> true
        AppLaunchPolicy.ALWAYS_FREE_WINDOW -> false
        AppLaunchPolicy.FULLSCREEN_LONG_PRESS_FREE_WINDOW -> !longPressTriggered
        AppLaunchPolicy.FREE_WINDOW_LONG_PRESS_FULLSCREEN -> longPressTriggered
    }
}

fun AppSettings.effectiveLongPressDurationMs(): Int =
    longPressLaunchDurationMs.coerceIn(250, 900)

fun AppSettings.launchPolicyLongPressEligible(): Boolean =
    freeWindowEnabled && resolvedLaunchPolicy().usesLongPress()
