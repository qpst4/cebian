@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.overlay.searchpanel.FilePermissionTrampolineActivity
import com.slideindex.app.search.contacts.ContactSearchIndex
import com.slideindex.app.search.files.FileSearchIndex
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchPanelInputBehavior
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope

@Composable
fun SearchPanelSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSetDefaultEngineId: (String?) -> Unit,
    onSetSearchPanelInputBehavior: (SearchPanelInputBehavior) -> Unit,
    onSetSearchPanelContactSearchEnabled: (Boolean) -> Unit,
    onSetSearchPanelFileSearchEnabled: (Boolean) -> Unit,
    onOpenPreviewSort: () -> Unit,
    onOpenTextSearchEngines: () -> Unit,
    onOpenImageSearchEngines: () -> Unit,
) {
    val context = LocalContext.current
    val engines = remember(settings.searchEngines) {
        SearchEngineStore.textSettingsEngines(settings.searchEngines)
    }
    val inputBehaviorEntries = SearchPanelInputBehavior.entries
    val noneEngineLabel = stringResource(R.string.search_panel_default_engine_none)
    val defaultEngineItems = listOf(noneEngineLabel) + engines.map { it.name }
    val defaultEngineIndex = if (settings.searchPanelDefaultEngineId == null) {
        0
    } else {
        engines.indexOfFirst { it.id == settings.searchPanelDefaultEngineId }.let { idx ->
            if (idx >= 0) idx + 1 else 0
        }
    }

    var hasContactPermission by remember {
        mutableStateOf(ContactSearchIndex.hasPermission(context))
    }
    var hasFilePermission by remember {
        mutableStateOf(FileSearchIndex.hasPermission(context))
    }
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        hasContactPermission = isGranted
        if (isGranted) {
            ContactSearchIndex.invalidateCache()
        }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_settings_title),
        subtitle = stringResource(R.string.search_panel_settings_subtitle),
        onBack = onBack,
    ) {
        MiuixSmallTitle(
            stringResource(R.string.search_panel_settings_section_behavior),
            modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop),
        )
        SettingsCard {
            SettingDropdownRow(
                icon = { label -> Icon(Icons.Default.Search, contentDescription = label) },
                title = stringResource(R.string.search_panel_default_engine_title),
                items = defaultEngineItems,
                selectedIndex = defaultEngineIndex,
                enabled = engines.isNotEmpty(),
                onSelectedIndexChange = { index ->
                    onSetDefaultEngineId(if (index == 0) null else engines[index - 1].id)
                },
            )
            SettingDropdownRow(
                icon = { label -> Icon(Icons.Default.Search, contentDescription = label) },
                title = stringResource(R.string.search_panel_input_behavior_title),
                items = inputBehaviorEntries.map { searchPanelInputBehaviorLabel(it) },
                selectedIndex = inputBehaviorEntries.indexOf(settings.searchPanelInputBehavior).coerceAtLeast(0),
                onSelectedIndexChange = { onSetSearchPanelInputBehavior(inputBehaviorEntries[it]) },
            )
        }

        MiuixSmallTitle(
            stringResource(R.string.search_panel_settings_section_candidates),
            modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop),
        )
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.search_panel_contact_search_title),
                subtitle = stringResource(R.string.search_panel_contact_search_desc),
                checked = settings.searchPanelContactSearchEnabled,
                enabled = true,
                onCheckedChange = onSetSearchPanelContactSearchEnabled,
            )
            if (settings.searchPanelContactSearchEnabled) {
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.Person, contentDescription = label) },
                    title = stringResource(R.string.search_panel_contact_permission_title),
                    subtitle = if (hasContactPermission) {
                        stringResource(R.string.search_panel_contact_permission_granted)
                    } else {
                        stringResource(R.string.search_panel_contact_permission_missing)
                    },
                    enabled = !hasContactPermission,
                    onClick = {
                        contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    },
                )
            }
            SettingSwitchRow(
                title = stringResource(R.string.search_panel_file_search_title),
                subtitle = stringResource(R.string.search_panel_file_search_desc),
                checked = settings.searchPanelFileSearchEnabled,
                enabled = true,
                onCheckedChange = onSetSearchPanelFileSearchEnabled,
            )
            if (settings.searchPanelFileSearchEnabled) {
                SettingNavigationRow(
                    icon = { label ->
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = label)
                    },
                    title = stringResource(R.string.search_panel_file_permission_title),
                    subtitle = if (hasFilePermission) {
                        stringResource(R.string.search_panel_file_permission_granted)
                    } else {
                        stringResource(R.string.search_panel_file_permission_missing)
                    },
                    enabled = !hasFilePermission,
                    onClick = {
                        FilePermissionTrampolineActivity.launch(context) { granted ->
                            hasFilePermission = granted
                        }
                    },
                )
            }
        }

        MiuixSmallTitle(
            stringResource(R.string.search_panel_settings_section_engines),
            modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop),
        )
        SettingsCard {
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.DragHandle, contentDescription = label) },
                title = stringResource(R.string.search_engine_settings_preview_mode),
                subtitle = stringResource(R.string.search_engine_settings_preview_mode_summary),
                enabled = engines.isNotEmpty(),
                onClick = onOpenPreviewSort,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.Search, contentDescription = label) },
                title = stringResource(R.string.search_engine_settings_title),
                subtitle = stringResource(R.string.search_panel_text_engines_entry_desc),
                onClick = onOpenTextSearchEngines,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.Image, contentDescription = label) },
                title = stringResource(R.string.image_search_engine_settings_title),
                subtitle = stringResource(R.string.search_panel_image_engines_entry_desc),
                onClick = onOpenImageSearchEngines,
            )
        }
    }
}

@Composable
fun SettingsCardScope.SearchPanelEntryCard(
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label ->
            Icon(HubLeadingIcons.searchPanel(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.search_panel_entry_title),
        subtitle = stringResource(R.string.search_panel_entry_desc),
        onClick = onClick,
    )
}

@Composable
private fun searchPanelInputBehaviorLabel(behavior: SearchPanelInputBehavior): String = when (behavior) {
    SearchPanelInputBehavior.SELECT_ALL -> stringResource(R.string.search_panel_input_behavior_select_all)
    SearchPanelInputBehavior.CLEAR -> stringResource(R.string.search_panel_input_behavior_clear)
    SearchPanelInputBehavior.KEEP -> stringResource(R.string.search_panel_input_behavior_keep)
}
