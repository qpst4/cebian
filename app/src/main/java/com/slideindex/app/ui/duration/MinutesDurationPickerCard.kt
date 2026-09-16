package com.slideindex.app.ui.duration

import androidx.annotation.StringRes
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.remind.RemindAlarmScheduler
import com.slideindex.app.ui.miuix.MiuixSliderRow
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MinutesDurationPickerCard(
    @StringRes titleRes: Int,
    @StringRes presetMinutesRes: Int,
    @StringRes minutesLabelRes: Int,
    @StringRes minutesValueRes: Int,
    @StringRes confirmRes: Int,
    @StringRes cancelRes: Int,
    pendingMinutesForSelection: (selectedMinutes: Int) -> Int?,
    formatMinutes: (Int) -> String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedMinutes by remember { mutableIntStateOf(5) }
    var sliderValue by remember { mutableFloatStateOf(5f) }
    val pendingMinutes = pendingMinutesForSelection(selectedMinutes)
    Card(
        modifier = Modifier
            .padding(16.dp)
            .width(320.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RemindAlarmScheduler.PRESET_MINUTES.forEach { preset ->
                    FilterChip(
                        selected = selectedMinutes == preset,
                        onClick = {
                            selectedMinutes = preset
                            sliderValue = preset.toFloat()
                        },
                        label = {
                            Text(stringResource(presetMinutesRes, preset))
                        }
                    )
                }
            }
            MiuixSliderRow(
                title = stringResource(minutesLabelRes),
                value = sliderValue,
                valueRange = RemindAlarmScheduler.MIN_MINUTES.toFloat()..RemindAlarmScheduler.MAX_MINUTES.toFloat(),
                steps = RemindAlarmScheduler.MAX_MINUTES - RemindAlarmScheduler.MIN_MINUTES - 1,
                formatLabel = { value ->
                    val minutes = value.roundToInt().coerceIn(
                        RemindAlarmScheduler.MIN_MINUTES,
                        RemindAlarmScheduler.MAX_MINUTES
                    )
                    formatMinutes(minutes)
                },
                onValueChange = { value ->
                    sliderValue = value
                    selectedMinutes = value.roundToInt().coerceIn(
                        RemindAlarmScheduler.MIN_MINUTES,
                        RemindAlarmScheduler.MAX_MINUTES
                    )
                }
            )
            Button(
                onClick = { onConfirm(pendingMinutes ?: selectedMinutes) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (pendingMinutes != null) {
                        stringResource(cancelRes, pendingMinutes)
                    } else {
                        stringResource(confirmRes, selectedMinutes)
                    }
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
