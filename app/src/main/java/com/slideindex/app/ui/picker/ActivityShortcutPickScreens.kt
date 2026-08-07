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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
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
import com.slideindex.app.ui.Md3PickerAppLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerListHorizontalPadding
import com.slideindex.app.ui.PickerSearchListHeader
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.SearchBar
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.pickerListSegmentedGap
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.util.ExportedActivityInfo
import com.slideindex.app.util.PackageActivityResolver
import com.slideindex.app.util.PinyinHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ActivityShortcutPickAppScreen(
    onBack: () -> Unit,
    onSelectApp: (AppInfo) -> Unit,
    titleResId: Int = R.string.activity_shortcut_pick_app_title,
    selectedPackageName: String? = null,
    excludePackageNames: Set<String> = emptySet(),
    embedInParentChrome: Boolean = false,
) {
    val appRepository = rememberAppRepository()
    var apps by remember { mutableStateOf(appRepository.getCachedApps()) }
    var loading by remember { mutableStateOf(appRepository.getCachedApps().isEmpty()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (apps.isEmpty()) {
            loading = true
        }
        apps = appRepository.loadApps(force = apps.isEmpty())
        loading = false
    }

    val filtered = remember(apps, query, excludePackageNames) {
        appRepository.searchApps(apps, query)
            .filter { it.packageName !in excludePackageNames }
    }

    if (embedInParentChrome) {
        EmbeddedActivityPickAppList(
            query = query,
            onQueryChange = { query = it },
            loading = loading,
            filtered = filtered,
            selectedPackageName = selectedPackageName,
            onSelectApp = onSelectApp,
        )
        return
    }

    val emptyAppsText = stringResource(R.string.no_apps)
    SettingsLazyScreenScaffold(
        title = stringResource(titleResId),
        onBack = onBack,
    ) {
        item(key = "search") {
            PickerSearchListHeader(
                query = query,
                onQueryChange = { query = it },
                hintResId = R.string.notification_rule_app_search_hint,
            )
        }
        activityPickerAppListBody(
            loading = loading,
            filtered = filtered,
            selectedPackageName = selectedPackageName,
            onSelectApp = onSelectApp,
            emptyText = emptyAppsText,
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

    val packageAppInfo = remember(packageName, appLabel) {
        AppInfo(
            packageName = packageName,
            label = appLabel,
            letter = PinyinHelper.firstLetter(appLabel),
            pinyinKey = PinyinHelper.sortKey(appLabel),
        )
    }
    val notExportedLabel = stringResource(R.string.search_engine_activity_not_exported)

    if (embedInParentChrome) {
        EmbeddedActivityPickActivityList(
            query = query,
            onQueryChange = { query = it },
            loading = loading,
            filtered = filtered,
            packageAppInfo = packageAppInfo,
            notExportedLabel = notExportedLabel,
            selectedClassName = selectedClassName,
            onSelectActivity = onSelectActivity,
        )
        return
    }

    val emptyActivitiesText = stringResource(R.string.search_engine_activity_empty)
    SettingsLazyScreenScaffold(
        title = stringResource(R.string.search_engine_pick_activity_title),
        subtitle = appLabel,
        onBack = onBack,
    ) {
        item(key = "search") {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                hintResId = R.string.search_engine_activity_search_hint,
                modifier = Modifier.padding(horizontal = PickerListHorizontalPadding, vertical = 8.dp),
            )
        }
        activityPickerActivityListBody(
            loading = loading,
            filtered = filtered,
            packageAppInfo = packageAppInfo,
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
}

private fun LazyListScope.activityPickerActivityListBody(
    loading: Boolean,
    filtered: List<ExportedActivityInfo>,
    packageAppInfo: AppInfo,
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
                    leadingContent = { Md3PickerAppLeading(packageAppInfo) },
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
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PickerSearchListHeader(
            query = query,
            onQueryChange = onQueryChange,
            hintResId = R.string.notification_rule_app_search_hint,
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
                EmbeddedActivityPickerLazyList(itemCount = filtered.size) { index ->
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
    packageAppInfo: AppInfo,
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
                        leadingContent = { Md3PickerAppLeading(packageAppInfo) },
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
    }
}
