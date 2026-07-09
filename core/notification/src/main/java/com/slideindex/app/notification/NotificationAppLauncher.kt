package com.slideindex.app.notification

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log

object NotificationAppLauncher {
    private const val TAG = "NotifAppLauncher"

    fun canOpen(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (context.packageManager.getLaunchIntentForPackage(packageName) != null) return true
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
        return launcherApps.getActivityList(packageName, Process.myUserHandle()).isNotEmpty()
    }

    fun open(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching {
                context.startActivity(launchIntent)
            }.onFailure { error ->
                Log.w(TAG, "getLaunchIntentForPackage failed for $packageName", error)
            }.isSuccess
        }
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
        val activities = launcherApps.getActivityList(packageName, Process.myUserHandle())
        if (activities.isEmpty()) return false
        return runCatching {
            launcherApps.startMainActivity(
                activities[0].componentName,
                Process.myUserHandle(),
                null,
                null,
            )
        }.onFailure { error ->
            Log.w(TAG, "LauncherApps.startMainActivity failed for $packageName", error)
        }.isSuccess
    }
}
