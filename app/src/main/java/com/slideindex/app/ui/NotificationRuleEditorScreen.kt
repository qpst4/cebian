package com.slideindex.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.notification.AppTarget
import com.slideindex.app.notification.NotificationFilterRule
import com.slideindex.app.notification.NotificationRuleChargeMask
import com.slideindex.app.notification.ScreenMode
import com.slideindex.app.ui.notificationrule.NotificationRuleActionPicker
import com.slideindex.app.ui.notificationrule.NotificationRuleAppPickerDialog
import com.slideindex.app.ui.notificationrule.NotificationRuleConditionEditor
import com.slideindex.app.ui.notificationrule.msToTimeString
import com.slideindex.app.ui.notificationrule.parseLines
import com.slideindex.app.ui.notificationrule.parseTimeMs
import com.slideindex.app.ui.notificationrule.resolveChargeMask
import com.slideindex.app.ui.notificationrule.resolveScreenMode
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.viewmodel.NotificationHistoryViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import top.yukonga.miuix.kmp.basic.TextButton

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

    val title = if (initialRule == null) {
        stringResource(R.string.notification_rule_add)
    } else {
        stringResource(R.string.notification_rule_edit)
    }

    val saveRule: () -> Unit = {
        if (actionEntries.isEmpty()) {
            Toast.makeText(context, R.string.notification_rule_invalid, Toast.LENGTH_SHORT).show()
        } else {
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
        }
    }

    SettingsScreenScaffold(
        title = title,
        onBack = onBack,
        actions = {
            top.yukonga.miuix.kmp.basic.IconButton(onClick = saveRule) {
                top.yukonga.miuix.kmp.basic.Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.confirm),
                )
            }
        },
    ) {
        LazySettingsItem(key = "notification-rule-editor") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NotificationRuleConditionEditor(
                name = name,
                onNameChange = { name = it },
                channelId = channelId,
                onChannelIdChange = { channelId = it },
                appMode = appMode,
                onAppModeChange = { appMode = it },
                appTargets = appTargets,
                onPickApps = { showAppPicker = true },
                textMode = textMode,
                onTextModeChange = { textMode = it },
                keywordsText = keywordsText,
                onKeywordsTextChange = { keywordsText = it },
                keywordsExcludeText = keywordsExcludeText,
                onKeywordsExcludeTextChange = { keywordsExcludeText = it },
                regex = regex,
                onRegexChange = { regex = it },
                advancedJson = advancedJson,
                onAdvancedJsonChange = { advancedJson = it },
                timeStart = timeStart,
                onTimeStartChange = { timeStart = it },
                timeEnd = timeEnd,
                onTimeEndChange = { timeEnd = it },
                weekDays = weekDays,
                onWeekDaysChange = { weekDays = it },
                screenOn = screenOn,
                onScreenOnChange = { screenOn = it },
                screenOff = screenOff,
                onScreenOffChange = { screenOff = it },
                chargeBattery = chargeBattery,
                onChargeBatteryChange = { chargeBattery = it },
                chargeWired = chargeWired,
                onChargeWiredChange = { chargeWired = it },
                chargeWireless = chargeWireless,
                onChargeWirelessChange = { chargeWireless = it },
            )

            NotificationRuleActionPicker(
                actionEntries = actionEntries,
                onActionEntriesChange = { actionEntries = it },
            )
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
