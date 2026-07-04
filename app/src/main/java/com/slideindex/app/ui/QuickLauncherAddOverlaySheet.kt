package com.slideindex.app.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.CreatedShortcut
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.ShortcutScanPhase
import com.slideindex.app.util.ShortcutScanProgress
import com.slideindex.app.util.toSafeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class QuickLauncherAddTab { ACTIONS, APPS, SHORTCUTS }

private const val QUICK_LAUNCHER_SHEET_ENTER_MS = 240
private const val QUICK_LAUNCHER_SHEET_EXIT_MS = 200

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLauncherAddOverlaySheet(
    panelSide: PanelSide,
    apps: List<AppInfo>,
    configuredAppPackages: Set<String>,
    configuredShortcutKeys: Set<String>,
    configuredActionKeys: Set<String>,
    onDismiss: () -> Unit,
    onDismissComplete: () -> Unit = onDismiss,
    registerBackHandler: ((() -> Unit) -> Unit)? = null,
    onAdd: (QuickLauncherItem) -> Unit,
    onRemove: (QuickLauncherItem) -> Unit = {},
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val requestDismiss = remember { { visible = false } }

    SideEffect {
        registerBackHandler?.invoke(requestDismiss)
    }

    LaunchedEffect(Unit) {
        visible = true
    }

    LaunchedEffect(visible) {
        if (!visible) {
            delay(QUICK_LAUNCHER_SHEET_EXIT_MS.toLong())
            onDismissComplete()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(QUICK_LAUNCHER_SHEET_ENTER_MS)),
            exit = fadeOut(tween(QUICK_LAUNCHER_SHEET_EXIT_MS)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f)),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(QUICK_LAUNCHER_SHEET_ENTER_MS)) +
                slideInHorizontally(
                    initialOffsetX = { fullWidth ->
                        if (panelSide == PanelSide.LEFT) -fullWidth / 3 else fullWidth / 3
                    },
                    animationSpec = tween(QUICK_LAUNCHER_SHEET_ENTER_MS),
                ),
            exit = fadeOut(tween(QUICK_LAUNCHER_SHEET_EXIT_MS)) +
                slideOutHorizontally(
                    targetOffsetX = { fullWidth ->
                        if (panelSide == PanelSide.LEFT) -fullWidth / 3 else fullWidth / 3
                    },
                    animationSpec = tween(QUICK_LAUNCHER_SHEET_EXIT_MS),
                ),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = when (panelSide) {
                    PanelSide.LEFT -> Alignment.CenterStart
                    PanelSide.RIGHT -> Alignment.CenterEnd
                },
            ) {
                Surface(
                    modifier = Modifier
                        .padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 56.dp)
                        .widthIn(min = 280.dp, max = 400.dp)
                        .fillMaxHeight(0.78f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 4.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = requestDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.quick_launcher_add),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(R.string.quick_launcher_add_overlay_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = {
                                    Text(stringResource(R.string.quick_launcher_add_overlay_badge))
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            TextButton(onClick = requestDismiss) {
                                Text(stringResource(R.string.quick_launcher_add_overlay_done))
                            }
                        }
                        HorizontalDivider()
                        QuickLauncherAddOverlaySheetContent(
                            apps = apps,
                            configuredAppPackages = configuredAppPackages,
                            configuredShortcutKeys = configuredShortcutKeys,
                            configuredActionKeys = configuredActionKeys,
                            onDismiss = requestDismiss,
                            onAdd = onAdd,
                            onRemove = onRemove,
                            launchCreateShortcut = launchCreateShortcut,
                            showTopBar = false,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickLauncherAddOverlaySheetContent(
    apps: List<AppInfo>,
    configuredAppPackages: Set<String>,
    configuredShortcutKeys: Set<String>,
    configuredActionKeys: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (QuickLauncherItem) -> Unit,
    onRemove: (QuickLauncherItem) -> Unit,
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit,
    showTopBar: Boolean = true,
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var catalog by remember { mutableStateOf<AppShortcutLoader.ShortcutCatalog?>(null) }
    var shortcutsLoading by remember { mutableStateOf(true) }
    var scanProgress by remember { mutableStateOf<ShortcutScanProgress?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var addedAppPackages by remember(configuredAppPackages) { mutableStateOf(configuredAppPackages) }
    var addedShortcutKeys by remember(configuredShortcutKeys) { mutableStateOf(configuredShortcutKeys) }
    var addedActionKeys by remember(configuredActionKeys) { mutableStateOf(configuredActionKeys) }

    LaunchedEffect(apps) {
        if (apps.isEmpty()) {
            shortcutsLoading = false
            scanProgress = null
            return@LaunchedEffect
        }
        shortcutsLoading = true
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
            shortcutsLoading = false
            scanProgress = null
        }
    }

    fun addItem(item: QuickLauncherItem) {
        onAdd(item)
        when (item.type) {
            QuickLauncherItemType.APP -> addedAppPackages = addedAppPackages + item.payload
            QuickLauncherItemType.SHORTCUT -> {
                QuickLauncherItemCodec.shortcutItemKey(item)?.let { key ->
                    addedShortcutKeys = addedShortcutKeys + key
                }
            }
            QuickLauncherItemType.ACTION -> {
                QuickLauncherItemCodec.parseActionPayload(item.payload)?.let { action ->
                    addedActionKeys = addedActionKeys + QuickLauncherItemCodec.actionKey(action)
                }
            }
            QuickLauncherItemType.WIDGET -> Unit
        }
    }

    fun removeItem(item: QuickLauncherItem) {
        onRemove(item)
        when (item.type) {
            QuickLauncherItemType.APP -> addedAppPackages = addedAppPackages - item.payload
            QuickLauncherItemType.SHORTCUT -> {
                QuickLauncherItemCodec.shortcutItemKey(item)?.let { key ->
                    addedShortcutKeys = addedShortcutKeys - key
                }
            }
            QuickLauncherItemType.ACTION -> {
                QuickLauncherItemCodec.parseActionPayload(item.payload)?.let { action ->
                    addedActionKeys = addedActionKeys - QuickLauncherItemCodec.actionKey(action)
                }
            }
            QuickLauncherItemType.WIDGET -> Unit
        }
    }

    fun toggleItem(item: QuickLauncherItem, added: Boolean) {
        if (added) removeItem(item) else addItem(item)
    }

    if (showTopBar) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.quick_launcher_add)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            },
        ) { padding ->
            QuickLauncherAddOverlaySheetBody(
                padding = padding,
                selectedTab = selectedTab,
                onSelectedTabChange = { selectedTab = it },
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                apps = apps,
                catalog = catalog,
                shortcutsLoading = shortcutsLoading,
                scanProgress = scanProgress,
                addedAppPackages = addedAppPackages,
                addedShortcutKeys = addedShortcutKeys,
                addedActionKeys = addedActionKeys,
                onToggle = ::toggleItem,
                launchCreateShortcut = launchCreateShortcut,
            )
        }
    } else {
        QuickLauncherAddOverlaySheetBody(
            padding = PaddingValues(0.dp),
            selectedTab = selectedTab,
            onSelectedTabChange = { selectedTab = it },
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            apps = apps,
            catalog = catalog,
            shortcutsLoading = shortcutsLoading,
            scanProgress = scanProgress,
            addedAppPackages = addedAppPackages,
            addedShortcutKeys = addedShortcutKeys,
            addedActionKeys = addedActionKeys,
            onToggle = ::toggleItem,
            launchCreateShortcut = launchCreateShortcut,
        )
    }
}

