package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.util.PinyinHelper

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MessageReminderUnlockRulesScreen(
    settings: MessageSettings,
    onBack: () -> Unit,
    onAlwaysAllowChange: (String, Boolean) -> Unit,
) {
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = true)
    }

    val apps = remember(allApps, settings.enabledPackages, searchQuery) {
        val query = searchQuery.trim().lowercase()
        allApps
            .filter { it.packageName in settings.enabledPackages }
            .filter {
                query.isEmpty() ||
                    it.label.lowercase().contains(query) ||
                    it.packageName.lowercase().contains(query) ||
                    PinyinHelper.sortKey(it.label).contains(query)
            }
            .sortedBy { PinyinHelper.sortKey(it.label) }
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.message_reminder_open_last_rules_title),
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack,
    ) {
        item(key = "desc") {
            Text(
                text = stringResource(R.string.message_reminder_open_last_rules_desc_page),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
            )
        }
        if (apps.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.message_reminder_open_last_rules_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
                )
            }
        } else {
            items(apps, key = { it.packageName }) { app ->
                val entry = AppPackageEntry.Installed(app)
                Md3PickerListRow(
                    segmentIndex = apps.indexOf(app),
                    segmentCount = apps.size,
                    title = app.label,
                    subtitle = app.packageName,
                    selected = app.packageName in settings.openLastMessageAlwaysPackages,
                    onClick = {
                        onAlwaysAllowChange(
                            app.packageName,
                            app.packageName !in settings.openLastMessageAlwaysPackages,
                        )
                    },
                    leadingContent = {
                        Md3PickerAppEntryLeading(entry = entry, missingIcon = Icons.Default.Block)
                    },
                    trailingMode = PickerTrailingMode.Toggle,
                    onTrailingClick = {
                        onAlwaysAllowChange(
                            app.packageName,
                            app.packageName !in settings.openLastMessageAlwaysPackages,
                        )
                    },
                )
            }
        }
    }
}
