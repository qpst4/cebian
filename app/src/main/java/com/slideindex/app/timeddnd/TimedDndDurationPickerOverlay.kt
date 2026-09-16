package com.slideindex.app.timeddnd

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.slideindex.app.R
import com.slideindex.app.overlay.MessageOverlayHost
import com.slideindex.app.overlay.OverlayComposeDialogHost
import com.slideindex.app.ui.duration.MinutesDurationPickerCard
import com.slideindex.app.util.PermissionHelper
import kotlinx.coroutines.launch

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
            fullScreen = false,
        ).also { host = it }
        dialogHost.show {
            TimedDndPickerContent(
                hostContext = hostContext,
                onDismiss = { dialogHost.dismiss() },
            )
        }
    }

    fun dismiss() {
        host?.dismiss()
    }
}

@Composable
private fun TimedDndPickerContent(
    hostContext: Context,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pendingMinutes by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(hostContext) {
        pendingMinutes = TempDndManager.scheduledMinutes(hostContext)
    }

    MinutesDurationPickerCard(
        titleRes = R.string.gesture_timed_dnd_picker_title,
        presetMinutesRes = R.string.gesture_remind_picker_preset_minutes,
        minutesLabelRes = R.string.gesture_remind_picker_minutes_label,
        minutesValueRes = R.string.gesture_remind_picker_minutes_value,
        confirmRes = R.string.gesture_timed_dnd_picker_confirm,
        cancelRes = R.string.gesture_timed_dnd_picker_cancel,
        pendingMinutesForSelection = { _ -> pendingMinutes },
        formatMinutes = { minutes ->
            hostContext.getString(R.string.gesture_remind_picker_minutes_value, minutes)
        },
        onConfirm = { minutes ->
            scope.launch {
                val active = pendingMinutes
                if (active != null) {
                    TempDndManager.cancelActive(hostContext)
                } else {
                    TempDndManager.enableTempDnd(hostContext, minutes)
                }
                onDismiss()
            }
        },
        onDismiss = onDismiss,
    )
}
