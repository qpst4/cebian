package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.slideindex.app.ui.compose.rememberAppRepository
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
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.shake.ShakeGestureType
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.util.PinyinHelper

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShakeIndependentAppSettingsScreen(
    perAppActions: Map<String, Map<ShakeGestureType, GestureAction>>,
    onBack: () -> Unit,
    onOpenAppConfig: (String) -> Unit,
    onOpenConfiguredApp: (String) -> Unit,
    onRemoveAppConfig: (String) -> Unit,
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
    val configuredEntries = remember(perAppActions, allApps) {
        perAppActions.keys.sorted().map { packageName ->
            appsByPackage[packageName]?.let { AppPackageEntry.Installed(it) }
                ?: AppPackageEntry.Missing(packageName)
        }
    }
    val addableApps = remember(allApps, perAppActions, searchQuery) {
        val query = searchQuery.trim().lowercase()
        allApps
            .filter { it.packageName !in perAppActions }
            .filter { app ->
                if (query.isEmpty()) return@filter true
                app.label.lowercase().contains(query) ||
                    app.packageName.lowercase().contains(query) ||
                    PinyinHelper.sortKey(app.label).contains(query)
            }
            .sortedBy { PinyinHelper.sortKey(it.label) }
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.shake_gestures_independent_app),
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack,
    ) {
            item(key = "desc") {
                Text(
                    text = stringResource(R.string.shake_gestures_independent_app_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (configuredEntries.isNotEmpty()) {
                item(key = "section-configured") {
                    MiuixSmallTitle(stringResource(R.string.shake_gestures_per_app_configured))
                }
                items(
                    configuredEntries.size,
                    key = { configuredEntries[it].packageName },
                ) { index ->
                    val entry = configuredEntries[index]
                    AppPackageListRow(
                        entry = entry,
                        segmentIndex = index,
                        segmentCount = configuredEntries.size,
                        actionIcon = Icons.Default.Close,
                        actionDescription = stringResource(R.string.shake_gestures_per_app_remove),
                        missingIcon = Icons.Default.Block,
                        onAction = { onRemoveAppConfig(entry.packageName) },
                        onRowClick = { onOpenConfiguredApp(entry.packageName) },
                    )
                }
            }
            item(key = "section-add") {
                MiuixSmallTitle(stringResource(R.string.shake_gestures_per_app_add), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
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
                                stringResource(R.string.shake_gestures_per_app_all_configured)
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
                            actionDescription = stringResource(R.string.shake_gestures_per_app_configure),
                            missingIcon = Icons.Default.Apps,
                            onAction = { onOpenAppConfig(app.packageName) },
                        )
                    }
                }
            }
    }
}
