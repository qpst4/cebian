package com.slideindex.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsHintText
import com.slideindex.app.ui.settings.components.SettingsSectionTitle
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.util.PackageActivityResolver

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ActivityShortcutScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSaveShortcuts: (List<ActivityShortcut>) -> Unit,
    onAdd: () -> Unit,
) {
    val context = LocalContext.current
    val appRepository = rememberAppRepository()
    var shortcuts by remember(settings.activityShortcuts) { mutableStateOf(settings.activityShortcuts) }
    var pendingDelete by remember { mutableStateOf<ActivityShortcut?>(null) }

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

    fun addShortcut(shortcut: ActivityShortcut) {
        if (shortcuts.any {
                it.packageName == shortcut.packageName &&
                    it.activityClassName == shortcut.activityClassName
            }
        ) {
            return
        }
        persist(shortcuts + shortcut)
    }

    val deleteTarget = pendingDelete
    MiuixConfirmDialog(
        show = deleteTarget != null,
        onDismissRequest = { pendingDelete = null },
        title = stringResource(R.string.activity_shortcut_delete_title),
        message = deleteTarget?.let {
            stringResource(R.string.activity_shortcut_delete_message, it.label)
        },
        confirmText = stringResource(R.string.search_engine_delete_confirm),
        onConfirm = {
            deleteTarget?.let { target ->
                persist(shortcuts.filter { it.id != target.id })
                pendingDelete = null
            }
        },
    )

    val presets = remember { ActivityShortcutCatalog.presets() }
    SettingsLazyScreenScaffold(
        title = stringResource(R.string.activity_shortcut_title),
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.activity_shortcut_add))
            }
        },
    ) {
        item(key = "hint") {
            SettingsHintText(stringResource(R.string.activity_shortcut_hint))
        }

        item(key = "presets-title") {
            SettingsSectionTitle(stringResource(R.string.activity_shortcut_presets_title))
        }

        itemsIndexed(
            items = presets,
            key = { _, preset -> "preset-${preset.packageName}/${preset.activityClassName}" },
        ) { _, preset ->
            ActivityShortcutPresetRow(
                preset = preset,
                saved = shortcuts.any {
                    it.packageName == preset.packageName &&
                        it.activityClassName == preset.activityClassName
                },
                onLaunch = {
                    launchShortcut(preset.toShortcut())
                },
                onAdd = {
                    addShortcut(preset.toShortcut())
                },
            )
        }

        item(key = "mine-title") {
            SettingsSectionTitle(stringResource(R.string.activity_shortcut_mine_title))
        }

        if (shortcuts.isEmpty()) {
            item(key = "mine-empty") {
                Text(
                    text = stringResource(R.string.activity_shortcut_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            itemsIndexed(
                items = shortcuts,
                key = { _, shortcut -> shortcut.id },
            ) { _, shortcut ->
                val appLabel = remember(shortcut.packageName) {
                    runCatching {
                        val pm = context.packageManager
                        pm.getApplicationLabel(
                            pm.getApplicationInfo(shortcut.packageName, 0),
                        ).toString()
                    }.getOrDefault("")
                }
                val exported = remember(shortcut.packageName, shortcut.activityClassName) {
                    PackageActivityResolver.isActivityExported(
                        context,
                        shortcut.packageName,
                        shortcut.activityClassName,
                    )
                }
                ActivityShortcutSavedRow(
                    shortcut = shortcut,
                    appLabel = appLabel,
                    exported = exported,
                    onClick = { launchShortcut(shortcut) },
                    onLongClick = { pendingDelete = shortcut },
                )
            }
        }
    }
}

@Composable
private fun ActivityShortcutPresetRow(
    preset: ActivityShortcutPreset,
    saved: Boolean,
    onLaunch: () -> Unit,
    onAdd: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = label) },
        title = preset.label,
        subtitle = preset.activityClassName,
        onClick = onLaunch,
        trailingContent = {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivityShortcutSavedRow(
    shortcut: ActivityShortcut,
    appLabel: String,
    exported: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val subtitle = buildString {
        if (appLabel.isNotBlank()) {
            append(appLabel)
            append(" · ")
        }
        append(shortcut.activityClassName)
        if (!exported) {
            append(" · ")
            append(stringResource(R.string.activity_shortcut_not_exported))
        }
    }
    SettingNavigationRow(
        icon = { label -> Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = label) },
        title = shortcut.label,
        subtitle = subtitle,
        onClick = onClick,
        trailingContent = {
            TextButton(onClick = onLongClick) {
                Text(stringResource(R.string.stash_action_delete))
            }
        },
    )
}

@Composable
fun SettingsCardScope.ActivityShortcutEntryCard(
    shortcutCount: Int,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = label) },
        title = stringResource(R.string.activity_shortcut_entry_title),
        subtitle = if (shortcutCount > 0) {
            stringResource(R.string.activity_shortcut_entry_desc_count, shortcutCount)
        } else {
            stringResource(R.string.activity_shortcut_entry_desc)
        },
        onClick = onClick,
    )
}
