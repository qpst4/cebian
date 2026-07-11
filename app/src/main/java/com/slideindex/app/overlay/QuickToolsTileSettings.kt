package com.slideindex.app.overlay

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.TaskManagerUtil

/** Long-press targets: open the system screen that matches each quick-toggle tile. */
object QuickToolsTileSettings {
    val longPressTiles = setOf(
        OhoTile.WIFI,
        OhoTile.MOBILE_DATA,
        OhoTile.SOUND,
        OhoTile.BLUETOOTH,
        OhoTile.DO_NOT_DISTURB,
    )

    fun open(context: Context, tile: OhoTile): Boolean {
        if (tile !in longPressTiles) return false
        if (tile == OhoTile.MOBILE_DATA && openMobileDataSettings(context)) return true
        val appContext = context.applicationContext
        val pm = appContext.packageManager
        for (intent in intentsFor(appContext, tile)) {
            if (pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) continue
            if (runCatching { appContext.startActivity(intent) }.isSuccess) return true
        }
        return false
    }

    private fun openMobileDataSettings(context: Context): Boolean {
        if (TaskManagerUtil.hasPermission() &&
            TaskManagerUtil.runShellCommand(*MOBILE_DATA_AM_START_COMMAND)
        ) {
            return true
        }
        val appContext = context.applicationContext
        val pm = appContext.packageManager
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        for (intent in mobileDataIntents(appContext, flags)) {
            if (pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) continue
            if (runCatching { appContext.startActivity(intent) }.isSuccess) return true
        }
        return false
    }

    private fun intentsFor(context: Context, tile: OhoTile): List<Intent> {
        val newTask = Intent.FLAG_ACTIVITY_NEW_TASK
        return when (tile) {
            OhoTile.WIFI -> listOf(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(newTask))
            OhoTile.BLUETOOTH -> listOf(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(newTask))
            OhoTile.SOUND -> listOf(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(newTask))
            OhoTile.DO_NOT_DISTURB -> dndIntents(context, newTask)
            OhoTile.MOBILE_DATA -> emptyList()
            else -> emptyList()
        }
    }

    private fun dndIntents(context: Context, flags: Int): List<Intent> {
        val intents = mutableListOf<Intent>()
        if (!PermissionHelper.hasNotificationPolicyAccess(context)) {
            intents += PermissionHelper.notificationPolicySettingsIntent()
        }
        intents += Intent(ACTION_ZEN_MODE_SETTINGS).addFlags(flags)
        intents += Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(flags)
        return intents
    }

    private fun mobileDataIntents(context: Context, flags: Int): List<Intent> {
        val intents = mutableListOf(
            Intent(Settings.ACTION_DATA_USAGE_SETTINGS).addFlags(flags),
            Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(flags),
        )
        context.packageManager.getLaunchIntentForPackage(FLYME_SETTINGS_PACKAGE)
            ?.addFlags(flags)
            ?.let { intents += it }
        return intents
    }

    private val MOBILE_DATA_AM_START_COMMAND = arrayOf(
        "am", "start",
        "-a", "android.settings.MMS_MESSAGE_SETTING",
        "-f", "0x10008000",
        "-n", "com.android.settings/.Settings\$MobileNetworkActivity",
    )

    private const val FLYME_SETTINGS_PACKAGE = "com.meizu.flyme.settings"
    private const val ACTION_ZEN_MODE_SETTINGS = "android.settings.ZEN_MODE_SETTINGS"
}
