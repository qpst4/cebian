package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.overlay.searchpanel.ContactPermissionTrampolineActivity
import com.slideindex.app.search.contacts.ContactSearchIndex
import com.slideindex.app.search.settings.SystemSettingsSearchIndex
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchPanelAppSearchSettingsScreen(
    onBack: () -> Unit,
) {
    val desc = stringResource(R.string.search_panel_app_search_desc)
    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_section_apps),
        subtitle = desc,
        onBack = onBack,
    ) {
        settingsLazyHint(key = "app-search-desc", text = desc)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchPanelContactSearchSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContactSearchIndex.hasPermission(context))
    }
    val desc = stringResource(R.string.search_panel_contact_search_desc)
    val permissionSectionTitle = stringResource(R.string.search_panel_contact_permission_title)

    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_section_contacts),
        subtitle = desc,
        onBack = onBack,
    ) {
        settingsLazyHint(key = "contact-search-desc", text = desc)
        item(key = "contact-permission-title") {
            MiuixSmallTitle(permissionSectionTitle)
        }
        groupedCardItems(
            keyPrefix = "contact-permission",
            items = buildList {
                add(
                    settingsCardScopeItem("permission") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Outlined.Contacts, contentDescription = label) },
                            title = if (hasPermission) {
                                stringResource(R.string.search_panel_contact_permission_granted)
                            } else {
                                stringResource(R.string.search_panel_contact_permission_prompt)
                            },
                            subtitle = permissionSectionTitle,
                            onClick = {
                                ContactPermissionTrampolineActivity.launch(context) {
                                    hasPermission = ContactSearchIndex.hasPermission(context)
                                }
                            },
                        )
                    },
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchPanelSystemSettingsSearchSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var indexCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        indexCount = withContext(Dispatchers.IO) {
            SystemSettingsSearchIndex.ensureLoaded(context).size
        }
    }
    val desc = stringResource(R.string.search_panel_settings_search_desc)
    val indexSectionTitle = stringResource(R.string.search_panel_settings_index_title)

    LaunchedEffect(indexCount) {
        if (indexCount != null) return@LaunchedEffect
        indexCount = withContext(Dispatchers.IO) {
            SystemSettingsSearchIndex.ensureLoaded(context).size
        }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_section_settings),
        subtitle = desc,
        onBack = onBack,
    ) {
        settingsLazyHint(key = "settings-search-desc", text = desc)
        item(key = "settings-index-title") {
            MiuixSmallTitle(indexSectionTitle)
        }
        groupedCardItems(
            keyPrefix = "settings-index",
            items = buildList {
                add(
                    settingsCardScopeItem("index-refresh") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Outlined.Settings, contentDescription = label) },
                            title = stringResource(R.string.search_panel_settings_index_refresh),
                            subtitle = indexCount?.let {
                                pluralStringResource(R.plurals.search_panel_settings_index_count, it, it)
                            } ?: stringResource(R.string.search_panel_settings_index_loading),
                            onClick = {
                                SystemSettingsSearchIndex.invalidate()
                                indexCount = null
                            },
                        )
                    },
                )
            },
        )
    }
}
