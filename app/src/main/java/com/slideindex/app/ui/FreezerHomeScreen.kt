package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import com.slideindex.app.freezer.FreezerPanelContent
import com.slideindex.app.settings.SettingsRepository

@Composable
fun FreezerHomeScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onOpenManageApps: () -> Unit,
) {
    FreezerPanelContent(
        settingsRepository = settingsRepository,
        onBack = onBack,
        onManageApps = onOpenManageApps,
    )
}
