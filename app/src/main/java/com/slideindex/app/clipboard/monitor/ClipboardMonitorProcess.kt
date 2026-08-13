package com.slideindex.app.clipboard.monitor

/**
 * Based on [ClipboardListener](https://github.com/aa2013/ClipboardListener) (MIT).
 */
import android.app.Application
import android.content.Context

internal object ClipboardMonitorProcess {
    fun isMainProcess(context: Context): Boolean {
        val processName = Application.getProcessName()
        return processName.isNullOrEmpty() || processName == context.packageName
    }
}
