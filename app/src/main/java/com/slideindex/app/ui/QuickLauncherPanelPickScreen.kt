package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickLauncherPanelPickScreen(
    settings: AppSettings,
    currentPanelId: String,
    onBack: () -> Unit,
    onSelect: (QuickLauncherPanel) -> Unit,
) {
    val panels = QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels)
    val effectiveCurrentPanelId = QuickLauncherPanelDefaults.resolvePanelId(settings.quickLauncherPanels, currentPanelId)
    val pickDesc = stringResource(R.string.quick_launcher_panel_pick_desc)

    SettingsScreenScaffold(
        title = stringResource(R.string.quick_launcher_panel_pick_title),
        onBack = onBack,
    ) {
        item(key = "quick-launcher-panel-pick-desc") {
            MiuixSmallTitle(
                pickDesc,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MiuixSmallTitleSectionTop),
            )
        }
        groupedCardItems(
            keyPrefix = "quick-launcher-panel-pick",
            selectableGroup = true,
            items = buildList {
                panels.forEachIndexed { index, panel ->
                    add(
                        settingsCardScopeItem("panel-${panel.id}") {
                            val displayName = panel.name.ifBlank {
                                stringResource(R.string.quick_launcher_panel_default_name, index + 1)
                            }
                            val subtitle = pluralStringResource(
                                R.plurals.quick_launcher_panel_pick_summary,
                                panel.items.size,
                                panel.columnsPerPage,
                                panel.rowsPerPage,
                                panel.items.size,
                            )
                            SettingRadioRow(
                                title = displayName,
                                subtitle = subtitle,
                                selected = panel.id == effectiveCurrentPanelId,
                                onClick = { onSelect(panel) },
                            )
                        },
                    )
                }
            },
        )
    }
}
