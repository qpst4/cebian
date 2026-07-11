package com.slideindex.app.ui.notificationrule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.notification.AppMatchMode
import com.slideindex.app.notification.AppTarget
import com.slideindex.app.notification.NotificationRuleChargeMask
import com.slideindex.app.notification.ScreenMode
import com.slideindex.app.notification.TextMatchMode
import com.slideindex.app.ui.SettingsSectionTitle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NotificationRuleConditionEditor(
    name: String,
    onNameChange: (String) -> Unit,
    channelId: String,
    onChannelIdChange: (String) -> Unit,
    appMode: AppMatchMode,
    onAppModeChange: (AppMatchMode) -> Unit,
    appTargets: List<AppTarget>,
    onPickApps: () -> Unit,
    textMode: TextMatchMode,
    onTextModeChange: (TextMatchMode) -> Unit,
    keywordsText: String,
    onKeywordsTextChange: (String) -> Unit,
    keywordsExcludeText: String,
    onKeywordsExcludeTextChange: (String) -> Unit,
    regex: String,
    onRegexChange: (String) -> Unit,
    advancedJson: String,
    onAdvancedJsonChange: (String) -> Unit,
    timeStart: String,
    onTimeStartChange: (String) -> Unit,
    timeEnd: String,
    onTimeEndChange: (String) -> Unit,
    weekDays: Set<Int>,
    onWeekDaysChange: (Set<Int>) -> Unit,
    screenOn: Boolean,
    onScreenOnChange: (Boolean) -> Unit,
    screenOff: Boolean,
    onScreenOffChange: (Boolean) -> Unit,
    chargeBattery: Boolean,
    onChargeBatteryChange: (Boolean) -> Unit,
    chargeWired: Boolean,
    onChargeWiredChange: (Boolean) -> Unit,
    chargeWireless: Boolean,
    onChargeWirelessChange: (Boolean) -> Unit,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.notification_rule_name)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    SettingsSectionTitle(stringResource(R.string.notification_rule_section_apps))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppMatchMode.entries.forEach { mode ->
            FilterChip(
                selected = appMode == mode,
                onClick = { onAppModeChange(mode) },
                label = { Text(appModeLabel(mode)) },
            )
        }
    }
    if (appMode != AppMatchMode.ALL) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.notification_rule_selected_apps_count,
                    appTargets.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = onPickApps) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.notification_rule_pick_app))
            }
        }
    }

    SettingsSectionTitle(stringResource(R.string.notification_rule_section_text))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextMatchMode.entries.forEach { mode ->
            FilterChip(
                selected = textMode == mode,
                onClick = { onTextModeChange(mode) },
                label = { Text(textModeLabel(mode)) },
            )
        }
    }
    when (textMode) {
        TextMatchMode.CONTAIN_ANY, TextMatchMode.NOT_CONTAIN_ANY,
        TextMatchMode.CONTAIN_ALL, TextMatchMode.NOT_CONTAIN_ALL,
        -> {
            OutlinedTextField(
                value = keywordsText,
                onValueChange = onKeywordsTextChange,
                label = { Text(stringResource(R.string.notification_rule_keywords_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
        TextMatchMode.CONTAIN_AND_NOT_CONTAIN -> {
            OutlinedTextField(
                value = keywordsText,
                onValueChange = onKeywordsTextChange,
                label = { Text(stringResource(R.string.notification_rule_keywords_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = keywordsExcludeText,
                onValueChange = onKeywordsExcludeTextChange,
                label = { Text(stringResource(R.string.notification_rule_keywords_exclude_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
        TextMatchMode.REGEX -> {
            OutlinedTextField(
                value = regex,
                onValueChange = onRegexChange,
                label = { Text(stringResource(R.string.notification_rule_regex_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextMatchMode.ADVANCED -> {
            OutlinedTextField(
                value = advancedJson,
                onValueChange = onAdvancedJsonChange,
                label = { Text(stringResource(R.string.notification_rule_advanced_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
        }
        TextMatchMode.ALL -> Unit
    }
    OutlinedTextField(
        value = channelId,
        onValueChange = onChannelIdChange,
        label = { Text(stringResource(R.string.notification_rule_channel)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    SettingsSectionTitle(stringResource(R.string.notification_rule_section_time))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = timeStart,
            onValueChange = onTimeStartChange,
            label = { Text(stringResource(R.string.notification_rule_time_start)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("00:00") },
        )
        OutlinedTextField(
            value = timeEnd,
            onValueChange = onTimeEndChange,
            label = { Text(stringResource(R.string.notification_rule_time_end)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("00:00") },
        )
    }
    Text(
        text = stringResource(R.string.notification_rule_time_all_day),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SettingsSectionTitle(stringResource(R.string.notification_rule_week_days))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..7).forEach { day ->
            FilterChip(
                selected = day in weekDays,
                onClick = {
                    onWeekDaysChange(if (day in weekDays) weekDays - day else weekDays + day)
                },
                label = { Text(weekDayLabel(day)) },
            )
        }
    }

    SettingsSectionTitle(stringResource(R.string.notification_rule_section_device))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onScreenOnChange(!screenOn) }) {
        Checkbox(checked = screenOn, onCheckedChange = onScreenOnChange)
        Text(stringResource(R.string.notification_rule_screen_on))
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onScreenOffChange(!screenOff) }) {
        Checkbox(checked = screenOff, onCheckedChange = onScreenOffChange)
        Text(stringResource(R.string.notification_rule_screen_off))
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChargeBatteryChange(!chargeBattery) }) {
        Checkbox(checked = chargeBattery, onCheckedChange = onChargeBatteryChange)
        Text(stringResource(R.string.notification_rule_charge_battery))
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChargeWiredChange(!chargeWired) }) {
        Checkbox(checked = chargeWired, onCheckedChange = onChargeWiredChange)
        Text(stringResource(R.string.notification_rule_charge_wired))
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChargeWirelessChange(!chargeWireless) }) {
        Checkbox(checked = chargeWireless, onCheckedChange = onChargeWirelessChange)
        Text(stringResource(R.string.notification_rule_charge_wireless))
    }
}

@Composable
private fun appModeLabel(mode: AppMatchMode): String = when (mode) {
    AppMatchMode.ALL -> stringResource(R.string.notification_rule_app_mode_all)
    AppMatchMode.INCLUDE -> stringResource(R.string.notification_rule_app_mode_include)
    AppMatchMode.EXCLUDE -> stringResource(R.string.notification_rule_app_mode_exclude)
}

@Composable
private fun textModeLabel(mode: TextMatchMode): String = when (mode) {
    TextMatchMode.ALL -> stringResource(R.string.notification_rule_text_mode_all)
    TextMatchMode.CONTAIN_ANY -> stringResource(R.string.notification_rule_text_mode_contain_any)
    TextMatchMode.NOT_CONTAIN_ANY -> stringResource(R.string.notification_rule_text_mode_not_contain_any)
    TextMatchMode.CONTAIN_ALL -> stringResource(R.string.notification_rule_text_mode_contain_all)
    TextMatchMode.NOT_CONTAIN_ALL -> stringResource(R.string.notification_rule_text_mode_not_contain_all)
    TextMatchMode.CONTAIN_AND_NOT_CONTAIN -> stringResource(R.string.notification_rule_text_mode_contain_and_not)
    TextMatchMode.REGEX -> stringResource(R.string.notification_rule_text_mode_regex)
    TextMatchMode.ADVANCED -> stringResource(R.string.notification_rule_text_mode_advanced)
}

@Composable
internal fun weekDayLabel(day: Int): String = when (day) {
    1 -> stringResource(R.string.weekday_short_sunday)
    2 -> stringResource(R.string.weekday_short_monday)
    3 -> stringResource(R.string.weekday_short_tuesday)
    4 -> stringResource(R.string.weekday_short_wednesday)
    5 -> stringResource(R.string.weekday_short_thursday)
    6 -> stringResource(R.string.weekday_short_friday)
    7 -> stringResource(R.string.weekday_short_saturday)
    else -> day.toString()
}

internal fun parseLines(text: String): List<String> =
    text.lines().map { it.trim() }.filter { it.isNotBlank() }

internal fun parseTimeMs(value: String): Int {
    val parts = value.trim().split(":")
    if (parts.size != 2) return 0
    val hour = parts[0].toIntOrNull() ?: return 0
    val minute = parts[1].toIntOrNull() ?: return 0
    return ((hour * 60) + minute) * 60 * 1000
}

internal fun msToTimeString(ms: Int): String {
    if (ms <= 0) return "00:00"
    val totalMinutes = ms / 60_000
    val hour = totalMinutes / 60
    val minute = totalMinutes % 60
    return "%02d:%02d".format(hour, minute)
}

internal fun resolveScreenMode(on: Boolean, off: Boolean): ScreenMode = when {
    on && off -> ScreenMode.BOTH
    on -> ScreenMode.ON
    off -> ScreenMode.OFF
    else -> ScreenMode.BOTH
}

internal fun resolveChargeMask(battery: Boolean, wired: Boolean, wireless: Boolean): Int {
    if (battery && wired && wireless) return NotificationRuleChargeMask.ALL
    var mask = 0
    if (battery) mask = mask or NotificationRuleChargeMask.BATTERY
    if (wired) mask = mask or NotificationRuleChargeMask.WIRED
    if (wireless) mask = mask or NotificationRuleChargeMask.WIRELESS
    return if (mask == 0) NotificationRuleChargeMask.ALL else mask
}
