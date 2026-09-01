package com.slideindex.app.freezer

/**
 * Portions derived from EdgeX (https://github.com/oxohang/EdgeX)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object FreezerBootstrap {
    fun scanDisabledLauncherPackages(context: Context): Set<String> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_DISABLED_COMPONENTS)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { pkg ->
                runCatching {
                    pm.getApplicationInfo(pkg, 0).enabled.not()
                }.getOrDefault(false)
            }
            .toSet()
    }
}
