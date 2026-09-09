package com.slideindex.app.gesture

import android.content.ComponentName
import com.slideindex.app.util.TaskExclusions

internal object ActionExecutorPolicy {
    fun shouldLaunchPackageFallback(rawIdentifier: String, packageName: String): Boolean {
        val raw = rawIdentifier.trim()
        return raw.isBlank() || raw == packageName
    }

    fun componentFromRawIdentifier(rawIdentifier: String): ComponentName? {
        val trimmed = rawIdentifier.trim()
        if (!trimmed.contains('/')) return null
        val pkg = trimmed.substringBefore('/').trim()
        var cls = trimmed.substringAfter('/').trim()
        if (cls.startsWith('.')) cls = pkg + cls
        if (pkg.isEmpty() || cls.isEmpty()) return null
        return ComponentName(pkg, cls)
    }

    fun resolveFreeWindowTargetPackage(
        selfPackage: String,
        gestureForegroundPackage: String?,
        foregroundPackage: String?
    ): String? =
        listOfNotNull(gestureForegroundPackage, foregroundPackage)
            .firstOrNull { !TaskExclusions.shouldSkipFreeWindow(it, selfPackage) }
}
