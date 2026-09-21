package com.slideindex.app.ui.picker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.ui.Md3PickerActivityLeading
import com.slideindex.app.ui.Md3PickerAppLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerListHorizontalPadding
import com.slideindex.app.ui.PickerSearchListHeader
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.SearchBar
import com.slideindex.app.ui.compose.collectLaunchableAppsAsState
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.pickerListSegmentedGap
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.util.ExportedActivityInfo
import com.slideindex.app.util.PackageActivityResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ActivityShortcutPickAppScreen(
    onBack: () -> Unit,
    onSelectApp: (AppInfo) -> Unit,
    titleResId: Int = R.string.activity_shortcut_pick_app_title,
    selectedPackageName: String? = null,
    excludePackageNames: Set<String> = emptySet(),
    embedInParentChrome: Boolean = false,
    enableBackHandler: Boolean = true,
    showSystemAppsOption: Boolean = true,
) {
    val appRepository = rememberAppRepository()
    val apps by collectLaunchableAppsAsState()
    val loading = apps.isEmpty()
    var query by remember { mutableStateOf("") }
    // 系统应用默认不显示：打开开关才枚举（仓库内每进程缓存一次），仅会话级，不落设置
    var showSystemApps by remember { mutableStateOf(false) }
    var systemApps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var loadingSystemApps by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (apps.isEmpty()) {
            appRepository.loadApps()
        }
    }

    LaunchedEffect(showSystemApps) {
        if (showSystemApps && systemApps == null) {
            loadingSystemApps = true
            try {
                systemApps = appRepository.loadActivityTargetApps()
            } finally {
                // 开关快速来回切会取消本次加载，这里要保证指示器不会卡住
                loadingSystemApps = false
            }
        }
    }

    val candidateApps = remember(apps, systemApps, showSystemApps) {
        if (showSystemApps) {
            appRepository.mergeActivityTargets(apps, systemApps.orEmpty())
        } else {
            apps
        }
    }

    val filtered = remember(candidateApps, query, excludePackageNames) {
        val matched = appRepository.searchApps(candidateApps, query)
            .filter { it.packageName !in excludePackageNames }
        // 空查询没有相关度可依，统一按「首字母 → 拼音」排序；有查询保留相关度排序
        if (query.isBlank()) appRepository.sortedByLetter(matched) else matched
    }

    val systemAppsLoading = showSystemApps && loadingSystemApps

    if (embedInParentChrome) {
        EmbeddedActivityPickAppList(
            query = query,
            onQueryChange = { query = it },
            loading = loading,
            filtered = filtered,
            selectedPackageName = selectedPackageName,
            onSelectApp = onSelectApp,
            showSystemAppsOption = showSystemAppsOption,
            showSystemApps = showSystemApps,
            onToggleSystemApps = { showSystemApps = !showSystemApps },
            systemAppsLoading = systemAppsLoading,
        )
        return
    }

    val emptyAppsText = stringResource(R.string.no_apps)
    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(titleResId),
        pageHint = stringResource(R.string.activity_shortcut_pick_app_desc),
        searchQuery = query,
        onSearchQueryChange = { query = it },
        onBack = onBack,
        enableBackHandler = enableBackHandler,
        hintResId = R.string.notification_rule_app_search_hint,
        extraActions = {
            if (showSystemAppsOption) {
                SystemAppsMenuAction(
                    showSystemApps = showSystemApps,
                    onToggle = { showSystemApps = !showSystemApps },
                )
            }
        },
    ) {
        activityPickerAppListBody(
            loading = loading,
            filtered = filtered,
            selectedPackageName = selectedPackageName,
            onSelectApp = onSelectApp,
            emptyText = emptyAppsText,
            systemAppsLoading = systemAppsLoading,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ActivityShortcutPickActivityScreen(
    packageName: String,
    onBack: () -> Unit,
    onSelectActivity: (ExportedActivityInfo) -> Unit,
    selectedClassName: String? = null,
    embedInParentChrome: Boolean = false,
    enableBackHandler: Boolean = true,
) {
    val context = LocalContext.current
    var activities by remember(packageName) { mutableStateOf<List<ExportedActivityInfo>>(emptyList()) }
    var loading by remember(packageName) { mutableStateOf(true) }
    var query by remember(packageName) { mutableStateOf("") }
    var appLabel by remember(packageName) { mutableStateOf(packageName) }

    LaunchedEffect(packageName) {
        loading = true
        val result = withContext(Dispatchers.IO) {
            val loadedActivities = PackageActivityResolver.listActivities(context, packageName)
            val label = runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            }.getOrDefault(packageName)
            loadedActivities to label
        }
        activities = result.first
        appLabel = result.second
        loading = false
    }

    val filtered = remember(activities, query) {
        PackageActivityResolver.searchActivities(activities, query)
    }

    val notExportedLabel = stringResource(R.string.search_engine_activity_not_exported)

    if (embedInParentChrome) {
        EmbeddedActivityPickActivityList(
            query = query,
            onQueryChange = { query = it },
            loading = loading,
            filtered = filtered,
            notExportedLabel = notExportedLabel,
            selectedClassName = selectedClassName,
            onSelectActivity = onSelectActivity,
        )
        return
    }

    val emptyActivitiesText = stringResource(R.string.search_engine_activity_empty)
    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.search_engine_pick_activity_title),
        subtitle = appLabel,
        searchQuery = query,
        onSearchQueryChange = { query = it },
        onBack = onBack,
        enableBackHandler = enableBackHandler,
        hintResId = R.string.search_engine_activity_search_hint,
    ) {
        activityPickerActivityListBody(
            loading = loading,
            filtered = filtered,
            notExportedLabel = notExportedLabel,
            selectedClassName = selectedClassName,
            onSelectActivity = onSelectActivity,
            emptyText = emptyActivitiesText,
        )
    }
}

