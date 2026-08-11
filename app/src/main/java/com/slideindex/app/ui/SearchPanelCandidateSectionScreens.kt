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
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.overlay.searchpanel.ContactPermissionTrampolineActivity
import com.slideindex.app.search.contacts.ContactSearchIndex
import com.slideindex.app.search.settings.SystemSettingsSearchIndex
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsHintText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchPanelAppSearchSettingsScreen(
    onBack: () -> Unit,
) {
    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_section_apps),
        subtitle = stringResource(R.string.search_panel_app_search_desc),
        onBack = onBack,
    ) {
        SettingsHintText(stringResource(R.string.search_panel_app_search_desc))
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
    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_section_contacts),
        subtitle = stringResource(R.string.search_panel_contact_search_desc),
        onBack = onBack,
    ) {
        SettingsHintText(stringResource(R.string.search_panel_contact_search_desc))
        MiuixSmallTitle(stringResource(R.string.search_panel_contact_permission_title))
        SettingsCard {
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Outlined.Contacts, contentDescription = label) },
                title = if (hasPermission) {
                    stringResource(R.string.search_panel_contact_permission_granted)
                } else {
                    stringResource(R.string.search_panel_contact_permission_prompt)
                },
                subtitle = stringResource(R.string.search_panel_contact_permission_title),
                onClick = {
                    ContactPermissionTrampolineActivity.launch(context) {
                        hasPermission = ContactSearchIndex.hasPermission(context)
                    }
                },
            )
        }
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
    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_section_settings),
        subtitle = stringResource(R.string.search_panel_settings_search_desc),
        onBack = onBack,
    ) {
        SettingsHintText(stringResource(R.string.search_panel_settings_search_desc))
        MiuixSmallTitle(stringResource(R.string.search_panel_settings_index_title))
        SettingsCard {
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Outlined.Settings, contentDescription = label) },
                title = stringResource(R.string.search_panel_settings_index_refresh),
                subtitle = indexCount?.let {
                    stringResource(R.string.search_panel_settings_index_count, it)
                } ?: stringResource(R.string.search_panel_settings_index_loading),
                onClick = {
                    SystemSettingsSearchIndex.invalidate()
                    indexCount = null
                },
            )
        }
        LaunchedEffect(indexCount) {
            if (indexCount != null) return@LaunchedEffect
            indexCount = withContext(Dispatchers.IO) {
                SystemSettingsSearchIndex.ensureLoaded(context).size
            }
        }
    }
}
