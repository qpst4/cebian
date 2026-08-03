package com.slideindex.app.ui

import com.slideindex.app.ui.viewmodel.NotificationHistoryViewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.notification.NotificationFilterRule
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationRulesScreen(
    rules: List<NotificationFilterRule>,
    viewModel: NotificationHistoryViewModel,
    onBack: () -> Unit,
    onUpsertRule: (NotificationFilterRule) -> Unit,
    onRemoveRule: (String) -> Unit,
    onSetRuleEnabled: (String, Boolean) -> Unit,
    onOpenRuleEditor: (String?) -> Unit,
) {
    SettingsScreenScaffold(
        title = stringResource(R.string.notification_filter_tab_rules),
        subtitle = stringResource(R.string.notification_rules_screen_subtitle),
        onBack = onBack,
        scrollContent = false,
    ) {
        NotificationRulesTab(
            rules = rules,
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
            onUpsertRule = onUpsertRule,
            onRemoveRule = onRemoveRule,
            onSetRuleEnabled = onSetRuleEnabled,
            onOpenRuleEditor = onOpenRuleEditor,
        )
    }
}
