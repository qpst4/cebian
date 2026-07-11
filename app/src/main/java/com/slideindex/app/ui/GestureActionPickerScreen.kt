package com.slideindex.app.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.gesturepicker.ActionPickerAppsTab
import com.slideindex.app.ui.gesturepicker.ActionPickerActionsTab
import com.slideindex.app.ui.gesturepicker.ActionPickerShortcutsTab
import com.slideindex.app.ui.gesturepicker.ActionPickerTab
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.ShortcutScanPhase
import com.slideindex.app.util.ShortcutScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GestureActionPickerScreen(
    trigger: GestureTriggerType,
    current: GestureAction,
    onDismiss: () -> Unit,
    onSelect: (GestureAction) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var shortcutCatalog by remember { mutableStateOf<AppShortcutLoader.ShortcutCatalog?>(null) }
    var shortcutCatalogLoading by remember { mutableStateOf(true) }
    var scanProgress by remember { mutableStateOf<ShortcutScanProgress?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = true)
    }

    LaunchedEffect(allApps) {
        if (allApps.isEmpty()) {
            shortcutCatalogLoading = false
            scanProgress = null
            return@LaunchedEffect
        }
        shortcutCatalogLoading = true
        scanProgress = ShortcutScanProgress(ShortcutScanPhase.DUMPSYS, 0, 0)
        try {
            shortcutCatalog = withContext(Dispatchers.IO) {
                AppShortcutLoader.loadShortcutCatalog(
                    context = context,
                    apps = allApps,
                    includeShell = true,
                    onProgress = { progress ->
                        mainHandler.post { scanProgress = progress }
                    },
                )
            }
        } catch (_: Exception) {
            shortcutCatalog = AppShortcutLoader.ShortcutCatalog(createHosts = emptyList())
        } finally {
            shortcutCatalogLoading = false
            scanProgress = null
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { SettingsAppBarTitle(stringResource(R.string.slot_pick_action)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
            PrimaryTabRow(selectedTabIndex = selectedTab) {
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
            when (ActionPickerTab.entries[selectedTab]) {
                ActionPickerTab.ACTIONS -> ActionPickerActionsTab(
                    trigger = trigger,
                    current = current,
                    onSelect = onSelect,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    modifier = modifier,
                )
                ActionPickerTab.APPS -> ActionPickerAppsTab(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    apps = allApps,
                    current = current,
                    onSelect = { app -> onSelect(GestureAction.LaunchApp(app.packageName)) },
                    modifier = modifier,
                )
                ActionPickerTab.SHORTCUTS -> ActionPickerShortcutsTab(
                    apps = allApps,
                    catalog = shortcutCatalog,
                    loading = shortcutCatalogLoading,
                    scanProgress = scanProgress,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    current = current,
                    onSelect = onSelect,
                    modifier = modifier,
                )
            }
        }
    }
}
