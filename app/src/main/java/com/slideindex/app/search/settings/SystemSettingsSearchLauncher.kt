package com.slideindex.app.search.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.privilege.PrivilegeUiStrings
import com.slideindex.app.search.NonExportedActivityLauncher
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.shouldLaunchFullscreen
import com.slideindex.app.util.FreeWindowLauncher
import com.slideindex.app.util.PackageActivityResolver
import com.slideindex.app.util.TaskManagerUtil

object SystemSettingsSearchLauncher {
    private const val EXTRA_SHOW_FRAGMENT = ":settings:show_fragment"
    private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
    private const val EXTRA_FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"

    fun buildLaunchIntent(entry: SystemSettingsSearchEntry): Intent {
        val packageName = entry.packageName.ifBlank { "com.android.settings" }
        val className = entry.className?.trim().orEmpty()
        val action = entry.action?.trim().orEmpty()
        val fragmentKey = entry.key?.trim().orEmpty()

        return when {
            action.isNotEmpty() && className.isNotEmpty() -> {
                Intent(action).apply {
                    component = ComponentName(packageName, className)
                    applySettingsExtras(fragmentKey)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            className.isNotEmpty() -> {
                Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(packageName, className)
                    applySettingsExtras(fragmentKey)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            action.isNotEmpty() -> {
                Intent(action).apply {
                    applySettingsExtras(fragmentKey)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            fragmentKey.isNotEmpty() -> {
                Intent(Settings.ACTION_SETTINGS).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_SHOW_FRAGMENT, fragmentKey)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            else -> {
                Intent(Settings.ACTION_SETTINGS).apply {
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
    }

    fun launch(
        context: Context,
        entry: SystemSettingsSearchEntry,
        settings: AppSettings,
        longPressTriggered: Boolean,
    ): Boolean {
        val intent = buildLaunchIntent(entry)
        val component = intent.component
        if (component != null &&
            !PackageActivityResolver.isActivityExported(context, component.packageName, component.className)
        ) {
            return launchNonExported(context, component.packageName, component.className)
        }
        return runCatching {
            val fullscreen = settings.shouldLaunchFullscreen(longPressTriggered)
            FreeWindowLauncher.launch(context, intent, settings, fullscreen)
            true
        }.getOrElse {
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun launchNonExported(
        context: Context,
        packageName: String,
        activityClassName: String,
    ): Boolean {
        if (!TaskManagerUtil.hasPermission()) {
            Toast.makeText(context, PrivilegeUiStrings.privilegedAccessRequiredRes(), Toast.LENGTH_LONG).show()
            return false
        }
        var launched = false
        NonExportedActivityLauncher.launch(
            context = context,
            packageName = packageName,
            activityName = activityClassName,
        ) { success ->
            launched = success
            if (!success) {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            }
        }
        return launched
    }

    private fun Intent.applySettingsExtras(fragmentKey: String) {
        if (fragmentKey.isEmpty()) return
        if (fragmentKey.startsWith("android-app:") || fragmentKey.contains("#Intent;")) return
        putExtra(EXTRA_SHOW_FRAGMENT, fragmentKey)
        putExtra(EXTRA_SHOW_FRAGMENT_ARGS, fragmentKey)
        putExtra(EXTRA_FRAGMENT_ARGS_KEY, fragmentKey)
    }
}
