package com.slideindex.app.clipboard.monitor

/**
 * Based on [ClipboardListener](https://github.com/aa2013/ClipboardListener) (MIT).
 */
import android.content.Context
import com.slideindex.app.R

internal object ClipboardMonitorNotificationTexts {
    fun modeLabel(context: Context, useRoot: Boolean, useHiddenApi: Boolean, useStandard: Boolean = false): String =
        context.getString(
            when {
                useStandard -> R.string.clipboard_monitoring_mode_standard
                useRoot && useHiddenApi -> R.string.clipboard_monitoring_mode_root_hidden_api
                useRoot -> R.string.clipboard_monitoring_mode_root_logs
                useHiddenApi -> R.string.clipboard_monitoring_mode_shizuku_hidden_api
                else -> R.string.clipboard_monitoring_mode_shizuku_logs
            },
        )

    fun waitingTitle(context: Context): String =
        context.getString(R.string.clipboard_monitor_notification_waiting_title)

    fun waitingText(
        context: Context,
        useRoot: Boolean,
        useHiddenApi: Boolean,
        useStandard: Boolean = false,
    ): String =
        context.getString(
            R.string.clipboard_monitor_notification_starting,
            modeLabel(context, useRoot, useHiddenApi, useStandard),
        )

    fun runningTitle(context: Context): String =
        context.getString(R.string.clipboard_monitor_notification_running_title)

    fun runningText(
        context: Context,
        useRoot: Boolean,
        @Suppress("UNUSED_PARAMETER") useHiddenApi: Boolean,
        useStandard: Boolean = false,
    ): String =
        context.getString(
            when {
                useStandard -> R.string.clipboard_monitor_notification_standard_running
                useRoot -> R.string.clipboard_monitor_notification_root_running
                else -> R.string.clipboard_monitor_notification_shizuku_running
            },
        )
}