private fun LazyListScope.activityPickerAppListBody(
    loading: Boolean,
    filtered: List<AppInfo>,
    selectedPackageName: String?,
    onSelectApp: (AppInfo) -> Unit,
    emptyText: String,
    systemAppsLoading: Boolean = false,
) {
    when {
        loading -> activityPickerLoadingItem()
        filtered.isEmpty() -> activityPickerEmptyItem(emptyText)
        else -> {
            val segmentCount = filtered.size
            itemsIndexed(
                items = filtered,
                key = { _, app -> app.packageName },
            ) { index, app ->
                val selected = selectedPackageName != null && app.packageName == selectedPackageName
                Md3PickerListRow(
                    segmentIndex = index,
                    segmentCount = segmentCount,
                    title = app.label,
                    subtitle = app.packageName,
                    selected = selected,
                    onClick = { onSelectApp(app) },
                    leadingContent = { Md3PickerAppLeading(app) },
                    trailingMode = if (selectedPackageName != null) {
                        PickerTrailingMode.Radio
                    } else {
                        PickerTrailingMode.None
                    },
                )
            }
        }
    }
    if (systemAppsLoading && !loading) activityPickerSystemAppsLoadingItem()
}

private fun LazyListScope.activityPickerActivityListBody(
    loading: Boolean,
    filtered: List<ExportedActivityInfo>,
    notExportedLabel: String,
    selectedClassName: String?,
    onSelectActivity: (ExportedActivityInfo) -> Unit,
    emptyText: String,
) {
    when {
        loading -> activityPickerLoadingItem()
        filtered.isEmpty() -> activityPickerEmptyItem(emptyText)
        else -> {
            val segmentCount = filtered.size
            itemsIndexed(
                items = filtered,
                key = { _, activity -> activity.className },
            ) { index, activity ->
                val selected = selectedClassName != null && activity.className == selectedClassName
                val subtitle = if (activity.exported) {
                    activity.className
                } else {
                    "${activity.className} · $notExportedLabel"
                }
                Md3PickerListRow(
                    segmentIndex = index,
                    segmentCount = segmentCount,
                    title = activity.label,
                    subtitle = subtitle,
                    selected = selected,
                    onClick = { onSelectActivity(activity) },
                    leadingContent = {
                        Md3PickerActivityLeading(
                            packageName = activity.packageName,
                            className = activity.className,
                            contentDescription = activity.label,
                        )
                    },
                    trailingMode = if (selectedClassName != null) {
                        PickerTrailingMode.Radio
                    } else {
                        PickerTrailingMode.None
                    },
                )
            }
        }
    }
}

private fun LazyListScope.activityPickerLoadingItem() {
    item(key = "loading") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

private fun LazyListScope.activityPickerSystemAppsLoadingItem() {
    item(key = "system-apps-loading") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }
    }
}

