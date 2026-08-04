package com.slideindex.app.ui

import com.slideindex.app.ui.viewmodel.NotificationHistoryViewModel
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationFilterSettingsScreen(
    viewModel: NotificationHistoryViewModel,
    listenerEnabled: Boolean,
    onBack: () -> Unit,
    onRequestListenerAccess: () -> Unit,
) {
    val filterSettings by viewModel.filterSettings.collectAsStateWithLifecycle()

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.notification_filter_settings_title),
        onBack = onBack,
    ) {
        notificationSettingsItems(
            filterSettings = filterSettings,
            listenerEnabled = listenerEnabled,
            onRequestListenerAccess = onRequestListenerAccess,
            onSetNotificationHistoryMaxCount = viewModel::setNotificationHistoryMaxCount,
            onRestoreAllSnoozed = viewModel::restoreAllSnoozed,
        )
    }
}
