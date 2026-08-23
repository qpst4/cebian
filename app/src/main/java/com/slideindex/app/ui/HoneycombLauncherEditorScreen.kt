package com.slideindex.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.overlay.honeycombRuntimeItems
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.navigation.rememberContentReady
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsDeferredLoadingIndicator
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardItems

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HoneycombLauncherEditorScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSaveItems: (List<QuickLauncherItem>) -> Unit,
    onOpenDisplaySettings: () -> Unit,
    onAdd: () -> Unit,
) {
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf(appRepository.getCachedApps()) }
    var layoutEditing by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(settings.honeycombLauncher.honeycombRuntimeItems()) }

    LaunchedEffect(settings.honeycombLauncher) {
        if (!layoutEditing) {
            items = settings.honeycombLauncher.honeycombRuntimeItems()
        }
    }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = false)
    }

    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }

    fun saveAndBack() {
        onBack()
    }

    fun persistItems(next: List<QuickLauncherItem>) {
        val normalized = next.honeycombRuntimeItems()
        items = normalized
        onSaveItems(normalized)
    }

    val displaySettingsCard = settingsCardItems {
        SettingNavigationRow(
            icon = { label ->
                Icon(Icons.Outlined.Tune, contentDescription = label)
            },
            title = stringResource(R.string.honeycomb_display_settings_entry),
            subtitle = stringResource(R.string.honeycomb_display_settings_entry_desc),
            onClick = onOpenDisplaySettings,
        )
    }

    val contentReady = rememberContentReady()
    SettingsScreenScaffold(
        title = stringResource(R.string.honeycomb_launcher_editor_title),
        onBack = { saveAndBack() },
        scrollContent = false,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!contentReady) {
            LazySettingsItem(key = "honeycomb-launcher-loading", fillParentMaxSize = true) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SettingsDeferredLoadingIndicator()
                }
            }
        } else {
            LazySettingsItem(key = "honeycomb-launcher-main", fillParentMaxSize = true) {
                Column(modifier = Modifier.fillMaxSize()) {
                    displaySettingsCard.RenderRows()
                    HoneycombLauncherItemsSection(
                        modifier = Modifier.weight(1f),
                        items = items,
                        display = settings.honeycombDisplay,
                        appsByPackage = appsByPackage,
                        onItemsChange = ::persistItems,
                        onAdd = onAdd,
                        onInteractionActiveChange = { layoutEditing = it },
                        activityShortcuts = settings.activityShortcuts,
                        shellCommands = settings.shellCommands,
                    )
                }
            }
        }
    }
}
