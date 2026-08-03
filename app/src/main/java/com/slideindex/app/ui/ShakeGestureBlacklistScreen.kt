package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
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
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSectionTitle
import com.slideindex.app.util.PinyinHelper

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShakeGestureBlacklistScreen(
    blacklistedPackages: Set<String>,
    onBack: () -> Unit,
    onBlacklistApp: (String) -> Unit,
    onRemoveBlacklistedApp: (String) -> Unit,
) {
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = true)
        isLoading = false
    }

    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }
    val blacklistedEntries = remember(blacklistedPackages, allApps) {
        blacklistedPackages.sorted().map { packageName ->
            appsByPackage[packageName]?.let { AppPackageEntry.Installed(it) }
                ?: AppPackageEntry.Missing(packageName)
        }
    }
    val addableApps = remember(allApps, blacklistedPackages, searchQuery) {
        val query = searchQuery.trim().lowercase()
        allApps
            .filter { it.packageName !in blacklistedPackages }
            .filter { app ->
                if (query.isEmpty()) return@filter true
                app.label.lowercase().contains(query) ||
                    app.packageName.lowercase().contains(query) ||
                    PinyinHelper.sortKey(app.label).contains(query)
            }
            .sortedBy { PinyinHelper.sortKey(it.label) }
    }

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.shake_gestures_app_blacklist),
        onBack = onBack,
    ) {
        item(key = "desc") {
            Text(
                text = stringResource(R.string.shake_gestures_app_blacklist_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "section-blacklisted") {
            SettingsSectionTitle(stringResource(R.string.shake_gestures_blacklist_section_blocked))
        }
        if (blacklistedEntries.isEmpty()) {
            item(key = "blacklisted-empty") {
                Text(
                    text = stringResource(R.string.shake_gestures_blacklist_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(
                blacklistedEntries.size,
                key = { blacklistedEntries[it].packageName },
            ) { index ->
                AppPackageListRow(
                    entry = blacklistedEntries[index],
                    segmentIndex = index,
                    segmentCount = blacklistedEntries.size,
                    actionIcon = Icons.Default.Close,
                    actionDescription = stringResource(R.string.shake_gestures_blacklist_remove),
                    missingIcon = Icons.Default.Block,
                    onAction = { onRemoveBlacklistedApp(blacklistedEntries[index].packageName) },
                )
            }
        }
        item(key = "section-add") {
            SettingsSectionTitle(stringResource(R.string.shake_gestures_blacklist_section_add))
        }
        item(key = "search") {
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when {
            isLoading -> {
                item(key = "loading") {
                    LoadingContent(
                        message = stringResource(R.string.loading),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            addableApps.isEmpty() -> {
                item(key = "addable-empty") {
                    Text(
                        text = if (searchQuery.isBlank()) {
                            stringResource(R.string.shake_gestures_blacklist_all_blocked)
                        } else {
                            stringResource(R.string.no_apps)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            else -> {
                items(
                    addableApps.size,
                    key = { addableApps[it].packageName },
                ) { index ->
                    val app = addableApps[index]
                    AppPackageListRow(
                        entry = AppPackageEntry.Installed(app),
                        segmentIndex = index,
                        segmentCount = addableApps.size,
                        actionIcon = Icons.Default.Add,
                        actionDescription = stringResource(R.string.shake_gestures_blacklist_add),
                        missingIcon = Icons.Default.Block,
                        onAction = { onBlacklistApp(app.packageName) },
                    )
                }
            }
        }
    }
}
