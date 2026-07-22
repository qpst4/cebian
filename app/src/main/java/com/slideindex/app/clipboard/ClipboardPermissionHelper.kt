package com.slideindex.app.clipboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.slideindex.app.util.TaskManagerUtil

object ClipboardPermissionHelper {
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
