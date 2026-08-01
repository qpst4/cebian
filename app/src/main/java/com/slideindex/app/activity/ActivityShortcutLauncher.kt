package com.slideindex.app.activity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.search.NonExportedActivityLauncher
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.shouldLaunchFullscreen
import com.slideindex.app.util.FreeWindowLauncher
import com.slideindex.app.util.PackageActivityResolver
import com.slideindex.app.util.TaskManagerUtil

object ActivityShortcutLauncher {
    fun launch(
        context: Context,
        shortcut: ActivityShortcut,
        settings: AppSettings,
        longPressTriggered: Boolean = false,
    ): Boolean = launch(
        context = context,
        packageName = shortcut.packageName,
        activityClassName = shortcut.activityClassName,
        settings = settings,
        longPressTriggered = longPressTriggered,
    )

    fun launch(
        context: Context,
        packageName: String,
        activityClassName: String,
        settings: AppSettings,
        longPressTriggered: Boolean = false,
    ): Boolean {
        if (packageName.isBlank() || activityClassName.isBlank()) return false
        return if (PackageActivityResolver.isActivityExported(context, packageName, activityClassName)) {
            val intent = Intent()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setComponent(ComponentName(packageName, activityClassName))
            startActivity(context, intent, settings, longPressTriggered)
        } else {
            launchNonExported(context, packageName, activityClassName)
        }
    }

    private fun launchNonExported(context: Context, packageName: String, activityClassName: String): Boolean {
        if (!TaskManagerUtil.hasPermission()) {
            Toast.makeText(context, R.string.activity_shortcut_shizuku_required, Toast.LENGTH_LONG).show()
            return false
        }
        NonExportedActivityLauncher.launch(
            context = context,
            packageName = packageName,
            activityName = activityClassName,
        ) { success ->
            if (!success) {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    private fun startActivity(
        context: Context,
        intent: Intent,
        settings: AppSettings,
        longPressTriggered: Boolean,
    ): Boolean {
        return runCatching {
            val fullscreen = settings.shouldLaunchFullscreen(longPressTriggered)
            FreeWindowLauncher.launch(context, intent, settings, fullscreen)
            true
        }.getOrElse {
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }
}
