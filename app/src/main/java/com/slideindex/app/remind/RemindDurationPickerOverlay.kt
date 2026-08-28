package com.slideindex.app.remind

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.MessageOverlayHost
import com.slideindex.app.overlay.OverlayComposeDialogHost
import com.slideindex.app.ui.miuix.MiuixSliderRow
import com.slideindex.app.util.PermissionHelper
import kotlin.math.roundToInt

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
            fullScreen = false,
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

@OptIn(ExperimentalLayoutApi::class)
@androidx.compose.runtime.Composable
private fun RemindDurationPickerContent(
    context: Context,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedMinutes by remember { mutableIntStateOf(5) }
    var sliderValue by remember { mutableFloatStateOf(5f) }
    val pending = remember(selectedMinutes) { RemindAlarmScheduler.isPending(context, selectedMinutes) }
    Card(
        modifier = Modifier
            .padding(16.dp)
            .width(320.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.gesture_remind_picker_title),
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RemindAlarmScheduler.PRESET_MINUTES.forEach { preset ->
                    FilterChip(
                        selected = selectedMinutes == preset,
                        onClick = {
                            selectedMinutes = preset
                            sliderValue = preset.toFloat()
                        },
                        label = {
                            Text(
                                stringResource(
                                    R.string.gesture_remind_picker_preset_minutes,
                                    preset,
                                ),
                            )
                        },
                    )
                }
            }
            MiuixSliderRow(
                title = stringResource(R.string.gesture_remind_picker_minutes_label),
                value = sliderValue,
                valueRange = RemindAlarmScheduler.MIN_MINUTES.toFloat()..RemindAlarmScheduler.MAX_MINUTES.toFloat(),
                steps = RemindAlarmScheduler.MAX_MINUTES - RemindAlarmScheduler.MIN_MINUTES - 1,
                formatLabel = { value ->
                    val minutes = value.roundToInt().coerceIn(
                        RemindAlarmScheduler.MIN_MINUTES,
                        RemindAlarmScheduler.MAX_MINUTES,
                    )
                    context.getString(R.string.gesture_remind_picker_minutes_value, minutes)
                },
                onValueChange = { value ->
                    sliderValue = value
                    selectedMinutes = value.roundToInt().coerceIn(
                        RemindAlarmScheduler.MIN_MINUTES,
                        RemindAlarmScheduler.MAX_MINUTES,
                    )
                },
            )
            Button(
                onClick = { onConfirm(selectedMinutes) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (pending) {
                        stringResource(R.string.gesture_remind_picker_cancel, selectedMinutes)
                    } else {
                        stringResource(R.string.gesture_remind_picker_confirm, selectedMinutes)
                    },
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
