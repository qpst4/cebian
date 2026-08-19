package com.slideindex.app.ui.quicklauncher

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.ui.AppPackageEntry
import com.slideindex.app.ui.Md3PickerAppEntryLeading
import com.slideindex.app.ui.Md3PickerAppLeading
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.Md3PickerSectionHeader
import com.slideindex.app.ui.PickerListGroupSpacing
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.ui.PickerListOverlayHorizontalPadding
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.displayLabelForExecuteShellCommand
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.pickerHorizontalSlideTransitionByDepth
import com.slideindex.app.ui.quicklauncher.quickLauncherAddPickerActionItems
import com.slideindex.app.ui.quicklauncher.rememberQuickLauncherFilteredActions
import com.slideindex.app.ui.picker.activityShortcutPickerToggleSection
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.picker.systemShortcutCatalogItems
import com.slideindex.app.ui.gesturepicker.gestureActionDescription
import com.slideindex.app.ui.gesturepicker.gestureActionLabel
import com.slideindex.app.ui.gesturepicker.requestPermissionForAdjustAction
import com.slideindex.app.ui.pickerListSegmentedGap
import com.slideindex.app.ui.pickerSegmentCount
import com.slideindex.app.ui.pickerSegmentIndex
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.CreatedShortcut
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.ShortcutScanPhase
import com.slideindex.app.util.ShortcutScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface QuickLauncherAddSubScreen {
    data object Main : QuickLauncherAddSubScreen
    data object PickApp : QuickLauncherAddSubScreen
    data class PickActivity(val packageName: String) : QuickLauncherAddSubScreen
    data class ShellCommandConfig(val initialCommand: String = "") : QuickLauncherAddSubScreen
    data object CreateFolder : QuickLauncherAddSubScreen
}

internal fun QuickLauncherAddSubScreen.navDepth(): Int = when (this) {
    QuickLauncherAddSubScreen.Main -> 0
    QuickLauncherAddSubScreen.PickApp -> 1
    is QuickLauncherAddSubScreen.PickActivity -> 2
    is QuickLauncherAddSubScreen.ShellCommandConfig -> 1
    QuickLauncherAddSubScreen.CreateFolder -> 1
}

