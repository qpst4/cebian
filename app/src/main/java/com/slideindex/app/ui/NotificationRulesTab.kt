package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.notificationrule.actionTypeLabel
import com.slideindex.app.ui.viewmodel.NotificationHistoryViewModel
import com.slideindex.app.notification.AppMatchMode
import com.slideindex.app.notification.NotificationFilterRule
import com.slideindex.app.notification.TextMatchMode

fun LazyListScope.notificationRulesItems(
    rules: List<NotificationFilterRule>,
    viewModel: NotificationHistoryViewModel,
    onRemoveRule: (String) -> Unit,
    onSetRuleEnabled: (String, Boolean) -> Unit,
    onOpenRuleEditor: (String?) -> Unit,
) {
    item(key = "rules_section") {
        val context = LocalContext.current
        val exportChooserTitle = stringResource(R.string.notification_rule_export)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiuixSmallTitle(stringResource(R.string.notification_rule_section_title))
            TextButton(
                onClick = {
                    val json = viewModel.exportRulesJson()
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, json)
                    }
                    context.startActivity(Intent.createChooser(share, exportChooserTitle))
                },
            ) { Text(stringResource(R.string.notification_rule_export)) }
        }
    }
    if (rules.isEmpty()) {
        item(key = "rules_empty") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.notification_rule_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        items(rules, key = { it.id }) { rule ->
            NotificationRuleCard(
                rule = rule.normalized(),
                packageLabel = formatRulePackageLabel(rule.normalized(), viewModel),
                onEnabledChange = { enabled -> onSetRuleEnabled(rule.id, enabled) },
                onEdit = { onOpenRuleEditor(rule.id) },
                onDelete = { onRemoveRule(rule.id) },
            )
        }
    }
    item(key = "rules_bottom_spacer") {
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun formatRulePackageLabel(
    rule: NotificationFilterRule,
    viewModel: NotificationHistoryViewModel,
): String {
    return when (rule.appMode) {
        AppMatchMode.ALL -> stringResource(R.string.notification_rule_all_apps)
        AppMatchMode.INCLUDE, AppMatchMode.EXCLUDE -> {
            val prefix = when (rule.appMode) {
                AppMatchMode.INCLUDE -> stringResource(R.string.notification_rule_app_mode_include)
                AppMatchMode.EXCLUDE -> stringResource(R.string.notification_rule_app_mode_exclude)
                AppMatchMode.ALL -> ""
            }
            val names = rule.appTargets.map { it.packageName }
            if (names.isEmpty()) return prefix
            if (names.size == 1) {
                val pkg = names.first()
                val label = viewModel.ensureAppInfo(pkg)?.label
                return "$prefix: ${label ?: pkg}"
            }
            stringResource(R.string.notification_rule_selected_apps_count, names.size).let { count ->
                "$prefix $count"
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationRuleCard(
    rule: NotificationFilterRule,
    packageLabel: String,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = rule.displayName().ifBlank { stringResource(R.string.notification_rule_unnamed) },
                    style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.notification_rule_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    Switch(checked = rule.enabled, onCheckedChange = onEnabledChange)
                }
            }
            Text(
                text = stringResource(R.string.notification_rule_package_summary, packageLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rule.textMode != TextMatchMode.ALL) {
                Text(
                    text = stringResource(R.string.notification_rule_text_summary, textModeLabel(rule.textMode)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                rule.actionEntries.forEach { action ->
                    Text(
                        text = actionTypeLabel(action.type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }
        }
    }
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
