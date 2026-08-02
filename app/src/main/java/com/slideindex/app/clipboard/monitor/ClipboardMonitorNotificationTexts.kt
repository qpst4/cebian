package com.slideindex.app.clipboard.monitor

/**
 * Based on [ClipboardListener](https://github.com/aa2013/ClipboardListener) (MIT).
 */
import android.content.Context
import com.slideindex.app.R

internal object ClipboardMonitorNotificationTexts {
    fun modeLabel(context: Context, useRoot: Boolean, useHiddenApi: Boolean): String =
        context.getString(
            when {
                useRoot && useHiddenApi -> R.string.clipboard_monitoring_mode_root_hidden_api
                useRoot -> R.string.clipboard_monitoring_mode_root_logs
                useHiddenApi -> R.string.clipboard_monitoring_mode_shizuku_hidden_api
                else -> R.string.clipboard_monitoring_mode_shizuku_logs
            },
        )

    fun waitingTitle(context: Context): String =
        context.getString(R.string.clipboard_monitor_notification_waiting_title)

    fun waitingText(context: Context, useRoot: Boolean, useHiddenApi: Boolean): String =
        context.getString(
            R.string.clipboard_monitor_notification_starting,
            modeLabel(context, useRoot, useHiddenApi),
        )

    fun runningTitle(context: Context): String =
        context.getString(R.string.clipboard_monitor_notification_running_title)

    fun runningText(context: Context, useRoot: Boolean, useHiddenApi: Boolean): String =
        modeLabel(context, useRoot, useHiddenApi)
}
