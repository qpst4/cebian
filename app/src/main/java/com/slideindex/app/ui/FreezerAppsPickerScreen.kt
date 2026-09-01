package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import com.slideindex.app.freezer.FreezerBootstrap
import com.slideindex.app.freezer.FreezerLauncherHelper
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var launcherApps by remember { mutableStateOf(appRepository.getCachedApps()) }
    var systemApps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var memberApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoadingLauncherApps by remember { mutableStateOf(launcherApps.isEmpty()) }
    var isLoadingSystemApps by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var privilegedAccessGranted by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        privilegedAccessGranted = withContext(Dispatchers.IO) {
            TaskManagerUtil.hasPrivilegedAccess()
        }
        if (launcherApps.isEmpty()) {
            launcherApps = appRepository.loadApps(force = false)
        }
        isLoadingLauncherApps = false
        withContext(Dispatchers.IO) {
            val bootstrap = FreezerBootstrap.scanDisabledLauncherPackages(context)
            if (bootstrap.isNotEmpty()) {
                val current = settingsRepository.readSnapshot().freezerAppPackages
                val merged = current + bootstrap
                if (merged != current) {
                    settingsRepository.setFreezerAppPackages(merged)
                }
            }
        }
    }

    LaunchedEffect(settings.freezerAppPackages) {
        memberApps = appRepository.resolveFreezerMembers(settings.freezerAppPackages)
    }

    LaunchedEffect(showSystemApps) {
        if (showSystemApps && systemApps == null) {
            isLoadingSystemApps = true
            systemApps = appRepository.loadFreezerApps(force = false)
            isLoadingSystemApps = false
        }
    }

    val addableSourceApps = if (showSystemApps) systemApps ?: launcherApps else launcherApps
    val addableApps = remember(addableSourceApps, settings.freezerAppPackages, searchQuery, showSystemApps) {
        val query = searchQuery.trim().lowercase()
        addableSourceApps
            .filter { it.packageName !in settings.freezerAppPackages }
            .filter { showSystemApps || !it.isSystem }
            .filter { app ->
                query.isBlank() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true) ||
                    app.pinyinKey.contains(query)
            }
            .sortedBy { it.pinyinKey }
    }
    val isLoadingAddSection = isLoadingLauncherApps || (showSystemApps && isLoadingSystemApps)

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
            ),
        )
        item(key = "freezer-picker-members-title") {
            MiuixSmallTitle(
                stringResource(R.string.freezer_list_section_title, memberApps.size),
            )
        }
        if (memberApps.isEmpty()) {
            item(key = "freezer-picker-members-empty") {
                Text(
                    text = stringResource(R.string.freezer_list_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                )
            }
        } else {
            items(memberApps.size, key = { memberApps[it].packageName }) { index ->
                val app = memberApps[index]
                AppPackageListRow(
                    entry = AppPackageEntry.Installed(app),
                    segmentIndex = index,
                    segmentCount = memberApps.size,
                    actionIcon = Icons.Default.Close,
                    actionDescription = stringResource(R.string.freezer_remove_from_list),
                    missingIcon = Icons.Default.TouchApp,
                    onAction = {
                        scope.launch { settingsRepository.removeFreezerApp(app.packageName) }
                    },
                )
            }
        }
        item(key = "freezer-picker-add-title") {
            MiuixSmallTitle(
                stringResource(R.string.freezer_section_add),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MiuixSmallTitleSectionTop),
            )
        }
        when {
            isLoadingAddSection -> {
                item(key = "freezer-picker-loading") {
                    LoadingContent(
                        message = stringResource(R.string.loading),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            addableApps.isEmpty() -> {
                item(key = "freezer-picker-add-empty") {
                    Text(
                        text = if (searchQuery.isBlank()) {
                            stringResource(R.string.freezer_add_all_added)
                        } else {
                            stringResource(R.string.no_apps)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    )
                }
            }
            else -> {
                items(addableApps.size, key = { addableApps[it].packageName }) { index ->
                    val app = addableApps[index]
                    AppPackageListRow(
                        entry = AppPackageEntry.Installed(app),
                        segmentIndex = index,
                        segmentCount = addableApps.size,
                        actionIcon = Icons.Default.Add,
                        actionDescription = stringResource(R.string.freezer_add_to_list),
                        missingIcon = Icons.Default.TouchApp,
                        onAction = {
                            scope.launch { settingsRepository.addFreezerApp(app.packageName) }
                        },
                    )
                }
            }
        }
    }
}
