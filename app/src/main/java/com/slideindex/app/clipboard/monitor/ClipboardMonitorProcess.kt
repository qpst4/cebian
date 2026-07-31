package com.slideindex.app.clipboard.monitor

import android.app.Application
import android.content.Context
import android.os.Build

internal object ClipboardMonitorProcess {
    fun isMainProcess(context: Context): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                val activityThread = Class.forName("android.app.ActivityThread")
                val current = activityThread.getMethod("currentProcessName").invoke(null) as? String
                current
            }.getOrNull()
        }
        return processName.isNullOrEmpty() || processName == context.packageName
    }
}
