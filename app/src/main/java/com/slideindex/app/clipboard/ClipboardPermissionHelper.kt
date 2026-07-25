package com.slideindex.app.clipboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.slideindex.app.util.TaskManagerUtil

object ClipboardPermissionHelper {
    fun mediaReadPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasMediaReadPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                return true
            }
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true
        }
        return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasReadLogsPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED

    fun adbGrantReadLogsCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} ${Manifest.permission.READ_LOGS}"

    fun grantViaShizuku(context: Context): Boolean {
        if (!TaskManagerUtil.hasPermission()) return false
        val packageName = context.packageName
        val granted = TaskManagerUtil.runShellCommand(
            "pm",
            "grant",
            packageName,
            Manifest.permission.READ_LOGS,
        )
        return granted && hasReadLogsPermission(context)
    }
}
