package com.slideindex.app.ui.notificationrule

import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.notification.NotificationFilterRule
import com.slideindex.app.notification.NotificationRuleActionType
import com.slideindex.app.notification.RuleActionEntry

@Composable
internal fun NotificationRuleActionPicker(
    actionEntries: List<RuleActionEntry>,
    onActionEntriesChange: (List<RuleActionEntry>) -> Unit,
) {
    MiuixSmallTitle(stringResource(R.string.notification_rule_actions))
    NotificationRuleActionType.entries.forEach { type ->
        val selected = actionEntries.any { it.type == type }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onActionEntriesChange(
                        if (selected) {
                            actionEntries.filterNot { it.type == type }
                        } else {
                            actionEntries + NotificationFilterRule.defaultAction(type)
                        },
                    )
                },
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { enabled ->
                    onActionEntriesChange(
                        if (enabled) {
                            actionEntries + NotificationFilterRule.defaultAction(type)
                        } else {
                            actionEntries.filterNot { it.type == type }
                        },
                    )
                },
            )
            Text(actionTypeLabel(type))
        }
        if (selected) {
            ActionConfigFields(
                entry = actionEntries.first { it.type == type },
                onChange = { updated ->
                    onActionEntriesChange(actionEntries.map { if (it.type == type) updated else it })
                },
            )
        }
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
                MiuixLabeledTextField(
                    value = entry.delayTimeMs.toString(),
                    onValueChange = { onChange(entry.copy(delayTimeMs = it.toLongOrNull() ?: 0)) },
                    label = stringResource(R.string.notification_rule_action_delay),
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
                MiuixLabeledTextField(
                    value = entry.laterTimesMs.firstOrNull()?.let(::msToTimeString).orEmpty(),
                    onValueChange = {
                        onChange(entry.copy(laterTimesMs = listOf(parseTimeMs(it))))
                    },
                    label = stringResource(R.string.notification_rule_action_later_time),
                )
            }
            NotificationRuleActionType.REPLACE -> {
                MiuixLabeledTextField(
                    value = entry.replaceTitle.orEmpty(),
                    onValueChange = { onChange(entry.copy(replaceTitle = it)) },
                    label = stringResource(R.string.notification_rule_action_replace_title),
                )
                MiuixLabeledTextField(
                    value = entry.replaceMessage.orEmpty(),
                    onValueChange = { onChange(entry.copy(replaceMessage = it)) },
                    label = stringResource(R.string.notification_rule_action_replace_message),
                )
            }
            NotificationRuleActionType.CHANGE_SOUND -> {
                MiuixLabeledTextField(
                    value = entry.soundUri.orEmpty(),
                    onValueChange = { onChange(entry.copy(soundUri = it)) },
                    label = stringResource(R.string.notification_rule_action_sound_uri),
                )
            }
            NotificationRuleActionType.TTS -> {
                MiuixLabeledTextField(
                    value = entry.ttsTemplate.orEmpty(),
                    onValueChange = { onChange(entry.copy(ttsTemplate = it)) },
                    label = stringResource(R.string.notification_rule_action_tts_template),
                )
            }
            NotificationRuleActionType.CLICK_BUTTON -> {
                var rawText by remember { mutableStateOf(entry.buttonNames.joinToString("\n")) }
                MiuixLabeledTextField(
                    value = rawText,
                    onValueChange = { newText ->
                        rawText = newText
                        onChange(entry.copy(buttonNames = parseLines(newText)))
                    },
                    label = stringResource(R.string.notification_rule_action_button_names),
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                )
            }
            NotificationRuleActionType.WEBHOOK -> {
                MiuixLabeledTextField(
                    value = entry.webhookUrl.orEmpty(),
                    onValueChange = { onChange(entry.copy(webhookUrl = it)) },
                    label = stringResource(R.string.notification_rule_action_webhook_url),
                )
                MiuixLabeledTextField(
                    value = entry.webhookBody.orEmpty(),
                    onValueChange = { onChange(entry.copy(webhookBody = it)) },
                    label = stringResource(R.string.notification_rule_action_webhook_body),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 6,
                )
            }
            else -> Unit
        }
    }
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
