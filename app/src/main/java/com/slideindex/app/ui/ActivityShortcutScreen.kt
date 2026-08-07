package com.slideindex.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.ActivityShortcutCatalog
import com.slideindex.app.activity.ActivityShortcutLauncher
import com.slideindex.app.activity.ActivityShortcutPreset
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixSettingsFab
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsHintText
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.util.PackageActivityResolver
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.preference.ArrowPreference

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActivityShortcutScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSaveShortcuts: (List<ActivityShortcut>) -> Unit,
    onAdd: () -> Unit,
    onOpenPresets: () -> Unit,
) {
    val context = LocalContext.current
    val appRepository = rememberAppRepository()
    var shortcuts by remember(settings.activityShortcuts) { mutableStateOf(settings.activityShortcuts) }
    var pendingDelete by remember { mutableStateOf<ActivityShortcut?>(null) }
    var renamingShortcut by remember { mutableStateOf<ActivityShortcut?>(null) }

    LaunchedEffect(Unit) {
        if (!appRepository.hasCachedApps()) {
            appRepository.loadApps()
        }
    }

    fun persist(items: List<ActivityShortcut>) {
        shortcuts = items
        onSaveShortcuts(items)
    }

    fun launchShortcut(shortcut: ActivityShortcut) {
        ActivityShortcutLauncher.launch(context, shortcut, settings)
    }

    ActivityShortcutDeleteDialog(
        target = pendingDelete,
        onDismiss = { pendingDelete = null },
        onConfirm = { target ->
            persist(shortcuts.filter { it.id != target.id })
            pendingDelete = null
        },
    )

    ActivityShortcutRenameDialog(
        target = renamingShortcut,
        onDismiss = { renamingShortcut = null },
        onConfirm = { target, trimmed ->
            persist(
                shortcuts.map { shortcut ->
                    if (shortcut.id == target.id) shortcut.copy(label = trimmed) else shortcut
                },
            )
            renamingShortcut = null
        },
    )

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.activity_shortcut_title),
        onBack = onBack,
        floatingActionButton = {
            MiuixSettingsFab(
                onClick = onAdd,
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.activity_shortcut_browse_custom),
            )
        },
    ) {
        item(key = "hint") {
            SettingsHintText(stringResource(R.string.activity_shortcut_hint))
        }

        item(key = "mine-title") {
            MiuixSmallTitle(
                text = stringResource(R.string.activity_shortcut_mine_title),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MiuixSmallTitleSectionTop),
            )
        }

        activityShortcutMineListItems(
            context = context,
            shortcuts = shortcuts,
            onLaunch = ::launchShortcut,
            onRename = { renamingShortcut = it },
            onDelete = { pendingDelete = it },
        )

        groupedCardItems(
            keyPrefix = "activity-shortcut-add",
            outerTopPadding = MiuixSmallTitleSectionTop,
            items = listOf(
                CardItem("presets") {
                    ArrowPreference(
                        title = stringResource(R.string.activity_shortcut_add_from_presets),
                        summary = stringResource(R.string.activity_shortcut_add_from_presets_sub),
                        onClick = onOpenPresets,
                    )
                },
                CardItem("browse") {
                    ArrowPreference(
                        title = stringResource(R.string.activity_shortcut_browse_custom),
                        summary = stringResource(R.string.activity_shortcut_browse_custom_sub),
                        onClick = onAdd,
                    )
                },
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActivityShortcutPresetsScreen(
    settings: AppSettings,
    shortcuts: List<ActivityShortcut>,
    onBack: () -> Unit,
    onSaveShortcuts: (List<ActivityShortcut>) -> Unit,
) {
    val context = LocalContext.current
    val appRepository = rememberAppRepository()
    var localShortcuts by remember(shortcuts) { mutableStateOf(shortcuts) }
    val presets = remember { ActivityShortcutCatalog.presets() }

    LaunchedEffect(Unit) {
        if (!appRepository.hasCachedApps()) {
            appRepository.loadApps()
        }
    }

    fun persist(items: List<ActivityShortcut>) {
        localShortcuts = items
        onSaveShortcuts(items)
    }

    fun addShortcut(shortcut: ActivityShortcut) {
        if (localShortcuts.any {
                it.packageName == shortcut.packageName &&
                    it.activityClassName == shortcut.activityClassName
            }
        ) {
            return
        }
        persist(localShortcuts + shortcut)
    }

    fun launchShortcut(shortcut: ActivityShortcut) {
        ActivityShortcutLauncher.launch(context, shortcut, settings)
    }

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.activity_shortcut_presets_title),
        onBack = onBack,
    ) {
        item(key = "presets-hint") {
            SettingsHintText(stringResource(R.string.activity_shortcut_presets_desc))
        }

        if (presets.isEmpty()) {
            item(key = "presets-empty") {
                Text(
                    text = stringResource(R.string.activity_shortcut_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            groupedCardItems(
                keyPrefix = "activity-shortcut-presets",
                outerTopPadding = MiuixSmallTitleSectionTop,
                items = presets.map { preset ->
                    val saved = localShortcuts.any {
                        it.packageName == preset.packageName &&
                            it.activityClassName == preset.activityClassName
                    }
                    CardItem("${preset.packageName}/${preset.activityClassName}") {
                        ActivityShortcutPresetRowContent(
                            preset = preset,
                            saved = saved,
                            onLaunch = { launchShortcut(preset.toShortcut()) },
                            onAdd = { addShortcut(preset.toShortcut()) },
                        )
                    }
                },
            )
        }
    }
}

private fun LazyListScope.activityShortcutMineListItems(
    context: android.content.Context,
    shortcuts: List<ActivityShortcut>,
    onLaunch: (ActivityShortcut) -> Unit,
    onRename: (ActivityShortcut) -> Unit,
    onDelete: (ActivityShortcut) -> Unit,
) {
    if (shortcuts.isEmpty()) {
        groupedCardItems(
            keyPrefix = "activity-shortcut-mine-empty",
            items = listOf(
                CardItem("empty") {
                    Text(
                        text = stringResource(R.string.activity_shortcut_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                },
            ),
        )
        return
    }
    groupedCardItems(
        keyPrefix = "activity-shortcut-mine",
        items = shortcuts.map { shortcut ->
            CardItem(shortcut.id) {
                val appLabel = remember(shortcut.packageName) {
                    resolveAppLabel(context, shortcut.packageName)
                }
                val exported = remember(shortcut.packageName, shortcut.activityClassName) {
                    PackageActivityResolver.isActivityExported(
                        context,
                        shortcut.packageName,
                        shortcut.activityClassName,
                    )
                }
                ActivityShortcutSavedRowContent(
                    shortcut = shortcut,
                    appLabel = appLabel,
                    exported = exported,
                    onLaunch = { onLaunch(shortcut) },
                    onRename = { onRename(shortcut) },
                    onDelete = { onDelete(shortcut) },
                )
            }
        },
    )
}

@Composable
private fun ActivityShortcutPresetRowContent(
    preset: ActivityShortcutPreset,
    saved: Boolean,
    onLaunch: () -> Unit,
    onAdd: () -> Unit,
) {
    val context = LocalContext.current
    val appLabel = remember(preset.packageName) {
        resolveAppLabel(context, preset.packageName)
    }
    val title = if (appLabel.isNotBlank()) {
        "$appLabel·${preset.label}"
    } else {
        preset.label
    }
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        title = title,
        summary = preset.activityClassName,
        onClick = onLaunch,
        startAction = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Launch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        endActions = {
            TextButton(onClick = onAdd, enabled = !saved) {
                Text(
                    text = stringResource(
                        if (saved) {
                            R.string.activity_shortcut_preset_added
                        } else {
                            R.string.activity_shortcut_preset_add
                        },
                    ),
                )
            }
        },
    )
}

@Composable
private fun ActivityShortcutSavedRowContent(
    shortcut: ActivityShortcut,
    appLabel: String,
    exported: Boolean,
    onLaunch: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember(shortcut.id) { mutableStateOf(false) }
    val subtitle = buildString {
        if (appLabel.isNotBlank()) {
            append(appLabel)
        } else {
            append(shortcut.packageName)
        }
        if (!exported) {
            append(" · ")
            append(stringResource(R.string.activity_shortcut_not_exported))
        }
    }
    val title = shortcut.label
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        title = title,
        summary = subtitle,
        onClick = onLaunch,
        startAction = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Launch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        endActions = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.notification_filter_more_menu),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.activity_shortcut_rename)) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.stash_action_delete)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun ActivityShortcutDeleteDialog(
    target: ActivityShortcut?,
    onDismiss: () -> Unit,
    onConfirm: (ActivityShortcut) -> Unit,
) {
    MiuixConfirmDialog(
        show = target != null,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.activity_shortcut_delete_title),
        message = target?.let {
            stringResource(R.string.activity_shortcut_delete_message, it.label)
        },
        confirmText = stringResource(R.string.search_engine_delete_confirm),
        onConfirm = {
            target?.let(onConfirm)
        },
    )
}

