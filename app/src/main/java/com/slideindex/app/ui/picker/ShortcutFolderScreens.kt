package com.slideindex.app.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.subtitleDetail
import com.slideindex.app.activity.toLaunchShortcut
import com.slideindex.app.activity.toQuickLauncherItem
import com.slideindex.app.data.PresetShortcutAppGroup
import com.slideindex.app.data.PresetShortcutRepository
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.Md3PickerManagedShortcutLeading
import com.slideindex.app.ui.Md3PickerPackageLeading
import com.slideindex.app.ui.Md3PickerSectionHeader
import com.slideindex.app.ui.PickerListHorizontalPadding
import com.slideindex.app.ui.PickerListOverlayHorizontalPadding
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.pickerListSegmentedGap
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.widget.ShortcutEntry
import top.yukonga.miuix.kmp.preference.ArrowPreference

fun LazyListScope.shortcutFolderCardsSection(
    activityShortcutsCount: Int,
    onOpenMyShortcuts: () -> Unit,
    onOpenPresetShortcuts: () -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp = 12.dp,
    showMyShortcuts: Boolean = true,
) {
    val items = mutableListOf<CardItem>()
    if (showMyShortcuts) {
        items += CardItem("my-shortcuts") {
            ArrowPreference(
                title = "我的直达",
                summary = if (activityShortcutsCount > 0) {
                    "${activityShortcutsCount} 个自定义直达快捷方式"
                } else {
                    "自定义创建与管理的直达快捷方式"
                },
                onClick = onOpenMyShortcuts,
            )
        }
    }
    items += CardItem("preset-shortcuts") {
        ArrowPreference(
            title = "预设快捷方式库",
            summary = "内置精选直达规则 · 涵盖常用应用",
            onClick = onOpenPresetShortcuts,
        )
    }
    groupedCardItems(
        keyPrefix = "shortcut-folder-cards",
        items = items,
        outerTopPadding = 4.dp,
        outerBottomPadding = 10.dp,
        outerHorizontalPadding = horizontalPadding,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyShortcutsFolderScreen(
    activityShortcuts: List<ActivityShortcut>,
    onBack: () -> Unit,
    onBrowseNewShortcut: () -> Unit,
    currentAction: GestureAction? = null,
    onSelectRadio: ((GestureAction) -> Unit)? = null,
    configuredShortcutKeys: Set<String>? = null,
    onToggle: ((QuickLauncherItem, Boolean) -> Unit)? = null,
    onSelectShortcutEntry: ((ShortcutEntry) -> Unit)? = null,
    enableBackHandler: Boolean = true,
    overlayMode: Boolean = false,
    embedInParentChrome: Boolean = false,
    searchQuery: String = "",
) {
    var standaloneQuery by remember { mutableStateOf("") }
    val effectiveQuery = if (embedInParentChrome) searchQuery else standaloneQuery
    val q = effectiveQuery.trim().lowercase()
    val filtered = remember(activityShortcuts, q) {
        if (q.isEmpty()) activityShortcuts
        else activityShortcuts.filter {
            it.label.lowercase().contains(q) ||
                it.packageName.lowercase().contains(q) ||
                it.subtitleDetail().lowercase().contains(q)
        }
    }

    if (embedInParentChrome) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = if (overlayMode) PickerListOverlayHorizontalPadding else PickerListHorizontalPadding,
                end = if (overlayMode) PickerListOverlayHorizontalPadding else PickerListHorizontalPadding,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
        ) {
            myShortcutsListContent(
                filtered = filtered,
                currentAction = currentAction,
                onSelectRadio = onSelectRadio,
                configuredShortcutKeys = configuredShortcutKeys,
                onToggle = onToggle,
                onSelectShortcutEntry = onSelectShortcutEntry,
                onBrowseNewShortcut = onBrowseNewShortcut,
            )
        }
        return
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = "我的直达",
        searchQuery = standaloneQuery,
        onSearchQueryChange = { standaloneQuery = it },
        onBack = onBack,
        enableBackHandler = if (overlayMode) false else enableBackHandler,
        hintResId = R.string.search_hint,
    ) {
        myShortcutsListContent(
            filtered = filtered,
            currentAction = currentAction,
            onSelectRadio = onSelectRadio,
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = onToggle,
            onSelectShortcutEntry = onSelectShortcutEntry,
            onBrowseNewShortcut = onBrowseNewShortcut,
        )
    }
}

