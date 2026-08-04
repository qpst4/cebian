package com.slideindex.app.ui

import com.slideindex.app.ui.viewmodel.NotificationHistoryViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.notification.NotificationFilterRule
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationRulesScreen(
    rules: List<NotificationFilterRule>,
    viewModel: NotificationHistoryViewModel,
    onBack: () -> Unit,
    onRemoveRule: (String) -> Unit,
    onSetRuleEnabled: (String, Boolean) -> Unit,
    onOpenRuleEditor: (String?) -> Unit,
) {
    SettingsLazyScreenScaffold(
        title = stringResource(R.string.notification_filter_tab_rules),
        subtitle = stringResource(R.string.notification_rules_screen_subtitle),
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenRuleEditor(null) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.notification_rule_add))
            }
        },
    ) {
        notificationRulesItems(
            rules = rules,
            viewModel = viewModel,
            onRemoveRule = onRemoveRule,
            onSetRuleEnabled = onSetRuleEnabled,
            onOpenRuleEditor = onOpenRuleEditor,
        )
    }
}