@Composable
private fun ActivityShortcutRenameDialog(
    target: ActivityShortcut?,
    onDismiss: () -> Unit,
    onConfirm: (ActivityShortcut, String) -> Unit,
) {
    target ?: return
    var label by remember(target.id) { mutableStateOf(target.label) }
    MiuixFormDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.activity_shortcut_rename_title),
        confirmEnabled = label.isNotBlank(),
        onConfirm = {
            val trimmed = label.trim()
            if (trimmed.isNotBlank()) {
                onConfirm(target, trimmed)
            } else {
                onDismiss()
            }
        },
    ) {
        MiuixLabeledTextField(
            value = label,
            onValueChange = { label = it },
            label = stringResource(R.string.activity_shortcut_name_hint),
        )
    }
}

private fun resolveAppLabel(context: android.content.Context, packageName: String): String =
    runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault("")

@Composable
fun SettingsCardScope.ActivityShortcutEntryCard(
    shortcutCount: Int,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label ->
            Icon(HubLeadingIcons.activityShortcut(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.activity_shortcut_entry_title),
        subtitle = if (shortcutCount > 0) {
            stringResource(R.string.activity_shortcut_entry_desc_count, shortcutCount)
        } else {
            stringResource(R.string.activity_shortcut_entry_desc)
        },
        onClick = onClick,
    )
}