@Composable
private fun QuickLauncherAddOverlaySheetBody(
    padding: PaddingValues,
    selectedTab: Int,
    onSelectedTabChange: (Int) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    apps: List<AppInfo>,
    catalog: AppShortcutLoader.ShortcutCatalog?,
    shortcutsLoading: Boolean,
    scanProgress: ShortcutScanProgress?,
    addedAppPackages: Set<String>,
    addedShortcutKeys: Set<String>,
    addedActionKeys: Set<String>,
    onToggle: (QuickLauncherItem, Boolean) -> Unit,
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onSelectedTabChange(0) },
                text = { Text(stringResource(R.string.action_picker_tab_actions)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onSelectedTabChange(1) },
                text = { Text(stringResource(R.string.action_picker_tab_apps)) },
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { onSelectedTabChange(2) },
                text = { Text(stringResource(R.string.action_picker_tab_shortcuts)) },
            )
        }
        when (QuickLauncherAddTab.entries[selectedTab]) {
            QuickLauncherAddTab.ACTIONS -> QuickLauncherAddActionsTab(
                configuredActionKeys = addedActionKeys,
                onToggle = onToggle,
            )
            QuickLauncherAddTab.APPS -> QuickLauncherAddAppsTab(
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                apps = apps,
                configuredAppPackages = addedAppPackages,
                onToggle = { app, added ->
                    onToggle(QuickLauncherItem.app(app.packageName, app.label), added)
                },
            )
            QuickLauncherAddTab.SHORTCUTS -> QuickLauncherAddShortcutsTab(
                apps = apps,
                catalog = catalog,
                loading = shortcutsLoading,
                scanProgress = scanProgress,
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                configuredShortcutKeys = addedShortcutKeys,
                onToggle = onToggle,
                launchCreateShortcut = launchCreateShortcut,
            )
        }
    }
}

@Composable
internal fun QuickLauncherToggleRow(
    entry: AppPackageEntry,
    segmentIndex: Int,
    segmentCount: Int,
    added: Boolean,
    onToggle: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    showAction: Boolean = true,
    modifier: Modifier = Modifier,
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
internal fun QuickLauncherActionRow(
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
    configuredActionKeys: Set<String>,
    onToggle: (QuickLauncherItem, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val actionOptions = remember {
        listOf(
            GestureAction.OpenIndex,
            GestureAction.TaskSwitcher,
            GestureAction.ShellCommandPanel,
            GestureAction.QuickToolsOverlay,
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
            .padding(horizontal = PickerListHorizontalPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
    ) {
        items(sortedActions.size, key = { sortedActions[it].type.id }) { index ->
            val action = sortedActions[index]
            val label = gestureActionLabel(action)
            val added = QuickLauncherItemCodec.actionKey(action) in configuredActionKeys
            QuickLauncherActionRow(
                action = action,
                segmentIndex = index,
                segmentCount = sortedActions.size,
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

@Composable
private fun QuickLauncherAddAppsTab(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    apps: List<AppInfo>,
    configuredAppPackages: Set<String>,
    onToggle: (AppInfo, Boolean) -> Unit,
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PickerListHorizontalPadding),
    ) {
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = 8.dp),
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
    catalog: AppShortcutLoader.ShortcutCatalog?,
    loading: Boolean,
    scanProgress: ShortcutScanProgress?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    configuredShortcutKeys: Set<String>,
    onToggle: (QuickLauncherItem, Boolean) -> Unit,
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit,
) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PickerListHorizontalPadding),
    ) {
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
        ) {
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
            } else if (filteredGroups.isNotEmpty()) {
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
