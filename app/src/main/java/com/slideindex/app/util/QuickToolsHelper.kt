package com.slideindex.app.util

import android.content.Context
import android.provider.Settings
import android.view.Surface

object QuickToolsHelper {
    fun readWifiEnabled(context: Context): Boolean? {
        val appContext = context.applicationContext
        if (TaskManagerUtil.hasPermission()) {
            val output = TaskManagerUtil.runShellCommandOutput("settings", "get", "global", "wifi_on")
                .output.trim()
            if (output == "0" || output == "1") return output == "1"
        }
        return runCatching {
            Settings.Global.getInt(
                appContext.contentResolver,
                Settings.Global.WIFI_ON,
                0,
            ) == 1
        }.getOrNull()
    }

    fun toggleWifi(context: Context): Boolean? {
        val current = readWifiEnabled(context) ?: return null
        val enable = !current
        if (TaskManagerUtil.hasPermission()) {
            val ok = TaskManagerUtil.runShellCommand(
                "svc",
                "wifi",
                if (enable) "enable" else "disable",
            )
            if (ok) return readWifiEnabled(context) ?: enable
        }
        return null
    }

    fun readMobileDataEnabled(context: Context): Boolean? =
        readMobileDataSwitchState(context)

    fun readMobileDataSwitchState(context: Context): Boolean? =
        readMobileData1(context)

    /**
     * @param currentHint 面板当前亮/暗；与 [readMobileData1] 一起决定 enable/disable 方向。
     */
    fun toggleMobileData(context: Context, currentHint: Boolean? = null): Boolean? {
        if (!TaskManagerUtil.hasPermission()) return null
        val switchOn = currentHint ?: readMobileData1(context) ?: return null
        val action = if (switchOn) "disable" else "enable"
        if (!TaskManagerUtil.runShellCommand("svc", "data", action)) return null
        return readMobileData1(context) ?: !switchOn
    }

    /**
     * Flyme 双卡实测：默认上网卡移动数据开关对应 `settings global mobile_data1`（0=关，1=开）。
     * 忽略 `mobile_data`（恒为 1）与 `mobile_data0`（null）。
     */
    private fun readMobileData1(context: Context): Boolean? {
        readMobileData1FromResolver(context)?.let { return it }
        if (TaskManagerUtil.hasPermission()) {
            readMobileData1FromShell()?.let { return it }
        }
        return null
    }

    private fun readMobileData1FromResolver(context: Context): Boolean? {
        val value = runCatching {
            Settings.Global.getInt(
                context.applicationContext.contentResolver,
                MOBILE_DATA_SETTING_KEY,
                -1,
            )
        }.getOrDefault(-1)
        return when (value) {
            1 -> true
            0 -> false
            else -> null
        }
    }

    private fun readMobileData1FromShell(): Boolean? = when (
        TaskManagerUtil.runShellCommandOutput("settings", "get", "global", MOBILE_DATA_SETTING_KEY)
            .output
            .trim()
            .lowercase()
    ) {
        "1" -> true
        "0" -> false
        else -> null
    }

    private const val MOBILE_DATA_SETTING_KEY = "mobile_data1"

    fun readBluetoothEnabled(context: Context): Boolean? {
        if (TaskManagerUtil.hasPermission()) {
            val output = TaskManagerUtil.runShellCommandOutput(
                "settings",
                "get",
                "global",
                "bluetooth_on",
            ).output.trim()
            if (output == "0" || output == "1") return output == "1"
        }
        return null
    }

    fun toggleBluetooth(context: Context): Boolean? {
        val current = readBluetoothEnabled(context) ?: return null
        val enable = !current
        if (TaskManagerUtil.hasPermission()) {
            val ok = TaskManagerUtil.runShellCommand(
                "cmd",
                "bluetooth_manager",
                if (enable) "enable" else "disable",
            )
            if (ok) return enable
        }
        return null
    }

    fun readAutoRotateEnabled(context: Context): Boolean {
        return Settings.System.getInt(
            context.applicationContext.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0,
        ) == 1
    }

    fun toggleAutoRotate(context: Context): Boolean? {
        val enable = !readAutoRotateEnabled(context)
        val value = if (enable) "1" else "0"
        if (TaskManagerUtil.hasPermission()) {
            if (TaskManagerUtil.runShellCommand(
                    "settings",
                    "put",
                    "system",
                    Settings.System.ACCELEROMETER_ROTATION,
                    value,
                )
            ) {
                return enable
            }
        }
        return if (Settings.System.putInt(
                context.applicationContext.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (enable) 1 else 0,
            )
        ) {
            enable
        } else {
            null
        }
    }

    fun setOrientation(context: Context, orientation: Int): Boolean {
        val appContext = context.applicationContext
        val rotationVal = when (orientation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 1
            Surface.ROTATION_180 -> 2
            Surface.ROTATION_270 -> 3
            else -> 0
        }
        if (TaskManagerUtil.hasPermission()) {
            TaskManagerUtil.runShellCommand(
                "settings",
                "put",
                "system",
                Settings.System.ACCELEROMETER_ROTATION,
                "0",
            )
            val ok = TaskManagerUtil.runShellCommand(
                "settings",
                "put",
                "system",
                Settings.System.USER_ROTATION,
                rotationVal.toString(),
            )
            if (ok) return true
        }
        return runCatching {
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0,
            )
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.USER_ROTATION,
                rotationVal,
            )
        }.getOrDefault(false)
    }

    fun forcePortrait(context: Context): Boolean = setOrientation(context, Surface.ROTATION_0)

    fun forceLandscape(context: Context): Boolean = setOrientation(context, Surface.ROTATION_90)
}
