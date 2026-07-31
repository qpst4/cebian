package com.slideindex.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.launcher.HoneycombLauncherDefaults
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.quicklauncher.QuickLauncherEditorAddPicker
import com.slideindex.app.ui.quicklauncher.QuickLauncherEditorMainSection

private sealed class HoneycombEditorMode {
    data object Main : HoneycombEditorMode()
    data object AddPicker : HoneycombEditorMode()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HoneycombLauncherEditorScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSaveItems: (List<QuickLauncherItem>) -> Unit,
) {
    val context = LocalContext.current
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf(appRepository.getCachedApps()) }
    var mode by remember { mutableStateOf<HoneycombEditorMode>(HoneycombEditorMode.Main) }
    var searchQuery by remember { mutableStateOf("") }
    val currentItems = settings.honeycombLauncher
    var items by remember(currentItems) { mutableStateOf(currentItems) }
    var gridInteractionActive by remember { mutableStateOf(false) }

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
        if (items.size >= HoneycombLauncherDefaults.MAX_ITEMS) return
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    SettingsAppBarTitle(
                        when (mode) {
                            HoneycombEditorMode.AddPicker -> stringResource(R.string.honeycomb_launcher_add)
                            else -> stringResource(R.string.honeycomb_launcher_editor_title)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (mode) {
                            HoneycombEditorMode.Main -> saveAndBack()
                            HoneycombEditorMode.AddPicker -> {
                                mode = HoneycombEditorMode.Main
                                searchQuery = ""
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_navigate_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when (mode) {
            HoneycombEditorMode.Main -> QuickLauncherEditorMainSection(
                padding = padding,
                settings = settings,
                items = items,
                appsByPackage = appsByPackage,
                gridInteractionActive = gridInteractionActive,
                onColumnsChange = {},
                onRowsChange = {},
                onItemsChange = { items = it },
                onAdd = {
                    if (items.size < HoneycombLauncherDefaults.MAX_ITEMS) {
                        searchQuery = ""
                        mode = HoneycombEditorMode.AddPicker
                    }
                },
                onInteractionActiveChange = { gridInteractionActive = it },
                descriptionResId = R.string.honeycomb_launcher_editor_desc,
                showLayoutSettings = false,
            )
            HoneycombEditorMode.AddPicker -> QuickLauncherEditorAddPicker(
                padding = padding,
                nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                apps = allApps,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                configuredAppPackages = configuredAppPackages,
                configuredShortcutKeys = configuredShortcutKeys,
                configuredActionKeys = emptySet(),
                onToggleAction = { _, _, _ -> },
                onToggleApp = { app, added ->
                    toggleItem(QuickLauncherItem.app(app.packageName, app.label), added)
                },
                onToggleShortcut = { app, shortcut, added ->
                    toggleItem(shortcut.toQuickLauncherItem(app.packageName), added)
                },
                onCreatedShortcut = { created ->
                    addItem(created.toQuickLauncherItem())
                },
                includeActionsTab = false,
            )
        }
    }
}
