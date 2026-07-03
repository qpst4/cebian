package com.slideindex.app.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherDefaults
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.ShortcutScanPhase
import com.slideindex.app.util.ShortcutScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed class EditorMode {
    data object Main : EditorMode()
    data object AddPicker : EditorMode()
}

private enum class QuickLauncherEditorAddTab { ACTIONS, APPS, SHORTCUTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLauncherEditorScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSaveItems: (List<QuickLauncherItem>) -> Unit,
) {
    val context = LocalContext.current
    val appRepository = remember { (context.applicationContext as SlideIndexApp).appRepository }
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var mode by remember { mutableStateOf<EditorMode>(EditorMode.Main) }
    var searchQuery by remember { mutableStateOf("") }
    val currentItems = settings.quickLauncher
    var items by remember(currentItems) { mutableStateOf(currentItems) }

    LaunchedEffect(allApps, currentItems) {
        if (allApps.isNotEmpty() && items.isEmpty()) {
            val effective = QuickLauncherDefaults.effectiveItems(currentItems, allApps)
            if (effective.isNotEmpty()) {
                items = effective
                if (currentItems.isEmpty()) {
                    onSaveItems(effective)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = true)
    }

    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }
    val configuredAppPackages = remember(items) {
        items.filter { it.type == QuickLauncherItemType.APP }.map { it.payload }.toSet()
    }
    val configuredShortcutKeys = remember(items) {
        items.filter { it.type == QuickLauncherItemType.SHORTCUT }.mapNotNull { item ->
            QuickLauncherItemCodec.shortcutItemKey(item)
        }.toSet()
    }
    val configuredActionKeys = remember(items) {
        items.filter { it.type == QuickLauncherItemType.ACTION }.mapNotNull { item ->
            QuickLauncherItemCodec.parseActionPayload(item.payload)?.let(QuickLauncherItemCodec::actionKey)
        }.toSet()
    }

    fun saveAndBack() {
        onSaveItems(items)
        onBack()
    }

    fun addItem(item: QuickLauncherItem) {
        items = items + item
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (mode) {
                            EditorMode.AddPicker -> stringResource(R.string.quick_launcher_add)
                            else -> stringResource(R.string.quick_launcher_editor_title)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (mode) {
                            EditorMode.Main -> saveAndBack()
                            EditorMode.AddPicker -> {
                                mode = EditorMode.Main
                                searchQuery = ""
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when (mode) {
            EditorMode.Main -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.quick_launcher_editor_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                QuickLauncherGridEditor(
                    settings = settings,
                    items = items,
                    appsByPackage = appsByPackage,
                    onItemsChange = { items = it },
                    onAdd = {
                        searchQuery = ""
                        mode = EditorMode.AddPicker
                    },
                )
            }
            EditorMode.AddPicker -> QuickLauncherEditorAddPicker(
                padding = padding,
                apps = allApps,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                configuredAppPackages = configuredAppPackages,
                configuredShortcutKeys = configuredShortcutKeys,
                configuredActionKeys = configuredActionKeys,
                onAddAction = { action, label ->
                    addItem(QuickLauncherItem.action(action, label))
                },
                onAddApp = { app ->
                    addItem(QuickLauncherItem.app(app.packageName, app.label))
                },
                onAddShortcut = { app, shortcut ->
                    addItem(shortcut.toQuickLauncherItem(app.packageName))
                },
                onCreatedShortcut = { created ->
                    addItem(created.toQuickLauncherItem())
                },
            )
        }
    }
}

@Composable
private fun QuickLauncherEditorAddPicker(
    padding: androidx.compose.foundation.layout.PaddingValues,
    apps: List<AppInfo>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    configuredAppPackages: Set<String>,
    configuredShortcutKeys: Set<String>,
    configuredActionKeys: Set<String>,
    onAddAction: (GestureAction, String) -> Unit,
    onAddApp: (AppInfo) -> Unit,
    onAddShortcut: (AppInfo, TaskSwitcherMenuItem) -> Unit,
    onCreatedShortcut: (AppShortcutLoader.CreatedShortcut) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.action_picker_tab_actions)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.action_picker_tab_apps)) },
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(stringResource(R.string.action_picker_tab_shortcuts)) },
            )
        }
        when (QuickLauncherEditorAddTab.entries[selectedTab]) {
            QuickLauncherEditorAddTab.ACTIONS -> QuickLauncherEditorActionsTab(
                configuredActionKeys = configuredActionKeys,
                onAdd = onAddAction,
            )
            QuickLauncherEditorAddTab.APPS -> QuickLauncherEditorAppsTab(
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                apps = apps,
                configuredAppPackages = configuredAppPackages,
                onAdd = onAddApp,
            )
            QuickLauncherEditorAddTab.SHORTCUTS -> QuickLauncherEditorShortcutsTab(
                apps = apps,
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                configuredShortcutKeys = configuredShortcutKeys,
                onAdd = onAddShortcut,
                onCreatedShortcut = onCreatedShortcut,
            )
        }
    }
}

