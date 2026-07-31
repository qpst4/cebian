package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ExcludedAppScopes
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.util.PinyinHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExcludedAppsScreen(
    settings: AppSettings,
    usageAccessGranted: Boolean,
    onBack: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onSuppressTriggersChange: (Boolean) -> Unit,
    onSuppressCornerWheelChange: (Boolean) -> Unit,
    onSuppressFloatBallChange: (Boolean) -> Unit,
    onExcludeApp: (String) -> Unit,
    onRemoveExcludedApp: (String) -> Unit,
    onExcludedAppScopesChange: (String, ExcludedAppScopes) -> Unit,
) {
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var editingEntry by remember { mutableStateOf<EditingExcludedApp?>(null) }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = true)
        isLoading = false
    }

    val excludedPackages = settings.excludedAppScopes.keys
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.excluded_apps_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_navigate_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            item(key = "desc") {
                Text(
                    text = stringResource(R.string.excluded_apps_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!usageAccessGranted) {
                item(key = "usage-permission") {
                    PermissionCard(
                        title = stringResource(R.string.permission_usage_title),
                        description = stringResource(
                            R.string.permission_usage_desc_excluded_apps,
                            stringResource(R.string.app_name),
                        ),
                        onGrant = onRequestUsageAccess,
                    )
                }
            }
            item(key = "section-scopes") {
                SettingsSectionTitle(stringResource(R.string.excluded_apps_section_default_scopes))
            }
            item(key = "scopes-card") {
                SettingsCard {
                    SettingSwitchRow(
                        title = stringResource(R.string.excluded_apps_scope_triggers),
                        checked = settings.excludedAppDefaultScopes.suppressTriggers,
                        enabled = true,
                        onCheckedChange = onSuppressTriggersChange,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.excluded_apps_scope_corner_wheel),
                        checked = settings.excludedAppDefaultScopes.suppressCornerWheel,
                        enabled = true,
                        onCheckedChange = onSuppressCornerWheelChange,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.excluded_apps_scope_float_ball),
                        checked = settings.excludedAppDefaultScopes.suppressFloatBall,
                        enabled = true,
                        onCheckedChange = onSuppressFloatBallChange,
                    )
                }
            }
            item(key = "section-excluded") {
                SettingsSectionTitle(stringResource(R.string.excluded_apps_section_excluded))
            }
            if (excludedEntries.isEmpty()) {
                item(key = "excluded-empty") {
                    Text(
                        text = stringResource(R.string.excluded_apps_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(
                    excludedEntries.size,
                    key = { excludedEntries[it].packageName },
                ) { index ->
                    val entry = excludedEntries[index]
                    val scopes = settings.excludedAppScopes[entry.packageName] ?: settings.excludedAppDefaultScopes
                    AppPackageListRow(
                        entry = entry,
                        segmentIndex = index,
                        segmentCount = excludedEntries.size,
                        actionIcon = Icons.Default.Close,
                        actionDescription = stringResource(R.string.excluded_apps_remove),
                        missingIcon = Icons.Default.Block,
                        subtitle = formatExcludedAppScopesSummary(scopes),
                        onRowClick = {
                            editingEntry = EditingExcludedApp(
                                packageName = entry.packageName,
                                label = entryLabel(entry),
                                scopes = scopes,
                            )
                        },
                        onAction = { onRemoveExcludedApp(entry.packageName) },
                    )
                }
            }
            item(key = "section-add") {
                SettingsSectionTitle(stringResource(R.string.excluded_apps_section_add))
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
                                stringResource(R.string.excluded_apps_all_excluded)
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
                            actionDescription = stringResource(R.string.excluded_apps_add),
                            missingIcon = Icons.Default.Block,
                            onAction = { onExcludeApp(app.packageName) },
                        )
                    }
                }
            }
        }
    }

    editingEntry?.let { editing ->
        ExcludedAppScopesEditorDialog(
            appLabel = editing.label,
            scopes = editing.scopes,
            onDismiss = { editingEntry = null },
            onConfirm = { scopes ->
                onExcludedAppScopesChange(editing.packageName, scopes)
                editingEntry = null
            },
        )
    }
}

@Composable
fun SettingsCardScope.ExcludedAppsEntryCard(
    excludedCount: Int,
    onClick: () -> Unit,
) {
    val subtitle = if (excludedCount > 0) {
        stringResource(R.string.excluded_apps_entry_count, excludedCount)
    } else {
        stringResource(R.string.excluded_apps_entry_desc)
    }
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.Block, contentDescription = label) },
        title = stringResource(R.string.excluded_apps_entry_title),
        subtitle = subtitle,
        onClick = onClick,
    )
}

@Composable
private fun formatExcludedAppScopesSummary(scopes: ExcludedAppScopes): String {
    val parts = buildList {
        if (scopes.suppressTriggers) add(stringResource(R.string.excluded_apps_scope_triggers_short))
        if (scopes.suppressCornerWheel) add(stringResource(R.string.excluded_apps_scope_corner_wheel_short))
        if (scopes.suppressFloatBall) add(stringResource(R.string.excluded_apps_scope_float_ball_short))
    }
    return if (parts.isEmpty()) {
        stringResource(R.string.excluded_apps_scope_none)
    } else {
        parts.joinToString(" · ")
    }
}

@Composable
private fun ExcludedAppScopesEditorDialog(
    appLabel: String,
    scopes: ExcludedAppScopes,
    onDismiss: () -> Unit,
    onConfirm: (ExcludedAppScopes) -> Unit,
) {
    var localScopes by remember(scopes, appLabel) { mutableStateOf(scopes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appLabel) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.excluded_apps_scope_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                ExcludedAppScopeSwitchRow(
                    title = stringResource(R.string.excluded_apps_scope_triggers),
                    checked = localScopes.suppressTriggers,
                    onCheckedChange = { localScopes = localScopes.copy(suppressTriggers = it) },
                )
                ExcludedAppScopeSwitchRow(
                    title = stringResource(R.string.excluded_apps_scope_corner_wheel),
                    checked = localScopes.suppressCornerWheel,
                    onCheckedChange = { localScopes = localScopes.copy(suppressCornerWheel = it) },
                )
                ExcludedAppScopeSwitchRow(
                    title = stringResource(R.string.excluded_apps_scope_float_ball),
                    checked = localScopes.suppressFloatBall,
                    onCheckedChange = { localScopes = localScopes.copy(suppressFloatBall = it) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(localScopes) },
                enabled = localScopes.hasAny(),
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ExcludedAppScopeSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private data class EditingExcludedApp(
    val packageName: String,
    val label: String,
    val scopes: ExcludedAppScopes,
)

private fun entryLabel(entry: AppPackageEntry): String = when (entry) {
    is AppPackageEntry.Installed -> entry.app.label
    is AppPackageEntry.Missing -> entry.packageName
}
