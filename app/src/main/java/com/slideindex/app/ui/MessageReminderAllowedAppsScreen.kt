package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.slideindex.app.ui.compose.rememberAppRepository
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.message.MessageAppFilterRule
import com.slideindex.app.message.MessageFilterMode
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.util.PinyinHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MessageReminderAllowedAppsScreen(
    settings: MessageSettings,
    onBack: () -> Unit,
    onAddPackage: (String) -> Unit,
    onRemovePackage: (String) -> Unit,
    onSaveFilterRule: (MessageAppFilterRule) -> Unit,
    onOpenFilterEditor: (String) -> Unit,
) {
    val context = LocalContext.current
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = true)
        isLoading = false
    }

    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }
    val configuredEntries = remember(settings.enabledPackages, allApps) {
        settings.enabledPackages.sorted().map { packageName ->
            appsByPackage[packageName]?.let { AppPackageEntry.Installed(it) }
                ?: AppPackageEntry.Missing(packageName)
        }
    }
    val addableApps = remember(allApps, settings.enabledPackages, searchQuery) {
        val query = searchQuery.trim().lowercase()
        allApps
            .filter { it.packageName !in settings.enabledPackages }
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
                        text = stringResource(R.string.message_reminder_allowed_apps_title),
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "desc") {
                Text(
                    text = stringResource(R.string.message_reminder_allowed_apps_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "section-configured") {
                SettingsSectionTitle(stringResource(R.string.message_reminder_allowed_apps_section_configured))
            }
            if (configuredEntries.isEmpty()) {
                item(key = "configured-empty") {
                    Text(
                        text = stringResource(R.string.message_reminder_allowed_apps_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(
                    configuredEntries.size,
                    key = { configuredEntries[it].packageName },
                ) { index ->
                    val entry = configuredEntries[index]
                    val packageName = entry.packageName
                    val rule = settings.filterRuleFor(packageName)
                        AppPackageListRow(
                            entry = entry,
                            segmentIndex = index,
                            segmentCount = configuredEntries.size,
                            actionIcon = Icons.Default.Close,
                            actionDescription = stringResource(R.string.message_reminder_allowed_apps_remove),
                            missingIcon = Icons.Default.Block,
                            onAction = { onRemovePackage(packageName) },
                            onRowClick = { onOpenFilterEditor(packageName) },
                            subtitle = messageFilterModeSummary(rule),
                        )
                }
            }
            item(key = "section-add") {
                SettingsSectionTitle(stringResource(R.string.message_reminder_allowed_apps_section_add))
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
                                stringResource(R.string.message_reminder_allowed_apps_all_added)
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
                            actionDescription = stringResource(R.string.message_reminder_allowed_apps_add),
                            missingIcon = Icons.Default.Block,
                            onAction = { onAddPackage(app.packageName) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun messageFilterModeSummary(rule: MessageAppFilterRule): String? = when (rule.mode) {
    MessageFilterMode.NO_FILTER -> {
        if (rule.hasCustomFilter()) null
        else stringResource(R.string.message_filter_mode_no_filter)
    }
    MessageFilterMode.ONLY_MATCHING -> stringResource(R.string.message_filter_mode_only_matching)
    MessageFilterMode.BLOCK_MATCHING -> stringResource(R.string.message_filter_mode_block_matching)
}
