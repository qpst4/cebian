package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.notification.NotificationFilterPreferences
import com.slideindex.app.notification.NotificationFilterSettings
import com.slideindex.app.ui.viewmodel.NotificationHistoryViewModel
import kotlin.math.roundToInt

fun LazyListScope.notificationSettingsItems(
    filterSettings: NotificationFilterSettings,
    listenerEnabled: Boolean,
    onRequestListenerAccess: () -> Unit,
    onSetNotificationHistoryMaxCount: (Int) -> Unit,
    onRestoreAllSnoozed: (Boolean) -> Int,
) {
    item(key = "restore_snoozed") {
        val context = LocalContext.current
        val appContext = context.applicationContext
        val restoreEmptyMessage = stringResource(R.string.notification_restore_snoozed_empty)
        SettingsCard {
            Text(
                text = stringResource(R.string.notification_restore_snoozed_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            Button(
                onClick = {
                    if (!listenerEnabled) {
                        Toast.makeText(
                            context,
                            R.string.notification_hide_listener_required,
                            Toast.LENGTH_SHORT,
                        ).show()
                        onRequestListenerAccess()
                        return@Button
                    }
                    val restored = onRestoreAllSnoozed(listenerEnabled)
                    if (restored < 0) return@Button
                    Toast.makeText(
                        context,
                        if (restored > 0) {
                            appContext.getString(R.string.notification_restore_snoozed_result, restored)
                        } else {
                            restoreEmptyMessage
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.notification_restore_snoozed_action))
            }
        }
    }
    item(key = "history_section") {
        MiuixSmallTitle(stringResource(R.string.notification_settings_history_section))
    }
    item(key = "history_max_count") {
        val appContext = LocalContext.current.applicationContext
        val formatMaxCountLabel = remember(appContext) {
            { value: Float ->
                appContext.getString(
                    R.string.notification_history_max_count_value,
                    value.roundToInt(),
                )
            }
        }
        val maxCountRange = NotificationFilterPreferences.MIN_NOTIFICATION_HISTORY_MAX_COUNT.toFloat()..
            NotificationFilterPreferences.MAX_NOTIFICATION_HISTORY_MAX_COUNT.toFloat()
        val maxCountSteps = (
            (NotificationFilterPreferences.MAX_NOTIFICATION_HISTORY_MAX_COUNT -
                NotificationFilterPreferences.MIN_NOTIFICATION_HISTORY_MAX_COUNT) /
                NotificationFilterPreferences.NOTIFICATION_HISTORY_MAX_COUNT_STEP
            ) - 1
        val snapMaxCount: (Float) -> Float = { value ->
            val step = NotificationFilterPreferences.NOTIFICATION_HISTORY_MAX_COUNT_STEP
            val snapped = ((value / step).roundToInt() * step)
                .coerceIn(
                    NotificationFilterPreferences.MIN_NOTIFICATION_HISTORY_MAX_COUNT,
                    NotificationFilterPreferences.MAX_NOTIFICATION_HISTORY_MAX_COUNT,
                )
            snapped.toFloat()
        }
        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.notification_history_max_count_title),
                value = filterSettings.notificationHistoryMaxCount.toFloat(),
                valueRange = maxCountRange,
                steps = maxCountSteps,
                enabled = true,
                label = formatMaxCountLabel(filterSettings.notificationHistoryMaxCount.toFloat()),
                formatLabel = formatMaxCountLabel,
                snapValue = snapMaxCount,
                onValueChange = { value ->
                    val count = snapMaxCount(value).roundToInt()
                    onSetNotificationHistoryMaxCount(count)
                },
            )
        }
    }
    item(key = "settings_hint") {
        Text(
            text = stringResource(R.string.notification_settings_rules_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}
