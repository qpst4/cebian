package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.backtap.BackTapMode
import com.slideindex.app.data.AppInfo
import com.slideindex.app.freezer.FreezerBootstrap
import com.slideindex.app.freezer.FreezerOperations
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.gesturepicker.gestureActionLabelText
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixTabRowContourHost
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.xposed.config.XposedConfigWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator

private enum class FreezerTab { ALL, FROZEN, ACTIVE }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FreezerAppsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = settingsRepository.readSnapshot(),
    )
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(FreezerTab.ALL.ordinal) }
    var showSystemApps by remember { mutableStateOf(false) }
    var rootAvailable by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    val shizukuGranted = remember { TaskManagerUtil.hasPermission() }

    LaunchedEffect(Unit) {
        isLoadingApps = true
        allApps = appRepository.loadFreezerApps(force = true)
        val bootstrap = FreezerBootstrap.scanDisabledLauncherPackages(context)
        if (bootstrap.isNotEmpty()) {
            val current = settingsRepository.readSnapshot().freezerAppPackages
            val merged = current + bootstrap
            if (merged != current) {
                settingsRepository.setFreezerAppPackages(merged)
            }
        }
        rootAvailable = withContext(Dispatchers.IO) { TaskManagerUtil.probeRootAvailable() }
        isLoadingApps = false
    }

    fun isFrozenApp(app: AppInfo): Boolean =
        settings.freezerAppPackages.contains(app.packageName) ||
            FreezerOperations.isFrozen(context, app.packageName)

    val filteredApps = remember(allApps, searchQuery, selectedTab, showSystemApps, settings) {
        val query = searchQuery.trim().lowercase()
        allApps.filter { app ->
            (showSystemApps || !app.isSystem) &&
                (query.isBlank() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true) ||
                    PinyinHelper.sortKey(app.label).contains(query)) &&
                when (FreezerTab.entries[selectedTab]) {
                    FreezerTab.ALL -> true
                    FreezerTab.FROZEN -> isFrozenApp(app)
                    FreezerTab.ACTIVE -> !isFrozenApp(app)
                }
        }
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.extension_freezer_title),
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack,
    ) {
        item(key = "freezer-hint") {
            MiuixHintText(
                buildString {
                    append(
                        if (shizukuGranted) {
                            stringResource(R.string.freezer_shizuku_granted)
                        } else {
                            stringResource(R.string.freezer_shizuku_denied)
                        },
                    )
                    rootAvailable?.let { root ->
                        append('\n')
                        append(
                            if (root) {
                                stringResource(R.string.freezer_root_available)
                            } else {
                                stringResource(R.string.freezer_root_unavailable)
                            },
                        )
                    }
                },
            )
        }
        groupedCardItems(
            keyPrefix = "freezer-options",
            items = listOf(
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
        item(key = "freezer-tabs") {
            MiuixTabRowWithContour(
                tabs = listOf(
                    stringResource(R.string.freezer_tab_all),
                    stringResource(R.string.freezer_tab_frozen),
                    stringResource(R.string.freezer_tab_active),
                ),
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                contourHost = MiuixTabRowContourHost.AppScaffold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        item(key = "freezer-apps-title") {
            MiuixSmallTitle(
                stringResource(R.string.freezer_apps_section_title, filteredApps.size),
            )
        }
        if (isLoadingApps) {
            item(key = "freezer-loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                }
            }
        } else if (filteredApps.isEmpty()) {
            item(key = "freezer-empty") {
                Text(
                    text = stringResource(R.string.no_apps),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                )
            }
        } else {
            items(filteredApps.size, key = { filteredApps[it].packageName }) { index ->
                val app = filteredApps[index]
                val frozen = isFrozenApp(app)
                Md3PickerListRow(
                    segmentIndex = index,
                    segmentCount = filteredApps.size,
                    title = app.label,
                    subtitle = buildString {
                        append(app.packageName)
                        if (app.isSystem) {
                            append(" · ")
                            append(stringResource(R.string.freezer_system_app_label))
                        }
                    },
                    selected = frozen,
                    onClick = {
                        scope.launch {
                            val next = !frozen
                            if (FreezerOperations.setFrozen(context, app.packageName, next)) {
                                val updated = settings.freezerAppPackages.toMutableSet()
                                if (next) updated += app.packageName else updated -= app.packageName
                                settingsRepository.setFreezerAppPackages(updated)
                            }
                        }
                    },
                    leadingContent = {
                        Md3PickerAppEntryLeading(
                            entry = AppPackageEntry.Installed(app),
                            missingIcon = Icons.Default.TouchApp,
                        )
                    },
                    trailingMode = PickerTrailingMode.Toggle,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HideRecentAppsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = settingsRepository.readSnapshot(),
    )
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { allApps = appRepository.loadApps(force = true) }
    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) allApps else {
            allApps.filter { it.label.contains(searchQuery, ignoreCase = true) }
        }
    }
    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.extension_hide_recent_title),
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack,
    ) {
        item {
            Text(
                text = stringResource(R.string.hide_recent_restart_hint),
                modifier = Modifier.padding(16.dp),
            )
        }
        items(filteredApps, key = { it.packageName }) { app ->
            val hideTask = settings.hideRecentTaskPackages.contains(app.packageName)
            val hidePreview = settings.hideRecentPreviewPackages.contains(app.packageName)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(text = app.label, modifier = Modifier.weight(1f))
                Switch(
                    checked = hideTask,
                    onCheckedChange = { checked ->
                        scope.launch {
                            val updated = settings.hideRecentTaskPackages.toMutableSet()
                            if (checked) updated += app.packageName else updated -= app.packageName
                            settingsRepository.setHideRecentTaskPackages(updated)
                            XposedConfigWriter.writeHideRecent(context, updated, settings.hideRecentPreviewPackages)
                        }
                    },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.hide_recent_preview_mode),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = hidePreview,
                    onCheckedChange = { checked ->
                        scope.launch {
                            val updated = settings.hideRecentPreviewPackages.toMutableSet()
                            if (checked) updated += app.packageName else updated -= app.packageName
                            settingsRepository.setHideRecentPreviewPackages(updated)
                            XposedConfigWriter.writeHideRecent(context, settings.hideRecentTaskPackages, updated)
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackTapSettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onOpenActionPick: () -> Unit,
) {
    val context = LocalContext.current
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = settingsRepository.readSnapshot(),
    )
    val backTap = settings.backTapSettings
    val scope = rememberCoroutineScope()

    SettingsScreenScaffold(
        title = stringResource(R.string.extension_back_tap_title),
        onBack = onBack,
    ) {
        groupedCardItems(
            keyPrefix = "back-tap-main",
            items = buildList {
                add(
                    settingsCardScopeItem("enabled") {
                        SettingSwitchRow(
                            title = stringResource(R.string.back_tap_enabled),
                            subtitle = stringResource(R.string.extension_back_tap_subtitle),
                            icon = { label -> Icon(Icons.Default.TouchApp, contentDescription = label) },
                            checked = backTap.enabled,
                            enabled = true,
                            onCheckedChange = { scope.launch { settingsRepository.setBackTapEnabled(it) } },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("action") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Default.TouchApp, contentDescription = label) },
                            title = stringResource(R.string.back_tap_action),
                            subtitle = gestureActionLabelText(context, backTap.action),
                            onClick = onOpenActionPick,
                        )
                    },
                )
            },
        )
        groupedCardItems(
            keyPrefix = "back-tap-tuning",
            items = buildList {
                add(
                    settingsCardScopeItem("sensitivity") {
                        SettingsSliderRow(
                            title = stringResource(R.string.back_tap_sensitivity),
                            value = backTap.sensitivity.toFloat(),
                            valueRange = 1f..10f,
                            steps = 8,
                            enabled = backTap.enabled,
                            label = backTap.sensitivity.toString(),
                            onValueChange = { scope.launch { settingsRepository.setBackTapSensitivity(it.toInt()) } },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("range") {
                        SettingsSliderRow(
                            title = stringResource(R.string.back_tap_range),
                            value = backTap.range.toFloat(),
                            valueRange = 1f..10f,
                            steps = 8,
                            enabled = backTap.enabled,
                            label = backTap.range.toString(),
                            onValueChange = { scope.launch { settingsRepository.setBackTapRange(it.toInt()) } },
                        )
                    },
                )
            },
        )
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(text = stringResource(R.string.back_tap_mode), modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BackTapMode.entries.forEach { mode ->
                    FilterChip(
                        selected = backTap.mode == mode,
                        onClick = { scope.launch { settingsRepository.setBackTapMode(mode) } },
                        enabled = backTap.enabled,
                        label = {
                            Text(
                                when (mode) {
                                    BackTapMode.ALWAYS -> stringResource(R.string.back_tap_mode_always)
                                    BackTapMode.SCREEN_ON -> stringResource(R.string.back_tap_mode_screen_on)
                                    BackTapMode.SCREEN_OFF -> stringResource(R.string.back_tap_mode_screen_off)
                                },
                            )
                        },
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(text = stringResource(R.string.back_tap_vibration_feedback), modifier = Modifier.weight(1f))
                Switch(
                    checked = backTap.vibrationFeedbackEnabled,
                    enabled = backTap.enabled,
                    onCheckedChange = { scope.launch { settingsRepository.setBackTapVibrationFeedbackEnabled(it) } },
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(text = stringResource(R.string.back_tap_pause_charging), modifier = Modifier.weight(1f))
                Switch(
                    checked = backTap.pauseWhileCharging,
                    enabled = backTap.enabled,
                    onCheckedChange = { scope.launch { settingsRepository.setBackTapPauseWhileCharging(it) } },
                )
            }
        }
    }
}
