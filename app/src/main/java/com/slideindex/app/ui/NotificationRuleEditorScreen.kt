package com.slideindex.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.ui.viewmodel.NotificationHistoryViewModel
import com.slideindex.app.notification.AppMatchMode
import com.slideindex.app.notification.AppTarget
import com.slideindex.app.notification.NotificationFilterRule
import com.slideindex.app.notification.NotificationRuleActionType
import com.slideindex.app.notification.NotificationRuleChargeMask
import com.slideindex.app.notification.NotificationRuleWeekDays
import com.slideindex.app.notification.RuleActionEntry
import com.slideindex.app.notification.ScreenMode
import com.slideindex.app.notification.TextMatchMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationRuleEditorScreen(
    initialRule: NotificationFilterRule?,
    viewModel: NotificationHistoryViewModel,
    onBack: () -> Unit,
    onSave: (NotificationFilterRule) -> Unit,
) {
    val context = LocalContext.current
    val seed = remember(initialRule) { (initialRule ?: NotificationFilterRule()).normalized() }

    var name by remember(seed) { mutableStateOf(seed.name) }
    var channelId by remember(seed) { mutableStateOf(seed.channelId.orEmpty()) }
    var appMode by remember(seed) { mutableStateOf(seed.appMode) }
    var appTargets by remember(seed) { mutableStateOf(seed.appTargets) }
    var textMode by remember(seed) { mutableStateOf(seed.textMode) }
    var keywordsText by remember(seed) { mutableStateOf(seed.keywords.joinToString("\n")) }
    var keywordsExcludeText by remember(seed) { mutableStateOf(seed.keywordsExclude.joinToString("\n")) }
    var regex by remember(seed) { mutableStateOf(seed.regex.orEmpty()) }
    var advancedJson by remember(seed) { mutableStateOf(seed.advancedFilterJson.orEmpty()) }
    var timeStart by remember(seed) { mutableStateOf(msToTimeString(seed.timeStartMs)) }
    var timeEnd by remember(seed) { mutableStateOf(msToTimeString(seed.timeEndMs)) }
    var weekDays by remember(seed) { mutableStateOf(seed.weekDays) }
    var screenOn by remember(seed) { mutableStateOf(seed.screenMode != ScreenMode.OFF) }
    var screenOff by remember(seed) { mutableStateOf(seed.screenMode != ScreenMode.ON) }
    var chargeBattery by remember(seed) {
        mutableStateOf(seed.chargeMask and NotificationRuleChargeMask.BATTERY != 0)
    }
    var chargeWired by remember(seed) {
        mutableStateOf(seed.chargeMask and NotificationRuleChargeMask.WIRED != 0)
    }
    var chargeWireless by remember(seed) {
        mutableStateOf(seed.chargeMask and NotificationRuleChargeMask.WIRELESS != 0)
    }
    var actionEntries by remember(seed) { mutableStateOf(seed.actionEntries) }
    var showAppPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (initialRule == null) {
                            stringResource(R.string.notification_rule_add)
                        } else {
                            stringResource(R.string.notification_rule_edit)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (actionEntries.isEmpty()) {
                                Toast.makeText(context, R.string.notification_rule_invalid, Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            onSave(
                                NotificationFilterRule(
                                    id = initialRule?.id ?: java.util.UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    enabled = initialRule?.enabled ?: true,
                                    userCreated = true,
                                    createdAtMs = initialRule?.createdAtMs ?: System.currentTimeMillis(),
                                    channelId = channelId.trim().takeIf { it.isNotBlank() },
                                    appMode = appMode,
                                    appTargets = appTargets,
                                    textMode = textMode,
                                    keywords = parseLines(keywordsText),
                                    keywordsExclude = parseLines(keywordsExcludeText),
                                    regex = regex.trim().takeIf { it.isNotBlank() },
                                    advancedFilterJson = advancedJson.trim().takeIf { it.isNotBlank() },
                                    timeStartMs = parseTimeMs(timeStart),
                                    timeEndMs = parseTimeMs(timeEnd),
                                    weekDays = weekDays,
                                    screenMode = resolveScreenMode(screenOn, screenOff),
                                    chargeMask = resolveChargeMask(chargeBattery, chargeWired, chargeWireless),
                                    actionEntries = actionEntries,
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.notification_rule_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            SettingsSectionTitle(stringResource(R.string.notification_rule_section_apps))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppMatchMode.entries.forEach { mode ->
                    FilterChip(
                        selected = appMode == mode,
                        onClick = { appMode = mode },
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
                    IconButton(onClick = { showAppPicker = true }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.notification_rule_pick_app))
                    }
                }
            }

            SettingsSectionTitle(stringResource(R.string.notification_rule_section_text))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextMatchMode.entries.forEach { mode ->
                    FilterChip(
                        selected = textMode == mode,
                        onClick = { textMode = mode },
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
                        onValueChange = { keywordsText = it },
                        label = { Text(stringResource(R.string.notification_rule_keywords_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
                TextMatchMode.CONTAIN_AND_NOT_CONTAIN -> {
                    OutlinedTextField(
                        value = keywordsText,
                        onValueChange = { keywordsText = it },
                        label = { Text(stringResource(R.string.notification_rule_keywords_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    OutlinedTextField(
                        value = keywordsExcludeText,
                        onValueChange = { keywordsExcludeText = it },
                        label = { Text(stringResource(R.string.notification_rule_keywords_exclude_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
                TextMatchMode.REGEX -> {
                    OutlinedTextField(
                        value = regex,
                        onValueChange = { regex = it },
                        label = { Text(stringResource(R.string.notification_rule_regex_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextMatchMode.ADVANCED -> {
                    OutlinedTextField(
                        value = advancedJson,
                        onValueChange = { advancedJson = it },
                        label = { Text(stringResource(R.string.notification_rule_advanced_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                    )
                }
                TextMatchMode.ALL -> Unit
            }
            OutlinedTextField(
                value = channelId,
                onValueChange = { channelId = it },
                label = { Text(stringResource(R.string.notification_rule_channel)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            SettingsSectionTitle(stringResource(R.string.notification_rule_section_time))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = timeStart,
                    onValueChange = { timeStart = it },
                    label = { Text(stringResource(R.string.notification_rule_time_start)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("00:00") },
                )
                OutlinedTextField(
                    value = timeEnd,
                    onValueChange = { timeEnd = it },
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
                            weekDays = if (day in weekDays) weekDays - day else weekDays + day
                        },
                        label = { Text(weekDayLabel(day)) },
                    )
                }
            }

            SettingsSectionTitle(stringResource(R.string.notification_rule_section_device))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { screenOn = !screenOn }) {
                Checkbox(checked = screenOn, onCheckedChange = { screenOn = it })
                Text(stringResource(R.string.notification_rule_screen_on))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { screenOff = !screenOff }) {
                Checkbox(checked = screenOff, onCheckedChange = { screenOff = it })
                Text(stringResource(R.string.notification_rule_screen_off))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { chargeBattery = !chargeBattery }) {
                Checkbox(checked = chargeBattery, onCheckedChange = { chargeBattery = it })
                Text(stringResource(R.string.notification_rule_charge_battery))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { chargeWired = !chargeWired }) {
                Checkbox(checked = chargeWired, onCheckedChange = { chargeWired = it })
                Text(stringResource(R.string.notification_rule_charge_wired))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { chargeWireless = !chargeWireless }) {
                Checkbox(checked = chargeWireless, onCheckedChange = { chargeWireless = it })
                Text(stringResource(R.string.notification_rule_charge_wireless))
            }

            SettingsSectionTitle(stringResource(R.string.notification_rule_actions))
            NotificationRuleActionType.entries.forEach { type ->
                val selected = actionEntries.any { it.type == type }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            actionEntries = if (selected) {
                                actionEntries.filterNot { it.type == type }
                            } else {
                                actionEntries + NotificationFilterRule.defaultAction(type)
                            }
                        },
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { enabled ->
                            actionEntries = if (enabled) {
                                actionEntries + NotificationFilterRule.defaultAction(type)
                            } else {
                                actionEntries.filterNot { it.type == type }
                            }
                        },
                    )
                    Text(actionTypeLabel(type))
                }
                if (selected) {
                    ActionConfigFields(
                        entry = actionEntries.first { it.type == type },
                        onChange = { updated ->
                            actionEntries = actionEntries.map { if (it.type == type) updated else it }
                        },
                    )
                }
            }
        }
    }

    if (showAppPicker) {
        NotificationRuleAppPickerDialog(
            viewModel = viewModel,
            initialPackageNames = appTargets.map { it.packageName }.toSet(),
            onDismiss = { showAppPicker = false },
            onConfirm = { selected ->
                appTargets = selected.map { AppTarget(it) }
                showAppPicker = false
            },
        )
    }
}

@Composable
private fun ActionConfigFields(
    entry: RuleActionEntry,
    onChange: (RuleActionEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (entry.type) {
            NotificationRuleActionType.HIDE -> {
                OutlinedTextField(
                    value = entry.delayTimeMs.toString(),
                    onValueChange = { onChange(entry.copy(delayTimeMs = it.toLongOrNull() ?: 0)) },
                    label = { Text(stringResource(R.string.notification_rule_action_delay)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = entry.includeOngoing,
                        onCheckedChange = { onChange(entry.copy(includeOngoing = it)) },
                    )
                    Text(stringResource(R.string.notification_rule_action_include_ongoing))
                }
            }
            NotificationRuleActionType.LATER -> {
                OutlinedTextField(
                    value = entry.laterTimesMs.firstOrNull()?.let(::msToTimeString).orEmpty(),
                    onValueChange = {
                        onChange(entry.copy(laterTimesMs = listOf(parseTimeMs(it))))
                    },
                    label = { Text(stringResource(R.string.notification_rule_action_later_time)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            NotificationRuleActionType.REPLACE -> {
                OutlinedTextField(
                    value = entry.replaceTitle.orEmpty(),
                    onValueChange = { onChange(entry.copy(replaceTitle = it)) },
                    label = { Text(stringResource(R.string.notification_rule_action_replace_title)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = entry.replaceMessage.orEmpty(),
                    onValueChange = { onChange(entry.copy(replaceMessage = it)) },
                    label = { Text(stringResource(R.string.notification_rule_action_replace_message)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            NotificationRuleActionType.CHANGE_SOUND -> {
                OutlinedTextField(
                    value = entry.soundUri.orEmpty(),
                    onValueChange = { onChange(entry.copy(soundUri = it)) },
                    label = { Text(stringResource(R.string.notification_rule_action_sound_uri)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            NotificationRuleActionType.TTS -> {
                OutlinedTextField(
                    value = entry.ttsTemplate.orEmpty(),
                    onValueChange = { onChange(entry.copy(ttsTemplate = it)) },
                    label = { Text(stringResource(R.string.notification_rule_action_tts_template)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            NotificationRuleActionType.CLICK_BUTTON -> {
                var rawText by remember { mutableStateOf(entry.buttonNames.joinToString("\n")) }
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { newText ->
                        rawText = newText
                        onChange(entry.copy(buttonNames = parseLines(newText)))
                    },
                    label = { Text(stringResource(R.string.notification_rule_action_button_names)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    singleLine = false,
                )
            }
            NotificationRuleActionType.WEBHOOK -> {
                OutlinedTextField(
                    value = entry.webhookUrl.orEmpty(),
                    onValueChange = { onChange(entry.copy(webhookUrl = it)) },
                    label = { Text(stringResource(R.string.notification_rule_action_webhook_url)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = entry.webhookBody.orEmpty(),
                    onValueChange = { onChange(entry.copy(webhookBody = it)) },
                    label = { Text(stringResource(R.string.notification_rule_action_webhook_body)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
            else -> Unit
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
fun actionTypeLabel(type: NotificationRuleActionType): String = when (type) {
    NotificationRuleActionType.HIDE -> stringResource(R.string.notification_rule_action_hide)
    NotificationRuleActionType.MUTE -> stringResource(R.string.notification_rule_action_mute)
    NotificationRuleActionType.LATER -> stringResource(R.string.notification_rule_action_later)
    NotificationRuleActionType.REPLACE -> stringResource(R.string.notification_rule_action_replace)
    NotificationRuleActionType.CHANGE_SOUND -> stringResource(R.string.notification_rule_action_change_sound)
    NotificationRuleActionType.CALL_NOTIFY -> stringResource(R.string.notification_rule_action_call)
    NotificationRuleActionType.TTS -> stringResource(R.string.notification_rule_action_tts)
    NotificationRuleActionType.CLICK_BUTTON -> stringResource(R.string.notification_rule_action_click_button)
    NotificationRuleActionType.OPEN -> stringResource(R.string.notification_rule_action_open)
    NotificationRuleActionType.WEBHOOK -> stringResource(R.string.notification_rule_action_webhook)
}

private fun weekDayLabel(day: Int): String = when (day) {
    1 -> "日"
    2 -> "一"
    3 -> "二"
    4 -> "三"
    5 -> "四"
    6 -> "五"
    7 -> "六"
    else -> day.toString()
}

private fun parseLines(text: String): List<String> =
    text.lines().map { it.trim() }.filter { it.isNotBlank() }

private fun parseTimeMs(value: String): Int {
    val parts = value.trim().split(":")
    if (parts.size != 2) return 0
    val hour = parts[0].toIntOrNull() ?: return 0
    val minute = parts[1].toIntOrNull() ?: return 0
    return ((hour * 60) + minute) * 60 * 1000
}

private fun msToTimeString(ms: Int): String {
    if (ms <= 0) return "00:00"
    val totalMinutes = ms / 60_000
    val hour = totalMinutes / 60
    val minute = totalMinutes % 60
    return "%02d:%02d".format(hour, minute)
}

private fun resolveScreenMode(on: Boolean, off: Boolean): ScreenMode = when {
    on && off -> ScreenMode.BOTH
    on -> ScreenMode.ON
    off -> ScreenMode.OFF
    else -> ScreenMode.BOTH
}

private fun resolveChargeMask(battery: Boolean, wired: Boolean, wireless: Boolean): Int {
    if (battery && wired && wireless) return NotificationRuleChargeMask.ALL
    var mask = 0
    if (battery) mask = mask or NotificationRuleChargeMask.BATTERY
    if (wired) mask = mask or NotificationRuleChargeMask.WIRED
    if (wireless) mask = mask or NotificationRuleChargeMask.WIRELESS
    return if (mask == 0) NotificationRuleChargeMask.ALL else mask
}

@Composable
fun NotificationRuleAppPickerDialog(
    viewModel: NotificationHistoryViewModel,
    initialPackageNames: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember(initialPackageNames) { mutableStateOf(initialPackageNames) }
    LaunchedEffect(Unit) {
        apps = viewModel.loadAppsForPicker()
    }
    val filtered = remember(apps, query) {
        viewModel.searchApps(apps, query)
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notification_rule_pick_app)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    hintResId = R.string.notification_rule_app_search_hint,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = { selected = selected + filtered.map { it.packageName } },
                        enabled = filtered.isNotEmpty(),
                    ) { Text(stringResource(R.string.notification_rule_select_all)) }
                    TextButton(
                        onClick = {
                            val toggled = selected.toMutableSet()
                            filtered.forEach { appInfo ->
                                if (appInfo.packageName in toggled) toggled.remove(appInfo.packageName)
                                else toggled.add(appInfo.packageName)
                            }
                            selected = toggled
                        },
                        enabled = filtered.isNotEmpty(),
                    ) { Text(stringResource(R.string.notification_rule_invert_selection)) }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filtered, key = { it.packageName }) { appInfo ->
                        val checked = appInfo.packageName in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (checked) selected - appInfo.packageName else selected + appInfo.packageName
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text("${appInfo.label} (${appInfo.packageName})", modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