private fun QuickLauncherAddSubScreen.contentKey(): Any = when (this) {
    QuickLauncherAddSubScreen.Main -> "main"
    QuickLauncherAddSubScreen.PickApp -> "pickApp"
    is QuickLauncherAddSubScreen.PickActivity -> "pickActivity:$packageName"
    is QuickLauncherAddSubScreen.ShellCommandConfig -> "shellConfig"
    QuickLauncherAddSubScreen.CreateFolder -> "createFolder"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun QuickLauncherAddOverlaySheetBody(
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection?,
    searchQuery: String,
    apps: List<AppInfo>,
    addedAppPackages: Set<String>,
    addedShortcutKeys: Set<String>,
    addedActionKeys: Set<String>,
    activityShortcuts: List<ActivityShortcut>,
    shellCommands: List<com.slideindex.app.shell.ShellCommand> = emptyList(),
    onToggle: (QuickLauncherItem, Boolean) -> Unit,
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit,
    subScreen: QuickLauncherAddSubScreen,
    onSubScreenChange: (QuickLauncherAddSubScreen) -> Unit,
    selectedTab: Int,
    singleSelect: Boolean = false,
) {
    var visitedTabs by remember { mutableStateOf(setOf(selectedTab)) }
    LaunchedEffect(selectedTab) {
        visitedTabs = visitedTabs + selectedTab
    }

    AnimatedContent(
        targetState = subScreen,
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
        transitionSpec = { pickerHorizontalSlideTransitionByDepth(QuickLauncherAddSubScreen::navDepth) },
        contentKey = { it.contentKey() },
        label = "quickLauncherAddSubNav",
    ) { screen ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
        when (screen) {
            is QuickLauncherAddSubScreen.ShellCommandConfig -> {
                GestureExecuteShellCommandScreen(
                    initialCommand = screen.initialCommand,
                    shellCommands = shellCommands,
                    onBack = { onSubScreenChange(QuickLauncherAddSubScreen.Main) },
                    onConfirm = { command ->
                        val label = displayLabelForExecuteShellCommand(command, shellCommands)
                        val action = com.slideindex.app.gesture.GestureAction.ExecuteShellCommand(command)
                        onToggle(QuickLauncherItem.action(action, label), false)
                        onSubScreenChange(QuickLauncherAddSubScreen.Main)
                    },
                )
            }
            QuickLauncherAddSubScreen.PickApp -> {
                ActivityShortcutPickAppScreen(
                    embedInParentChrome = true,
                    onBack = { onSubScreenChange(QuickLauncherAddSubScreen.Main) },
                    onSelectApp = { app ->
                        onSubScreenChange(QuickLauncherAddSubScreen.PickActivity(app.packageName))
                    },
                )
            }
            is QuickLauncherAddSubScreen.PickActivity -> {
                ActivityShortcutPickActivityScreen(
                    packageName = screen.packageName,
                    embedInParentChrome = true,
                    onBack = { onSubScreenChange(QuickLauncherAddSubScreen.PickApp) },
                    onSelectActivity = { activity ->
                        onToggle(
                            QuickLauncherItem.shortcut(
                                "${activity.packageName}/${activity.className}",
                                activity.label,
                            ),
                            false,
                        )
                        onSubScreenChange(QuickLauncherAddSubScreen.Main)
                    },
                )
            }
            QuickLauncherAddSubScreen.CreateFolder -> {
                QuickLauncherCreateFolderScreen(
                    apps = apps,
                    activityShortcuts = activityShortcuts,
                    shellCommands = shellCommands,
                    onCreateFolder = { name, items ->
                        onToggle(QuickLauncherItem.folder(name, items), false)
                        onSubScreenChange(QuickLauncherAddSubScreen.Main)
                    },
                    onBack = { onSubScreenChange(QuickLauncherAddSubScreen.Main) },
                    launchCreateShortcut = launchCreateShortcut,
                    onBrowseActivityShortcut = {
                        onSubScreenChange(QuickLauncherAddSubScreen.PickApp)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            QuickLauncherAddSubScreen.Main -> {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        val tabModifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .then(
                if (nestedScrollConnection != null) {
                    Modifier.nestedScroll(nestedScrollConnection)
                } else {
                    Modifier
                },
            )
        Box(modifier = tabModifier) {
            if (0 in visitedTabs) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pickerTabPageVisible(selectedTab == 0),
                ) {
                    QuickLauncherAddActionsTab(
                        searchQuery = searchQuery,
                        configuredActionKeys = addedActionKeys,
                        onToggleItem = onToggle,
                        onOpenExecuteShellCommand = {
                            onSubScreenChange(QuickLauncherAddSubScreen.ShellCommandConfig())
                        },
                        singleSelect = singleSelect,
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
                        apps = apps,
                        configuredAppPackages = addedAppPackages,
                        onToggle = { app, added ->
                            onToggle(QuickLauncherItem.app(app.packageName, app.label), added)
                        },
                        singleSelect = singleSelect,
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
                        configuredShortcutKeys = addedShortcutKeys,
                        activityShortcuts = activityShortcuts,
                        onToggle = onToggle,
                        onBrowseActivityShortcut = {
                            onSubScreenChange(QuickLauncherAddSubScreen.PickApp)
                        },
                        launchCreateShortcut = launchCreateShortcut,
                        singleSelect = singleSelect,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
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
    singleSelect: Boolean = false,
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
        selected = if (singleSelect) false else added,
        onClick = if (showAction) onToggle else null,
        modifier = modifier,
        leadingContent = {
            Md3PickerAppEntryLeading(
                entry = entry,
                missingIcon = Icons.AutoMirrored.Outlined.Shortcut,
            )
        },
        trailingMode = if (singleSelect) PickerTrailingMode.None else if (showAction) PickerTrailingMode.Toggle else PickerTrailingMode.None,
        onTrailingClick = if (singleSelect) null else if (showAction) onToggle else null,
    )
}

@Composable
fun QuickLauncherShellCommandActionRow(
    action: GestureAction,
    segmentIndex: Int,
    segmentCount: Int,
    label: String,
    subtitle: String?,
    onOpenConfig: () -> Unit,
) {
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = label,
        subtitle = subtitle,
        selected = false,
        onClick = onOpenConfig,
        leadingContent = {
            Md3PickerIconLeading(
                icon = gestureActionIcon(action, outlined = true),
                selected = false,
            )
        },
        trailingMode = PickerTrailingMode.None,
    )
}

@Composable
fun QuickLauncherActionRow(
    action: GestureAction,
    segmentIndex: Int,
    segmentCount: Int,
    label: String,
    subtitle: String?,
    added: Boolean,
    singleSelect: Boolean = false,
    onToggle: () -> Unit,
) {
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = label,
        subtitle = subtitle,
        selected = if (singleSelect) false else added,
        onClick = onToggle,
        leadingContent = {
            Md3PickerIconLeading(
                icon = gestureActionIcon(action, outlined = true),
                selected = if (singleSelect) false else added,
            )
        },
        trailingMode = if (singleSelect) PickerTrailingMode.None else PickerTrailingMode.Toggle,
        onTrailingClick = if (singleSelect) null else onToggle,
    )
}

@Composable
private fun QuickLauncherAddActionsTab(
    searchQuery: String,
    configuredActionKeys: Set<String>,
    onToggleItem: (QuickLauncherItem, Boolean) -> Unit,
    onOpenExecuteShellCommand: () -> Unit,
    modifier: Modifier,
    singleSelect: Boolean = false,
) {
    val filtered = rememberQuickLauncherFilteredActions(searchQuery)
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = PickerListOverlayHorizontalPadding,
            end = PickerListOverlayHorizontalPadding,
            bottom = 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
    ) {
        quickLauncherAddPickerActionItems(
            filtered = filtered,
            configuredActionKeys = configuredActionKeys,
            onToggleItem = onToggleItem,
            onOpenExecuteShellCommand = onOpenExecuteShellCommand,
            singleSelect = singleSelect,
        )
    }
}

@Composable
private fun QuickLauncherAddAppsTab(
    searchQuery: String,
    apps: List<AppInfo>,
    configuredAppPackages: Set<String>,
    onToggle: (AppInfo, Boolean) -> Unit,
    modifier: Modifier,
    singleSelect: Boolean = false,
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
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = PickerListOverlayHorizontalPadding,
            end = PickerListOverlayHorizontalPadding,
            bottom = 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
    ) {
        quickLauncherAddPickerAppItems(
            filtered = filtered,
            configuredAppPackages = configuredAppPackages,
            onToggle = onToggle,
            singleSelect = singleSelect,
        )
    }
}

@Composable
private fun QuickLauncherAddShortcutsTab(
    apps: List<AppInfo>,
    searchQuery: String,
    configuredShortcutKeys: Set<String>,
    activityShortcuts: List<ActivityShortcut>,
    onToggle: (QuickLauncherItem, Boolean) -> Unit,
    onBrowseActivityShortcut: () -> Unit,
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit,
    modifier: Modifier,
    singleSelect: Boolean = false,
) {
    val loadedCatalog = rememberLoadedShortcutCatalog(apps)
    val filtered = remember(loadedCatalog.catalog, searchQuery) {
        filterShortcutCatalog(loadedCatalog.catalog, searchQuery)
    }
    val appsByPackage = remember(apps) { apps.associateBy { it.packageName } }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = PickerListOverlayHorizontalPadding,
            end = PickerListOverlayHorizontalPadding,
            bottom = 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
    ) {
        if (searchQuery.isBlank() || activityShortcuts.isNotEmpty()) {
            activityShortcutPickerToggleSection(
                activityShortcuts = activityShortcuts,
                configuredShortcutKeys = configuredShortcutKeys,
                onToggle = onToggle,
                onBrowse = onBrowseActivityShortcut,
                searchQuery = searchQuery,
                singleSelect = singleSelect,
            )
        }
        systemShortcutCatalogItems(
            filtered = filtered,
            appsByPackage = appsByPackage,
            loading = loadedCatalog.loading,
            scanProgress = loadedCatalog.scanProgress,
            loadingItemKey = "shortcut-loading",
            emptyItemKey = "shortcut-empty",
            onCreateHostClick = { host ->
                launchCreateShortcut(host) { created ->
                    created?.let { shortcut ->
                        onToggle(shortcut.toQuickLauncherItem(), false)
                    }
                }
            },
            shortcutRowContent = { group, shortcut, segmentIndex, segmentCount ->
                val item = shortcut.toQuickLauncherItem(group.app.packageName)
                val added = QuickLauncherItemCodec.shortcutItemKey(item) in configuredShortcutKeys
                QuickLauncherShortcutToggleRow(
                    app = group.app,
                    shortcut = shortcut,
                    segmentIndex = segmentIndex,
                    segmentCount = segmentCount,
                    added = added,
                    singleSelect = singleSelect,
                    onToggle = {
                        if (!added) {
                            AppShortcutLoader.cacheShortcutForLaunch(group.app.packageName, shortcut)
                        }
                        onToggle(item, if (singleSelect) false else added)
                    },
                )
            },
        )
    }
}

@Composable
private fun QuickLauncherShortcutToggleRow(
    app: AppInfo,
    shortcut: TaskSwitcherMenuItem,
    segmentIndex: Int,
    segmentCount: Int,
    added: Boolean,
    singleSelect: Boolean = false,
    onToggle: () -> Unit,
) {
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = shortcut.label,
        subtitle = shortcut.targetComponent?.takeIf { it.isNotBlank() },
        selected = if (singleSelect) false else added,
        onClick = onToggle,
        leadingContent = { Md3PickerAppLeading(app) },
        trailingMode = if (singleSelect) PickerTrailingMode.None else PickerTrailingMode.Toggle,
        onTrailingClick = if (singleSelect) null else onToggle,
    )
}

@Composable
internal fun QuickLauncherCreateFolderScreen(
    apps: List<AppInfo>,
    activityShortcuts: List<ActivityShortcut>,
    shellCommands: List<com.slideindex.app.shell.ShellCommand>,
    onCreateFolder: (String, List<QuickLauncherItem>) -> Unit,
    onBack: () -> Unit,
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit,
    onBrowseActivityShortcut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var folderName by remember { mutableStateOf("") }
    var selectedItems by remember { mutableStateOf<List<QuickLauncherItem>>(emptyList()) }
    var selectedTab by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val configuredActionKeys = remember(selectedItems) {
        selectedItems.filter { it.type == QuickLauncherItemType.ACTION }
            .mapNotNull { QuickLauncherItemCodec.parseActionPayload(it.payload)?.let(QuickLauncherItemCodec::actionKey) }
            .toSet()
    }
    val configuredAppPackages = remember(selectedItems) {
        selectedItems.filter { it.type == QuickLauncherItemType.APP }
            .map { it.payload }
            .toSet()
    }
    val configuredShortcutKeys = remember(selectedItems) {
        selectedItems.filter { it.type == QuickLauncherItemType.SHORTCUT }
            .mapNotNull { QuickLauncherItemCodec.shortcutItemKey(it) }
            .toSet()
    }

    fun toggleItem(item: QuickLauncherItem, added: Boolean) {
        selectedItems = if (added) {
            selectedItems.filterNot { it.type == item.type && it.payload == item.payload }
        } else {
            selectedItems + item
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text(stringResource(R.string.quick_launcher_folder_name)) },
                placeholder = { Text(stringResource(R.string.quick_launcher_new_folder)) },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (selectedItems.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.quick_launcher_folder_items_count, selectedItems.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }
        }

        com.slideindex.app.ui.miuix.MiuixTabRowWithContour(
            tabs = listOf(
                stringResource(R.string.action_picker_tab_actions),
                stringResource(R.string.action_picker_tab_apps),
                stringResource(R.string.action_picker_tab_shortcuts),
            ),
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
            contourHost = com.slideindex.app.ui.miuix.MiuixTabRowContourHost.SurfaceContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (selectedTab) {
                0 -> QuickLauncherAddActionsTab(
                    searchQuery = searchQuery,
                    configuredActionKeys = configuredActionKeys,
                    onToggleItem = ::toggleItem,
                    onOpenExecuteShellCommand = {},
                    singleSelect = false,
                    modifier = Modifier.fillMaxSize(),
                )
                1 -> QuickLauncherAddAppsTab(
                    searchQuery = searchQuery,
                    apps = apps,
                    configuredAppPackages = configuredAppPackages,
                    onToggle = { app, added ->
                        toggleItem(QuickLauncherItem.app(app.packageName, app.label), added)
                    },
                    singleSelect = false,
                    modifier = Modifier.fillMaxSize(),
                )
                2 -> QuickLauncherAddShortcutsTab(
                    apps = apps,
                    searchQuery = searchQuery,
                    configuredShortcutKeys = configuredShortcutKeys,
                    activityShortcuts = activityShortcuts,
                    onToggle = ::toggleItem,
                    onBrowseActivityShortcut = onBrowseActivityShortcut,
                    launchCreateShortcut = launchCreateShortcut,
                    singleSelect = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            androidx.compose.material3.Button(
                onClick = {
                    val defaultName = "文件夹"
                    val finalName = folderName.trim().ifBlank { defaultName }
                    onCreateFolder(finalName, selectedItems)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.quick_launcher_create_folder),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
        }
    }
}
