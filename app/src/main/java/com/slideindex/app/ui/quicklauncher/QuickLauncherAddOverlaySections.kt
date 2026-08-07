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
}

internal fun QuickLauncherAddSubScreen.navDepth(): Int = when (this) {
    QuickLauncherAddSubScreen.Main -> 0
    QuickLauncherAddSubScreen.PickApp -> 1
    is QuickLauncherAddSubScreen.PickActivity -> 2
    is QuickLauncherAddSubScreen.ShellCommandConfig -> 1
}

private fun QuickLauncherAddSubScreen.contentKey(): Any = when (this) {
    QuickLauncherAddSubScreen.Main -> "main"
    QuickLauncherAddSubScreen.PickApp -> "pickApp"
    is QuickLauncherAddSubScreen.PickActivity -> "pickActivity:$packageName"
    is QuickLauncherAddSubScreen.ShellCommandConfig -> "shellConfig"
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
                missingIcon = Icons.AutoMirrored.Outlined.Shortcut,
            )
        },
        trailingMode = if (showAction) PickerTrailingMode.Toggle else PickerTrailingMode.None,
        onTrailingClick = if (showAction) onToggle else null,
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
                icon = gestureActionIcon(action, outlined = true),
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
    configuredActionKeys: Set<String>,
    onToggleItem: (QuickLauncherItem, Boolean) -> Unit,
    onOpenExecuteShellCommand: () -> Unit,
    modifier: Modifier,
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
        items(filtered.size, key = { filtered[it].packageName }) { index ->
            val app = filtered[index]
            val added = app.packageName in configuredAppPackages
            QuickLauncherToggleRow(
                entry = AppPackageEntry.Installed(app),
                segmentIndex = pickerSegmentIndex(index, filtered.size),
                segmentCount = pickerSegmentCount(filtered.size),
                added = added,
                onToggle = { onToggle(app, added) },
            )
        }
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
                    onToggle = {
                        if (!added) {
                            AppShortcutLoader.cacheShortcutForLaunch(group.app.packageName, shortcut)
                        }
                        onToggle(item, added)
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
