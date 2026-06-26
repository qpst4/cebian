package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.SlideIndexApp
import com.slideindex.app.data.AppInfo
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.PinyinHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcludedAppsScreen(
    settings: AppSettings,
    usageAccessGranted: Boolean,
    onBack: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onExcludeApp: (String) -> Unit,
    onRemoveExcludedApp: (String) -> Unit,
) {
    val context = LocalContext.current
    val appRepository = remember { (context.applicationContext as SlideIndexApp).appRepository }
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = true)
        isLoading = false
    }

    val excludedPackages = settings.excludedTriggerAppPackages
    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }
    val excludedEntries = remember(excludedPackages, allApps) {
        excludedPackages.sorted().map { packageName ->
            appsByPackage[packageName]?.let { AppPackageEntry.Installed(it) }
                ?: AppPackageEntry.Missing(packageName)
        }
    }
    val addableApps = remember(allApps, excludedPackages, searchQuery) {
        val query = searchQuery.trim().lowercase()
        allApps
            .filter { it.packageName !in excludedPackages }
            .filter { app ->
                if (query.isEmpty()) return@filter true
                app.label.lowercase().contains(query) ||
                    app.packageName.lowercase().contains(query) ||
                    PinyinHelper.sortKey(app.label).contains(query)
            }
            .sortedBy { PinyinHelper.sortKey(it.label) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.excluded_apps_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.excluded_apps_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (!usageAccessGranted) {
                PermissionCard(
                    title = stringResource(R.string.permission_usage_title),
                    description = stringResource(R.string.permission_usage_desc),
                    onGrant = onRequestUsageAccess,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            SettingsSectionTitle(stringResource(R.string.excluded_apps_section_excluded))
            Spacer(modifier = Modifier.height(8.dp))

            if (excludedEntries.isEmpty()) {
                Text(
                    text = stringResource(R.string.excluded_apps_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(excludedEntries, key = { it.packageName }) { entry ->
                        AppPackageListRow(
                            entry = entry,
                            actionIcon = Icons.Default.Close,
                            actionDescription = stringResource(R.string.excluded_apps_remove),
                            missingIcon = Icons.Default.Block,
                            onAction = { onRemoveExcludedApp(entry.packageName) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SettingsSectionTitle(stringResource(R.string.excluded_apps_section_add))
            Spacer(modifier = Modifier.height(8.dp))
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
            )
            Spacer(modifier = Modifier.height(8.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.loading))
                    }
                }
                addableApps.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) {
                                stringResource(R.string.excluded_apps_all_excluded)
                            } else {
                                stringResource(R.string.no_apps)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(addableApps, key = { it.packageName }) { app ->
                            AppPackageListRow(
                                entry = AppPackageEntry.Installed(app),
                                actionIcon = Icons.Default.Add,
                                actionDescription = stringResource(R.string.excluded_apps_add),
                                missingIcon = Icons.Default.Block,
                                onAction = { onExcludeApp(app.packageName) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExcludedAppsEntryCard(
    excludedCount: Int,
    onClick: () -> Unit,
) {
    val subtitle = if (excludedCount > 0) {
        stringResource(R.string.excluded_apps_entry_count, excludedCount)
    } else {
        stringResource(R.string.excluded_apps_entry_desc)
    }
    SettingNavigationRow(
        icon = { Icon(Icons.Default.Block, contentDescription = null) },
        title = stringResource(R.string.excluded_apps_entry_title),
        subtitle = subtitle,
        onClick = onClick,
    )
}
