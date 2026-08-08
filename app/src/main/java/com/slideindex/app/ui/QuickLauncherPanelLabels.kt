package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.settings.AppSettings

@Composable
fun quickLauncherPanelLabel(settings: AppSettings, panelId: String): String {
    val panels = QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels)
    val index = panels.indexOfFirst { it.id == panelId }.takeIf { it >= 0 } ?: 0
    val panel = panels[index]
    return panel.name.ifBlank {
        stringResource(R.string.quick_launcher_panel_default_name, index + 1)
    }
}
