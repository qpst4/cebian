package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
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
import com.slideindex.app.message.MessageAppFilterRule
import com.slideindex.app.message.MessageFilterMode
import com.slideindex.app.message.MessageMatchCondition
import com.slideindex.app.message.MessageMatchField
import com.slideindex.app.message.MessageMatchType
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.settingsCardItems

@Composable
fun MessageAppFilterEditorScreen(
    appLabel: String,
    rule: MessageAppFilterRule,
    onBack: () -> Unit,
    onSave: (MessageAppFilterRule) -> Unit,
) {
    var mode by remember(rule) { mutableStateOf(rule.mode) }
    var onlyConditions by remember(rule) { mutableStateOf(rule.onlyMatchingConditions) }
    var blockConditions by remember(rule) { mutableStateOf(rule.blockMatchingConditions) }

    val editorSubtitle = stringResource(R.string.message_filter_editor_subtitle)
    val modeSectionTitle = stringResource(R.string.message_filter_mode_title)

    val modeRadioGroup = settingsCardItems(mode) {
        MessageFilterMode.entries.forEach { option ->
            SettingRadioRow(
                title = messageFilterModeLabel(option),
                selected = mode == option,
                onClick = { mode = option },
            )
        }
    }

    SettingsFormScreen(
        title = appLabel,
        onBack = onBack,
        onConfirm = {
            onSave(
                rule.copy(
                    mode = mode,
                    onlyMatchingConditions = onlyConditions,
                    blockMatchingConditions = blockConditions,
                ),
            )
        },
    ) {
        MiuixHintText(editorSubtitle)
        MiuixSmallTitle(modeSectionTitle)
        modeRadioGroup.RenderRows()

        when (mode) {
            MessageFilterMode.ONLY_MATCHING -> {
                MiuixSmallTitle(
                    stringResource(R.string.message_filter_only_conditions),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MiuixSmallTitleSectionTop),
                )
                MessageFilterConditionsEditor(
                    conditions = onlyConditions,
                    onConditionsChange = { onlyConditions = it },
                )
            }
            MessageFilterMode.BLOCK_MATCHING -> {
                MiuixSmallTitle(
                    stringResource(R.string.message_filter_block_conditions),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MiuixSmallTitleSectionTop),
                )
                MessageFilterConditionsEditor(
                    conditions = blockConditions,
                    onConditionsChange = { blockConditions = it },
                )
            }
            MessageFilterMode.NO_FILTER -> Unit
        }
    }
}

@Composable
private fun MessageFilterConditionsEditor(
    conditions: List<MessageMatchCondition>,
    onConditionsChange: (List<MessageMatchCondition>) -> Unit,
) {
    val conditionsCard = settingsCardItems(conditions) {
        if (conditions.isEmpty()) {
            Text(
                text = stringResource(R.string.message_filter_conditions_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            conditions.forEachIndexed { index, condition ->
                messageFilterConditionRow(
                    condition = condition,
                    onChange = { updated ->
                        onConditionsChange(conditions.toMutableList().apply { this[index] = updated })
                    },
                    onRemove = {
                        onConditionsChange(conditions.toMutableList().apply { removeAt(index) })
                    },
                )
            }
        }
        SettingLinkRow(
            title = stringResource(R.string.message_filter_add_condition),
            onClick = {
                onConditionsChange(conditions + MessageMatchCondition())
            },
        )
    }
    conditionsCard.RenderRows()
}

@Composable
private fun SettingsCardScope.messageFilterConditionRow(
    condition: MessageMatchCondition,
    onChange: (MessageMatchCondition) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.message_filter_condition_title),
                style = MaterialTheme.typography.titleSmall,
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.message_filter_remove_condition),
                )
            }
        }
        messageFilterEnumPicker(
            label = stringResource(R.string.message_match_field_label),
            options = MessageMatchField.entries,
            selected = condition.field,
            labelFor = { messageMatchFieldLabel(it) },
            onSelect = { onChange(condition.copy(field = it)) },
        )
        messageFilterEnumPicker(
            label = stringResource(R.string.message_match_type_label),
            options = MessageMatchType.entries,
            selected = condition.type,
            labelFor = { messageMatchTypeLabel(it) },
            onSelect = { onChange(condition.copy(type = it)) },
        )
        MiuixLabeledTextField(
            value = condition.keyword,
            onValueChange = { onChange(condition.copy(keyword = it)) },
            label = stringResource(R.string.message_match_keyword_label),
        )
    }
}

@Composable
private fun <T> SettingsCardScope.messageFilterEnumPicker(
    label: String,
    options: List<T>,
    selected: T,
    labelFor: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        options.forEach { option ->
            SettingRadioRow(
                title = labelFor(option),
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
fun messageFilterModeLabel(mode: MessageFilterMode): String = when (mode) {
    MessageFilterMode.NO_FILTER -> stringResource(R.string.message_filter_mode_no_filter)
    MessageFilterMode.ONLY_MATCHING -> stringResource(R.string.message_filter_mode_only_matching)
    MessageFilterMode.BLOCK_MATCHING -> stringResource(R.string.message_filter_mode_block_matching)
}

@Composable
private fun messageMatchFieldLabel(field: MessageMatchField): String = when (field) {
    MessageMatchField.TITLE -> stringResource(R.string.message_match_field_title)
    MessageMatchField.CONTENT -> stringResource(R.string.message_match_field_content)
    MessageMatchField.TITLE_AND_CONTENT -> stringResource(R.string.message_match_field_both)
}

@Composable
private fun messageMatchTypeLabel(type: MessageMatchType): String = when (type) {
    MessageMatchType.CONTAINS -> stringResource(R.string.message_match_type_contains)
    MessageMatchType.FULL_MATCH -> stringResource(R.string.message_match_type_full)
    MessageMatchType.REGEX -> stringResource(R.string.message_match_type_regex)
}
