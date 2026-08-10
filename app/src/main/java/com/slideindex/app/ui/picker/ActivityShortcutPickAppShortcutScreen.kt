package com.slideindex.app.ui.picker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.activityShortcutFromQuickLauncherItem
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerSearchListHeader
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActivityShortcutPickAppShortcutScreen(
    existingIdentityKeys: Set<String>,
    onBack: () -> Unit,
    onAddShortcut: (ActivityShortcut) -> Unit,
) {
    val appRepository = rememberAppRepository()
    var apps by remember { mutableStateOf(appRepository.getCachedApps()) }
    var query by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (apps.isEmpty()) {
            apps = appRepository.loadApps(force = true)
        }
    }

    val loadedCatalog = rememberLoadedShortcutCatalog(apps = apps, enabled = true)
    val filtered = remember(loadedCatalog.catalog, query) {
        filterShortcutCatalog(
            loadedCatalog.catalog ?: AppShortcutLoader.ShortcutCatalog(emptyList()),
            query,
        )
    }

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.activity_shortcut_pick_app_shortcut_title),
        onBack = onBack,
    ) {
        item(key = "search") {
            PickerSearchListHeader(
                query = query,
                onQueryChange = { query = it },
            )
        }
        if (loadedCatalog.loading && filtered.groups.isEmpty()) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (filtered.groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.activity_shortcut_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
        } else {
            filtered.groups.forEach { group ->
                item(key = "group-${group.app.packageName}") {
                    Text(
                        text = group.app.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
                items(
                    items = group.shortcuts,
                    key = { sc ->
                        "${group.app.packageName}/${sc.shortcutId}/${sc.label}/${sc.intentUris}"
                    },
                ) { shortcut ->
                    val qlItem = shortcut.toQuickLauncherItem(group.app.packageName)
                    val managed = activityShortcutFromQuickLauncherItem(
                        qlItem.copy(label = shortcut.label.ifBlank { qlItem.label }),
                    )
                    val key = managed?.identityKey().orEmpty()
                    val alreadyAdded = key.isNotBlank() && key in existingIdentityKeys
                    Md3PickerListRow(
                        segmentIndex = 0,
                        segmentCount = 1,
                        title = shortcut.label,
                        subtitle = group.app.packageName,
                        selected = alreadyAdded,
                        onClick = {
                            if (!alreadyAdded && managed != null) {
                                onAddShortcut(managed)
                            }
                        },
                        leadingContent = {
                            Md3PickerIconLeading(
                                icon = Icons.AutoMirrored.Filled.Shortcut,
                                selected = alreadyAdded,
                            )
                        },
                        trailingMode = PickerTrailingMode.Toggle,
                    )
                }
            }
        }
    }
}