private fun LazyListScope.activityPickerEmptyItem(text: String) {
    item(key = "empty") {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun EmbeddedActivityPickAppList(
    query: String,
    onQueryChange: (String) -> Unit,
    loading: Boolean,
    filtered: List<AppInfo>,
    selectedPackageName: String?,
    onSelectApp: (AppInfo) -> Unit,
    showSystemAppsOption: Boolean,
    showSystemApps: Boolean,
    onToggleSystemApps: () -> Unit,
    systemAppsLoading: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PickerSearchListHeader(
            query = query,
            onQueryChange = onQueryChange,
            hintResId = R.string.notification_rule_app_search_hint,
            trailing = if (showSystemAppsOption) {
                {
                    SystemAppsMenuAction(
                        showSystemApps = showSystemApps,
                        onToggle = onToggleSystemApps,
                    )
                }
            } else {
                null
            },
        )
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            filtered.isEmpty() -> {
                Text(
                    text = stringResource(R.string.no_apps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            else -> {
                EmbeddedActivityPickerLazyList(
                    itemCount = filtered.size,
                    footer = if (systemAppsLoading && !loading) {
                        { activityPickerSystemAppsLoadingItem() }
                    } else {
                        null
                    },
                ) { index ->
                    val app = filtered[index]
                    val selected = selectedPackageName != null &&
                        app.packageName == selectedPackageName
                    Md3PickerListRow(
                        segmentIndex = index,
                        segmentCount = filtered.size,
                        title = app.label,
                        subtitle = app.packageName,
                        selected = selected,
                        onClick = { onSelectApp(app) },
                        leadingContent = { Md3PickerAppLeading(app) },
                        trailingMode = if (selectedPackageName != null) {
                            PickerTrailingMode.Radio
                        } else {
                            PickerTrailingMode.None
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun EmbeddedActivityPickActivityList(
    query: String,
    onQueryChange: (String) -> Unit,
    loading: Boolean,
    filtered: List<ExportedActivityInfo>,
    notExportedLabel: String,
    selectedClassName: String?,
    onSelectActivity: (ExportedActivityInfo) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            hintResId = R.string.search_engine_activity_search_hint,
            modifier = Modifier.padding(horizontal = PickerListHorizontalPadding, vertical = 8.dp),
        )
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            filtered.isEmpty() -> {
                Text(
                    text = stringResource(R.string.search_engine_activity_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            else -> {
                EmbeddedActivityPickerLazyList(
                    itemCount = filtered.size,
                    itemKey = { filtered[it].className },
                ) { index ->
                    val activity = filtered[index]
                    val selected = selectedClassName != null &&
                        activity.className == selectedClassName
                    val subtitle = if (activity.exported) {
                        activity.className
                    } else {
                        "${activity.className} · $notExportedLabel"
                    }
                    Md3PickerListRow(
                        segmentIndex = index,
                        segmentCount = filtered.size,
                        title = activity.label,
                        subtitle = subtitle,
                        selected = selected,
                        onClick = { onSelectActivity(activity) },
                        leadingContent = {
                            Md3PickerActivityLeading(
                                packageName = activity.packageName,
                                className = activity.className,
                                contentDescription = activity.label,
                            )
                        },
                        trailingMode = if (selectedClassName != null) {
                            PickerTrailingMode.Radio
                        } else {
                            PickerTrailingMode.None
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.EmbeddedActivityPickerLazyList(
    itemCount: Int,
    itemKey: (Int) -> Any = { it },
    footer: (LazyListScope.() -> Unit)? = null,
    itemContent: @Composable (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .selectableGroup(),
        contentPadding = PaddingValues(
            start = PickerListHorizontalPadding,
            end = PickerListHorizontalPadding,
            bottom = 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
    ) {
        items(
            count = itemCount,
            key = itemKey,
            contentType = { "picker_row" },
        ) { index ->
            itemContent(index)
        }
        footer?.invoke(this)
    }
}

/**
 * 顶栏/内嵌头部右上的「显示系统应用」菜单，与分应用代理页一致的入口形态。
 * 浮层内嵌时也用它——Miuix 的 OverlayIconDropdownMenu 在 WindowManager 浮层里可用
 * （冷冻库浮层就是同一用法）。
 */
@Composable
private fun SystemAppsMenuAction(
    showSystemApps: Boolean,
    onToggle: () -> Unit,
) {
    val showLabel = stringResource(R.string.app_picker_show_system_apps)
    val hideLabel = stringResource(R.string.app_picker_hide_system_apps)
    val entry = remember(showSystemApps, showLabel, hideLabel) {
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = if (showSystemApps) hideLabel else showLabel,
                    selected = showSystemApps,
                    onClick = onToggle,
                ),
            ),
        )
    }
    OverlayIconDropdownMenu(entry = entry) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.app_picker_more_menu),
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}
