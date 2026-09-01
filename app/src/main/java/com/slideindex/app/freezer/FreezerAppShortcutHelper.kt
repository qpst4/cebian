package com.slideindex.app.freezer

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import androidx.core.graphics.drawable.toBitmap

object FreezerAppShortcutHelper {
    fun requestPinAppShortcut(context: Context, app: AppInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val appContext = context.applicationContext
        val shortcutManager = appContext.getSystemService(ShortcutManager::class.java) ?: return false
        if (!shortcutManager.isRequestPinShortcutSupported) return false
        val launchIntent = Intent(appContext, FreezerAppLaunchTrampolineActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(FreezerAppLaunchTrampolineActivity.EXTRA_PACKAGE, app.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val shortcut = ShortcutInfo.Builder(appContext, shortcutId(app.packageName))
            .setShortLabel(app.label)
            .setLongLabel(app.label)
            .setIcon(appShortcutIcon(appContext, app.packageName))
            .setIntent(launchIntent)
            .build()
        return runCatching {
            shortcutManager.requestPinShortcut(shortcut, null)
        }.getOrDefault(false)
    }

    fun showPinShortcutFailedToast(context: Context) {
        Toast.makeText(context, R.string.freezer_pin_shortcut_failed, Toast.LENGTH_SHORT).show()
    }

    private fun shortcutId(packageName: String): String = "freezer_app_$packageName"

    private fun appShortcutIcon(context: Context, packageName: String): Icon {
        val drawable = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        return if (drawable != null) {
            Icon.createWithAdaptiveBitmap(drawable.toBitmap(96, 96))
        } else {
            Icon.createWithResource(context, R.drawable.ic_freezer_launcher)
        }
    }
}
