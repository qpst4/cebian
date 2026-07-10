package com.slideindex.app.ui

import com.slideindex.app.ui.viewmodel.NotificationHistoryViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.notification.NotificationFilterRule

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationRulesScreen(
    rules: List<NotificationFilterRule>,
    viewModel: NotificationHistoryViewModel,
    onBack: () -> Unit,
    onUpsertRule: (NotificationFilterRule) -> Unit,
    onRemoveRule: (String) -> Unit,
    onSetRuleEnabled: (String, Boolean) -> Unit,
) {
    BackHandler(onBack = onBack)

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { SettingsAppBarTitle(stringResource(R.string.notification_filter_tab_rules)) },
                subtitle = { Text(stringResource(R.string.notification_rules_screen_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        NotificationRulesTab(
            rules = rules,
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            onUpsertRule = onUpsertRule,
            onRemoveRule = onRemoveRule,
            onSetRuleEnabled = onSetRuleEnabled,
        )
    }
}
