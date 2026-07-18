package com.slideindex.app.ui.searchengine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
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
import com.slideindex.app.ui.Md3PickerDrawableLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.SearchBar
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.pickerListSegmentedGap
import com.slideindex.app.search.ShareImageTarget
import com.slideindex.app.search.ShareImageTargetResolver
import com.slideindex.app.util.ExportedActivityInfo
import com.slideindex.app.util.PackageActivityResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SearchEngineAppPickerDialog(
    initialPackageName: String,
    onDismiss: () -> Unit,
    onSelect: (AppInfo) -> Unit,
    titleResId: Int = R.string.search_engine_pick_app_title,
) {
    val appRepository = rememberAppRepository()
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        loading = true
        apps = appRepository.loadApps(force = false)
        loading = false
    }
    val filtered = remember(apps, query) {
        appRepository.searchApps(apps, query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleResId)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    hintResId = R.string.notification_rule_app_search_hint,
                )
                when {
                    loading -> PickerLoadingState()
                    filtered.isEmpty() -> PickerEmptyState(stringResource(R.string.no_apps))
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp),
                            verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
                        ) {
                            itemsIndexed(
                                items = filtered,
                                key = { _, app -> app.packageName },
                            ) { index, appInfo ->
                                val selected = appInfo.packageName == initialPackageName
                                Md3PickerListRow(
                                    segmentIndex = index,
                                    segmentCount = filtered.size,
                                    title = appInfo.label,
                                    subtitle = appInfo.packageName,
                                    selected = selected,
                                    onClick = { onSelect(appInfo) },
                                    leadingContent = { Md3PickerAppLeading(appInfo) },
                                    trailingMode = PickerTrailingMode.Radio,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun ShareImageTargetPickerDialog(
    initialPackageName: String,
    initialActivityClassName: String,
    onDismiss: () -> Unit,
    onSelect: (ShareImageTarget) -> Unit,
) {
    val context = LocalContext.current
    var targets by remember { mutableStateOf<List<ShareImageTarget>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        loading = true
        targets = withContext(Dispatchers.IO) {
            ShareImageTargetResolver.listTargets(context)
        }
        loading = false
    }
    val filtered = remember(targets, query) {
        ShareImageTargetResolver.searchTargets(targets, query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_engine_pick_share_image_target_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    hintResId = R.string.search_engine_share_image_target_search_hint,
                )
                when {
                    loading -> PickerLoadingState()
                    filtered.isEmpty() -> PickerEmptyState(
                        stringResource(R.string.search_engine_share_image_target_empty),
                    )
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp),
                            verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
                        ) {
                            itemsIndexed(
                                items = filtered,
                                key = { _, target -> "${target.packageName}/${target.activityClassName}" },
                            ) { index, target ->
                                val selected = target.packageName == initialPackageName &&
                                    target.activityClassName == initialActivityClassName
                                Md3PickerListRow(
                                    segmentIndex = index,
                                    segmentCount = filtered.size,
                                    title = target.label,
                                    subtitle = ShareImageTargetResolver.displaySubtitle(target),
                                    selected = selected,
                                    onClick = { onSelect(target) },
                                    leadingContent = {
                                        val pm = context.packageManager
                                        val drawable = target.icon ?: runCatching {
                                            pm.getApplicationIcon(target.packageName)
                                        }.getOrNull()
                                        if (drawable != null) {
                                            Md3PickerDrawableLeading(
                                                drawable = drawable,
                                                contentDescription = target.label,
                                                cacheKey = target.activityClassName,
                                            )
                                        }
                                    },
                                    trailingMode = PickerTrailingMode.Radio,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun SearchEngineActivityPickerDialog(
    packageName: String,
    initialClassName: String,
    onDismiss: () -> Unit,
    onSelect: (ExportedActivityInfo) -> Unit,
) {
    val context = LocalContext.current
    var activities by remember(packageName) { mutableStateOf<List<ExportedActivityInfo>>(emptyList()) }
    var loading by remember(packageName) { mutableStateOf(true) }
    var query by remember(packageName) { mutableStateOf("") }
    LaunchedEffect(packageName) {
        loading = true
        activities = withContext(Dispatchers.IO) {
            PackageActivityResolver.listActivities(context, packageName)
        }
        loading = false
    }
    val filtered = remember(activities, query) {
        PackageActivityResolver.searchActivities(activities, query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_engine_pick_activity_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    hintResId = R.string.search_engine_activity_search_hint,
                )
                when {
                    loading -> PickerLoadingState()
                    filtered.isEmpty() -> PickerEmptyState(stringResource(R.string.search_engine_activity_empty))
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp),
                            verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
                        ) {
                            itemsIndexed(
                                items = filtered,
                                key = { _, activity -> activity.className },
                            ) { index, activity ->
                                val selected = activity.className == initialClassName
                                Md3PickerListRow(
                                    segmentIndex = index,
                                    segmentCount = filtered.size,
                                    title = activity.label,
                                    selected = selected,
                                    onClick = { onSelect(activity) },
                                    leadingContent = {
                                        Md3PickerDrawableLeading(
                                            drawable = activity.icon,
                                            contentDescription = activity.label,
                                            cacheKey = activity.className,
                                        )
                                    },
                                    supportingContent = {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = activity.className,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (!activity.exported) {
                                                AssistChip(
                                                    onClick = {},
                                                    enabled = false,
                                                    label = {
                                                        Text(
                                                            stringResource(
                                                                R.string.search_engine_activity_not_exported,
                                                            ),
                                                        )
                                                    },
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        disabledContainerColor =
                                                            MaterialTheme.colorScheme.secondaryContainer,
                                                        disabledLabelColor =
                                                            MaterialTheme.colorScheme.onSecondaryContainer,
                                                    ),
                                                )
                                            }
                                        }
                                    },
                                    trailingMode = PickerTrailingMode.Radio,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun PickerLoadingState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PickerEmptyState(message: String) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .padding(vertical = 24.dp),
    )
}
