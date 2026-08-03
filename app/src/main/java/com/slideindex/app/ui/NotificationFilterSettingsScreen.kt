package com.slideindex.app.ui

import com.slideindex.app.ui.viewmodel.NotificationHistoryViewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationFilterSettingsScreen(
    viewModel: NotificationHistoryViewModel,
    listenerEnabled: Boolean,
    onBack: () -> Unit,
    onRequestListenerAccess: () -> Unit,
) {
    SettingsScreenScaffold(
        title = stringResource(R.string.notification_filter_settings_title),
        onBack = onBack,
        scrollContent = false,
    ) {
        NotificationSettingsTab(
            viewModel = viewModel,
            listenerEnabled = listenerEnabled,
            onRequestListenerAccess = onRequestListenerAccess,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
