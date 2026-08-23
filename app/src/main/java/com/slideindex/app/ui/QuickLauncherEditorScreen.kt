package com.slideindex.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.launcher.QuickLauncherDefaults
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.launcher.QuickLauncherPanelMutator
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import com.slideindex.app.ui.navigation.rememberContentReady
import com.slideindex.app.ui.quicklauncher.QuickLauncherPanelManagementSection
import com.slideindex.app.ui.quicklauncher.quickLauncherAppearanceCardItems
import com.slideindex.app.ui.quicklauncher.quickLauncherAppearanceSettingsSection
import com.slideindex.app.ui.settings.components.SettingsDeferredLoadingIndicator
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickLauncherEditorScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSavePanels: (List<QuickLauncherPanel>) -> Unit,
    onDisplayChange: (com.slideindex.app.settings.QuickLauncherDisplaySettings) -> Unit,
    onAdd: (String) -> Unit,
) {
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf(appRepository.getCachedApps()) }
    var panels by remember {
        mutableStateOf(QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels))
    }
    var selectedPanelIndex by remember { mutableIntStateOf(0) }
    var gridInteractionActive by remember { mutableStateOf(false) }

    LaunchedEffect(settings.quickLauncherPanels) {
        panels = QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels)
        selectedPanelIndex = selectedPanelIndex.coerceIn(0, (panels.size - 1).coerceAtLeast(0))
    }

    val currentPanel = panels.getOrElse(selectedPanelIndex) {
        QuickLauncherPanelDefaults.defaultPanel()
    }
    var items by remember(currentPanel.id) { mutableStateOf(currentPanel.items) }

    val appearanceItems = quickLauncherAppearanceCardItems(
        display = settings.quickLauncherDisplay,
        enabled = true,
        onDisplayChange = onDisplayChange,
    )
    val appearanceSectionTitle = stringResource(R.string.quick_launcher_appearance_section)

    var defaultsSeeded by remember { mutableStateOf(false) }

    LaunchedEffect(allApps, panels.size, settings.quickLauncherPanels) {
        if (defaultsSeeded || allApps.isEmpty()) return@LaunchedEffect
        if (panels.size != 1) {
            defaultsSeeded = true
            return@LaunchedEffect
        }
        val onlyPanel = panels.first()
        if (onlyPanel.items.isNotEmpty()) {
            defaultsSeeded = true
            return@LaunchedEffect
        }
        val effective = QuickLauncherDefaults.effectiveItems(emptyList(), allApps)
        if (effective.isNotEmpty()) {
            val updated = QuickLauncherPanelMutator.updatePanelItems(panels, onlyPanel.id, effective)
            panels = updated
            items = effective
            onSavePanels(updated)
        }
        defaultsSeeded = true
    }

    LaunchedEffect(selectedPanelIndex, panels) {
        items = panels.getOrElse(selectedPanelIndex) {
            QuickLauncherPanelDefaults.defaultPanel()
        }.items
    }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = false)
    }

    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }

    fun persistCurrentPanelItems(next: List<QuickLauncherItem> = items) {
        val updated = QuickLauncherPanelMutator.updatePanelItems(panels, currentPanel.id, next)
        panels = updated
        onSavePanels(updated)
    }

    fun saveAndBack() {
        persistCurrentPanelItems()
        onBack()
    }

    val contentReady = rememberContentReady()
    SettingsLazyScreenScaffold(
        title = stringResource(R.string.quick_launcher_editor_title),
        onBack = { saveAndBack() },
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !gridInteractionActive,
    ) {
        if (!contentReady) {
            item(key = "loading") {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SettingsDeferredLoadingIndicator()
                }
            }
        } else {
            item(key = "desc") {
                MiuixHintText(stringResource(R.string.quick_launcher_editor_desc))
            }
            settingsLazySmallTitle(
                key = "quick-launcher-appearance",
                title = appearanceSectionTitle,
            )
            quickLauncherAppearanceSettingsSection(
                appearanceItems = appearanceItems,
            )
            item(key = "panel_and_grid") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    QuickLauncherPanelManagementSection(
                        panels = panels,
                        selectedIndex = selectedPanelIndex,
                        defaultColumns = settings.quickLauncherColumnsPerPage,
                        defaultRows = settings.quickLauncherRowsPerPage,
                        onPanelsChange = { updated ->
                            panels = updated
                            onSavePanels(updated)
                        },
                        onSelectedIndexChange = { selectedPanelIndex = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MiuixSmallTitle(
                        stringResource(R.string.quick_launcher_items_section),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MiuixSmallTitleSectionTop),
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        key(currentPanel.id) {
                            QuickLauncherGridEditor(
                                settings = settings,
                                items = items,
                                appsByPackage = appsByPackage,
                                onItemsChange = {
                                    items = it
                                    persistCurrentPanelItems(it)
                                },
                                onAdd = {
                                    onAdd(currentPanel.id)
                                },
                                onInteractionActiveChange = { gridInteractionActive = it },
                                gridColumnsOverride = currentPanel.columnsPerPage,
                                gridRowsOverride = currentPanel.rowsPerPage,
                            )
                        }
                    }
                }
            }
        }
    }
}
