package com.slideindex.app.freezer

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
                    pm.getApplicationEnabledSetting(pkg) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }.getOrDefault(false)
            }
            .toSet()
    }
}
