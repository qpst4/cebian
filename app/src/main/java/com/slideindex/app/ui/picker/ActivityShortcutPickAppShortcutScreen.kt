package com.slideindex.app.ui.picker

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.activityShortcutFromQuickLauncherItem
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem

private sealed interface ActivityShortcutPickAppShortcutSubScreen {
    data object Main : ActivityShortcutPickAppShortcutSubScreen
    data object PresetShortcuts : ActivityShortcutPickAppShortcutSubScreen
}

private fun ActivityShortcutPickAppShortcutSubScreen.navDepth(): Int = when (this) {
    ActivityShortcutPickAppShortcutSubScreen.Main -> 0
    ActivityShortcutPickAppShortcutSubScreen.PresetShortcuts -> 1
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActivityShortcutPickAppShortcutScreen(
    existingIdentityKeys: Set<String>,
    onBack: () -> Unit,
    onAddShortcut: (ActivityShortcut) -> Unit,
) {
    var subScreen by remember {
        mutableStateOf<ActivityShortcutPickAppShortcutSubScreen>(
            ActivityShortcutPickAppShortcutSubScreen.Main,
        )
    }
    val appRepository = rememberAppRepository()
    var apps by remember { mutableStateOf(appRepository.getCachedApps()) }
    var query by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (apps.isEmpty()) {
            apps = appRepository.loadApps(force = true)
        }
    }

    AnimatedContent(
        targetState = subScreen,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            pickerHorizontalSlideTransitionByDepth(
                ActivityShortcutPickAppShortcutSubScreen::navDepth,
            )
        },
        label = "activityShortcutPickAppShortcutSubNav",
    ) { currentSub ->
        when (currentSub) {
            ActivityShortcutPickAppShortcutSubScreen.PresetShortcuts -> {
                PresetShortcutsFolderScreen(
                    onBack = { subScreen = ActivityShortcutPickAppShortcutSubScreen.Main },
                    configuredShortcutKeys = existingIdentityKeys,
                )
            }
            ActivityShortcutPickAppShortcutSubScreen.Main -> {
                val loadedCatalog = rememberLoadedShortcutCatalog(apps = apps, enabled = true)
                val filtered = remember(loadedCatalog.catalog, query) {
                    filterShortcutCatalog(
                        loadedCatalog.catalog ?: AppShortcutLoader.ShortcutCatalog(emptyList()),
                        query,
                    )
                }
                val launchOnlyFiltered = remember(filtered) {
                    FilteredShortcutCatalog(createHosts = emptyList(), groups = filtered.groups)
                }
                val appsByPackage = remember(apps) { apps.associateBy { it.packageName } }

                SettingsLazyScreenScaffoldWithExpandableSearch(
                    title = stringResource(R.string.activity_shortcut_pick_app_shortcut_title),
                    searchQuery = query,
                    onSearchQueryChange = { query = it },
                    onBack = onBack,
                    hintResId = R.string.search_hint,
                ) {
                    if (query.isBlank()) {
                        shortcutFolderCardsSection(
                            activityShortcutsCount = 0,
                            onOpenMyShortcuts = {},
                            onOpenPresetShortcuts = {
                                subScreen =
                                    ActivityShortcutPickAppShortcutSubScreen.PresetShortcuts
                            },
                            showMyShortcuts = false,
                        )
                    }
                    when {
                        loadedCatalog.loading && launchOnlyFiltered.groups.isEmpty() -> {
                            systemShortcutCatalogItems(
                                filtered = launchOnlyFiltered,
                                appsByPackage = appsByPackage,
                                loading = true,
                                scanProgress = loadedCatalog.scanProgress,
                                loadingItemKey = "loading",
                                onCreateHostClick = {},
                                shortcutRowContent = { _, _, _, _ -> },
                            )
                        }
                        launchOnlyFiltered.groups.isEmpty() -> {
                            item(key = "empty") {
                                Text(
                                    text = stringResource(R.string.activity_shortcut_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                                )
                            }
                        }
                        else -> {
                            systemShortcutCatalogItems(
                                filtered = launchOnlyFiltered,
                                appsByPackage = appsByPackage,
                                loading = false,
                                scanProgress = null,
                                onCreateHostClick = {},
                                shortcutRowContent = { group, shortcut, segmentIndex, segmentCount ->
                                    ActivityShortcutPickAppShortcutRow(
                                        group = group,
                                        shortcut = shortcut,
                                        segmentIndex = segmentIndex,
                                        segmentCount = segmentCount,
                                        existingIdentityKeys = existingIdentityKeys,
                                        onAddShortcut = onAddShortcut,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityShortcutPickAppShortcutRow(
    group: AppShortcutLoader.AppShortcutGroup,
    shortcut: TaskSwitcherMenuItem,
    segmentIndex: Int,
    segmentCount: Int,
    existingIdentityKeys: Set<String>,
    onAddShortcut: (ActivityShortcut) -> Unit,
) {
    val qlItem = shortcut.toQuickLauncherItem(group.app.packageName)
    val managed = activityShortcutFromQuickLauncherItem(
        qlItem.copy(label = shortcut.label.ifBlank { qlItem.label }),
    )
    val key = managed?.identityKey().orEmpty()
    val alreadyAdded = key.isNotBlank() && key in existingIdentityKeys
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = shortcut.label,
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
