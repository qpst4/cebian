package com.slideindex.app.ui.notificationrule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiuixLabeledTextField(
                value = name,
                onValueChange = onNameChange,
                label = stringResource(R.string.notification_rule_name),
            )
            MiuixLabeledTextField(
                value = channelId,
                onValueChange = onChannelIdChange,
                label = stringResource(R.string.notification_rule_channel),
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            val appModes = AppMatchMode.entries
            val appModeLabels = listOf(
                stringResource(R.string.notification_rule_app_mode_all),
                stringResource(R.string.notification_rule_app_mode_include),
                stringResource(R.string.notification_rule_app_mode_exclude),
            )
            WindowDropdownPreference(
                title = stringResource(R.string.notification_rule_section_apps),
                items = appModeLabels,
                selectedIndex = appModes.indexOf(appMode).coerceAtLeast(0),
                onSelectedIndexChange = { index ->
                    if (index in appModes.indices) {
                        onAppModeChange(appModes[index])
                    }
                },
            )
            if (appMode != AppMatchMode.ALL) {
                ArrowPreference(
                    title = stringResource(R.string.notification_rule_pick_app),
                    summary = stringResource(
                        R.string.notification_rule_selected_apps_count,
                        appTargets.size,
                    ),
                    onClick = onPickApps,
                )
            }

            val textModes = TextMatchMode.entries
            val textModeLabels = listOf(
                stringResource(R.string.notification_rule_text_mode_all),
                stringResource(R.string.notification_rule_text_mode_contain_any),
                stringResource(R.string.notification_rule_text_mode_not_contain_any),
                stringResource(R.string.notification_rule_text_mode_contain_all),
                stringResource(R.string.notification_rule_text_mode_not_contain_all),
                stringResource(R.string.notification_rule_text_mode_contain_and_not),
                stringResource(R.string.notification_rule_text_mode_regex),
                stringResource(R.string.notification_rule_text_mode_advanced),
            )
            WindowDropdownPreference(
                title = stringResource(R.string.notification_rule_section_text),
                items = textModeLabels,
                selectedIndex = textModes.indexOf(textMode).coerceAtLeast(0),
                onSelectedIndexChange = { index ->
                    if (index in textModes.indices) {
                        onTextModeChange(textModes[index])
                    }
                },
            )

            when (textMode) {
                TextMatchMode.CONTAIN_ANY, TextMatchMode.NOT_CONTAIN_ANY,
                TextMatchMode.CONTAIN_ALL, TextMatchMode.NOT_CONTAIN_ALL,
                -> {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        MiuixLabeledTextField(
                            value = keywordsText,
                            onValueChange = onKeywordsTextChange,
                            label = stringResource(R.string.notification_rule_keywords_hint),
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                        )
                    }
                }
                TextMatchMode.CONTAIN_AND_NOT_CONTAIN -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MiuixLabeledTextField(
                            value = keywordsText,
                            onValueChange = onKeywordsTextChange,
                            label = stringResource(R.string.notification_rule_keywords_hint),
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                        )
                        MiuixLabeledTextField(
                            value = keywordsExcludeText,
                            onValueChange = onKeywordsExcludeTextChange,
                            label = stringResource(R.string.notification_rule_keywords_exclude_hint),
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                        )
                    }
                }
                TextMatchMode.REGEX -> {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        MiuixLabeledTextField(
                            value = regex,
                            onValueChange = onRegexChange,
                            label = stringResource(R.string.notification_rule_regex_hint),
                        )
                    }
                }
                TextMatchMode.ADVANCED -> {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        MiuixLabeledTextField(
                            value = advancedJson,
                            onValueChange = onAdvancedJsonChange,
                            label = stringResource(R.string.notification_rule_advanced_hint),
                            singleLine = false,
                            minLines = 4,
                            maxLines = 8,
                        )
                    }
                }
                TextMatchMode.ALL -> Unit
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiuixSmallTitle(stringResource(R.string.notification_rule_section_time))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiuixLabeledTextField(
                    value = timeStart,
                    onValueChange = onTimeStartChange,
                    label = stringResource(R.string.notification_rule_time_start),
                    modifier = Modifier.weight(1f),
                )
                MiuixLabeledTextField(
                    value = timeEnd,
                    onValueChange = onTimeEndChange,
                    label = stringResource(R.string.notification_rule_time_end),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.notification_rule_time_all_day),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )

            MiuixSmallTitle(stringResource(R.string.notification_rule_week_days), modifier = Modifier.padding(top = 4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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

            MiuixSmallTitle(stringResource(R.string.notification_rule_section_device), modifier = Modifier.padding(top = 4.dp))
            CheckboxPreference(
                title = stringResource(R.string.notification_rule_screen_on),
                checked = screenOn,
                onCheckedChange = onScreenOnChange,
            )
            CheckboxPreference(
                title = stringResource(R.string.notification_rule_screen_off),
                checked = screenOff,
                onCheckedChange = onScreenOffChange,
            )
            CheckboxPreference(
                title = stringResource(R.string.notification_rule_charge_battery),
                checked = chargeBattery,
                onCheckedChange = onChargeBatteryChange,
            )
            CheckboxPreference(
                title = stringResource(R.string.notification_rule_charge_wired),
                checked = chargeWired,
                onCheckedChange = onChargeWiredChange,
            )
            CheckboxPreference(
                title = stringResource(R.string.notification_rule_charge_wireless),
                checked = chargeWireless,
                onCheckedChange = onChargeWirelessChange,
            )
        }
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
