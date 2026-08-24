package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.util.PinyinHelper

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HiddenAppsScreen(
    hiddenPackages: Set<String>,
    onBack: () -> Unit,
    onHideApp: (String) -> Unit,
    onUnhideApp: (String) -> Unit,
    titleRes: Int = R.string.hidden_apps_title,
    descriptionRes: Int = R.string.hidden_apps_desc,
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
    val hiddenEntries = remember(hiddenPackages, allApps) {
        hiddenPackages.sorted().map { packageName ->
            appsByPackage[packageName]?.let { AppPackageEntry.Installed(it) }
                ?: AppPackageEntry.Missing(packageName)
        }
    }
    val addableApps = remember(allApps, hiddenPackages, searchQuery) {
        val query = searchQuery.trim().lowercase()
        allApps
            .filter { it.packageName !in hiddenPackages }
            .filter { app ->
                if (query.isEmpty()) return@filter true
                app.label.lowercase().contains(query) ||
                    app.packageName.lowercase().contains(query) ||
                    PinyinHelper.sortKey(app.label).contains(query)
            }
            .sortedBy { PinyinHelper.sortKey(it.label) }
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(titleRes),
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack,
    ) {
        item(key = "desc") {
            MiuixHintText(stringResource(descriptionRes))
        }
        item(key = "section-hidden") {
            MiuixSmallTitle(stringResource(R.string.hidden_apps_section_hidden))
        }
        if (hiddenEntries.isEmpty()) {
            item(key = "hidden-empty") {
                Text(
                    text = stringResource(R.string.hidden_apps_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                )
            }
        } else {
            items(
                hiddenEntries.size,
                key = { hiddenEntries[it].packageName },
            ) { index ->
                AppPackageListRow(
                    entry = hiddenEntries[index],
                    segmentIndex = index,
                    segmentCount = hiddenEntries.size,
                    actionIcon = Icons.Default.Close,
                    actionDescription = stringResource(R.string.hidden_apps_unhide),
                    missingIcon = Icons.Default.VisibilityOff,
                    onAction = { onUnhideApp(hiddenEntries[index].packageName) },
                )
            }
        }
        item(key = "section-add") {
            MiuixSmallTitle(stringResource(R.string.hidden_apps_section_add), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        }
        when {
            isLoading -> {
                item(key = "loading") {
                    LoadingContent(
                        message = stringResource(R.string.loading), modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            addableApps.isEmpty() -> {
                item(key = "addable-empty") {
                    Text(
                        text = if (searchQuery.isBlank()) {
                            stringResource(R.string.hidden_apps_all_hidden)
                        } else {
                            stringResource(R.string.no_apps)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
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
                        actionDescription = stringResource(R.string.hidden_apps_hide),
                        missingIcon = Icons.Default.VisibilityOff,
                        onAction = { onHideApp(app.packageName) },
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCardScope.HiddenAppsEntryCard(
    hiddenCount: Int,
    onClick: () -> Unit,
    titleRes: Int = R.string.hidden_apps_entry_title,
    descriptionRes: Int = R.string.hidden_apps_entry_desc,
) {
    val subtitle = if (hiddenCount > 0) {
        stringResource(R.string.hidden_apps_entry_count, hiddenCount)
    } else {
        stringResource(descriptionRes)
    }
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.VisibilityOff, contentDescription = label) },
        title = stringResource(titleRes),
        subtitle = subtitle,
        onClick = onClick,
    )
}
