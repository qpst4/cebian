package com.slideindex.app.remind

/**
 * Portions derived from EdgeGesture (https://github.com/evilgodxu/EdgeGesture)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.Context
import com.slideindex.app.R
import com.slideindex.app.overlay.MessageOverlayHost
import com.slideindex.app.overlay.OverlayComposeDialogHost
import com.slideindex.app.ui.duration.MinutesDurationPickerCard
import com.slideindex.app.util.PermissionHelper

object RemindDurationPickerOverlay {
    private var host: OverlayComposeDialogHost? = null

    fun show(context: Context) {
        if (!PermissionHelper.canDrawOverlays(context)) {
            context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            return
        }
        val hostContext = MessageOverlayHost.resolveHostContext(context) ?: context.applicationContext
        val dialogHost = host ?: OverlayComposeDialogHost(
            context = hostContext,
            fullScreen = false
        ).also { host = it }
        dialogHost.show {
            RemindDurationPickerContent(
                context = hostContext,
                onConfirm = { minutes ->
                    RemindAlarmScheduler.toggle(hostContext, minutes)
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

@androidx.compose.runtime.Composable
private fun RemindDurationPickerContent(
    context: Context,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    MinutesDurationPickerCard(
        titleRes = R.string.gesture_remind_picker_title,
        presetMinutesRes = R.string.gesture_remind_picker_preset_minutes,
        minutesLabelRes = R.string.gesture_remind_picker_minutes_label,
        minutesValueRes = R.string.gesture_remind_picker_minutes_value,
        confirmRes = R.string.gesture_remind_picker_confirm,
        cancelRes = R.string.gesture_remind_picker_cancel,
        pendingMinutesForSelection = { selectedMinutes ->
            if (RemindAlarmScheduler.isPending(context, selectedMinutes)) selectedMinutes else null
        },
        formatMinutes = { minutes ->
            context.getString(R.string.gesture_remind_picker_minutes_value, minutes)
        },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
