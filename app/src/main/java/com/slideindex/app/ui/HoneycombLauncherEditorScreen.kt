package com.slideindex.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.pickerHorizontalSlideTransitionByDepth
import com.slideindex.app.ui.quicklauncher.QuickLauncherEditorAddPicker
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold

private sealed class HoneycombEditorMode {
    data object Main : HoneycombEditorMode()
    data object AddPicker : HoneycombEditorMode()
    data object PickApp : HoneycombEditorMode()
    data class PickActivity(val packageName: String) : HoneycombEditorMode()
}

private fun HoneycombEditorMode.navDepth(): Int = when (this) {
    HoneycombEditorMode.Main -> 0
    HoneycombEditorMode.AddPicker -> 1
    HoneycombEditorMode.PickApp -> 2
    is HoneycombEditorMode.PickActivity -> 3
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HoneycombLauncherEditorScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSaveItems: (List<QuickLauncherItem>) -> Unit,
    onOpenDisplaySettings: () -> Unit,
) {
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf(appRepository.getCachedApps()) }
    var mode by remember { mutableStateOf<HoneycombEditorMode>(HoneycombEditorMode.Main) }
    var searchQuery by remember { mutableStateOf("") }
    val currentItems = settings.honeycombLauncher
    var items by remember(currentItems) { mutableStateOf(currentItems) }
    val noOpNestedScroll = remember { object : NestedScrollConnection {} }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = false)
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

    fun saveAndBack() {
        onSaveItems(items)
        onBack()
    }

    fun addItem(item: QuickLauncherItem) {
        if (item.type != QuickLauncherItemType.APP && item.type != QuickLauncherItemType.SHORTCUT) return
        items = items + item
    }

    fun removeItem(item: QuickLauncherItem) {
        items = when (item.type) {
            QuickLauncherItemType.APP ->
                items.filterNot { it.type == QuickLauncherItemType.APP && it.payload == item.payload }
            QuickLauncherItemType.SHORTCUT -> {
                val key = QuickLauncherItemCodec.shortcutItemKey(item) ?: return
                items.filterNot {
                    it.type == QuickLauncherItemType.SHORTCUT &&
                        QuickLauncherItemCodec.shortcutItemKey(it) == key
                }
            }
            else -> items
        }
    }

    fun toggleItem(item: QuickLauncherItem, added: Boolean) {
        if (added) removeItem(item) else addItem(item)
    }

    AnimatedContent(
        targetState = mode,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { pickerHorizontalSlideTransitionByDepth(HoneycombEditorMode::navDepth) },
        label = "honeycombLauncherEditorSubNav",
    ) { currentMode ->
        when (currentMode) {
            HoneycombEditorMode.PickApp -> {
                ActivityShortcutPickAppScreen(
                    onBack = { mode = HoneycombEditorMode.AddPicker },
                    onSelectApp = { app -> mode = HoneycombEditorMode.PickActivity(app.packageName) },
                )
            }
            is HoneycombEditorMode.PickActivity -> {
                ActivityShortcutPickActivityScreen(
                    packageName = currentMode.packageName,
                    onBack = { mode = HoneycombEditorMode.PickApp },
                    onSelectActivity = { activity ->
                        addItem(
                            QuickLauncherItem.shortcut(
                                "${activity.packageName}/${activity.className}",
                                activity.label,
                            ),
                        )
                        mode = HoneycombEditorMode.AddPicker
                    },
                )
            }
            HoneycombEditorMode.Main,
            HoneycombEditorMode.AddPicker,
            -> {
                val title = when (currentMode) {
                    HoneycombEditorMode.AddPicker -> stringResource(R.string.honeycomb_launcher_add)
                    else -> stringResource(R.string.honeycomb_launcher_editor_title)
                }
                SettingsScreenScaffold(
                    title = title,
                    onBack = {
                        when (currentMode) {
                            HoneycombEditorMode.Main -> saveAndBack()
                            HoneycombEditorMode.AddPicker -> {
                                mode = HoneycombEditorMode.Main
                                searchQuery = ""
                            }
                            HoneycombEditorMode.PickApp,
                            is HoneycombEditorMode.PickActivity,
                            -> Unit
                        }
                    },
                    scrollContent = false,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (currentMode) {
                        HoneycombEditorMode.Main -> Column(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            SettingsCard(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                SettingNavigationRow(
                                    icon = { label ->
                                        Icon(Icons.Default.Tune, contentDescription = label)
                                    },
                                    title = stringResource(R.string.honeycomb_display_settings_entry),
                                    subtitle = stringResource(R.string.honeycomb_display_settings_entry_desc),
                                    onClick = onOpenDisplaySettings,
                                )
                            }
                            HoneycombLauncherItemsSection(
                                modifier = Modifier.weight(1f),
                                items = items,
                                display = settings.honeycombDisplay,
                                appsByPackage = appsByPackage,
                                onItemsChange = { items = it },
                                onAdd = {
                                    searchQuery = ""
                                    mode = HoneycombEditorMode.AddPicker
                                },
                                onInteractionActiveChange = {},
                            )
                        }
                        HoneycombEditorMode.AddPicker -> QuickLauncherEditorAddPicker(
                            padding = PaddingValues(0.dp),
                            nestedScrollConnection = noOpNestedScroll,
                            apps = allApps,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            configuredAppPackages = configuredAppPackages,
                            configuredShortcutKeys = configuredShortcutKeys,
                            configuredActionKeys = emptySet(),
                            activityShortcuts = settings.activityShortcuts,
                            onToggleAction = { _, _, _ -> },
                            onToggleApp = { app, added ->
                                toggleItem(QuickLauncherItem.app(app.packageName, app.label), added)
                            },
                            onToggleShortcut = { app, shortcut, added ->
                                toggleItem(shortcut.toQuickLauncherItem(app.packageName), added)
                            },
                            onToggleActivityShortcut = { item, added -> toggleItem(item, added) },
                            onCreatedShortcut = { created ->
                                addItem(created.toQuickLauncherItem())
                            },
                            onBrowseActivityShortcut = { mode = HoneycombEditorMode.PickApp },
                            includeActionsTab = false,
                        )
                        HoneycombEditorMode.PickApp,
                        is HoneycombEditorMode.PickActivity,
                        -> Unit
                    }
                }
            }
        }
    }
}