private fun LazyListScope.myShortcutsListContent(
    filtered: List<ActivityShortcut>,
    currentAction: GestureAction?,
    onSelectRadio: ((GestureAction) -> Unit)?,
    configuredShortcutKeys: Set<String>?,
    onToggle: ((QuickLauncherItem, Boolean) -> Unit)?,
    onSelectShortcutEntry: ((ShortcutEntry) -> Unit)?,
    onBrowseNewShortcut: () -> Unit,
) {
    item(key = "my-shortcuts-header") {
        Md3PickerSectionHeader("自定义快捷方式 (${filtered.size})")
    }

    val segmentCount = filtered.size + 1
    items(
        count = filtered.size,
        key = { "my_shortcut_${it}_${filtered[it].id}" },
    ) { index ->
        val shortcut = filtered[index]
        val action = shortcut.toLaunchShortcut()
        val item = shortcut.toQuickLauncherItem()
        val key = QuickLauncherItemCodec.shortcutItemKey(item).orEmpty()
        val added = configuredShortcutKeys?.let { key in it } ?: false
        val radioSelected = currentAction is GestureAction.LaunchShortcut && currentAction.payloadKey == action.payloadKey

        val trailingMode = when {
            onSelectRadio != null -> PickerTrailingMode.Radio
            onToggle != null -> PickerTrailingMode.Toggle
            else -> PickerTrailingMode.None
        }

        Md3PickerListRow(
            segmentIndex = index,
            segmentCount = segmentCount,
            title = shortcut.label,
            subtitle = shortcut.subtitleDetail(),
            selected = if (onSelectRadio != null) radioSelected else added,
            onClick = {
                if (onSelectRadio != null) {
                    onSelectRadio(action)
                } else if (onToggle != null) {
                    onToggle(item, added)
                } else if (onSelectShortcutEntry != null) {
                    onSelectShortcutEntry(
                        ShortcutEntry(
                            packageName = shortcut.packageName,
                            shortcutId = shortcut.id,
                            label = shortcut.label,
                            sortKey = shortcut.label,
                            initialKey = "",
                            iconBitmap = null,
                            intentUri = shortcut.intentUris.firstOrNull().orEmpty(),
                        )
                    )
                }
            },
            leadingContent = {
                Md3PickerManagedShortcutLeading(
                    shortcut = shortcut,
                    selected = if (onSelectRadio != null) radioSelected else added,
                )
            },
            trailingMode = trailingMode,
            onTrailingClick = if (onToggle != null) { { onToggle(item, added) } } else null,
        )
    }

    item(key = "my-shortcuts-browse-new") {
        Md3PickerListRow(
            segmentIndex = filtered.size,
            segmentCount = segmentCount,
            title = stringResource(R.string.activity_shortcut_browse),
            subtitle = stringResource(R.string.activity_shortcut_browse_hint),
            selected = false,
            onClick = onBrowseNewShortcut,
            leadingContent = {
                Md3PickerIconLeading(
                    icon = Icons.AutoMirrored.Filled.Shortcut,
                    selected = false,
                )
            },
            trailingMode = PickerTrailingMode.Icon,
            trailingIcon = Icons.AutoMirrored.Filled.Shortcut,
            trailingIconDescription = stringResource(R.string.activity_shortcut_browse),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PresetShortcutsFolderScreen(
    onBack: () -> Unit,
    currentAction: GestureAction? = null,
    onSelectRadio: ((GestureAction) -> Unit)? = null,
    configuredShortcutKeys: Set<String>? = null,
    onToggle: ((QuickLauncherItem, Boolean) -> Unit)? = null,
    onSelectShortcutEntry: ((ShortcutEntry) -> Unit)? = null,
    enableBackHandler: Boolean = true,
    overlayMode: Boolean = false,
    embedInParentChrome: Boolean = false,
    searchQuery: String = "",
) {
    val context = LocalContext.current
    var presetGroups by remember { mutableStateOf<List<PresetShortcutAppGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var standaloneQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        presetGroups = PresetShortcutRepository.loadGroups(context)
        loading = false
    }

    val effectiveQuery = if (embedInParentChrome) searchQuery else standaloneQuery
    val filtered = remember(presetGroups, effectiveQuery) {
        PresetShortcutRepository.filterGroups(presetGroups, effectiveQuery)
    }

    if (embedInParentChrome) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "未找到匹配的预设快捷方式",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = if (overlayMode) PickerListOverlayHorizontalPadding else PickerListHorizontalPadding,
                    end = if (overlayMode) PickerListOverlayHorizontalPadding else PickerListHorizontalPadding,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
            ) {
                presetShortcutsListContent(
                    filtered = filtered,
                    currentAction = currentAction,
                    onSelectRadio = onSelectRadio,
                    configuredShortcutKeys = configuredShortcutKeys,
                    onToggle = onToggle,
                    onSelectShortcutEntry = onSelectShortcutEntry,
                )
            }
        }
        return
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = "预设快捷方式库",
        searchQuery = standaloneQuery,
        onSearchQueryChange = { standaloneQuery = it },
        onBack = onBack,
        enableBackHandler = if (overlayMode) false else enableBackHandler,
        hintResId = R.string.widget_picker_search_hint,
    ) {
        if (loading) {
            item(key = "preset_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (filtered.isEmpty()) {
            item(key = "preset_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "未找到匹配的预设快捷方式",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            presetShortcutsListContent(
                filtered = filtered,
                currentAction = currentAction,
                onSelectRadio = onSelectRadio,
                configuredShortcutKeys = configuredShortcutKeys,
                onToggle = onToggle,
                onSelectShortcutEntry = onSelectShortcutEntry,
            )
        }
    }
}

private fun LazyListScope.presetShortcutsListContent(
    filtered: List<PresetShortcutAppGroup>,
    currentAction: GestureAction?,
    onSelectRadio: ((GestureAction) -> Unit)?,
    configuredShortcutKeys: Set<String>?,
    onToggle: ((QuickLauncherItem, Boolean) -> Unit)?,
    onSelectShortcutEntry: ((ShortcutEntry) -> Unit)?,
) {
    filtered.forEachIndexed { groupIndex, group ->
        item(key = "preset_header_${group.packageName}_${group.appLabel}_$groupIndex") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = group.appLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${group.shortcuts.size} 项",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val segmentCount = group.shortcuts.size
        items(
            count = group.shortcuts.size,
            key = { itemIndex -> "preset_item_${groupIndex}_${itemIndex}_${group.shortcuts[itemIndex].name}_${group.shortcuts[itemIndex].targetActionUrl.hashCode()}" },
        ) { index ->
            val item = group.shortcuts[index]
            val shortcutLabel = "${group.appLabel} - ${item.name}"
            val qlItem = QuickLauncherItem.intentShortcut(
                intentUri = item.targetActionUrl,
                label = shortcutLabel,
                hostPackage = group.packageName,
            )
            val key = QuickLauncherItemCodec.shortcutItemKey(qlItem).orEmpty()
            val added = configuredShortcutKeys?.let { key in it } ?: false

            val action = GestureAction.LaunchShortcut.intent(item.targetActionUrl, shortcutLabel)
            val radioSelected = currentAction is GestureAction.LaunchShortcut && currentAction.payloadKey == action.payloadKey

            val trailingMode = when {
                onSelectRadio != null -> PickerTrailingMode.Radio
                onToggle != null -> PickerTrailingMode.Toggle
                else -> PickerTrailingMode.None
            }

            Md3PickerListRow(
                segmentIndex = index,
                segmentCount = segmentCount,
                title = item.name,
                subtitle = item.introduction.ifBlank { item.targetActionUrl },
                selected = if (onSelectRadio != null) radioSelected else added,
                onClick = {
                    if (onSelectRadio != null) {
                        onSelectRadio(action)
                    } else if (onToggle != null) {
                        onToggle(qlItem, added)
                    } else if (onSelectShortcutEntry != null) {
                        onSelectShortcutEntry(
                            ShortcutEntry(
                                packageName = group.packageName,
                                shortcutId = "preset_${item.name.hashCode()}",
                                label = shortcutLabel,
                                sortKey = item.pinyinName,
                                initialKey = item.initialName,
                                iconBitmap = null,
                                intentUri = item.targetActionUrl,
                            )
                        )
                    }
                },
                leadingContent = {
                    Md3PickerPackageLeading(
                        packageName = group.packageName,
                        contentDescription = item.name,
                        selected = if (onSelectRadio != null) radioSelected else added,
                    )
                },
                trailingMode = trailingMode,
                onTrailingClick = if (onToggle != null) { { onToggle(qlItem, added) } } else null,
            )
        }
    }
}
