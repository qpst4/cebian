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
private fun QuickLauncherToggleRow(
    entry: AppPackageEntry,
    added: Boolean,
    onToggle: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    showAction: Boolean = true,
    modifier: Modifier = Modifier,
) {
    AppPackageListRow(
        entry = entry,
        actionIcon = if (added) Icons.Default.Check else Icons.Default.Add,
        actionDescription = stringResource(
            if (added) R.string.quick_launcher_remove else R.string.quick_launcher_add,
        ),
        missingIcon = Icons.AutoMirrored.Filled.Shortcut,
        onAction = onToggle,
        title = title,
        subtitle = subtitle,
        showAction = showAction,
        modifier = modifier,
    )
}

@Composable
private fun QuickLauncherShortcutSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
internal fun QuickLauncherActionRow(
    label: String,
    subtitle: String?,
    added: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.firstOrNull()?.uppercaseChar()?.toString() ?: "•",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (added) Icons.Default.Check else Icons.Default.Add,
                contentDescription = stringResource(
                    if (added) R.string.quick_launcher_remove else R.string.quick_launcher_add,
                ),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
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
            val label = gestureActionLabel(action)
            val added = QuickLauncherItemCodec.actionKey(action) in configuredActionKeys
            QuickLauncherActionRow(
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
                val added = app.packageName in configuredAppPackages
                QuickLauncherToggleRow(
                    entry = AppPackageEntry.Installed(app),
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
            .padding(horizontal = 20.dp),
    ) {
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (filteredCreateHosts.isNotEmpty()) {
                item(key = "create-header") {
                    QuickLauncherShortcutSectionHeader(stringResource(R.string.create_shortcut))
                }
                items(filteredCreateHosts, key = { it.qualifiedName }) { host ->
                    val app = appsByPackage[host.packageName]
                    AppPackageListRow(
                        entry = app?.let { AppPackageEntry.Installed(it) }
                            ?: AppPackageEntry.Missing(host.packageName),
                        actionIcon = Icons.AutoMirrored.Filled.Shortcut,
                        actionDescription = stringResource(R.string.create_shortcut),
                        missingIcon = Icons.AutoMirrored.Filled.Shortcut,
                        onAction = {
                            launchCreateShortcut(host) { created ->
                                created?.let { shortcut ->
                                    onToggle(shortcut.toQuickLauncherItem(), false)
                                }
                            }
                        },
                        title = host.label,
                        subtitle = stringResource(R.string.create_shortcut_tap_hint),
                    )
                }
                item(key = "create-gap") { Spacer(modifier = Modifier.height(8.dp)) }
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
                        QuickLauncherToggleRow(
                            entry = AppPackageEntry.Installed(group.app),
                            added = false,
                            onToggle = {},
                            showAction = false,
                        )
                    }
                    items(
                        items = group.shortcuts,
                        key = { "${group.app.packageName}:${it.shortcutId ?: it.label}" },
                    ) { shortcut ->
                        val shortcutId = shortcut.shortcutId ?: shortcut.label
                        val key = QuickLauncherItemCodec.shortcutToggleKey(
                            group.app.packageName,
                            shortcutId,
                            shortcut.intentUris,
                        )
                        val added = key in configuredShortcutKeys
                        val item = shortcut.toQuickLauncherItem(group.app.packageName)
                        QuickLauncherShortcutToggleRow(
                            app = group.app,
                            shortcut = shortcut,
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
                        Spacer(modifier = Modifier.height(6.dp))
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
    added: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(start = 28.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bitmap = remember(app.packageName) { app.icon.toSafeImageBitmap(96) }
        Image(
            bitmap = bitmap,
            contentDescription = app.label,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shortcut.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            shortcut.targetComponent?.takeIf { it.isNotBlank() }?.let { component ->
                Text(
                    text = component,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (added) Icons.Default.Check else Icons.Default.Add,
                contentDescription = stringResource(
                    if (added) R.string.quick_launcher_remove else R.string.quick_launcher_add,
                ),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