@Composable
private fun QuickLauncherEditorActionsTab(
    configuredActionKeys: Set<String>,
    onAdd: (GestureAction, String) -> Unit,
) {
    val context = LocalContext.current
    val actionOptions = remember {
        listOf(
            GestureAction.OpenIndex,
            GestureAction.TaskSwitcher,
            GestureAction.ShellCommandPanel,
            GestureAction.Back,
            GestureAction.Home,
            GestureAction.Recents,
            GestureAction.CloseCurrentApp,
            GestureAction.FreeWindowCurrentApp,
            GestureAction.Flashlight,
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
    val sortedActions = remember(actionOptions, context) {
        actionOptions.sortedBy { gestureActionSortKey(context, it) }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(sortedActions, key = { it.type.id }) { action ->
            if (QuickLauncherItemCodec.actionKey(action) in configuredActionKeys) return@items
            val label = gestureActionLabel(action)
            QuickLauncherActionRow(
                label = label,
                subtitle = gestureActionDescription(action),
                added = false,
                onToggle = {
                    requestPermissionForAdjustAction(context, action)
                    onAdd(action, label)
                },
            )
        }
    }
}

@Composable
private fun QuickLauncherEditorAppsTab(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    apps: List<AppInfo>,
    configuredAppPackages: Set<String>,
    onAdd: (AppInfo) -> Unit,
) {
    val query = searchQuery.trim().lowercase()
    val filtered = remember(apps, query, configuredAppPackages) {
        apps.filter { it.packageName !in configuredAppPackages }
            .filter { app ->
                query.isEmpty() ||
                    app.label.lowercase().contains(query) ||
                    app.packageName.lowercase().contains(query) ||
                    PinyinHelper.sortKey(app.label).contains(query)
            }
            .sortedBy { PinyinHelper.sortKey(it.label) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered, key = { it.packageName }) { app ->
                AppPackageListRow(
                    entry = AppPackageEntry.Installed(app),
                    actionIcon = Icons.Default.Add,
                    actionDescription = stringResource(R.string.quick_launcher_add),
                    missingIcon = Icons.Default.Add,
                    onAction = { onAdd(app) },
                )
            }
        }
    }
}

@Composable
private fun QuickLauncherEditorShortcutsTab(
    apps: List<AppInfo>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    configuredShortcutKeys: Set<String>,
    onAdd: (AppInfo, TaskSwitcherMenuItem) -> Unit,
    onCreatedShortcut: (AppShortcutLoader.CreatedShortcut) -> Unit,
) {
    val context = LocalContext.current
    var catalog by remember { mutableStateOf<AppShortcutLoader.ShortcutCatalog?>(null) }
    var loading by remember { mutableStateOf(true) }
    var scanProgress by remember { mutableStateOf<ShortcutScanProgress?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var pendingCreateHost by remember { mutableStateOf<AppShortcutLoader.CreateShortcutHost?>(null) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val host = pendingCreateHost
        pendingCreateHost = null
        if (result.resultCode != Activity.RESULT_OK || host == null) return@rememberLauncherForActivityResult
        val created = AppShortcutLoader.parseCreateShortcutResult(host.packageName, result.data)
            ?: return@rememberLauncherForActivityResult
        onCreatedShortcut(created)
    }

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
    val createHosts = catalog?.createHosts.orEmpty()
    val shortcutGroups = catalog?.groups.orEmpty()
    val filteredCreateHosts = remember(createHosts, query) {
        createHosts.filter { host ->
            query.isEmpty() ||
                host.label.lowercase().contains(query) ||
                host.packageName.lowercase().contains(query) ||
                PinyinHelper.sortKey(host.label).contains(query)
        }
    }
    val filteredGroups = remember(shortcutGroups, query, configuredShortcutKeys) {
        shortcutGroups.mapNotNull { group ->
            val available = group.shortcuts.filter { shortcut ->
                val id = shortcut.shortcutId ?: shortcut.label
                QuickLauncherItemCodec.shortcutToggleKey(
                    group.app.packageName,
                    id,
                    shortcut.intentUris,
                ) !in configuredShortcutKeys
            }
            if (available.isEmpty()) return@mapNotNull null
            val appMatches = query.isEmpty() ||
                group.app.label.lowercase().contains(query) ||
                group.app.packageName.lowercase().contains(query) ||
                PinyinHelper.sortKey(group.app.label).contains(query)
            val matchedShortcuts = if (query.isEmpty()) {
                available
            } else if (appMatches) {
                available
            } else {
                available.filter { shortcut ->
                    shortcut.label.lowercase().contains(query) ||
                        (shortcut.shortcutId?.lowercase()?.contains(query) == true)
                }
            }
            if (matchedShortcuts.isEmpty()) null
            else group.copy(shortcuts = matchedShortcuts)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        SearchBar(query = searchQuery, onQueryChange = onSearchChange, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        when {
            loading -> {
                ShortcutScanProgressContent(
                    progress = scanProgress,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            filteredCreateHosts.isEmpty() && filteredGroups.isEmpty() -> {
                Text(
                    text = stringResource(R.string.shortcut_kind_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (filteredCreateHosts.isNotEmpty()) {
                        item(key = "header-create") {
                            QuickLauncherEditorShortcutSectionHeader(stringResource(R.string.create_shortcut))
                        }
                        items(filteredCreateHosts, key = { it.qualifiedName }) { host ->
                            val app = apps.firstOrNull { it.packageName == host.packageName }
                            AppPackageListRow(
                                entry = app?.let { AppPackageEntry.Installed(it) }
                                    ?: AppPackageEntry.Missing(host.label),
                                actionIcon = Icons.AutoMirrored.Filled.Shortcut,
                                actionDescription = stringResource(R.string.create_shortcut),
                                missingIcon = Icons.AutoMirrored.Filled.Shortcut,
                                onAction = {
                                    pendingCreateHost = host
                                    runCatching { createLauncher.launch(host.createIntent()) }
                                        .onFailure { pendingCreateHost = null }
                                },
                                title = host.label,
                                subtitle = stringResource(R.string.create_shortcut_tap_hint),
                            )
                        }
                        item(key = "gap-create") { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                    if (filteredGroups.isNotEmpty()) {
                        item(key = "header-launch") {
                            QuickLauncherEditorShortcutSectionHeader(stringResource(R.string.launch_shortcut))
                        }
                        filteredGroups.forEach { group ->
                            item(key = "header-${group.app.packageName}") {
                                AppPackageListRow(
                                    entry = AppPackageEntry.Installed(group.app),
                                    actionIcon = Icons.Default.Add,
                                    actionDescription = null,
                                    missingIcon = Icons.Default.Add,
                                    onAction = {},
                                    showAction = false,
                                )
                            }
                            items(
                                items = group.shortcuts,
                                key = { shortcut ->
                                    "${group.app.packageName}:${shortcut.shortcutId ?: shortcut.label}"
                                },
                            ) { shortcut ->
                                ShortcutCatalogRow(
                                    shortcut = shortcut,
                                    onAdd = { onAdd(group.app, shortcut) },
                                )
                            }
                            item(key = "gap-${group.app.packageName}") {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickLauncherEditorShortcutSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
    )
}

@Composable
private fun ShortcutCatalogRow(
    shortcut: TaskSwitcherMenuItem,
    onAdd: () -> Unit,
) {
    AppPackageListRow(
        entry = AppPackageEntry.Missing(shortcut.label),
        actionIcon = Icons.Default.Add,
        actionDescription = stringResource(R.string.quick_launcher_add),
        missingIcon = Icons.AutoMirrored.Filled.Shortcut,
        onAction = onAdd,
        modifier = Modifier.padding(start = 28.dp),
        title = shortcut.label,
        subtitle = shortcut.targetComponent,
    )
}
