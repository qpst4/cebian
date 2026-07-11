package com.slideindex.app.ui.quicklauncher

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.ui.AppPackageEntry
import com.slideindex.app.ui.Md3PickerAppEntryLeading
import com.slideindex.app.ui.Md3PickerAppLeading
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.Md3PickerSectionHeader
import com.slideindex.app.ui.PickerListGroupSpacing
import com.slideindex.app.ui.PickerListHorizontalPadding
import com.slideindex.app.ui.PickerSearchListHeader
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.ShortcutScanProgressContent
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.ui.gesturepicker.filterGestureActions
import com.slideindex.app.ui.gesturepicker.gestureActionDescription
import com.slideindex.app.ui.gesturepicker.gestureActionLabel
import com.slideindex.app.ui.gesturepicker.requestPermissionForAdjustAction
import com.slideindex.app.ui.pickerListSegmentedGap
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.CreatedShortcut
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.ShortcutScanPhase
import com.slideindex.app.util.ShortcutScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun QuickLauncherAddOverlaySheetBody(
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    apps: List<AppInfo>,
    addedAppPackages: Set<String>,
    addedShortcutKeys: Set<String>,
    addedActionKeys: Set<String>,
    onToggle: (QuickLauncherItem, Boolean) -> Unit,
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var visitedTabs by remember { mutableStateOf(setOf(0)) }
    fun selectTab(index: Int) {
        selectedTab = index
        visitedTabs = visitedTabs + index
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectTab(0) },
                text = { Text(stringResource(R.string.action_picker_tab_actions)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectTab(1) },
                text = { Text(stringResource(R.string.action_picker_tab_apps)) },
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectTab(2) },
                text = { Text(stringResource(R.string.action_picker_tab_shortcuts)) },
            )
        }
        val modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .then(
                if (nestedScrollConnection != null) {
                    Modifier.nestedScroll(nestedScrollConnection)
                } else {
                    Modifier
                },
            )
        Box(modifier = modifier) {
            if (0 in visitedTabs) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pickerTabPageVisible(selectedTab == 0),
                ) {
                    QuickLauncherAddActionsTab(
                        searchQuery = searchQuery,
                        onSearchChange = onSearchChange,
                        configuredActionKeys = addedActionKeys,
                        onToggle = onToggle,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (1 in visitedTabs) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pickerTabPageVisible(selectedTab == 1),
                ) {
                    QuickLauncherAddAppsTab(
                        searchQuery = searchQuery,
                        onSearchChange = onSearchChange,
                        apps = apps,
                        configuredAppPackages = addedAppPackages,
                        onToggle = { app, added ->
                            onToggle(QuickLauncherItem.app(app.packageName, app.label), added)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (2 in visitedTabs) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pickerTabPageVisible(selectedTab == 2),
                ) {
                    QuickLauncherAddShortcutsTab(
                        apps = apps,
                        searchQuery = searchQuery,
                        onSearchChange = onSearchChange,
                        configuredShortcutKeys = addedShortcutKeys,
                        onToggle = onToggle,
                        launchCreateShortcut = launchCreateShortcut,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
fun QuickLauncherToggleRow(
    entry: AppPackageEntry,
    segmentIndex: Int,
    segmentCount: Int,
    added: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    showAction: Boolean = true,
) {
    val resolvedTitle = title ?: when (entry) {
        is AppPackageEntry.Installed -> entry.app.label
        is AppPackageEntry.Missing -> entry.packageName
    }
    val resolvedSubtitle = subtitle ?: when (entry) {
        is AppPackageEntry.Installed -> entry.app.packageName
        is AppPackageEntry.Missing -> null
    }
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = resolvedTitle,
        subtitle = resolvedSubtitle,
        selected = added,
        onClick = if (showAction) onToggle else null,
        modifier = modifier,
        leadingContent = {
            Md3PickerAppEntryLeading(
                entry = entry,
                missingIcon = Icons.AutoMirrored.Filled.Shortcut,
            )
        },
        trailingMode = if (showAction) PickerTrailingMode.Toggle else PickerTrailingMode.None,
        onTrailingClick = if (showAction) onToggle else null,
    )
}

@Composable
private fun QuickLauncherShortcutSectionHeader(title: String) {
    Md3PickerSectionHeader(title)
}

@Composable
fun QuickLauncherActionRow(
    action: GestureAction,
    segmentIndex: Int,
    segmentCount: Int,
    label: String,
    subtitle: String?,
    added: Boolean,
    onToggle: () -> Unit,
) {
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = label,
        subtitle = subtitle,
        selected = added,
        onClick = onToggle,
        leadingContent = {
            Md3PickerIconLeading(
                icon = gestureActionIcon(action),
                selected = added,
            )
        },
        trailingMode = PickerTrailingMode.Toggle,
        onTrailingClick = onToggle,
    )
}

@Composable
private fun QuickLauncherAddActionsTab(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    configuredActionKeys: Set<String>,
    onToggle: (QuickLauncherItem, Boolean) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val actionOptions = remember {
        listOf(
            GestureAction.OpenIndex,
            GestureAction.TaskSwitcher,
            GestureAction.ShellCommandPanel,
            GestureAction.QuickToolsOverlay,
            GestureAction.WidgetPopupOverlay,
            GestureAction.FloatingPointer,
            GestureAction.Back,
            GestureAction.Home,
            GestureAction.Recents,
            GestureAction.CloseCurrentApp,
            GestureAction.FreeWindowCurrentApp,
            GestureAction.Flashlight,
            GestureAction.ToggleDnd,
            GestureAction.ScreenRecord,
            GestureAction.ToggleWifi,
            GestureAction.ToggleMobileData,
            GestureAction.SwitchInputMethod,
            GestureAction.ToggleMute,
            GestureAction.MediaPlayPause,
            GestureAction.MediaPrevious,
            GestureAction.MediaNext,
            GestureAction.PreviousApp,
            GestureAction.OpenNotifications,
            GestureAction.OpenQuickSettings,
            GestureAction.LockScreen,
            GestureAction.Screenshot,
            GestureAction.PowerMenu,
            GestureAction.KeepScreenOn,
            GestureAction.ScrollToTop,
            GestureAction.ScrollToBottom,
            GestureAction.AdjustVolume,
            GestureAction.AdjustBrightness,
            GestureAction.LaunchAssistant,
        )
    }
    val filtered = remember(actionOptions, searchQuery, context) {
        filterGestureActions(context, actionOptions, searchQuery)
    }
    Column(modifier = modifier) {
        PickerSearchListHeader(
            query = searchQuery,
            onQueryChange = onSearchChange,
            hintResId = R.string.search_actions_hint,
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = PickerListHorizontalPadding,
                end = PickerListHorizontalPadding,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
        ) {
            if (filtered.isEmpty()) {
                item(key = "actions-empty") {
                    Text(
                        text = stringResource(R.string.search_no_actions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(filtered.size, key = { filtered[it].type.id }) { index ->
                    val action = filtered[index]
                    val label = gestureActionLabel(action)
                    val added = QuickLauncherItemCodec.actionKey(action) in configuredActionKeys
                    QuickLauncherActionRow(
                        action = action,
                        segmentIndex = index,
                        segmentCount = filtered.size,
                        label = label,
                        subtitle = gestureActionDescription(action),
                        added = added,
                        onToggle = {
                            val item = QuickLauncherItem.action(action, label)
                            if (!added) {
                                requestPermissionForAdjustAction(context, action)
                            }
                            onToggle(item, added)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickLauncherAddAppsTab(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    apps: List<AppInfo>,
    configuredAppPackages: Set<String>,
    onToggle: (AppInfo, Boolean) -> Unit,
    modifier: Modifier,
) {
    val query = searchQuery.trim().lowercase()
    val filtered = remember(apps, query) {
        apps.filter { app ->
            query.isEmpty() ||
                app.label.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query) ||
                PinyinHelper.sortKey(app.label).contains(query)
        }.sortedBy { PinyinHelper.sortKey(it.label) }
    }
    Column(modifier = modifier) {
        PickerSearchListHeader(
            query = searchQuery,
            onQueryChange = onSearchChange,
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = PickerListHorizontalPadding,
                end = PickerListHorizontalPadding,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
        ) {
            items(filtered.size, key = { filtered[it].packageName }) { index ->
                val app = filtered[index]
                val added = app.packageName in configuredAppPackages
                QuickLauncherToggleRow(
                    entry = AppPackageEntry.Installed(app),
                    segmentIndex = index,
                    segmentCount = filtered.size,
                    added = added,
                    onToggle = { onToggle(app, added) },
                )
            }
        }
    }
}

@Composable
private fun QuickLauncherAddShortcutsTab(
    apps: List<AppInfo>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    configuredShortcutKeys: Set<String>,
    onToggle: (QuickLauncherItem, Boolean) -> Unit,
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var catalog by remember { mutableStateOf<AppShortcutLoader.ShortcutCatalog?>(null) }
    var loading by remember { mutableStateOf(true) }
    var scanProgress by remember { mutableStateOf<ShortcutScanProgress?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(apps) {
        if (apps.isEmpty()) {
            loading = false
            scanProgress = null
            return@LaunchedEffect
        }
        loading = true
        scanProgress = ShortcutScanProgress(ShortcutScanPhase.DUMPSYS, 0, 0)
        try {
            catalog = withContext(Dispatchers.IO) {
                AppShortcutLoader.loadShortcutCatalog(
                    context = context,
                    apps = apps,
                    includeShell = true,
                    onProgress = { progress ->
                        mainHandler.post { scanProgress = progress }
                    },
                )
            }
        } catch (_: Exception) {
            catalog = AppShortcutLoader.ShortcutCatalog(createHosts = emptyList())
        } finally {
            loading = false
            scanProgress = null
        }
    }

    val query = searchQuery.trim().lowercase()
    val appsByPackage = remember(apps) { apps.associateBy { it.packageName } }
    val createHosts = catalog?.createHosts.orEmpty()
    val shortcutGroups = catalog?.groups.orEmpty()
    val filteredCreateHosts = remember(createHosts, query) {
        createHosts.filter { host ->
            query.isEmpty() ||
                host.label.lowercase().contains(query) ||
                host.packageName.lowercase().contains(query) ||
                PinyinHelper.sortKey(host.label).contains(query)
        }.sortedBy { PinyinHelper.sortKey(it.label) }
    }
    val filteredGroups = remember(shortcutGroups, query) {
        shortcutGroups.mapNotNull { group ->
            val appMatches = query.isEmpty() ||
                group.app.label.lowercase().contains(query) ||
                group.app.packageName.lowercase().contains(query) ||
                PinyinHelper.sortKey(group.app.label).contains(query)
            val matchedShortcuts = group.shortcuts.filter { shortcut ->
                query.isEmpty() ||
                    appMatches ||
                    shortcut.label.lowercase().contains(query) ||
                    (shortcut.shortcutId?.lowercase()?.contains(query) == true)
            }.sortedBy { PinyinHelper.sortKey(it.label) }
            if (matchedShortcuts.isEmpty()) null else group.copy(shortcuts = matchedShortcuts)
        }.orEmpty()
    }

    Column(modifier = modifier) {
        PickerSearchListHeader(
            query = searchQuery,
            onQueryChange = onSearchChange,
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = PickerListHorizontalPadding,
                end = PickerListHorizontalPadding,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
        ) {
            if (loading) {
                item(key = "shortcut-loading") {
                    ShortcutScanProgressContent(
                        progress = scanProgress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (filteredCreateHosts.isEmpty() && filteredGroups.isEmpty()) {
                item(key = "shortcut-empty") {
                    Text(
                        text = stringResource(R.string.shortcut_kind_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                if (filteredCreateHosts.isNotEmpty()) {
                    item(key = "create-header") {
                        QuickLauncherShortcutSectionHeader(stringResource(R.string.create_shortcut))
                    }
                    items(filteredCreateHosts.size, key = { filteredCreateHosts[it].qualifiedName }) { index ->
                        val host = filteredCreateHosts[index]
                        val app = appsByPackage[host.packageName]
                        Md3PickerListRow(
                            segmentIndex = index,
                            segmentCount = filteredCreateHosts.size,
                            title = host.label,
                            subtitle = stringResource(R.string.create_shortcut_tap_hint),
                            selected = false,
                            onClick = {
                                launchCreateShortcut(host) { created ->
                                    created?.let { shortcut ->
                                        onToggle(shortcut.toQuickLauncherItem(), false)
                                    }
                                }
                            },
                            leadingContent = {
                                if (app != null) {
                                    Md3PickerAppLeading(app)
                                } else {
                                    Md3PickerIconLeading(
                                        icon = Icons.AutoMirrored.Filled.Shortcut,
                                        selected = false,
                                    )
                                }
                            },
                            trailingMode = PickerTrailingMode.Icon,
                            trailingIcon = Icons.AutoMirrored.Filled.Shortcut,
                            trailingIconDescription = stringResource(R.string.create_shortcut),
                        )
                    }
                    item(key = "create-gap") { Spacer(modifier = Modifier.height(PickerListGroupSpacing)) }
                }
                if (filteredGroups.isNotEmpty()) {
                    item(key = "launch-header") {
                        QuickLauncherShortcutSectionHeader(stringResource(R.string.launch_shortcut))
                    }
                    filteredGroups.forEach { group ->
                        item(key = "shortcut-header-${group.app.packageName}") {
                            Md3PickerListRow(
                                segmentIndex = 0,
                                segmentCount = 1,
                                title = group.app.label,
                                subtitle = group.app.packageName,
                                selected = false,
                                onClick = null,
                                enabled = false,
                                leadingContent = { Md3PickerAppLeading(group.app) },
                            )
                        }
                        items(
                            count = group.shortcuts.size,
                            key = { idx ->
                                val shortcut = group.shortcuts[idx]
                                "${group.app.packageName}:${shortcut.shortcutId ?: shortcut.label}"
                            },
                        ) { index ->
                            val shortcut = group.shortcuts[index]
                            val item = shortcut.toQuickLauncherItem(group.app.packageName)
                            val added = QuickLauncherItemCodec.shortcutItemKey(item) in configuredShortcutKeys
                            QuickLauncherShortcutToggleRow(
                                app = group.app,
                                shortcut = shortcut,
                                segmentIndex = index,
                                segmentCount = group.shortcuts.size,
                                added = added,
                                onToggle = {
                                    if (!added) {
                                        AppShortcutLoader.cacheShortcutForLaunch(group.app.packageName, shortcut)
                                    }
                                    onToggle(item, added)
                                },
                            )
                        }
                        item(key = "gap-${group.app.packageName}") {
                            Spacer(modifier = Modifier.height(PickerListGroupSpacing))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickLauncherShortcutToggleRow(
    app: AppInfo,
    shortcut: TaskSwitcherMenuItem,
    segmentIndex: Int,
    segmentCount: Int,
    added: Boolean,
    onToggle: () -> Unit,
) {
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = shortcut.label,
        subtitle = shortcut.targetComponent?.takeIf { it.isNotBlank() },
        selected = added,
        onClick = onToggle,
        modifier = Modifier.padding(start = 12.dp),
        leadingContent = { Md3PickerAppLeading(app) },
        trailingMode = PickerTrailingMode.Toggle,
        onTrailingClick = onToggle,
    )
}
