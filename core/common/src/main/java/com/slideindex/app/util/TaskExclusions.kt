package com.slideindex.app.util

object TaskExclusions {
    val LAUNCHER_AND_SYSTEM = setOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.android.launcher3",
        "com.miui.home",
        "com.meizu.flyme.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.slideindex.app",
    )

    fun shouldSkipFreeWindow(packageName: String, selfPackage: String): Boolean {
        if (packageName.isBlank()) return true
        if (packageName == selfPackage) return true
        return packageName in LAUNCHER_AND_SYSTEM
    }
}
