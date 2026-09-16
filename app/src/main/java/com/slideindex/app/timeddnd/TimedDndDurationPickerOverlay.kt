package com.slideindex.app.timeddnd

import android.content.Context
import com.slideindex.app.R
import com.slideindex.app.overlay.MessageOverlayHost
import com.slideindex.app.overlay.OverlayComposeDialogHost
import com.slideindex.app.ui.duration.MinutesDurationPickerCard
import com.slideindex.app.util.PermissionHelper

object TimedDndDurationPickerOverlay {
    private var host: OverlayComposeDialogHost? = null

    fun show(context: Context) {
        if (!PermissionHelper.canDrawOverlays(context)) {
            context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            return
        }
        if (!PermissionHelper.hasNotificationPolicyAccess(context)) {
            PermissionHelper.requestNotificationPolicyAccess(context)
            return
        }
        val hostContext = MessageOverlayHost.resolveHostContext(context) ?: context.applicationContext
        val dialogHost = host ?: OverlayComposeDialogHost(
            context = hostContext,
            fullScreen = false
        ).also { host = it }
        dialogHost.show {
            MinutesDurationPickerCard(
                titleRes = R.string.gesture_timed_dnd_picker_title,
                presetMinutesRes = R.string.gesture_remind_picker_preset_minutes,
                minutesLabelRes = R.string.gesture_remind_picker_minutes_label,
                minutesValueRes = R.string.gesture_remind_picker_minutes_value,
                confirmRes = R.string.gesture_timed_dnd_picker_confirm,
                cancelRes = R.string.gesture_timed_dnd_picker_cancel,
                pendingMinutesForSelection = {
                    TimedDndScheduler.scheduledMinutes(hostContext)
                },
                formatMinutes = { minutes ->
                    hostContext.getString(R.string.gesture_remind_picker_minutes_value, minutes)
                },
                onConfirm = { minutes ->
                    TimedDndScheduler.startOrCancel(hostContext, minutes)
                    dialogHost.dismiss()
                },
                onDismiss = { dialogHost.dismiss() },
            )
        }
    }

    fun dismiss() {
        host?.dismiss()
    }
}
