package com.slideindex.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.freezer.FreezerLauncherHelper
import com.slideindex.app.freezer.FreezerListOperations
import com.slideindex.app.freezer.FreezerOperations
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class FreezerPickerFilter {
    ALL,
    FROZEN,
    ACTIVE,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FreezerAppsPickerScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = settingsRepository.readSnapshot(),
    )
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var listFilter by remember { mutableStateOf(FreezerPickerFilter.ALL) }
    var privilegedAccessGranted by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    val frozenLabel = stringResource(R.string.freezer_status_frozen)
    val activeLabel = stringResource(R.string.freezer_status_active)

    fun reloadApps() {
        scope.launch {
            isLoading = true
            allApps = withContext(Dispatchers.IO) {
                appRepository.loadFreezerApps(force = true)
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        privilegedAccessGranted = withContext(Dispatchers.IO) {
            TaskManagerUtil.hasPrivilegedAccess()
        }
        allApps = withContext(Dispatchers.IO) {
            appRepository.loadFreezerApps(force = true)
        }
        isLoading = false
    }

    val searchFilteredApps = remember(allApps, searchQuery, showSystemApps) {
        val query = searchQuery.trim().lowercase()
        allApps
            .filter { showSystemApps || !it.isSystem }
            .filter { app ->
                query.isBlank() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true) ||
                    app.pinyinKey.contains(query)
            }
    }

    val frozenTabCount = remember(searchFilteredApps) {
        searchFilteredApps.count { FreezerOperations.isFrozen(context, it.packageName) }
    }
    val allTabCount = searchFilteredApps.size
    val activeTabCount = allTabCount - frozenTabCount

    val displayedApps = remember(searchFilteredApps, settings.freezerAppPackages, listFilter) {
        searchFilteredApps
            .filter { app ->
                when (listFilter) {
                    FreezerPickerFilter.ALL -> true
                    FreezerPickerFilter.FROZEN -> FreezerOperations.isFrozen(context, app.packageName)
                    FreezerPickerFilter.ACTIVE -> !FreezerOperations.isFrozen(context, app.packageName)
                }
            }
            .sortedWith(
                compareByDescending<AppInfo> { it.packageName in settings.freezerAppPackages }
                    .thenBy { it.pinyinKey },
            )
    }

    val filterTabs = listOf(
        stringResource(R.string.freezer_tab_all_count, allTabCount),
        stringResource(R.string.freezer_tab_frozen_count, frozenTabCount),
        stringResource(R.string.freezer_tab_active_count, activeTabCount),
    )
    val selectedFilterTabIndex = when (listFilter) {
        FreezerPickerFilter.ALL -> 0
        FreezerPickerFilter.FROZEN -> 1
        FreezerPickerFilter.ACTIVE -> 2
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.freezer_manage_apps),
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack,
    ) {
        item(key = "freezer-picker-hint") {
            MiuixHintText(
                when (settings.privilegeMode) {
                    PrivilegeMode.SHIZUKU ->
                        if (privilegedAccessGranted == true) {
                            stringResource(R.string.freezer_shizuku_granted)
                        } else {
                            stringResource(R.string.freezer_shizuku_denied)
                        }
                    PrivilegeMode.ROOT ->
                        if (privilegedAccessGranted == true) {
                            stringResource(R.string.privilege_mode_status_root_ready)
                        } else {
                            stringResource(R.string.privilege_mode_status_root_missing)
                        }
                },
            )
        }
        item(key = "freezer-picker-launcher-hint") {
            MiuixHintText(stringResource(R.string.freezer_legacy_icon_cleanup))
        }
        groupedCardItems(
            keyPrefix = "freezer-picker-options",
            items = listOf(
                settingsCardScopeItem("show-launcher") {
                    SettingSwitchRow(
                        title = stringResource(R.string.freezer_show_in_launcher_title),
                        subtitle = stringResource(R.string.freezer_show_in_launcher_desc),
                        checked = settings.freezerShowInLauncher,
                        enabled = true,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                if (enabled) {
                                    if (!FreezerLauncherHelper.requestPinShortcut(context)) {
                                        FreezerLauncherHelper.showPinShortcutFailedToast(context)
                                        return@launch
                                    }
                                    settingsRepository.setFreezerShowInLauncher(true)
                                } else {
                                    settingsRepository.setFreezerShowInLauncher(false)
                                    FreezerLauncherHelper.cleanupLegacyAlias(context)
                                    FreezerLauncherHelper.showUnpinHintToast(context)
                                }
                            }
                        },
                    )
                },
                settingsCardScopeItem("show-system") {
                    SettingSwitchRow(
                        title = stringResource(R.string.freezer_show_system_apps),
                        checked = showSystemApps,
                        enabled = true,
                        onCheckedChange = { showSystemApps = it },
                    )
                },
                settingsCardScopeItem("import-frozen") {
                    SettingNavigationRow(
                        icon = { label -> Icon(Icons.Default.AcUnit, contentDescription = label) },
                        title = stringResource(R.string.freezer_import_frozen_apps),
                        subtitle = stringResource(R.string.freezer_import_frozen_apps_desc),
                        onClick = {
                            scope.launch {
                                val count = withContext(Dispatchers.IO) {
                                    FreezerListOperations.importFrozenApps(context, settingsRepository)
                                }
                                val message = if (count > 0) {
                                    context.getString(R.string.freezer_import_frozen_done, count)
                                } else {
                                    context.getString(R.string.freezer_import_frozen_none)
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                reloadApps()
                            }
                        },
                    )
                },
            ),
        )
        item(key = "freezer-picker-apps-title") {
            MiuixSmallTitle(
                stringResource(R.string.freezer_apps_section_title, displayedApps.size),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MiuixSmallTitleSectionTop),
            )
        }
        item(key = "freezer-picker-filter-tabs") {
            MiuixTabRowWithContour(
                tabs = filterTabs,
                selectedTabIndex = selectedFilterTabIndex,
                onTabSelected = { index ->
                    listFilter = when (index) {
                        0 -> FreezerPickerFilter.ALL
                        1 -> FreezerPickerFilter.FROZEN
                        else -> FreezerPickerFilter.ACTIVE
                    }
                },
                contentHorizontalPadding = 12.dp,
            )
        }
        when {
            isLoading -> {
                item(key = "freezer-picker-loading") {
                    LoadingContent(
                        message = stringResource(R.string.loading),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            displayedApps.isEmpty() -> {
                item(key = "freezer-picker-empty") {
                    Text(
                        text = if (searchQuery.isBlank()) {
                            stringResource(R.string.no_apps)
                        } else {
                            stringResource(R.string.no_apps)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    )
                }
            }
            else -> {
                items(displayedApps.size, key = { displayedApps[it].packageName }) { index ->
                    val app = displayedApps[index]
                    val inList = app.packageName in settings.freezerAppPackages
                    val frozen = FreezerOperations.isFrozen(context, app.packageName)
                    val statusSuffix = when {
                        inList && frozen -> " · $frozenLabel"
                        inList -> " · $activeLabel"
                        else -> ""
                    }
                    AppPackageListRow(
                        entry = AppPackageEntry.Installed(app),
                        segmentIndex = index,
                        segmentCount = displayedApps.size,
                        actionIcon = if (inList) Icons.Default.Close else Icons.Default.Add,
                        actionDescription = if (inList) {
                            stringResource(R.string.freezer_remove_from_list)
                        } else {
                            stringResource(R.string.freezer_add_to_list)
                        },
                        missingIcon = Icons.Default.TouchApp,
                        subtitle = app.packageName + statusSuffix,
                        enabled = !inList || !frozen,
                        onAction = {
                            scope.launch {
                                if (inList) {
                                    if (FreezerListOperations.removeFromList(
                                            context = context,
                                            settingsRepository = settingsRepository,
                                            packageName = app.packageName,
                                            appRepository = appRepository,
                                        )
                                    ) {
                                        reloadApps()
                                    }
                                } else {
                                    settingsRepository.addFreezerApp(app.packageName)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
