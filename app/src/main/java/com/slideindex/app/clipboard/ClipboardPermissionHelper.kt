package com.slideindex.app.clipboard

import android.Manifest
import android.content.Context

object ClipboardPermissionHelper {
    fun adbGrantReadLogsCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} ${Manifest.permission.READ_LOGS}"
}
