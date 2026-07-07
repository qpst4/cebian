package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.gesture.PointerSwipeDirection
import com.slideindex.app.gesture.PointerSwipeDistance
import kotlin.math.roundToInt

@Composable
fun PointerSwipeConfigDialog(
    initialConfig: PointerSwipeConfig,
    onDismissRequest: () -> Unit,
    onConfirm: (PointerSwipeConfig) -> Unit,
) {
    var direction by remember { mutableStateOf(initialConfig.direction) }
    var distance by remember { mutableStateOf(initialConfig.distance) }
    var durationMs by remember { mutableIntStateOf(initialConfig.durationMs) }
    var pointerCount by remember { mutableIntStateOf(initialConfig.pointerCount) }
    var pickingDirection by remember { mutableStateOf(false) }

    if (pickingDirection) {
        AlertDialog(
            onDismissRequest = { pickingDirection = false },
            title = { Text(stringResource(R.string.pointer_swipe_direction_title)) },
            text = {
                Column {
                    PointerSwipeDirection.entries.forEach { option ->
                        TextButton(
                            onClick = {
                                direction = option
                                pickingDirection = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(pointerSwipeDirectionLabel(option))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pickingDirection = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.pointer_swipe_config_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingNavigationRow(
                    icon = {
                        androidx.compose.material3.Icon(
                            imageVector = pointerSwipeDirectionIcon(direction),
                            contentDescription = null,
                        )
                    },
                    title = stringResource(R.string.pointer_swipe_direction_title),
                    subtitle = pointerSwipeDirectionLabel(direction),
                    onClick = { pickingDirection = true },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.pointer_swipe_distance_title),
                    value = distance.id.toFloat(),
                    valueRange = 0f..2f,
                    steps = 1,
                    enabled = true,
                    label = pointerSwipeDistanceLabel(distance),
                    onValueChange = { distance = PointerSwipeDistance.fromId(it.roundToInt()) },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.pointer_swipe_duration_title),
                    value = durationMs.toFloat(),
                    valueRange = 20f..500f,
                    steps = 23,
                    enabled = true,
                    label = stringResource(R.string.pointer_swipe_duration_value, durationMs),
                    onValueChange = { durationMs = it.roundToInt() },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.pointer_swipe_pointer_count_title),
                    value = pointerCount.toFloat(),
                    valueRange = 1f..5f,
                    steps = 3,
                    enabled = true,
                    label = pointerCount.toString(),
                    onValueChange = { pointerCount = it.roundToInt().coerceIn(1, 5) },
                )
                Text(
                    text = stringResource(R.string.pointer_swipe_config_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        PointerSwipeConfig(
                            direction = direction,
                            distance = distance,
                            durationMs = durationMs,
                            pointerCount = pointerCount,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun pointerSwipeDirectionLabel(direction: PointerSwipeDirection): String = when (direction) {
    PointerSwipeDirection.LEFT -> stringResource(R.string.pointer_swipe_direction_left)
    PointerSwipeDirection.UP -> stringResource(R.string.pointer_swipe_direction_up)
    PointerSwipeDirection.RIGHT -> stringResource(R.string.pointer_swipe_direction_right)
    PointerSwipeDirection.DOWN -> stringResource(R.string.pointer_swipe_direction_down)
}

@Composable
private fun pointerSwipeDistanceLabel(distance: PointerSwipeDistance): String = when (distance) {
    PointerSwipeDistance.SHORT -> stringResource(R.string.pointer_swipe_distance_short)
    PointerSwipeDistance.MEDIUM -> stringResource(R.string.pointer_swipe_distance_medium)
    PointerSwipeDistance.LONG -> stringResource(R.string.pointer_swipe_distance_long)
}
